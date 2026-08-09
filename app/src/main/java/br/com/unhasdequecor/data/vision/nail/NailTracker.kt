package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Suavização temporal das ROIs/máscaras entre frames (câmera ao vivo).
 * Em foto estática (um frame), apenas valida confiança.
 */
@Singleton
class NailTracker @Inject constructor() {
    private val previous = linkedMapOf<Finger, DetectedNail>()

    fun reset() {
        previous.clear()
    }

    fun stabilize(current: List<DetectedNail>): List<DetectedNail> {
        if (current.isEmpty()) {
            return emptyList()
        }
        val out = ArrayList<DetectedNail>(current.size)
        for (nail in current) {
            val prev = previous[nail.finger]
            val stabilized = if (prev == null || nail.confidence >= prev.confidence) {
                nail
            } else if (nail.confidence < NailColorApplier.MIN_CONFIDENCE &&
                prev.confidence >= NailColorApplier.MIN_CONFIDENCE
            ) {
                // Detecção ruim: reutiliza máscara anterior levemente.
                prev.copy(confidence = prev.confidence * 0.92f)
            } else {
                blend(prev, nail, alpha = 0.55f)
            }
            if (stabilized.confidence >= NailColorApplier.MIN_CONFIDENCE * KEEP_FACTOR) {
                out += stabilized
                previous[nail.finger] = stabilized
            }
        }
        return out
    }

    private fun blend(prev: DetectedNail, next: DetectedNail, alpha: Float): DetectedNail {
        val useMask = if (next.confidence >= prev.confidence) next.mask else prev.mask
        val t = alpha.coerceIn(0f, 1f)
        val pb = prev.roi.bounds
        val nb = next.roi.bounds
        val bounds = PixelRect(
            left = lerp(pb.left, nb.left, t),
            top = lerp(pb.top, nb.top, t),
            right = lerp(pb.right, nb.right, t),
            bottom = lerp(pb.bottom, nb.bottom, t),
        )
        val poly = next.roi.polygon.mapIndexed { i, p ->
            val q = prev.roi.polygon.getOrNull(i) ?: p
            PixelPoint(q.x + (p.x - q.x) * t, q.y + (p.y - q.y) * t)
        }
        val roi = next.roi.copy(
            bounds = bounds,
            polygon = poly,
            lengthPx = prev.roi.lengthPx + (next.roi.lengthPx - prev.roi.lengthPx) * t,
            widthPx = prev.roi.widthPx + (next.roi.widthPx - prev.roi.widthPx) * t,
            rotationDegrees = lerpAngle(prev.roi.rotationDegrees, next.roi.rotationDegrees, t),
            geometricConfidence = maxOf(prev.roi.geometricConfidence, next.roi.geometricConfidence),
        )
        return DetectedNail(
            finger = next.finger,
            roi = roi,
            mask = useMask,
            confidence = prev.confidence * (1 - t) + next.confidence * t,
        )
    }

    private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt()

    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        var delta = b - a
        while (delta > HALF_TURN_DEG) delta -= FULL_TURN_DEG
        while (delta < -HALF_TURN_DEG) delta += FULL_TURN_DEG
        return a + delta * t
    }

    private companion object {
        const val KEEP_FACTOR = 0.9f
        const val HALF_TURN_DEG = 180f
        const val FULL_TURN_DEG = 360f
    }
}
