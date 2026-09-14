package br.com.unhasdequecor.data.vision.nail

/**
 * Reproducible diagnostics for one nail candidate.
 *
 * Without ground truth these values describe the produced candidate only;
 * they must not be presented as IoU, spill or anatomical accuracy.
 */
data class NailMaskDiagnostic(
    val finger: Finger,
    val roiWidthPx: Float,
    val roiLengthPx: Float,
    val filledRatio: Float,
    val coverageRatio: Float,
    val confidence: Float,
    val hasBoundaryPolygon: Boolean,
    val classification: MaskDiagnosticClassification = MaskDiagnosticClassification.UNVERIFIED,
    val stage: NailDiagnosticStage = NailDiagnosticStage.UNKNOWN,
    val rejectionReason: String? = null,
    val geometricConfidence: Float = 0f,
    val segmentationConfidence: Float = 0f,
    val postGuardFilledRatio: Float = 0f,
)

enum class MaskDiagnosticClassification {
    UNVERIFIED,
    LANDMARK,
    ROI_GEOMETRY,
    SEGMENTATION,
    COMPOSITION,
}

enum class NailDiagnosticStage {
    UNKNOWN,
    ROI_REJECTED,
    ROI_READY,
    SEGMENTATION_REJECTED,
    MASK_READY,
    BOUNDARY_GUARD_REJECTED,
    CONFIDENCE_REJECTED,
}

object NailMaskDiagnostics {
    fun from(nail: DetectedNail): NailMaskDiagnostic {
        val roiArea = (nail.roi.widthPx * nail.roi.lengthPx).coerceAtLeast(1f)
        val maskArea = nail.mask.width.toFloat() * nail.mask.height.toFloat() * nail.mask.filledRatio()
        return NailMaskDiagnostic(
            finger = nail.finger,
            roiWidthPx = nail.roi.widthPx,
            roiLengthPx = nail.roi.lengthPx,
            filledRatio = nail.mask.filledRatio(),
            coverageRatio = (maskArea / roiArea).coerceIn(0f, 4f),
            confidence = nail.confidence,
            hasBoundaryPolygon = !nail.mask.boundaryPolygon.isNullOrEmpty(),
            stage = NailDiagnosticStage.MASK_READY,
            geometricConfidence = nail.roi.geometricConfidence,
            segmentationConfidence = nail.confidence,
            postGuardFilledRatio = nail.mask.filledRatio(),
        )
    }

    fun rejectedRoi(roi: NailRoi): NailMaskDiagnostic =
        base(roi).copy(
            stage = NailDiagnosticStage.ROI_REJECTED,
            rejectionReason = "roi_confidence_below_floor",
            geometricConfidence = roi.geometricConfidence,
        )

    fun rejectedSegmentation(roi: NailRoi): NailMaskDiagnostic =
        base(roi).copy(
            stage = NailDiagnosticStage.SEGMENTATION_REJECTED,
            rejectionReason = "segmenter_returned_null",
            geometricConfidence = roi.geometricConfidence,
        )

    fun rejectedBoundaryGuard(roi: NailRoi, rawFill: Float, guardedFill: Float): NailMaskDiagnostic =
        base(roi).copy(
            filledRatio = guardedFill,
            stage = NailDiagnosticStage.BOUNDARY_GUARD_REJECTED,
            rejectionReason = "post_guard_fill_below_floor(raw=${rawFill.compact()},guarded=${guardedFill.compact()})",
            geometricConfidence = roi.geometricConfidence,
            postGuardFilledRatio = guardedFill,
        )

    fun rejectedConfidence(
        roi: NailRoi,
        mask: NailMask,
        segmentationConfidence: Float,
        confidence: Float,
    ): NailMaskDiagnostic {
        val roiArea = (roi.widthPx * roi.lengthPx).coerceAtLeast(1f)
        val maskArea = mask.width.toFloat() * mask.height.toFloat() * mask.filledRatio()
        return NailMaskDiagnostic(
            finger = roi.finger,
            roiWidthPx = roi.widthPx,
            roiLengthPx = roi.lengthPx,
            filledRatio = mask.filledRatio(),
            coverageRatio = (maskArea / roiArea).coerceIn(0f, 4f),
            confidence = confidence,
            hasBoundaryPolygon = !mask.boundaryPolygon.isNullOrEmpty(),
            stage = NailDiagnosticStage.CONFIDENCE_REJECTED,
            rejectionReason = "combined_confidence_below_floor",
            geometricConfidence = roi.geometricConfidence,
            segmentationConfidence = segmentationConfidence,
            postGuardFilledRatio = mask.filledRatio(),
        )
    }

    private fun base(roi: NailRoi): NailMaskDiagnostic =
        NailMaskDiagnostic(
            finger = roi.finger,
            roiWidthPx = roi.widthPx,
            roiLengthPx = roi.lengthPx,
            filledRatio = 0f,
            coverageRatio = 0f,
            confidence = 0f,
            hasBoundaryPolygon = false,
        )

    private fun Float.compact(): String = "%.3f".format(java.util.Locale.US, this)
}
