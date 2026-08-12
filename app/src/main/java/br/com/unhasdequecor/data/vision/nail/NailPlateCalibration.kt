package br.com.unhasdequecor.data.vision.nail

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Calibração anatômica única da **placa ungueal** (mão).
 *
 * Mapper (elipse/Canvas) e ROI (almond/máscara) devem usar estes valores —
 * alterar um exige o outro + testes de paridade.
 */
object NailPlateCalibration {
    const val SHORT_TIP_DIP_PX = 16f
    const val FACING_LENGTH_SCALE = 0.48f
    const val THUMB_LENGTH_SCALE = 0.38f

    /** Centro da placa ao longo do eixo proximal→tip (0 = cutícula/eixo, 1 = tip). */
    const val CENTER_ALONG = 0.58f
    const val FACING_CENTER = 0.82f
    const val THUMB_CENTER = 0.78f

    /**
     * Fração do comprimento tip–referência empurrando o centro em direção à tip
     * (borda livre real passa um pouco da landmark tip).
     */
    const val TIP_OVERSHOOT = 0.02f

    const val MIN_NAIL_LEN_PX = 14f
    const val MAX_NAIL_LEN_PX = 160f
    const val MIN_NAIL_WID_PX = 10f
    const val MAX_NAIL_WID_PX = 110f

    /** Elipse de fallback: raios relativos à âncora (largura/altura). */
    const val ELLIPSE_RX_FACTOR = 0.48f
    const val ELLIPSE_RY_FACTOR = 0.50f
    /** Bias do núcleo opaco em direção à cutícula (após rotação da elipse). */
    const val ELLIPSE_CENTER_Y_BIAS = 0.03f
    const val ELLIPSE_OPAQUE_STOP = 0.76f

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
        val thumbMode: Boolean,
        val facing: Boolean,
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
        val widthPx = (lengthPx * scales.widthScale).coerceIn(MIN_NAIL_WID_PX, MAX_NAIL_WID_PX)

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
        // Facing: tip≈dip → overshoot pela falange tip–pip; polegar pela tip–MCP.
        val overshootBase = when {
            thumbMode -> tipMcp
            facing -> tipPip
            else -> tipDip
        }
        val centerX = axisStartX + dirX * centerT + ux * overshootBase * TIP_OVERSHOOT
        val centerY = axisStartY + dirY * centerT + uy * overshootBase * TIP_OVERSHOOT
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
            thumbMode = thumbMode,
            facing = facing,
        )
    }
}
