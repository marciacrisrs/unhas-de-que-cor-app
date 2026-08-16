package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import kotlin.math.abs
import kotlin.math.hypot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Suavização temporal das ROIs/máscaras entre frames (câmera ao vivo).
 *
 * Contrato importante: a máscara e a ROI precisam representar a mesma origem
 * geométrica. Em mudanças de escala/rotação, o par `next.mask + next.roi` é
 * mantido junto. Em translação pura, a origem da máscara acompanha a ROI.
 */
@Singleton
class NailTracker @Inject constructor() {
    private val previous = linkedMapOf<Finger, DetectedNail>()
    private val velocity = linkedMapOf<Finger, PixelPoint>()

    @Volatile
    var lastPredictionReport: NailPredictionReport = NailPredictionReport.stable()
        private set

    fun reset() {
        previous.clear()
        velocity.clear()
        lastPredictionReport = NailPredictionReport.stable()
    }

    fun stabilize(current: List<DetectedNail>): List<DetectedNail> {
        if (current.isEmpty()) {
            lastPredictionReport = NailPredictionReport(
                predictionApplied = false,
                trans = PixelPoint(0f, 0f),
                predictionReason = NailPredictionReason.RECOVERY,
            )
            return emptyList()
        }

        var report = NailPredictionReport.stable()
        val out = ArrayList<DetectedNail>(current.size)
        for (nail in current) {
            val prev = previous[nail.finger]
            if (prev == null) {
                out += nail
                previous[nail.finger] = nail
                velocity[nail.finger] = PixelPoint(0f, 0f)
                continue
            }

            val delta = translation(prev, nail)
            val rotationDelta = abs(shortestAngleDelta(prev.roi.rotationDegrees, nail.roi.rotationDegrees))
            val scaleDelta = scaleDelta(prev, nail)
            val translational = rotationDelta <= ROTATION_REJECTION_DEG && scaleDelta <= SCALE_REJECTION_RATIO

            val stabilized = when {
                nail.confidence >= prev.confidence -> {
                    if (translational) {
                        velocity[nail.finger] = delta
                        report = NailPredictionReport(
                            predictionApplied = false,
                            trans = delta,
                            predictionReason = NailPredictionReason.STABLE,
                        )
                    } else {
                        velocity[nail.finger] = PixelPoint(0f, 0f)
                        report = NailPredictionReport(
                            predictionApplied = false,
                            trans = delta,
                            predictionReason = if (rotationDelta > ROTATION_REJECTION_DEG) {
                                NailPredictionReason.ROTATION
                            } else {
                                NailPredictionReason.SCALE
                            },
                        )
                    }
                    nail
                }

                !DetectionConfidenceFloor.acceptsNail(nail.confidence) &&
                    DetectionConfidenceFloor.acceptsNail(prev.confidence) -> {
                    if (translational) {
                        val predicted = translate(prev, velocity[nail.finger] ?: delta)
                        report = NailPredictionReport(
                            predictionApplied = true,
                            trans = velocity[nail.finger] ?: delta,
                            predictionReason = NailPredictionReason.APPLIED,
                        )
                        predicted.copy(confidence = prev.confidence * RECOVERY_CONFIDENCE_DECAY)
                    } else {
                        velocity[nail.finger] = PixelPoint(0f, 0f)
                        report = NailPredictionReport(
                            predictionApplied = false,
                            trans = delta,
                            predictionReason = NailPredictionReason.RECOVERY,
                        )
                        nail
                    }
                }

                else -> {
                    val blended = blend(prev, nail, alpha = BLEND_ALPHA)
                    velocity[nail.finger] = delta
                    report = NailPredictionReport(
                        predictionApplied = false,
                        trans = delta,
                        predictionReason = if (rotationDelta > ROTATION_REJECTION_DEG) {
                            NailPredictionReason.ROTATION
                        } else if (scaleDelta > SCALE_REJECTION_RATIO) {
                            NailPredictionReason.SCALE
                        } else {
                            NailPredictionReason.STABLE
                        },
                    )
                    blended
                }
            }

            if (DetectionConfidenceFloor.acceptsNail(stabilized.confidence)) {
                out += stabilized
                previous[nail.finger] = stabilized
            }
        }
        lastPredictionReport = report
        return out
    }

