package br.com.unhasdequecor.data.vision.nail

import kotlin.math.abs

/**
 * Deterministic safety barrier for live nail geometry.
 *
 * The tracker may smooth or predict a detection, but it must never turn a
 * degenerate ROI/mask pair into a renderable nail.
 */
object NailGeometryValidator {
    enum class Reason {
        VALID,
        ROI_TOO_SMALL,
        ROI_TOO_LARGE,
        ROI_ASPECT_INVALID,
        NAIL_DIMENSIONS_INVALID,
        AXIS_TOO_SHORT,
        MASK_INVALID,
        MASK_ROI_MISMATCH,
    }

    data class Result(
        val valid: Boolean,
        val reason: Reason,
    )

    fun validate(nail: DetectedNail): Result {
        val roi = nail.roi
        val boundsWidth = roi.bounds.width()
        val boundsHeight = roi.bounds.height()

        if (roi.lengthPx < NailPlateCalibration.MIN_NAIL_LEN_PX ||
            roi.lengthPx > NailPlateCalibration.MAX_NAIL_LEN_PX ||
            roi.widthPx < NailPlateCalibration.MIN_NAIL_WID_PX ||
            roi.widthPx > NailPlateCalibration.MAX_NAIL_WID_PX
        ) {
            return Result(false, Reason.NAIL_DIMENSIONS_INVALID)
        }

        if (boundsWidth < MIN_ROI_WIDTH_PX || boundsHeight < MIN_ROI_HEIGHT_PX) {
            return Result(false, Reason.ROI_TOO_SMALL)
        }
        if (boundsWidth > MAX_ROI_DIMENSION_PX || boundsHeight > MAX_ROI_DIMENSION_PX) {
            return Result(false, Reason.ROI_TOO_LARGE)
        }

        val aspect = maxOf(boundsWidth, boundsHeight).toFloat() /
            minOf(boundsWidth, boundsHeight).coerceAtLeast(1).toFloat()
        if (aspect > MAX_ROI_ASPECT_RATIO) {
            return Result(false, Reason.ROI_ASPECT_INVALID)
        }

        val axisLength = ImageCoordinates.distancePx(roi.axisFromDip, roi.axisToTip)
        if (axisLength < MIN_AXIS_LENGTH_PX) {
            return Result(false, Reason.AXIS_TOO_SHORT)
        }

        val mask = nail.mask
        if (mask.width < MIN_MASK_DIMENSION_PX ||
            mask.height < MIN_MASK_DIMENSION_PX ||
            mask.width > MAX_MASK_DIMENSION_PX ||
            mask.height > MAX_MASK_DIMENSION_PX ||
            mask.alpha.size != mask.width * mask.height
        ) {
            return Result(false, Reason.MASK_INVALID)
        }

        if (abs(mask.originX - roi.bounds.left) > MAX_ORIGIN_DELTA_PX ||
            abs(mask.originY - roi.bounds.top) > MAX_ORIGIN_DELTA_PX
        ) {
            return Result(false, Reason.MASK_ROI_MISMATCH)
        }

        return Result(true, Reason.VALID)
    }

    private const val MIN_ROI_WIDTH_PX = 10
    private const val MIN_ROI_HEIGHT_PX = 14
    private const val MAX_ROI_DIMENSION_PX = 220
    private const val MAX_ROI_ASPECT_RATIO = 8f
    private const val MIN_AXIS_LENGTH_PX = 10f
    private const val MIN_MASK_DIMENSION_PX = 8
    private const val MAX_MASK_DIMENSION_PX = 220
    private const val MAX_ORIGIN_DELTA_PX = 1
}
