package br.com.unhasdequecor.data.vision.nail

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Calibração anatômica única da **placa ungueal** (mão).
 *
 * Mapper (elipse/Canvas), ROI (almond/máscara) e fallback devem usar estes valores —
 * alterar um exige o outro + testes de paridade.
 */
object NailPlateCalibration {
    const val SHORT_TIP_DIP_PX = 16f
    const val FACING_LENGTH_SCALE = 0.48f
    /** Largura em unha de frente: fração tip–pip (independente do comprimento encurtado). */
    const val FACING_WIDTH_SCALE = 0.45f
    const val THUMB_LENGTH_SCALE = 0.38f

    /** Centro da placa ao longo do eixo proximal→tip (0 = cutícula/eixo, 1 = tip). */
    const val CENTER_ALONG = 0.58f
    const val FACING_CENTER = 0.82f
    const val THUMB_CENTER = 0.78f

    /**
     * Fração do comprimento tip–referência empurrando a borda livre além da tip landmark.
     */
    const val TIP_OVERSHOOT = 0.02f

    const val MIN_NAIL_LEN_PX = 14f
    const val MAX_NAIL_LEN_PX = 160f
    const val MIN_NAIL_WID_PX = 10f
    const val MAX_NAIL_WID_PX = 110f

    /** Pisos de eixo para confidence geométrica. */
    const val MIN_AXIS_THUMB_PX = MIN_NAIL_LEN_PX
    const val MIN_AXIS_FACING_PX = SHORT_TIP_DIP_PX * 0.75f
    const val MIN_AXIS_OPEN_PX = SHORT_TIP_DIP_PX * 0.625f

    /** Elipse / Canvas de fallback: raios = fator × semi-eixo da âncora (= metade da placa). */
    const val ELLIPSE_RX_FACTOR = 0.48f
    const val ELLIPSE_RY_FACTOR = 0.50f
    /** Bias do núcleo opaco em direção à cutícula (após rotação). */
    const val ELLIPSE_CENTER_Y_BIAS = 0.03f
    const val ELLIPSE_OPAQUE_STOP = 0.76f

    /** Forma almond (ROI / segmentação). */
    const val TIP_WIDTH_FACTOR = 0.82f
    const val MID_WIDTH_FACTOR = 1.12f
    const val CUTICLE_WIDTH_FACTOR = 0.86f
    const val TIP_POINT_FACTOR = 0.70f
    const val CUTICLE_BACK = 0.90f
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
    )

    /** Extremos tip/cutícula do almond alinhados à tip landmark (não halfLen a partir do centro). */
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
    )

    fun scalesFor(finger: Finger): FingerScale = when (finger) {
        Finger.THUMB -> FingerScale(widthScale = 0.82f, lengthScale = 0.90f)
        Finger.INDEX -> FingerScale(widthScale = 0.74f, lengthScale = 0.96f)
        Finger.MIDDLE -> FingerScale(widthScale = 0.76f, lengthScale = 0.98f)
        Finger.RING -> FingerScale(widthScale = 0.72f, lengthScale = 0.96f)
        Finger.PINKY -> FingerScale(widthScale = 0.68f, lengthScale = 0.92f)
    }

    fun centerAlong(thumbMode: Boolean, facing: Boolean): Float = when {
        thumbMode -> THUMB_CENTER
        facing -> FACING_CENTER
        else -> CENTER_ALONG
    }

    fun ellipseRadiusX(anchorWidthNorm: Float, imageWidth: Int): Float =
        (anchorWidthNorm * imageWidth * ELLIPSE_RX_FACTOR).coerceAtLeast(4f)

    fun ellipseRadiusY(anchorHeightNorm: Float, imageHeight: Int): Float =
        (anchorHeightNorm * imageHeight * ELLIPSE_RY_FACTOR).coerceAtLeast(5f)

    /** Diâmetro Canvas ≡ 2 × raio elipse (mesma área aparente no fallback). */
    fun canvasNailWidthNorm(anchorWidthNorm: Float): Float =
        anchorWidthNorm * (2f * ELLIPSE_RX_FACTOR)

    fun canvasNailHeightNorm(anchorHeightNorm: Float): Float =
        anchorHeightNorm * (2f * ELLIPSE_RY_FACTOR)

    /**
     * Geometria da placa a partir de landmarks em **pixels**.
     * [pip] no polegar = IP MediaPipe; o eixo proximal do polegar usa [mcp].
     */
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
        val facing = !thumbMode && tipDip < SHORT_TIP_DIP_PX

        val lengthPx = when {
            thumbMode -> (tipMcp * THUMB_LENGTH_SCALE).coerceIn(MIN_NAIL_LEN_PX, MAX_NAIL_LEN_PX)
            facing -> (tipPip * FACING_LENGTH_SCALE).coerceIn(MIN_NAIL_LEN_PX, MAX_NAIL_LEN_PX)
            else -> (tipDip * scales.lengthScale).coerceIn(MIN_NAIL_LEN_PX, MAX_NAIL_LEN_PX)
        }
        val widthPx = when {
            facing -> (tipPip * FACING_WIDTH_SCALE).coerceIn(MIN_NAIL_WID_PX, MAX_NAIL_WID_PX)
            else -> (lengthPx * scales.widthScale).coerceIn(MIN_NAIL_WID_PX, MAX_NAIL_WID_PX)
        }

        val axisStartX = when {
            thumbMode -> mcpX
            facing -> pipX
            else -> dipX
        }
        val axisStartY = when {
            thumbMode -> mcpY
            facing -> pipY
            else -> dipY
        }
        val dirX = tipX - axisStartX
        val dirY = tipY - axisStartY
        val dirLen = hypot(dirX.toDouble(), dirY.toDouble()).toFloat().coerceAtLeast(1f)
        val ux = dirX / dirLen
        val uy = dirY / dirLen

        val centerT = centerAlong(thumbMode, facing)
        val overshootBase = when {
            thumbMode -> tipMcp
            facing -> tipPip
            else -> tipDip
        }
        val overshootPx = overshootBase * TIP_OVERSHOOT
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
        )
    }

    /**
     * Extremos do almond: ponta = tip landmark + overshoot (não `center + halfLen`,
     * que em facing ultrapassava demais a polpa).
     */
    fun almondExtents(plate: PlateGeometry): AlmondExtents {
        val ux = plate.ux
        val uy = plate.uy
        val px = -uy
        val py = ux
        val tipX = plate.tipX + ux * plate.overshootPx
        val tipY = plate.tipY + uy * plate.overshootPx
        val cuticleX = tipX - ux * plate.lengthPx
        val cuticleY = tipY - uy * plate.lengthPx
        val midT = 0.5f + MID_FORWARD * 0.5f
        val midX = cuticleX + (tipX - cuticleX) * midT
        val midY = cuticleY + (tipY - cuticleY) * midT
        val halfW = plate.widthPx * 0.5f
        return AlmondExtents(
            tipX = tipX,
            tipY = tipY,
            cuticleX = cuticleX,
            cuticleY = cuticleY,
            midX = midX,
            midY = midY,
            tipHalfW = halfW * TIP_WIDTH_FACTOR,
            midHalfW = halfW * MID_WIDTH_FACTOR,
            cuticleHalfW = halfW * CUTICLE_WIDTH_FACTOR,
            px = px,
            py = py,
        )
    }
}