    private fun blend(prev: DetectedNail, next: DetectedNail, alpha: Float): DetectedNail {
        val t = alpha.coerceIn(0f, 1f)
        val rotationDelta = abs(shortestAngleDelta(prev.roi.rotationDegrees, next.roi.rotationDegrees))
        val scaleDelta = scaleDelta(prev, next)

        // Mudança relevante de escala/rotação: nunca combine geometria antiga
        // com máscara nova. O frame NEXT é a unidade coerente.
        if (rotationDelta > ROTATION_REJECTION_DEG || scaleDelta > SCALE_REJECTION_RATIO) {
            return next
        }

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
            lengthPx = lerp(prev.roi.lengthPx, next.roi.lengthPx, t),
            widthPx = lerp(prev.roi.widthPx, next.roi.widthPx, t),
            rotationDegrees = lerpAngle(prev.roi.rotationDegrees, next.roi.rotationDegrees, t),
            geometricConfidence = maxOf(prev.roi.geometricConfidence, next.roi.geometricConfidence),
        )

        val mask = when {
            sameMaskShape(prev.mask, next.mask) -> {
                val originX = lerp(prev.mask.originX, next.mask.originX, t)
                val originY = lerp(prev.mask.originY, next.mask.originY, t)
                next.mask.copy(originX = originX, originY = originY)
            }
            else -> next.mask
        }

        return DetectedNail(
            finger = next.finger,
            roi = roi,
            mask = mask,
            confidence = lerp(prev.confidence, next.confidence, t),
        )
    }

    private fun translate(source: DetectedNail, delta: PixelPoint): DetectedNail {
        val roi = source.roi.copy(
            bounds = source.roi.bounds.translate(delta),
            polygon = source.roi.polygon.map { PixelPoint(it.x + delta.x, it.y + delta.y) },
            axisFromDip = PixelPoint(source.roi.axisFromDip.x + delta.x, source.roi.axisFromDip.y + delta.y),
            axisToTip = PixelPoint(source.roi.axisToTip.x + delta.x, source.roi.axisToTip.y + delta.y),
        )
        return source.copy(
            roi = roi,
            mask = source.mask.copy(
                originX = source.mask.originX + delta.x.toInt(),
                originY = source.mask.originY + delta.y.toInt(),
            ),
        )
    }

    private fun translation(prev: DetectedNail, next: DetectedNail): PixelPoint =
        PixelPoint(
            x = next.roi.axisToTip.x - prev.roi.axisToTip.x,
            y = next.roi.axisToTip.y - prev.roi.axisToTip.y,
        )

    private fun scaleDelta(prev: DetectedNail, next: DetectedNail): Float {
        val prevLength = prev.roi.lengthPx.coerceAtLeast(1f)
        val prevWidth = prev.roi.widthPx.coerceAtLeast(1f)
        val lengthRatio = next.roi.lengthPx / prevLength
        val widthRatio = next.roi.widthPx / prevWidth
        return maxOf(abs(lengthRatio - 1f), abs(widthRatio - 1f))
    }

    private fun sameMaskShape(a: NailMask, b: NailMask): Boolean =
        a.width == b.width && a.height == b.height

    private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt()

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun lerpAngle(a: Float, b: Float, t: Float): Float =
        a + shortestAngleDelta(a, b) * t

    private fun shortestAngleDelta(a: Float, b: Float): Float {
        var delta = b - a
        while (delta > HALF_TURN_DEG) delta -= FULL_TURN_DEG
        while (delta < -HALF_TURN_DEG) delta += FULL_TURN_DEG
        return delta
    }

    private companion object {
        const val HALF_TURN_DEG = 180f
        const val FULL_TURN_DEG = 360f
        const val ROTATION_REJECTION_DEG = 8f
        const val SCALE_REJECTION_RATIO = 0.15f
        const val BLEND_ALPHA = 0.55f
        const val RECOVERY_CONFIDENCE_DECAY = 0.92f
    }
}

enum class NailPredictionReason {
    APPLIED,
    ROTATION,
    SCALE,
    RECOVERY,
    STABLE,
}

data class NailPredictionReport(
    val predictionApplied: Boolean,
    val trans: PixelPoint,
    val predictionReason: NailPredictionReason,
) {
    companion object {
        fun stable() = NailPredictionReport(
            predictionApplied = false,
            trans = PixelPoint(0f, 0f),
            predictionReason = NailPredictionReason.STABLE,
        )
    }
}

private fun PixelRect.translate(delta: PixelPoint): PixelRect =
    PixelRect(
        left = left + delta.x.toInt(),
        top = top + delta.y.toInt(),
        right = right + delta.x.toInt(),
        bottom = bottom + delta.y.toInt(),
    )
