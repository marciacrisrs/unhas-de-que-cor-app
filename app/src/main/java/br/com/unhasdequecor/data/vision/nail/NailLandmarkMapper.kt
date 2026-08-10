package br.com.unhasdequecor.data.vision.nail

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Converte landmarks MediaPipe (normalizados 0–1) em âncoras de unha.
 *
 * Distâncias em **pixels** (corrige aspect da foto); depois volta a frações
 * da imagem para o preview FillBounds.
 *
 * Placa da unha ≈ do DIP até a ponta; centro proximal à tip (não além dela).
 */
object NailLandmarkMapper {
    private val FINGERS = listOf(
        Finger(tip = 4, dip = 3, pip = 2, widthScale = 0.78f, lengthScale = 0.92f),
        Finger(tip = 8, dip = 7, pip = 6, widthScale = 0.68f, lengthScale = 0.95f),
        Finger(tip = 12, dip = 11, pip = 10, widthScale = 0.70f, lengthScale = 1.00f),
        Finger(tip = 16, dip = 15, pip = 14, widthScale = 0.66f, lengthScale = 0.95f),
        Finger(tip = 20, dip = 19, pip = 18, widthScale = 0.62f, lengthScale = 0.90f),
    )

    fun fromNormalizedLandmarks(
        landmarks: List<NormalizedPoint>,
        imageWidth: Int = 1,
        imageHeight: Int = 1,
    ): List<NailOverlayAnchor>? {
        if (landmarks.size < MIN_LANDMARKS || imageWidth <= 0 || imageHeight <= 0) {
            return null
        }
        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()
        val anchors = FINGERS.map { finger ->
            val tip = landmarks[finger.tip]
            val dip = landmarks[finger.dip]
            val pip = landmarks[finger.pip]

            val tipX = tip.x * w
            val tipY = tip.y * h
            val dipX = dip.x * w
            val dipY = dip.y * h
            val pipX = pip.x * w
            val pipY = pip.y * h

            val dx = tipX - dipX
            val dy = tipY - dipY
            val tipDipPx = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val tipPipPx = hypot((tipX - pipX).toDouble(), (tipY - pipY).toDouble()).toFloat()

            // Unha de frente (punho): tip≈dip em 2D — estima pela falange tip–pip.
            val facingCamera = tipDipPx < SHORT_TIP_DIP_PX
            val nailLenPx = if (facingCamera) {
                (tipPipPx * 0.42f).coerceIn(MIN_NAIL_LEN_PX, MAX_NAIL_LEN_PX)
            } else {
                (tipDipPx * finger.lengthScale).coerceIn(MIN_NAIL_LEN_PX, MAX_NAIL_LEN_PX)
            }
            val nailWidPx = (nailLenPx * finger.widthScale)
                .coerceIn(MIN_NAIL_WID_PX, MAX_NAIL_WID_PX)

            val (centerXpx, centerYpx) = if (facingCamera) {
                tipX to tipY
            } else {
                // Centro da placa: entre DIP e TIP, sem passar da ponta.
                val t = NAIL_CENTER_ALONG
                (dipX + dx * t) to (dipY + dy * t)
            }

            val rotation = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
            NailOverlayAnchor(
                centerX = (centerXpx / w).coerceIn(0f, 1f),
                centerY = (centerYpx / h).coerceIn(0f, 1f),
                width = (nailWidPx / w).coerceIn(MIN_NAIL_WIDTH_NORM, MAX_NAIL_WIDTH_NORM),
                height = (nailLenPx / h).coerceIn(MIN_NAIL_HEIGHT_NORM, MAX_NAIL_HEIGHT_NORM),
                rotationDegrees = rotation,
            )
        }
        val plausible = anchors.count { nail ->
            nail.centerX in PLAUSIBLE_RANGE && nail.centerY in PLAUSIBLE_RANGE
        }
        return anchors.takeIf { plausible >= MIN_PLAUSIBLE_NAILS }
    }

    data class NormalizedPoint(val x: Float, val y: Float)

    private data class Finger(
        val tip: Int,
        val dip: Int,
        val pip: Int,
        val widthScale: Float,
        val lengthScale: Float,
    )

    private val PLAUSIBLE_RANGE = 0.02f..0.98f

    const val PREVIEW_ASPECT = 3f / 4f
    private const val MIN_LANDMARKS = 21
    private const val MIN_PLAUSIBLE_NAILS = 3
    private const val SHORT_TIP_DIP_PX = 18f
    private const val NAIL_CENTER_ALONG = 0.72f
    private const val MIN_NAIL_LEN_PX = 14f
    private const val MAX_NAIL_LEN_PX = 160f
    private const val MIN_NAIL_WID_PX = 10f
    private const val MAX_NAIL_WID_PX = 110f
    private const val MIN_NAIL_WIDTH_NORM = 0.018f
    private const val MAX_NAIL_WIDTH_NORM = 0.14f
    private const val MIN_NAIL_HEIGHT_NORM = 0.018f
    private const val MAX_NAIL_HEIGHT_NORM = 0.14f
}
