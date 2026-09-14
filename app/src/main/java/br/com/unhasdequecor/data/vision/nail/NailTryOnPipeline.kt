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
    val diagnostics: List<NailMaskDiagnostic> = emptyList(),
)

data class NailDetectionSnapshot(
    val workingBitmap: Bitmap,
    val nails: List<DetectedNail>,
    val landmarks: HandLandmarks?,
    val ownsWorkingBitmap: Boolean,
    val reliability: TryOnReliability,
    val failureReason: DetectionFailureReason? = null,
    val rejectionBarrier: RejectionBarrier = RejectionBarrier.NONE,
    val diagnostics: List<NailMaskDiagnostic> = emptyList(),
)

/** Pipeline: landmarks → ROI → segmentação → tracking → cor. */
@Singleton
class NailTryOnPipeline @Inject constructor(
    private val landmarkProcessor: HandLandmarkProcessor,
    private val roiEstimator: NailRoiEstimator,
    private val segmenter: NailSegmenter,
    private val colorApplier: NailColorApplier,
    private val tracker: NailTracker,
    private val pipelineMetrics: TryOnPipelineMetrics = TryOnPipelineMetrics(),
) {
    @Volatile
    var debugEnabled: Boolean = false

    fun resetTracking() = tracker.reset()

    fun metricsSnapshot(): TryOnPipelineMetricsSnapshot = pipelineMetrics.snapshot()

    fun process(image: Bitmap, polishColor: Color, stabilize: Boolean = false): NailTryOnResult? {
        val snapshot = detect(image, stabilize) ?: return null
        if (snapshot.reliability == TryOnReliability.REJECTED) {
            if (snapshot.ownsWorkingBitmap && snapshot.workingBitmap !== image && !snapshot.workingBitmap.isRecycled) {
                snapshot.workingBitmap.recycle()
            }
            return null
        }
        val result = recolor(snapshot, polishColor)
        if (result.bitmap !== snapshot.workingBitmap && snapshot.ownsWorkingBitmap && !snapshot.workingBitmap.isRecycled) {
            snapshot.workingBitmap.recycle()
        }
        return result
    }

    fun detect(image: Bitmap, stabilize: Boolean = false): NailDetectionSnapshot? {
        val frameStartNs = System.nanoTime()
        if (!stabilize) tracker.reset()

        val mediaPipeStartNs = System.nanoTime()
        val oriented = landmarkProcessor.detectLandmarksWithOrientationFallback(image)
        val mediaPipeMs = elapsedMs(mediaPipeStartNs)
        if (oriented == null) {
            recordMetrics(MetricsRecordInput(
                frameStartNs = frameStartNs,
                mediaPipeMs = mediaPipeMs,
                stabilized = stabilize,
                failureReason = DetectionFailureReason.Generic,
            ))
            return null
        }

        val landmarks = oriented.landmarks
        val working = oriented.bitmap
        val ownsWorking = working !== image
        val reliability = TryOnHandReliability.classify(landmarks)
        val lighting = ImageLightingSampler.sample(working)
        if (reliability == TryOnReliability.REJECTED) {
            val snapshot = rejectedSnapshot(working, landmarks, ownsWorking, lighting)
            recordMetrics(MetricsRecordInput(
                frameStartNs = frameStartNs,
                mediaPipeMs = mediaPipeMs,
                stabilized = stabilize,
                failureReason = snapshot.failureReason,
                rejectionBarrier = snapshot.rejectionBarrier,
            ))
            return snapshot
        }

        val segmentationStartNs = System.nanoTime()
        val segmented = segmentPaintableNails(working, landmarks)
        val segmentationMs = elapsedMs(segmentationStartNs)

        val trackingStartNs = System.nanoTime()
        val rawNails = if (stabilize) tracker.stabilize(segmented.nails) else segmented.nails
        val trackingMs = elapsedMs(trackingStartNs)
        val nails = DetectionConfidenceFloor.filterPaintable(rawNails)
        val diagnostics = segmented.diagnostics.map { diagnostic ->
            val surviving = nails.firstOrNull { it.finger == diagnostic.finger }
            if (surviving != null) NailMaskDiagnostics.from(surviving) else diagnostic
        }
        val adjusted = adjustReliability(reliability, nails)
        val barrier = resolveBarrier(
            nailsEmpty = nails.isEmpty(),
            droppedByRoi = segmented.droppedByRoi,
            droppedByNail = segmented.droppedByNail,
            droppedBySegmentation = segmented.droppedBySegmentation,
            droppedByGuard = segmented.droppedByGuard,
            hadRois = segmented.hadRois,
            detectedEmpty = segmented.nails.isEmpty(),
        )
        val failureReason = reasonFor(adjusted, landmarks, nails, barrier, lighting)
        val snapshot = NailDetectionSnapshot(
            workingBitmap = working,
            nails = nails,
            landmarks = landmarks,
            ownsWorkingBitmap = ownsWorking,
            reliability = adjusted,
            failureReason = failureReason,
            rejectionBarrier = barrier,
            diagnostics = diagnostics,
        )
        recordMetrics(MetricsRecordInput(
            frameStartNs = frameStartNs,
            mediaPipeMs = mediaPipeMs,
            segmentationMs = segmentationMs,
            trackingMs = trackingMs,
            stabilized = stabilize,
            nails = nails.size,
            failureReason = failureReason,
            rejectionBarrier = barrier,
            diagnosticCount = diagnostics.size,
            roiRejected = segmented.droppedByRoi,
            segmentationRejected = segmented.droppedBySegmentation,
            guardRejected = segmented.droppedByGuard,
            confidenceRejected = segmented.droppedByNail,
        ))
        return snapshot
    }

    private data class MetricsRecordInput(
        val frameStartNs: Long,
        val mediaPipeMs: Float,
        val segmentationMs: Float = 0f,
        val trackingMs: Float = 0f,
        val stabilized: Boolean,
        val nails: Int = 0,
        val failureReason: DetectionFailureReason? = null,
        val rejectionBarrier: RejectionBarrier = RejectionBarrier.NONE,
        val diagnosticCount: Int = 0,
        val roiRejected: Int = 0,
        val segmentationRejected: Int = 0,
        val guardRejected: Int = 0,
        val confidenceRejected: Int = 0,
    )

    private fun recordMetrics(input: MetricsRecordInput) {
        val report = tracker.lastPredictionReport
        pipelineMetrics.record(TryOnPipelineMetricsSample(
            totalMs = elapsedMs(input.frameStartNs),
            mediaPipeMs = input.mediaPipeMs,
            segmentationMs = input.segmentationMs,
            trackingMs = input.trackingMs,
            stabilized = input.stabilized,
            nailsDetected = input.nails,
            predictionApplied = report.predictionApplied,
            predictionReason = report.predictionReason,
            failureReason = input.failureReason,
            rejectionBarrier = input.rejectionBarrier,
            diagnosticCount = input.diagnosticCount,
            roiRejected = input.roiRejected,
            segmentationRejected = input.segmentationRejected,
            guardRejected = input.guardRejected,
            confidenceRejected = input.confidenceRejected,
        ))
    }

    private fun elapsedMs(startNs: Long): Float = (System.nanoTime() - startNs) / NANOS_PER_MILLISECOND

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
        val diagnostics: List<NailMaskDiagnostic>,
        val droppedByRoi: Int,
        val droppedByNail: Int,
        val droppedBySegmentation: Int,
        val droppedByGuard: Int,
        val hadRois: Boolean,
    )

    private fun segmentPaintableNails(working: Bitmap, landmarks: HandLandmarks): SegmentedNails {
        val estimation = roiEstimator.estimateAllWithDiagnostics(landmarks)
        val rois = estimation.rois
        var droppedByRoi = estimation.rejected.size
        var droppedByNail = 0
        var droppedBySegmentation = 0
        var droppedByGuard = 0
        val diagnostics = estimation.rejected.map { rejection ->
            NailMaskDiagnostic(
                finger = rejection.finger,
                roiWidthPx = 0f,
                roiLengthPx = 0f,
                filledRatio = 0f,
                coverageRatio = 0f,
                confidence = 0f,
                hasBoundaryPolygon = false,
                classification = MaskDiagnosticClassification.ROI_GEOMETRY,
                stage = NailDiagnosticStage.ROI_REJECTED,
                rejectionReason = rejection.reason,
                geometricConfidence = 0f,
                segmentationConfidence = 0f,
                postGuardFilledRatio = 0f,
            )
        }.toMutableList()
        val detected = rois.mapNotNull { roi ->
            if (!DetectionConfidenceFloor.acceptsRoi(roi.geometricConfidence)) {
                droppedByRoi += 1
                diagnostics += NailMaskDiagnostics.rejectedRoi(roi)
                return@mapNotNull null
            }

            val rawMask = segmenter.segment(working, roi)
            if (rawMask == null) {
                droppedBySegmentation += 1
                diagnostics += NailMaskDiagnostics.rejectedSegmentation(roi)
                return@mapNotNull null
            }

            val rawFill = rawMask.filledRatio()
            val mask = NailPlateMaskBoundaryGuard.clamp(rawMask, roi)
            val guardedFill = mask.filledRatio()
            if (guardedFill < MIN_POST_GUARD_FILL) {
                droppedByGuard += 1
                diagnostics += NailMaskDiagnostics.rejectedBoundaryGuard(roi, rawFill, guardedFill)
                return@mapNotNull null
            }

            val segScore = segmentationConfidence(mask, roi)
            val confidence = (GEO_WEIGHT * roi.geometricConfidence + SEG_WEIGHT * segScore).coerceIn(0f, 1f)
            if (!DetectionConfidenceFloor.acceptsNail(confidence)) {
                droppedByNail += 1
                diagnostics += NailMaskDiagnostics.rejectedConfidence(roi, mask, segScore, confidence)
                return@mapNotNull null
            }

            val nail = DetectedNail(
                finger = roi.finger,
                roi = roi,
                mask = mask,
                confidence = confidence,
            )
            diagnostics += NailMaskDiagnostics.from(nail)
            nail
        }
        return SegmentedNails(
            nails = detected,
            diagnostics = diagnostics,
            droppedByRoi = droppedByRoi,
            droppedByNail = droppedByNail,
            droppedBySegmentation = droppedBySegmentation,
            droppedByGuard = droppedByGuard,
            hadRois = rois.isNotEmpty(),
        )
    }

    private fun adjustReliability(reliability: TryOnReliability, nails: List<DetectedNail>): TryOnReliability {
        val demote = reliability == TryOnReliability.STRONG &&
            nails.size in 1 until NailLandmarkMapper.MIN_PLAUSIBLE_NAILS &&
            !DetectionConfidenceFloor.meetsFullNailFloor(nails)
        return if (demote) TryOnReliability.WEAK else reliability
    }

    private fun resolveBarrier(
        nailsEmpty: Boolean,
        droppedByRoi: Int,
        droppedByNail: Int,
        droppedBySegmentation: Int,
        droppedByGuard: Int,
        hadRois: Boolean,
        detectedEmpty: Boolean,
    ): RejectionBarrier = when {
        nailsEmpty && droppedByRoi > 0 && detectedEmpty -> RejectionBarrier.ROI
        nailsEmpty && droppedBySegmentation > 0 -> RejectionBarrier.NAIL_COMBINED
        nailsEmpty && droppedByGuard > 0 -> RejectionBarrier.NAIL_COMBINED
        nailsEmpty && droppedByNail > 0 -> RejectionBarrier.NAIL_COMBINED
        nailsEmpty && hadRois -> RejectionBarrier.NAIL_COMBINED
        else -> RejectionBarrier.NONE
    }

    private fun reasonFor(
        reliability: TryOnReliability,
        landmarks: HandLandmarks,
        nails: List<DetectedNail>,
        barrier: RejectionBarrier,
        lighting: ImageLightingSampler.Stats?,
    ): DetectionFailureReason? {
        if (reliability == TryOnReliability.STRONG && DetectionConfidenceFloor.meetsFullNailFloor(nails)) return null
        val hasMappable = NailLandmarkMapper.fromNormalizedLandmarks(
            landmarks = landmarks.points.map { NailLandmarkMapper.NormalizedPoint(it.x, it.y) },
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

    fun recolor(snapshot: NailDetectionSnapshot, polishColor: Color): NailTryOnResult {
        val working = snapshot.workingBitmap
        if (snapshot.reliability == TryOnReliability.REJECTED) {
            return NailTryOnResult(working, emptyList(), snapshot.landmarks, debugEnabled, snapshot.diagnostics)
        }
        if (debugEnabled) {
            return NailTryOnResult(working, snapshot.nails, snapshot.landmarks, true, snapshot.diagnostics)
        }
        val paintableCount = DetectionConfidenceFloor.countPaintable(snapshot.nails)
        val maskPaint = if (paintableCount > 0) colorApplier.apply(working, snapshot.nails, polishColor) else null
        return NailTryOnResult(maskPaint ?: working, snapshot.nails, snapshot.landmarks, debugEnabled, snapshot.diagnostics)
    }

    private fun segmentationConfidence(mask: NailMask, roi: NailRoi): Float {
        val filled = mask.filledRatio()
        val plateArea = (roi.lengthPx * roi.widthPx).coerceAtLeast(1f)
        val solidPixels = mask.width * mask.height * filled
        val coverage = (solidPixels / plateArea).coerceIn(0f, COVERAGE_CLAMP)
        return when {
            filled < FILL_TOO_LOW -> SCORE_VERY_LOW
            filled > FILL_TOO_HIGH && coverage < SKIN_RISK_COVERAGE_MAX -> SCORE_SKIN_RISK
            coverage in COVERAGE_GOOD -> SCORE_HIGH
            coverage in COVERAGE_OK -> SCORE_MID
            else -> SCORE_LOW
        }
    }

    private companion object {
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
        const val MIN_POST_GUARD_FILL = 0.02f
        const val NANOS_PER_MILLISECOND = 1_000_000f
        val COVERAGE_GOOD = 0.25f..1.4f
        val COVERAGE_OK = 0.14f..1.6f
    }
}
