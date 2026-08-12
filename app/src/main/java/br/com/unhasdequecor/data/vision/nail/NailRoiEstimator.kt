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
 *
 * Largura alinhada ao [NailLandmarkMapper] (proporcional ao comprimento tip–DIP).
 * Polegar usa eixo MCP→TIP (sem DIP distinto no MediaPipe).
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
        val tipMcp = ImageCoordinates.distancePx(tip, mcp)
        val scales = scalesFor(finger)

        // Polegar: DIP=PIP no MediaPipe → tipDip colapsa com frequência; usa MCP→TIP.
        val thumbMode = finger == Finger.THUMB
        val facing = !thumbMode && tipDip < SHORT_TIP_DIP_PX

        val nailLen = when {
            thumbMode -> (tipMcp * THUMB_LENGTH_SCALE).coerceIn(MIN_NAIL_LEN, MAX_NAIL_LEN)
            facing -> (tipPip * FACING_LENGTH_SCALE).coerceIn(MIN_NAIL_LEN, MAX_NAIL_LEN)
            else -> (tipDip * scales.lengthScale).coerceIn(MIN_NAIL_LEN, MAX_NAIL_LEN)
        }
        val nailWidth = (nailLen * scales.widthScale).coerceIn(MIN_NAIL_W, MAX_NAIL_W)

        val axisStart = when {
            thumbMode -> mcp
            facing -> pip
            else -> dip
        }
        val dirX = tip.x - axisStart.x
        val dirY = tip.y - axisStart.y
        val dirLen = hypot(dirX.toDouble(), dirY.toDouble()).toFloat().coerceAtLeast(1f)
        val ux = dirX / dirLen
        val uy = dirY / dirLen
        val px = -uy
        val py = ux

        // Centro proximal à ponta; leve overshoot da tip landmark (placa real passa um pouco).
        val centerT = when {
            thumbMode -> THUMB_CENTER
            facing -> FACING_CENTER
            else -> CENTER_ALONG
        }
        val overshootBase = if (thumbMode) tipMcp else tipDip
        val cx = axisStart.x + (tip.x - axisStart.x) * centerT + ux * overshootBase * TIP_OVERSHOOT
        val cy = axisStart.y + (tip.y - axisStart.y) * centerT + uy * overshootBase * TIP_OVERSHOOT

        val halfLen = nailLen * 0.50f
        val halfWBase = nailWidth * 0.50f
        // Almond suave (não pontiagudo): cobre a placa sem invadir tanto a pele.
        val tipHalfW = halfWBase * TIP_WIDTH_FACTOR
        val midHalfW = halfWBase * MID_WIDTH_FACTOR
        val cuticleHalfW = halfWBase * CUTICLE_WIDTH_FACTOR

        val tipPt = PixelPoint(cx + ux * halfLen, cy + uy * halfLen)
        val cuticlePt = PixelPoint(cx - ux * halfLen * CUTICLE_BACK, cy - uy * halfLen * CUTICLE_BACK)
        val mid = PixelPoint(cx + ux * halfLen * MID_FORWARD, cy + uy * halfLen * MID_FORWARD)

        val polygon = listOf(
            PixelPoint(tipPt.x + px * tipHalfW * TIP_POINT_FACTOR, tipPt.y + py * tipHalfW * TIP_POINT_FACTOR),
            PixelPoint(mid.x + px * midHalfW, mid.y + py * midHalfW),
            PixelPoint(cuticlePt.x + px * cuticleHalfW, cuticlePt.y + py * cuticleHalfW),
            PixelPoint(cuticlePt.x - px * cuticleHalfW, cuticlePt.y - py * cuticleHalfW),
            PixelPoint(mid.x - px * midHalfW, mid.y - py * midHalfW),
            PixelPoint(tipPt.x - px * tipHalfW * TIP_POINT_FACTOR, tipPt.y - py * tipHalfW * TIP_POINT_FACTOR),
        )

        val pad = max(nailWidth, nailLen) * PAD_SCALE + PAD_EXTRA
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
            tipMcp = tipMcp,
            nailLen = nailLen,
            nailWidth = nailWidth,
            thumbMode = thumbMode,
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

    private fun scalesFor(finger: Finger): FingerScale = when (finger) {
        // Ligeiramente mais largas/longas: landmarks tendem a subestimar a placa real.
        Finger.THUMB -> FingerScale(widthScale = 0.82f, lengthScale = 0.90f)
        Finger.INDEX -> FingerScale(widthScale = 0.74f, lengthScale = 0.96f)
        Finger.MIDDLE -> FingerScale(widthScale = 0.76f, lengthScale = 0.98f)
        Finger.RING -> FingerScale(widthScale = 0.72f, lengthScale = 0.96f)
        Finger.PINKY -> FingerScale(widthScale = 0.68f, lengthScale = 0.92f)
    }

    private fun geometricConfidence(
        tipDip: Float,
        tipPip: Float,
        tipMcp: Float,
        nailLen: Float,
        nailWidth: Float,
        thumbMode: Boolean,
        facing: Boolean,
        presence: Float,
    ): Float {
        val axisOk = when {
            thumbMode -> tipMcp > 16f
            facing -> tipPip > 12f
            else -> tipDip > 10f
        }
        if (!axisOk) return 0.15f
        val aspect = nailLen / nailWidth.coerceAtLeast(1f)
        val aspectScore = when {
            aspect in 1.15f..2.2f -> 1f
            aspect in 0.9f..2.8f -> 0.75f
            else -> 0.4f
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

    private data class FingerScale(val widthScale: Float, val lengthScale: Float)

    private companion object {
        const val SHORT_TIP_DIP_PX = 16f
        const val FACING_LENGTH_SCALE = 0.48f
        const val THUMB_LENGTH_SCALE = 0.38f
        const val FACING_CENTER = 0.88f
        const val THUMB_CENTER = 0.78f
        /** Alinhado ao [NailLandmarkMapper] — centro da placa, não além da ponta. */
        const val CENTER_ALONG = 0.58f
        const val TIP_OVERSHOOT = 0.02f
        const val TIP_WIDTH_FACTOR = 0.82f
        const val MID_WIDTH_FACTOR = 1.12f
        const val CUTICLE_WIDTH_FACTOR = 0.86f
        const val TIP_POINT_FACTOR = 0.70f
        const val CUTICLE_BACK = 0.90f
        const val MID_FORWARD = 0.20f
        const val PAD_SCALE = 0.22f
        const val PAD_EXTRA = 2f
        const val MIN_NAIL_LEN = 14f
        const val MAX_NAIL_LEN = 160f
        const val MIN_NAIL_W = 10f
        const val MAX_NAIL_W = 110f
        const val PRESENCE_WEIGHT = 0.30f
        const val ASPECT_WEIGHT = 0.40f
        const val SIZE_WEIGHT = 0.30f
    }
}
