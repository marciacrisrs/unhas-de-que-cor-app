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
 * Pipeline: landmarks → ROI → segmentação → tracking → cor.
 * Pronto para câmera ao vivo; hoje usado na foto estática do Resultado.
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
        // Almond suave (ROI alargada) primeiro; elipse+Recolorer se a geo falhar.
        val painted = colorApplier.apply(working, nails, polishColor)
            ?: ellipseFallback(working, landmarks, polishColor)
            ?: working
        // Se a pintura gerou bitmap nova e `working` era rotação intermediária, libera.
        if (painted !== working && working !== image && !working.isRecycled) {
            working.recycle()
        }
        return NailTryOnResult(
            bitmap = painted,
            nails = nails,
            landmarks = landmarks,
            debugEnabled = debugEnabled,
        )
    }

    private fun ellipseFallback(
        image: Bitmap,
        landmarks: HandLandmarks,
        polishColor: Color,
    ): Bitmap? {
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
        val area = (roi.lengthPx * roi.widthPx).coerceAtLeast(1f)
        val maskArea = mask.width * mask.height * filled
        val coverage = (maskArea / area).coerceIn(0f, COVERAGE_CLAMP)
        return when {
            filled < FILL_TOO_LOW -> SCORE_VERY_LOW
            filled > FILL_TOO_HIGH -> SCORE_SKIN_RISK // ROI quase toda preenchida: risco de pele
            coverage in COVERAGE_GOOD -> SCORE_HIGH
            coverage in COVERAGE_OK -> SCORE_MID
            else -> SCORE_LOW
        }
    }

    private companion object {
        const val MIN_ROI_CONFIDENCE = 0.28f
        // Prioriza geometria: unhas naturais falham em heurística de pele.
        const val GEO_WEIGHT = 0.70f
        const val SEG_WEIGHT = 0.30f
        const val COVERAGE_CLAMP = 1.5f
        const val FILL_TOO_LOW = 0.04f
        const val FILL_TOO_HIGH = 0.90f
        const val SCORE_VERY_LOW = 0.15f
        const val SCORE_SKIN_RISK = 0.55f
        const val SCORE_HIGH = 0.9f
        const val SCORE_MID = 0.7f
        const val SCORE_LOW = 0.45f
        val COVERAGE_GOOD = 0.20f..1.2f
        val COVERAGE_OK = 0.12f..1.4f
    }
}
