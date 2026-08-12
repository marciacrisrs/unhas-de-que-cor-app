package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import br.com.unhasdequecor.data.vision.HandLandmarkProcessor
import br.com.unhasdequecor.data.vision.HandLandmarks
import javax.inject.Inject
import javax.inject.Singleton

data class NailTryOnResult(
    val bitmap: Bitmap,
    val nails: List<DetectedNail>,
    val landmarks: HandLandmarks?,
    val debugEnabled: Boolean,
)

/**
 * Detecção estável (landmarks + máscaras) reutilizável quando só a cor do esmalte muda.
 */
data class NailDetectionSnapshot(
    val workingBitmap: Bitmap,
    val nails: List<DetectedNail>,
    val landmarks: HandLandmarks?,
    /** True se [workingBitmap] é bitmap intermediária (ex.: rotação) distinta da fonte. */
    val ownsWorkingBitmap: Boolean,
    val reliability: TryOnReliability = TryOnReliability.STRONG,
)

/**
 * Pipeline: landmarks → ROI → segmentação → tracking → cor.
 * Pronto para câmera ao vivo; hoje usado na foto estática do Resultado.
 *
 * Separe [detect] de [recolor] para não reprocessar MediaPipe a cada troca de cor.
 */
@Singleton
class NailTryOnPipeline @Inject constructor(
    private val landmarkProcessor: HandLandmarkProcessor,
    private val roiEstimator: NailRoiEstimator,
    private val segmenter: NailSegmenter,
    private val colorApplier: NailColorApplier,
    private val tracker: NailTracker,
) {
    @Volatile
    var debugEnabled: Boolean = false

    fun resetTracking() {
        tracker.reset()
    }

    fun process(
        image: Bitmap,
        polishColor: Color,
        stabilize: Boolean = false,
    ): NailTryOnResult? {
        val snapshot = detect(image, stabilize) ?: return null
        val result = recolor(snapshot, polishColor)
        // process() é one-shot: se a pintura gerou bitmap nova e working era intermediária, libera.
        if (result.bitmap !== snapshot.workingBitmap &&
            snapshot.ownsWorkingBitmap &&
            !snapshot.workingBitmap.isRecycled
        ) {
            snapshot.workingBitmap.recycle()
        }
        return result
    }

    fun detect(
        image: Bitmap,
        stabilize: Boolean = false,
    ): NailDetectionSnapshot? {
        val oriented = landmarkProcessor.detectLandmarksWithOrientationFallback(image)
            ?: return null
        val landmarks = oriented.landmarks
        val working = oriented.bitmap

        val rois = roiEstimator.estimateAll(landmarks)
        val detected = rois.mapNotNull { roi ->
            if (roi.geometricConfidence < MIN_ROI_CONFIDENCE) return@mapNotNull null
            val mask = segmenter.segment(working, roi) ?: return@mapNotNull null
            val segScore = segmentationConfidence(mask, roi)
            val confidence = (GEO_WEIGHT * roi.geometricConfidence + SEG_WEIGHT * segScore)
                .coerceIn(0f, 1f)
            if (confidence < NailColorApplier.MIN_CONFIDENCE) return@mapNotNull null
            DetectedNail(
                finger = roi.finger,
                roi = roi,
                mask = mask,
                confidence = confidence,
            )
        }
        val nails = if (stabilize) tracker.stabilize(detected) else {
            tracker.reset()
            detected
        }
        val reliability = TryOnHandReliability.classify(landmarks, nails.size)
        if (reliability == TryOnReliability.REJECTED) {
            if (working !== image && !working.isRecycled) {
                working.recycle()
            }
            return null
        }
        return NailDetectionSnapshot(
            workingBitmap = working,
            nails = nails,
            landmarks = landmarks,
            ownsWorkingBitmap = working !== image,
            reliability = reliability,
        )
    }

    fun recolor(
        snapshot: NailDetectionSnapshot,
        polishColor: Color,
    ): NailTryOnResult {
        val working = snapshot.workingBitmap
        // ≥3 máscaras: almond; 0 máscaras: elipse pelos landmarks; 1–2: tenta máscara e completa com elipse.
        val painted = when {
            snapshot.nails.size >= MIN_NAILS_FOR_MASK_PATH -> {
                colorApplier.apply(working, snapshot.nails, polishColor)
                    ?: ellipseFallback(working, snapshot.landmarks, polishColor)
                    ?: working
            }
            snapshot.nails.isEmpty() -> {
                ellipseFallback(working, snapshot.landmarks, polishColor) ?: working
            }
            else -> {
                colorApplier.apply(working, snapshot.nails, polishColor)
                    ?: ellipseFallback(working, snapshot.landmarks, polishColor)
                    ?: working
            }
        }
        return NailTryOnResult(
            bitmap = painted,
            nails = snapshot.nails,
            landmarks = snapshot.landmarks,
            debugEnabled = debugEnabled,
        )
    }

    private fun ellipseFallback(
        image: Bitmap,
        landmarks: HandLandmarks?,
        polishColor: Color,
    ): Bitmap? {
        if (landmarks == null) return null
        val anchors = NailLandmarkMapper.fromNormalizedLandmarks(
            landmarks = landmarks.points.map {
                NailLandmarkMapper.NormalizedPoint(it.x, it.y)
            },
            imageWidth = landmarks.imageWidth,
            imageHeight = landmarks.imageHeight,
        ) ?: return null
        return DetectedNailPolishApplier.apply(image, anchors, polishColor)
    }

    private fun segmentationConfidence(mask: NailMask, roi: NailRoi): Float {
        val filled = mask.filledRatio()
        // Cobertura relativa à área da placa (não ao crop com padding).
        val plateArea = (roi.lengthPx * roi.widthPx).coerceAtLeast(1f)
        val solidPixels = mask.width * mask.height * filled
        val coverage = (solidPixels / plateArea).coerceIn(0f, COVERAGE_CLAMP)
        return when {
            filled < FILL_TOO_LOW -> SCORE_VERY_LOW
            // Soft almond + pad costuma encher o crop; não trate isso como pele.
            filled > FILL_TOO_HIGH && coverage < SKIN_RISK_COVERAGE_MAX -> SCORE_SKIN_RISK
            coverage in COVERAGE_GOOD -> SCORE_HIGH
            coverage in COVERAGE_OK -> SCORE_MID
            else -> SCORE_LOW
        }
    }

    private companion object {
        const val MIN_ROI_CONFIDENCE = 0.24f
        const val MIN_NAILS_FOR_MASK_PATH = 3
        // Prioriza geometria: unhas naturais falham em heurística de pele.
        const val GEO_WEIGHT = 0.65f
        const val SEG_WEIGHT = 0.35f
        const val COVERAGE_CLAMP = 1.8f
        const val FILL_TOO_LOW = 0.03f
        const val FILL_TOO_HIGH = 0.92f
        const val SKIN_RISK_COVERAGE_MAX = 0.35f
        const val SCORE_VERY_LOW = 0.15f
        const val SCORE_SKIN_RISK = 0.50f
        const val SCORE_HIGH = 0.92f
        const val SCORE_MID = 0.75f
        const val SCORE_LOW = 0.50f
        val COVERAGE_GOOD = 0.25f..1.4f
        val COVERAGE_OK = 0.14f..1.6f
    }
}
