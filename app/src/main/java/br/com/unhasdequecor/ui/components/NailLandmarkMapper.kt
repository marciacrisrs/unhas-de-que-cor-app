package br.com.unhasdequecor.ui.components

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Converte landmarks normalizados da mão (espaço da imagem) em âncoras de unha.
 * Usado com ContentScale.FillBounds + aspect da bitmap (sem crop).
 *
 * Heurística: a placa da unha fica entre DIP e TIP, mais perto da ponta,
 * com tamanho menor que o segmento do dedo.
 */
object NailLandmarkMapper {
    private val FINGER_PAIRS = listOf(
        TipDip(tipIndex = 4, dipIndex = 3, pipIndex = 2, widthFactor = 0.72f, lengthFactor = 0.55f),
        TipDip(tipIndex = 8, dipIndex = 7, pipIndex = 6, widthFactor = 0.62f, lengthFactor = 0.58f),
        TipDip(tipIndex = 12, dipIndex = 11, pipIndex = 10, widthFactor = 0.62f, lengthFactor = 0.60f),
        TipDip(tipIndex = 16, dipIndex = 15, pipIndex = 14, widthFactor = 0.60f, lengthFactor = 0.58f),
        TipDip(tipIndex = 20, dipIndex = 19, pipIndex = 18, widthFactor = 0.58f, lengthFactor = 0.55f),
    )

    fun fromNormalizedLandmarks(
        landmarks: List<NormalizedPoint>,
        imageWidth: Int = 1,
        imageHeight: Int = 1,
    ): List<NailOverlayAnchor>? {
        if (landmarks.size < MIN_LANDMARKS || imageWidth <= 0 || imageHeight <= 0) {
            return null
        }
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
            val fingerLen = maxOf(tipDipLen, tipPipLen * 0.48f, MIN_FINGER_LEN)
            val height = (fingerLen * finger.lengthFactor).coerceIn(MIN_NAIL_HEIGHT, MAX_NAIL_HEIGHT)
            val width = (height * finger.widthFactor).coerceIn(MIN_NAIL_WIDTH, MAX_NAIL_WIDTH)
            val along = if (tipDipLen < SHORT_TIP_DIP) {
                NAIL_CENTER_FACING_CAMERA
            } else {
                NAIL_CENTER_ALONG_FINGER
            }
            // Empurra um pouco além do tip: a unha se estende da ponta do dedo.
            val tipBiasX = tip.x + dx * TIP_OVERSHOOT
            val tipBiasY = tip.y + dy * TIP_OVERSHOOT
            val centerX = (dip.x + (tipBiasX - dip.x) * along).coerceIn(0f, 1f)
            val centerY = (dip.y + (tipBiasY - dip.y) * along).coerceIn(0f, 1f)
            val rotation = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
            NailOverlayAnchor(
                centerX = centerX,
                centerY = centerY,
                width = width,
                height = height,
                rotationDegrees = rotation,
            )
        }
        // Aceita se a maioria das unhas está em região plausível (não descarta a mão inteira).
        val plausible = anchors.count { nail ->
            nail.centerX in PLAUSIBLE_RANGE && nail.centerY in PLAUSIBLE_RANGE
        }
        return anchors.takeIf { plausible >= MIN_PLAUSIBLE_NAILS }
    }

    data class NormalizedPoint(val x: Float, val y: Float)

    private data class TipDip(
        val tipIndex: Int,
        val dipIndex: Int,
        val pipIndex: Int,
        val widthFactor: Float,
        val lengthFactor: Float,
    )

    private val PLAUSIBLE_RANGE = 0.02f..0.98f

    const val PREVIEW_ASPECT = 3f / 4f
    private const val MIN_LANDMARKS = 21
    private const val MIN_PLAUSIBLE_NAILS = 3
    private const val MIN_FINGER_LEN = 0.018f
    private const val SHORT_TIP_DIP = 0.030f
    private const val NAIL_CENTER_ALONG_FINGER = 0.88f
    private const val NAIL_CENTER_FACING_CAMERA = 0.96f
    private const val TIP_OVERSHOOT = 0.08f
    private const val MIN_NAIL_WIDTH = 0.035f
    private const val MAX_NAIL_WIDTH = 0.13f
    private const val MIN_NAIL_HEIGHT = 0.030f
    private const val MAX_NAIL_HEIGHT = 0.11f
}
