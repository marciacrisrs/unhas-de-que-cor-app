package br.com.unhasdequecor.ui.components

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * Converte landmarks normalizados da mão (espaço da imagem) em âncoras de unha
 * no espaço do preview com [ContentScale.Crop] e aspect ratio fixo.
 */
object NailLandmarkMapper {
    private val FINGER_PAIRS = listOf(
        TipDip(tipIndex = 4, dipIndex = 3, widthFactor = 0.62f, lengthFactor = 0.78f),
        TipDip(tipIndex = 8, dipIndex = 7, widthFactor = 0.52f, lengthFactor = 0.88f),
        TipDip(tipIndex = 12, dipIndex = 11, widthFactor = 0.52f, lengthFactor = 0.92f),
        TipDip(tipIndex = 16, dipIndex = 15, widthFactor = 0.50f, lengthFactor = 0.88f),
        TipDip(tipIndex = 20, dipIndex = 19, widthFactor = 0.48f, lengthFactor = 0.82f),
    )

    fun fromNormalizedLandmarks(
        landmarks: List<NormalizedPoint>,
        imageWidth: Int,
        imageHeight: Int,
        viewAspect: Float = PREVIEW_ASPECT,
    ): List<NailOverlayAnchor>? {
        if (landmarks.size < MIN_LANDMARKS || imageWidth <= 0 || imageHeight <= 0) {
            return null
        }
        val anchors = FINGER_PAIRS.map { finger ->
            val tip = landmarks[finger.tipIndex]
            val dip = landmarks[finger.dipIndex]
            val tipView = mapImageNormToViewNorm(
                tip.x,
                tip.y,
                imageWidth,
                imageHeight,
                viewAspect,
            )
            val dipView = mapImageNormToViewNorm(
                dip.x,
                dip.y,
                imageWidth,
                imageHeight,
                viewAspect,
            )
            val dx = tipView.x - dipView.x
            val dy = tipView.y - dipView.y
            val fingerLen = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(MIN_FINGER_LEN)
            val height = fingerLen * finger.lengthFactor
            val width = height * finger.widthFactor
            val centerX = dipView.x + dx * NAIL_CENTER_ALONG_FINGER
            val centerY = dipView.y + dy * NAIL_CENTER_ALONG_FINGER
            // 0° = unha “em pé” no eixo Y; atan2(dx, -dy) acompanha a direção do dedo.
            val rotation = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
            NailOverlayAnchor(
                centerX = centerX,
                centerY = centerY,
                width = width.coerceIn(MIN_NAIL_WIDTH, MAX_NAIL_WIDTH),
                height = height.coerceIn(MIN_NAIL_HEIGHT, MAX_NAIL_HEIGHT),
                rotationDegrees = rotation,
            )
        }
        return anchors.takeIf { it.all { nail -> nail.centerX in VISIBLE_RANGE && nail.centerY in VISIBLE_RANGE } }
    }

    fun mapImageNormToViewNorm(
        x: Float,
        y: Float,
        imageWidth: Int,
        imageHeight: Int,
        viewAspect: Float,
    ): NormalizedPoint {
        val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
        return if (imageAspect > viewAspect) {
            val visibleNormW = viewAspect / imageAspect
            val offsetX = (1f - visibleNormW) / 2f
            NormalizedPoint(
                x = (x - offsetX) / visibleNormW,
                y = y,
            )
        } else {
            val visibleNormH = imageAspect / max(viewAspect, MIN_ASPECT)
            val offsetY = (1f - visibleNormH) / 2f
            NormalizedPoint(
                x = x,
                y = (y - offsetY) / visibleNormH,
            )
        }
    }

    data class NormalizedPoint(val x: Float, val y: Float)

    private data class TipDip(
        val tipIndex: Int,
        val dipIndex: Int,
        val widthFactor: Float,
        val lengthFactor: Float,
    )

    private val VISIBLE_RANGE = -0.05f..1.05f

    const val PREVIEW_ASPECT = 3f / 4f
    private const val MIN_LANDMARKS = 21
    private const val MIN_FINGER_LEN = 0.02f
    private const val NAIL_CENTER_ALONG_FINGER = 0.72f
    private const val MIN_NAIL_WIDTH = 0.04f
    private const val MAX_NAIL_WIDTH = 0.16f
    private const val MIN_NAIL_HEIGHT = 0.035f
    private const val MAX_NAIL_HEIGHT = 0.14f
    private const val MIN_ASPECT = 0.01f
}
