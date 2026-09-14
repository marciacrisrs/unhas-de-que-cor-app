package br.com.unhasdequecor.data.vision.nail

/**
 * Reproducible, geometry-only diagnostics for one detected nail.
 *
 * These values deliberately do not pretend to be accuracy metrics: without
 * ground truth we can measure the produced mask, but we cannot claim IoU,
 * spill or anatomical correctness.
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
)

enum class MaskDiagnosticClassification {
    UNVERIFIED,
    LANDMARK,
    ROI_GEOMETRY,
    SEGMENTATION,
    COMPOSITION,
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
        )
    }
}
