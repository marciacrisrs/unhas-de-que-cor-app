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
    /** True se a cor veio de elipse (não máscara) — UI não deve claim FULL. */
    val paintedViaEllipse: Boolean = false,
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
    val reliability: TryOnReliability,
    /**
     * Motivo tipado quando a detecção é fraca / rejeitada (feedback ISSUE 005).
     * Null em caminhos FULL saudáveis.
     */
    val failureReason: DetectionFailureReason? = null,
    /** Barreira do floor que limitou o resultado (rastreio / testes). */
    val rejectionBarrier: RejectionBarrier = RejectionBarrier.NONE,
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
        if (snapshot.reliability == TryOnReliability.REJECTED) {
            if (snapshot.ownsWorkingBitmap &&
                snapshot.workingBitmap !== image &&
                !snapshot.workingBitmap.isRecycled
            ) {
                snapshot.workingBitmap.recycle()
            }
            return null
        }
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
        val ownsWorking = working !== image

        // Reject precoce: não gasta ROI/segmentação em presence abaixo do piso.
        // Mantém snapshot REJECTED (com motivo) para feedback tipado na UI.
        val reliability = TryOnHandReliability.classify(landmarks)
        val lighting = ImageLightingSampler.sample(working)
        if (reliability == TryOnReliability.REJECTED) {
            return rejectedSnapshot(working, landmarks, ownsWorking, lighting)
        }

        val segmented = segmentPaintableNails(working, landmarks)
        val rawNails = if (stabilize) tracker.stabilize(segmented.nails) else {
            tracker.reset()
            segmented.nails
        }
        val nails = DetectionConfidenceFloor.filterPaintable(rawNails)
        val adjusted = adjustReliability(reliability, nails)
        val barrier = resolveBarrier(
            nailsEmpty = nails.isEmpty(),
            droppedByRoi = segmented.droppedByRoi,
            droppedByNail = segmented.droppedByNail,
            hadRois = segmented.hadRois,
            detectedEmpty = segmented.nails.isEmpty(),
        )
        return NailDetectionSnapshot(
            workingBitmap = working,
            nails = nails,
            landmarks = landmarks,
            ownsWorkingBitmap = ownsWorking,
            reliability = adjusted,
            failureReason = reasonFor(adjusted, landmarks, nails, barrier, lighting),
            rejectionBarrier = barrier,
        )
    }

    private fun rejectedSnapshot(
        working: Bitmap,
        landmarks: HandLandmarks,
        ownsWorking: Boolean,
        lighting: ImageLightingSampler.Stats?,
    ): NailDetectionSnapshot {
        val reason = DetectionFailureDiagnostics.fromLandmarks(
            landmarks = landmarks,
            reliability = TryOnReliability.REJECTED,
            barrier = RejectionBarrier.HAND_PRESENCE,
            meanLuminance = lighting?.meanLuminance,
            highlightShare = lighting?.highlightShare,
        )
        return NailDetectionSnapshot(
            workingBitmap = working,
            nails = emptyList(),
            landmarks = landmarks,
            ownsWorkingBitmap = ownsWorking,
            reliability = TryOnReliability.REJECTED,
            failureReason = reason,
            rejectionBarrier = RejectionBarrier.HAND_PRESENCE,
        )
    }

    private data class SegmentedNails(
        val nails: List<DetectedNail>,
        val droppedByRoi: Int,
        val droppedByNail: Int,
        val hadRois: Boolean,
    )

    private fun segmentPaintableNails(
        working: Bitmap,
        landmarks: HandLandmarks,
    ): SegmentedNails {
        val rois = roiEstimator.estimateAll(landmarks)
        var droppedByRoi = 0
        var droppedByNail = 0
        val detected = rois.mapNotNull { roi ->
            if (!DetectionConfidenceFloor.acceptsRoi(roi.geometricConfidence)) {
                droppedByRoi += 1
                return@mapNotNull null
            }
            val mask = segmenter.segment(working, roi) ?: return@mapNotNull null
            val segScore = segmentationConfidence(mask, roi)
            val confidence = (GEO_WEIGHT * roi.geometricConfidence + SEG_WEIGHT * segScore)
                .coerceIn(0f, 1f)
            if (!DetectionConfidenceFloor.acceptsNail(confidence)) {
                droppedByNail += 1
                return@mapNotNull null
            }
            DetectedNail(
                finger = roi.finger,
                roi = roi,
                mask = mask,
                confidence = confidence,
            )
        }
        return SegmentedNails(
            nails = detected,
            droppedByRoi = droppedByRoi,
            droppedByNail = droppedByNail,
            hadRois = rois.isNotEmpty(),
        )
    }

    private fun adjustReliability(
        reliability: TryOnReliability,
        nails: List<DetectedNail>,
    ): TryOnReliability {
        // Uma única unha paintable (assimétrico vs mapper ≥2): não claim STRONG.
        val demote =
            reliability == TryOnReliability.STRONG &&
                nails.size in 1 until NailLandmarkMapper.MIN_PLAUSIBLE_NAILS &&
                !DetectionConfidenceFloor.meetsFullNailFloor(nails)
        return if (demote) TryOnReliability.WEAK else reliability
    }

    private fun resolveBarrier(
        nailsEmpty: Boolean,
        droppedByRoi: Int,
        droppedByNail: Int,
        hadRois: Boolean,
        detectedEmpty: Boolean,
    ): RejectionBarrier = when {
        nailsEmpty && droppedByRoi > 0 && detectedEmpty -> RejectionBarrier.ROI
        nailsEmpty && (droppedByNail > 0 || hadRois) -> RejectionBarrier.NAIL_COMBINED
        else -> RejectionBarrier.NONE
    }

    private fun reasonFor(
        reliability: TryOnReliability,
        landmarks: HandLandmarks,
        nails: List<DetectedNail>,
        barrier: RejectionBarrier,
        lighting: ImageLightingSampler.Stats?,
    ): DetectionFailureReason? {
        if (reliability == TryOnReliability.STRONG &&
            DetectionConfidenceFloor.meetsFullNailFloor(nails)
        ) {
            return null
        }
        val hasMappable = NailLandmarkMapper.fromNormalizedLandmarks(
            landmarks = landmarks.points.map {
                NailLandmarkMapper.NormalizedPoint(it.x, it.y)
            },
            imageWidth = landmarks.imageWidth,
            imageHeight = landmarks.imageHeight,
        ) != null
        return DetectionFailureDiagnostics.fromLandmarks(
            landmarks = landmarks,
            reliability = reliability,
            barrier = barrier,
            paintableNailCount = nails.size,
            hasMappableAnchors = hasMappable,
            meanLuminance = lighting?.meanLuminance,
            highlightShare = lighting?.highlightShare,
        )
    }

    fun recolor(
        snapshot: NailDetectionSnapshot,
        polishColor: Color,
    ): NailTryOnResult {
        val working = snapshot.workingBitmap
        if (snapshot.reliability == TryOnReliability.REJECTED) {
            return NailTryOnResult(
                bitmap = working,
                nails = emptyList(),
                landmarks = snapshot.landmarks,
                debugEnabled = debugEnabled,
                paintedViaEllipse = false,
            )
        }
        // Caminho almond vs elipse: usa piso de pintura, não o de claim FULL.
        // Sem unhas paintable: não pintar elipse aqui — a UI decide via planRender.
        val paintableCount = DetectionConfidenceFloor.countPaintable(snapshot.nails)
        val (painted, viaEllipse) = when {
            paintableCount >= DetectionConfidenceFloor.MIN_PAINTABLE_FOR_MASK_PATH -> {
                val maskPaint = colorApplier.apply(working, snapshot.nails, polishColor)
                if (maskPaint != null) {
                    maskPaint to false
                } else {
                    val ellipse = ellipseFallback(working, snapshot.landmarks, polishColor)
                    (ellipse ?: working) to (ellipse != null)
                }
            }
            snapshot.nails.isEmpty() -> working to false
            else -> {
                val maskPaint = colorApplier.apply(working, snapshot.nails, polishColor)
                if (maskPaint != null) {
                    maskPaint to false
                } else {
                    val ellipse = ellipseFallback(working, snapshot.landmarks, polishColor)
                    (ellipse ?: working) to (ellipse != null)
                }
            }
        }
        return NailTryOnResult(
            bitmap = painted,
            nails = snapshot.nails,
            landmarks = snapshot.landmarks,
            debugEnabled = debugEnabled,
            paintedViaEllipse = viaEllipse,
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
