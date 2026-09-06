package br.com.unhasdequecor.data.vision.nail

import kotlin.math.atan2
import kotlin.math.hypot

/** Calibração anatômica única da placa ungueal para mapper e ROI. */
object NailPlateCalibration {
    const val SHORT_TIP_DIP_PX = 16f
    const val FACING_TIP_DIP_RATIO = 0.35f
    const val FACING_LENGTH_SCALE = 0.88f
    const val FACING_WIDTH_SCALE = 0.42f
    const val THUMB_LENGTH_SCALE = 0.44f
    const val CENTER_ALONG = 0.72f
    const val FACING_CENTER = 0.82f
    const val THUMB_CENTER = 0.78f
    const val TIP_OVERSHOOT = 0f
    const val MIN_NAIL_LEN_PX = 14f
    const val MAX_NAIL_LEN_PX = 160f
    const val MIN_NAIL_WID_PX = 10f
    const val MAX_NAIL_WID_PX = 110f
    const val MIN_AXIS_THUMB_PX = MIN_NAIL_LEN_PX
    const val MIN_AXIS_FACING_PX = SHORT_TIP_DIP_PX * 0.75f
    const val MIN_AXIS_OPEN_PX = SHORT_TIP_DIP_PX * 0.625f
    const val ELLIPSE_RX_FACTOR = 0.48f
    const val ELLIPSE_RY_FACTOR = 0.50f
    const val ELLIPSE_CENTER_Y_BIAS = 0.03f
    const val ELLIPSE_OPAQUE_STOP = 0.76f
    const val TIP_WIDTH_FACTOR = 0.78f
    const val MID_WIDTH_FACTOR = 0.86f
    const val SHORT_MID_WIDTH_FACTOR = 0.86f
    const val CUTICLE_WIDTH_FACTOR = 0.76f
    const val TIP_POINT_FACTOR = 0.66f
    const val SHORT_TIP_POINT_FACTOR = 0.72f
    const val SHORT_PLATE_ASPECT = 1.28f
    const val CUTICLE_BACK = 0.98f
    const val MID_FORWARD = 0.20f

    data class FingerScale(val widthScale: Float, val lengthScale: Float)

    data class PlateGeometry(
        val centerX: Float,
        val centerY: Float,
        val lengthPx: Float,
        val widthPx: Float,
        val rotationDegrees: Float,
        val axisStartX: Float,
        val axisStartY: Float,
        val tipX: Float,
        val tipY: Float,
        val ux: Float,
        val uy: Float,
        val overshootPx: Float,
        val thumbMode: Boolean,
        val facing: Boolean,
        val rawLengthPx: Float,
    )

    data class AlmondExtents(
        val tipX: Float,
        val tipY: Float,
        val cuticleX: Float,
        val cuticleY: Float,
        val midX: Float,
        val midY: Float,
        val tipHalfW: Float,
        val midHalfW: Float,
        val cuticleHalfW: Float,
        val px: Float,
        val py: Float,
        val tipPointFactor: Float,
    )

    fun scalesFor(finger: Finger): FingerScale = when (finger) {
        Finger.THUMB -> FingerScale(THUMB_WIDTH_SCALE, THUMB_LENGTH_SCALE)
        Finger.INDEX -> FingerScale(INDEX_WIDTH_SCALE, INDEX_LENGTH_SCALE)
        Finger.MIDDLE -> FingerScale(MIDDLE_WIDTH_SCALE, MIDDLE_LENGTH_SCALE)
        Finger.RING -> FingerScale(RING_WIDTH_SCALE, RING_LENGTH_SCALE)
        Finger.PINKY -> FingerScale(PINKY_WIDTH_SCALE, PINKY_LENGTH_SCALE)
    }

    fun centerAlong(thumbMode: Boolean, facing: Boolean): Float = when {
        thumbMode -> THUMB_CENTER
        facing -> FACING_CENTER
        else -> CENTER_ALONG
    }

    private const val FACING_TIP_DIP_ABS_FLOOR = 0.5f
    private const val USABLE_LENGTH_MIN_FACTOR = 0.85f
    private const val CUTICLE_PROXIMAL_EXTENSION = 0.18f
    private const val THUMB_WIDTH_SCALE = 0.82f
    private const val INDEX_WIDTH_SCALE = 0.68f
    private const val INDEX_LENGTH_SCALE = 0.80f
    private const val MIDDLE_WIDTH_SCALE = 0.70f
    private const val MIDDLE_LENGTH_SCALE = 0.82f
    private const val RING_WIDTH_SCALE = 0.68f
    private const val RING_LENGTH_SCALE = 0.80f
    private const val PINKY_WIDTH_SCALE = 0.66f
    private const val PINKY_LENGTH_SCALE = 0.76f

    fun facingTipDipThresholdPx(tipPipPx: Float): Float =
        maxOf(SHORT_TIP_DIP_PX * FACING_TIP_DIP_ABS_FLOOR, tipPipPx * FACING_TIP_DIP_RATIO)

    fun isFacing(thumbMode: Boolean, tipDipPx: Float, tipPipPx: Float): Boolean =
        !thumbMode && tipDipPx < facingTipDipThresholdPx(tipPipPx)

    fun isUsablePlate(plate: PlateGeometry): Boolean {
        val axisLen = hypot(
            (plate.tipX - plate.axisStartX).toDouble(),
            (plate.tipY - plate.axisStartY).toDouble(),
        ).toFloat()
        val minAxis = when {
            plate.thumbMode -> MIN_AXIS_THUMB_PX
            plate.facing -> MIN_AXIS_FACING_PX
            else -> MIN_AXIS_OPEN_PX
        }
        return axisLen >= minAxis &&
            plate.rawLengthPx >= MIN_NAIL_LEN_PX * USABLE_LENGTH_MIN_FACTOR
    }

