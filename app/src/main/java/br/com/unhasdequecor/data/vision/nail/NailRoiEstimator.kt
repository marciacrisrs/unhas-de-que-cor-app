package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estima ROI + polígono almond da unha a partir de MCP/PIP/DIP/TIP.
 * Tudo em pixels da imagem (independente de resolução/aspect).
 */
@Singleton
class NailRoiEstimator @Inject constructor() {

    fun estimateAll(hand: HandLandmarks): List<NailRoi> =
        Finger.ALL.mapNotNull { finger -> estimate(hand, finger) }

    fun estimate(hand: HandLandmarks, finger: Finger): NailRoi? {
        val w = hand.imageWidth
        val h = hand.imageHeight
        val mcp = ImageCoordinates.toPixel(hand.point(finger.mcpIndex), w, h)
        val pip = ImageCoordinates.toPixel(hand.point(finger.pipIndex), w, h)
        val dip = ImageCoordinates.toPixel(hand.point(finger.dipIndex), w, h)
        val tip = ImageCoordinates.toPixel(hand.point(finger.tipIndex), w, h)

        val tipDip = ImageCoordinates.distancePx(tip, dip)
        val tipPip = ImageCoordinates.distancePx(tip, pip)
        val pipMcp = ImageCoordinates.distancePx(pip, mcp)
        val facing = tipDip < SHORT_TIP_DIP_PX

        val axisLen = if (facing) tipPip.coerceAtLeast(1f) else tipDip.coerceAtLeast(1f)
        val nailLen = (if (facing) tipPip * 0.40f else tipDip * LENGTH_SCALE)
            .coerceIn(MIN_NAIL_LEN, MAX_NAIL_LEN)
        // Largura do dedo ≈ escala da falange média; unha um pouco mais estreita.
        val fingerWidth = (pipMcp * 0.28f + axisLen * 0.22f).coerceIn(MIN_FINGER_W, MAX_FINGER_W)
        val nailWidth = (fingerWidth * WIDTH_SCALE).coerceIn(MIN_NAIL_W, MAX_NAIL_W)

        val axisStart = if (facing) pip else dip
        val dirX = tip.x - axisStart.x
        val dirY = tip.y - axisStart.y
        val dirLen = hypot(dirX.toDouble(), dirY.toDouble()).toFloat().coerceAtLeast(1f)
        val ux = dirX / dirLen
        val uy = dirY / dirLen
        val px = -uy
        val py = ux

        // Centro da placa: 72% do caminho DIP/PIP → TIP (não ultrapassa a tip).
        val centerT = if (facing) 0.90f else CENTER_ALONG
        val cx = axisStart.x + (tip.x - axisStart.x) * centerT
        val cy = axisStart.y + (tip.y - axisStart.y) * centerT

        val halfLen = nailLen * 0.50f
        val halfWBase = nailWidth * 0.50f
        // Almond: mais estreito na tip e cutícula.
        val tipHalfW = halfWBase * 0.55f
        val midHalfW = halfWBase * 1.05f
        val cuticleHalfW = halfWBase * 0.75f

        val tipPt = PixelPoint(cx + ux * halfLen, cy + uy * halfLen)
        val cuticlePt = PixelPoint(cx - ux * halfLen * 0.85f, cy - uy * halfLen * 0.85f)
        val mid = PixelPoint(cx + ux * halfLen * 0.15f, cy + uy * halfLen * 0.15f)

        val polygon = listOf(
            PixelPoint(tipPt.x + px * tipHalfW * 0.15f, tipPt.y + py * tipHalfW * 0.15f),
            PixelPoint(mid.x + px * midHalfW, mid.y + py * midHalfW),
            PixelPoint(cuticlePt.x + px * cuticleHalfW, cuticlePt.y + py * cuticleHalfW),
            PixelPoint(cuticlePt.x - px * cuticleHalfW, cuticlePt.y - py * cuticleHalfW),
            PixelPoint(mid.x - px * midHalfW, mid.y - py * midHalfW),
            PixelPoint(tipPt.x - px * tipHalfW * 0.15f, tipPt.y - py * tipHalfW * 0.15f),
        )

        val pad = max(nailWidth, nailLen) * 0.35f + 4f
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in polygon) {
            minX = min(minX, p.x)
            minY = min(minY, p.y)
            maxX = max(maxX, p.x)
            maxY = max(maxY, p.y)
        }
        val bounds = PixelRect(
            left = (minX - pad).toInt().coerceIn(0, w - 1),
            top = (minY - pad).toInt().coerceIn(0, h - 1),
            right = (maxX + pad).toInt().coerceIn(1, w),
            bottom = (maxY + pad).toInt().coerceIn(1, h),
        )
        if (bounds.width() < 4 || bounds.height() < 4) return null

        val rotation = Math.toDegrees(atan2(dirX.toDouble(), -dirY.toDouble())).toFloat()
        val geometricConfidence = geometricConfidence(
            tipDip = tipDip,
            tipPip = tipPip,
            nailLen = nailLen,
            nailWidth = nailWidth,
            facing = facing,
            presence = hand.presenceScore,
        )

        return NailRoi(
            finger = finger,
            bounds = bounds,
            polygon = polygon,
            axisFromDip = PixelPoint(axisStart.x, axisStart.y),
            axisToTip = PixelPoint(tip.x, tip.y),
            lengthPx = nailLen,
            widthPx = nailWidth,
            rotationDegrees = rotation,
            geometricConfidence = geometricConfidence,
        )
    }

    private fun geometricConfidence(
        tipDip: Float,
        tipPip: Float,
        nailLen: Float,
        nailWidth: Float,
        facing: Boolean,
        presence: Float,
    ): Float {
        val axisOk = if (facing) tipPip > 12f else tipDip > 10f
        if (!axisOk) return 0.15f
        val aspect = nailLen / nailWidth.coerceAtLeast(1f)
        val aspectScore = when {
            aspect in 1.1f..2.8f -> 1f
            aspect in 0.8f..3.5f -> 0.7f
            else -> 0.35f
        }
        val sizeScore = when {
            nailLen in 16f..140f -> 1f
            nailLen in 10f..180f -> 0.65f
            else -> 0.3f
        }
        return (
            PRESENCE_WEIGHT * presence.coerceIn(0f, 1f) +
                ASPECT_WEIGHT * aspectScore +
                SIZE_WEIGHT * sizeScore
            ).coerceIn(0f, 1f)
    }

    private companion object {
        const val SHORT_TIP_DIP_PX = 18f
        const val LENGTH_SCALE = 0.90f
        const val WIDTH_SCALE = 0.72f
        const val CENTER_ALONG = 0.72f
        const val MIN_NAIL_LEN = 12f
        const val MAX_NAIL_LEN = 160f
        const val MIN_NAIL_W = 8f
        const val MAX_NAIL_W = 100f
        const val MIN_FINGER_W = 10f
        const val MAX_FINGER_W = 120f
        const val PRESENCE_WEIGHT = 0.35f
        const val ASPECT_WEIGHT = 0.35f
        const val SIZE_WEIGHT = 0.30f
    }
}
