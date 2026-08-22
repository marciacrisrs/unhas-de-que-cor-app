package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Suavização temporal das ROIs/máscaras entre frames (câmera ao vivo).
 *
 * Contrato importante: a máscara e a ROI precisam representar a mesma origem
 * geométrica. Em mudanças de escala/rotação, o par `next.mask + next.roi` é
 * mantido junto. Em translação pura, a origem da máscara acompanha a ROI.
 *
 * Singleton compartilhado por STILL e Live: [reset] e [stabilize] são
 * sincronizados para não corromper o estado interno quando a tela Live
 * fecha no meio de um frame e o Result volta a detectar.
 */
@Singleton
class NailTracker @Inject constructor() {
    private val previous = linkedMapOf<Finger, DetectedNail>()
    private val velocity = linkedMapOf<Finger, PixelPoint>()
    private val invalidGeometryRecoveryFrames = linkedMapOf<Finger, Int>()

    @Volatile
    var lastPredictionReport: NailPredictionReport = NailPredictionReport.stable()
        private set

    @Synchronized
    fun reset() {
        previous.clear()
        velocity.clear()
        invalidGeometryRecoveryFrames.clear()
        lastPredictionReport = NailPredictionReport.stable()
    }

    @Synchronized
    fun stabilize(current: List<DetectedNail>): List<DetectedNail> {
        if (current.isEmpty()) {
            lastPredictionReport = NailPredictionReport.recovery()
            return emptyList()
        }

        var report = NailPredictionReport.stable()
        val out = ArrayList<DetectedNail>(current.size)
        for (nail in current) {
            val result = stabilizeNail(nail)
            if (result.nail != null && DetectionConfidenceFloor.acceptsNail(result.nail.confidence)) {
                out += result.nail
                previous[nail.finger] = result.nail
            }
            report = result.report
        }
        lastPredictionReport = report
        return out
    }

    private fun stabilizeNail(nail: DetectedNail): StabilizedNail {
        val geometry = NailGeometryValidator.validate(nail)
        if (!geometry.valid) {
            return recoverInvalidGeometry(nail, geometry.reason)
        }

        invalidGeometryRecoveryFrames.remove(nail.finger)

        val prev = previous[nail.finger]
            ?: return StabilizedNail(
                nail = nail,
                report = NailPredictionReport.stable(),
            ).also {
                velocity[nail.finger] = PixelPoint(0f, 0f)
            }

        val motion = classifyMotion(prev, nail)
        return when {
            nail.confidence >= prev.confidence -> acceptConfidentFrame(nail, motion)
            isRecoverableLowConfidence(nail, prev) -> recoverLowConfidence(nail, prev, motion)
            else -> blendLowConfidence(prev, nail, motion)
        }
    }

    private fun recoverInvalidGeometry(
        nail: DetectedNail,
        reason: NailGeometryValidator.Reason,
    ): StabilizedNail {
        val prev = previous[nail.finger]
        val usedFrames = invalidGeometryRecoveryFrames[nail.finger] ?: 0
        if (prev == null || usedFrames >= MAX_INVALID_GEOMETRY_RECOVERY_FRAMES) {
            return StabilizedNail(
                nail = null,
                report = NailPredictionReport(
                    predictionApplied = false,
                    trans = PixelPoint(0f, 0f),
                    predictionReason = NailPredictionReason.GEOMETRY_REJECTED,
                    geometryReason = reason,
                ),
            )
        }

        invalidGeometryRecoveryFrames[nail.finger] = usedFrames + 1
        val recovered = prev.copy(
            confidence = prev.confidence * INVALID_RECOVERY_CONFIDENCE_DECAY,
        )
        return StabilizedNail(
            nail = recovered,
            report = NailPredictionReport(
                predictionApplied = false,
                trans = PixelPoint(0f, 0f),
                predictionReason = NailPredictionReason.RECOVERY,
                geometryReason = reason,
            ),
        )
    }

    private fun acceptConfidentFrame(
        nail: DetectedNail,
        motion: MotionClassification,
    ): StabilizedNail {
        velocity[nail.finger] = if (motion.isTranslational) motion.delta else PixelPoint(0f, 0f)
        return StabilizedNail(
            nail = nail,
            report = NailPredictionReport(
                predictionApplied = false,
                trans = motion.delta,
                predictionReason = motion.reason,
            ),
        )
    }