    fun ellipseRadiusX(anchorWidthNorm: Float, imageWidth: Int): Float =
        (anchorWidthNorm * imageWidth * ELLIPSE_RX_FACTOR).coerceAtLeast(4f)

    fun ellipseRadiusY(anchorHeightNorm: Float, imageHeight: Int): Float =
        (anchorHeightNorm * imageHeight * ELLIPSE_RY_FACTOR).coerceAtLeast(5f)

    fun canvasNailWidthNorm(anchorWidthNorm: Float): Float =
        anchorWidthNorm * (2f * ELLIPSE_RX_FACTOR)

    fun canvasNailHeightNorm(anchorHeightNorm: Float): Float =
        anchorHeightNorm * (2f * ELLIPSE_RY_FACTOR)

    fun plateFromPixels(
        finger: Finger,
        tipX: Float,
        tipY: Float,
        dipX: Float,
        dipY: Float,
        pipX: Float,
        pipY: Float,
        mcpX: Float,
        mcpY: Float,
    ): PlateGeometry {
        val tipDip = hypot((tipX - dipX).toDouble(), (tipY - dipY).toDouble()).toFloat()
        val tipPip = hypot((tipX - pipX).toDouble(), (tipY - pipY).toDouble()).toFloat()
        val tipMcp = hypot((tipX - mcpX).toDouble(), (tipY - mcpY).toDouble()).toFloat()
        val scales = scalesFor(finger)
        val thumbMode = finger == Finger.THUMB
        val facing = isFacing(thumbMode, tipDip, tipPip)

        val axisStartX: Float
        val axisStartY: Float
        val rawAxisLength: Float
        when {
            thumbMode -> {
                axisStartX = mcpX
                axisStartY = mcpY
                rawAxisLength = tipMcp
            }
            facing -> {
                axisStartX = pipX
                axisStartY = pipY
                rawAxisLength = tipPip
            }
            else -> {
                axisStartX = dipX + (dipX - pipX) * CUTICLE_PROXIMAL_EXTENSION
                axisStartY = dipY + (dipY - pipY) * CUTICLE_PROXIMAL_EXTENSION
                rawAxisLength = hypot(
                    (tipX - axisStartX).toDouble(),
                    (tipY - axisStartY).toDouble(),
                ).toFloat()
            }
        }

        val rawLengthPx = rawAxisLength * if (facing) FACING_LENGTH_SCALE else scales.lengthScale
        val lengthPx = rawLengthPx.coerceIn(MIN_NAIL_LEN_PX, MAX_NAIL_LEN_PX)
        val widthPx = if (facing) {
            (tipPip * FACING_WIDTH_SCALE).coerceIn(MIN_NAIL_WID_PX, MAX_NAIL_WID_PX)
        } else {
            (lengthPx * scales.widthScale).coerceIn(MIN_NAIL_WID_PX, MAX_NAIL_WID_PX)
        }

        val dirX = tipX - axisStartX
        val dirY = tipY - axisStartY
        val dirLen = hypot(dirX.toDouble(), dirY.toDouble()).toFloat().coerceAtLeast(1f)
        val ux = dirX / dirLen
        val uy = dirY / dirLen
        val overshootBase = when {
            thumbMode -> tipMcp
            facing -> tipPip
            else -> rawAxisLength
        }
        val overshootPx = overshootBase * TIP_OVERSHOOT
        val centerT = centerAlong(thumbMode, facing)
        val centerX = axisStartX + dirX * centerT + ux * overshootPx
        val centerY = axisStartY + dirY * centerT + uy * overshootPx
        val rotation = Math.toDegrees(atan2(dirX.toDouble(), -dirY.toDouble())).toFloat()

        return PlateGeometry(
            centerX = centerX,
            centerY = centerY,
            lengthPx = lengthPx,
            widthPx = widthPx,
            rotationDegrees = rotation,
            axisStartX = axisStartX,
            axisStartY = axisStartY,
            tipX = tipX,
            tipY = tipY,
            ux = ux,
            uy = uy,
            overshootPx = overshootPx,
            thumbMode = thumbMode,
            facing = facing,
            rawLengthPx = rawLengthPx,
        )
    }

    fun almondExtents(plate: PlateGeometry): AlmondExtents {
        val px = -plate.uy
        val py = plate.ux
        val tipX = plate.tipX + plate.ux * plate.overshootPx
        val tipY = plate.tipY + plate.uy * plate.overshootPx
        val plateLen = plate.lengthPx * CUTICLE_BACK
        val cuticleX = tipX - plate.ux * plateLen
        val cuticleY = tipY - plate.uy * plateLen
        val midT = 0.5f + MID_FORWARD * 0.5f
        val midX = cuticleX + (tipX - cuticleX) * midT
        val midY = cuticleY + (tipY - cuticleY) * midT
        val halfW = plate.widthPx * 0.5f
        val shortPlate = plate.lengthPx / plate.widthPx.coerceAtLeast(1f) < SHORT_PLATE_ASPECT
        val midFactor = if (shortPlate) SHORT_MID_WIDTH_FACTOR else MID_WIDTH_FACTOR
        val tipPoint = if (shortPlate) SHORT_TIP_POINT_FACTOR else TIP_POINT_FACTOR
        return AlmondExtents(
            tipX = tipX,
            tipY = tipY,
            cuticleX = cuticleX,
            cuticleY = cuticleY,
            midX = midX,
            midY = midY,
            tipHalfW = halfW * TIP_WIDTH_FACTOR,
            midHalfW = halfW * midFactor,
            cuticleHalfW = halfW * CUTICLE_WIDTH_FACTOR,
            px = px,
            py = py,
            tipPointFactor = tipPoint,
        )
    }
}
