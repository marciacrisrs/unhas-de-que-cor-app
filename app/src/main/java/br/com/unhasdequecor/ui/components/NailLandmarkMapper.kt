package br.com.unhasdequecor.ui.components

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Converte landmarks normalizados da mão (espaço da imagem) em âncoras de unha.
 * Usado com ContentScale.FillBounds + aspect da bitmap (sem crop).
 */
object NailLandmarkMapper {
    private val FINGER_PAIRS = listOf(
        TipDip(tipIndex = 4, dipIndex = 3, pipIndex = 2, widthFactor = 0.58f, lengthFactor = 0.72f),
        TipDip(tipIndex = 8, dipIndex = 7, pipIndex = 6, widthFactor = 0.50f, lengthFactor = 0.80f),
        TipDip(tipIndex = 12, dipIndex = 11, pipIndex = 10, widthFactor = 0.50f, lengthFactor = 0.84f),
        TipDip(tipIndex = 16, dipIndex = 15, pipIndex = 14, widthFactor = 0.48f, lengthFactor = 0.80f),
        TipDip(tipIndex = 20, dipIndex = 19, pipIndex = 18, widthFactor = 0.46f, lengthFactor = 0.76f),
    )

    fun fromNormalizedLandmarks(
        landmarks: List<NormalizedPoint>,
        imageWidth: Int = 1,
        imageHeight: Int = 1,
    ): List<NailOverlayAnchor>? {
        if (landmarks.size < MIN_LANDMARKS || imageWidth <= 0 || imageHeight <= 0) {
            return null
        }
        // FillBounds: coords da imagem == coords da view.
        val anchors = FINGER_PAIRS.map { finger ->
            val tip = landmarks[finger.tipIndex]
            val dip = landmarks[finger.dipIndex]
            val pip = landmarks[finger.pipIndex]
            val dx = tip.x - dip.x
            val dy = tip.y - dip.y
            val tipDipLen = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val tipPipLen = hypot(
                (tip.x - pip.x).toDouble(),
                (tip.y - pip.y).toDouble(),
            ).toFloat()
            // Pose “unha de frente”: tip≈dip em 2D — usa tip-pip para tamanho.
            val fingerLen = maxOf(tipDipLen, tipPipLen * 0.55f, MIN_FINGER_LEN)
            val height = fingerLen * finger.lengthFactor
            val width = height * finger.widthFactor
            val along = if (tipDipLen < SHORT_TIP_DIP) {
                NAIL_CENTER_FACING_CAMERA
            } else {
                NAIL_CENTER_ALONG_FINGER
            }
            val centerX = dip.x + dx * along
            val centerY = dip.y + dy * along
            val rotation = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
            NailOverlayAnchor(
                centerX = centerX,
                centerY = centerY,
                width = width.coerceIn(MIN_NAIL_WIDTH, MAX_NAIL_WIDTH),
                height = height.coerceIn(MIN_NAIL_HEIGHT, MAX_NAIL_HEIGHT),
                rotationDegrees = rotation,
            )
        }
        return anchors.takeIf { nails ->
            nails.all { nail -> nail.centerX in VISIBLE_RANGE && nail.centerY in VISIBLE_RANGE }
        }
    }

    data class NormalizedPoint(val x: Float, val y: Float)

    private data class TipDip(
        val tipIndex: Int,
        val dipIndex: Int,
        val pipIndex: Int,
        val widthFactor: Float,
        val lengthFactor: Float,
    )

    private val VISIBLE_RANGE = -0.05f..1.05f

    const val PREVIEW_ASPECT = 3f / 4f
    private const val MIN_LANDMARKS = 21
    private const val MIN_FINGER_LEN = 0.02f
    private const val SHORT_TIP_DIP = 0.035f
    private const val NAIL_CENTER_ALONG_FINGER = 0.78f
    private const val NAIL_CENTER_FACING_CAMERA = 0.92f
    private const val MIN_NAIL_WIDTH = 0.04f
    private const val MAX_NAIL_WIDTH = 0.16f
    private const val MIN_NAIL_HEIGHT = 0.035f
    private const val MAX_NAIL_HEIGHT = 0.14f
}