    private fun recoverLowConfidence(
        nail: DetectedNail,
        prev: DetectedNail,
        motion: MotionClassification,
    ): StabilizedNail {
        if (!motion.isTranslational) {
            velocity[nail.finger] = PixelPoint(0f, 0f)
            return StabilizedNail(
                nail = nail,
                report = NailPredictionReport(
                    predictionApplied = false,
                    trans = motion.delta,
                    predictionReason = NailPredictionReason.RECOVERY,
                ),
            )
        }

        val prediction = velocity[nail.finger] ?: motion.delta
        val predicted = translate(prev, prediction)
        return StabilizedNail(
            nail = predicted.copy(confidence = prev.confidence * RECOVERY_CONFIDENCE_DECAY),
            report = NailPredictionReport(
                predictionApplied = true,
                trans = prediction,
                predictionReason = NailPredictionReason.APPLIED,
            ),
        )
    }

    private fun blendLowConfidence(
        prev: DetectedNail,
        next: DetectedNail,
        motion: MotionClassification,
    ): StabilizedNail {
        velocity[next.finger] = motion.delta
        return StabilizedNail(
            nail = blend(prev, next, alpha = BLEND_ALPHA),
            report = NailPredictionReport(
                predictionApplied = false,
                trans = motion.delta,
                predictionReason = motion.reason,
            ),
        )
    }

    private fun classifyMotion(prev: DetectedNail, next: DetectedNail): MotionClassification {
        val delta = translation(prev, next)
        val rotationDelta = abs(shortestAngleDelta(prev.roi.rotationDegrees, next.roi.rotationDegrees))
        val scaleChange = scaleDelta(prev, next)
        val reason = when {
            rotationDelta > ROTATION_REJECTION_DEG -> NailPredictionReason.ROTATION
            scaleChange > SCALE_REJECTION_RATIO -> NailPredictionReason.SCALE
            else -> NailPredictionReason.STABLE
        }
        return MotionClassification(
            delta = delta,
            isTranslational = reason == NailPredictionReason.STABLE,
            reason = reason,
        )
    }

    private fun isRecoverableLowConfidence(
        nail: DetectedNail,
        prev: DetectedNail,
    ): Boolean =
        !DetectionConfidenceFloor.acceptsNail(nail.confidence) &&
            DetectionConfidenceFloor.acceptsNail(prev.confidence)

    private fun blend(prev: DetectedNail, next: DetectedNail, alpha: Float): DetectedNail {
        val t = alpha.coerceIn(0f, 1f)
        val motion = classifyMotion(prev, next)

        // Mudança relevante de escala/rotação ou incompatibilidade de máscara:
        // nunca combine geometria antiga com uma máscara nova. O frame NEXT é
        // a unidade coerente de máscara + ROI.
        if (!motion.isTranslational || !sameMaskShape(prev.mask, next.mask)) return next

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

        val mask = next.mask.copy(
            originX = lerp(prev.mask.originX, next.mask.originX, t),
            originY = lerp(prev.mask.originY, next.mask.originY, t),
        )

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
        const val INVALID_RECOVERY_CONFIDENCE_DECAY = 0.94f
        const val MAX_INVALID_GEOMETRY_RECOVERY_FRAMES = 2
    }
}

private data class MotionClassification(
    val delta: PixelPoint,
    val isTranslational: Boolean,
    val reason: NailPredictionReason,
)

private data class StabilizedNail(
    val nail: DetectedNail?,
    val report: NailPredictionReport,
)

enum class NailPredictionReason {
    APPLIED,
    ROTATION,
    SCALE,
    RECOVERY,
    GEOMETRY_REJECTED,
    STABLE,
}

data class NailPredictionReport(
    val predictionApplied: Boolean,
    val trans: PixelPoint,
    val predictionReason: NailPredictionReason,
    val geometryReason: NailGeometryValidator.Reason? = null,
) {
    companion object {
        fun stable() = NailPredictionReport(
            predictionApplied = false,
            trans = PixelPoint(0f, 0f),
            predictionReason = NailPredictionReason.STABLE,
        )

        fun recovery() = NailPredictionReport(
            predictionApplied = false,
            trans = PixelPoint(0f, 0f),
            predictionReason = NailPredictionReason.RECOVERY,
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
