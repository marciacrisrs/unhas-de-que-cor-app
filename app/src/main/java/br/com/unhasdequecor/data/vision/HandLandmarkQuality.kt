package br.com.unhasdequecor.data.vision

import br.com.unhasdequecor.data.vision.nail.DetectionConfidenceFloor
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates
import kotlin.math.hypot

/**
 * Qualidade geométrica dos landmarks para escolher entre variantes de inferência
 * (contraluz / enhance / rotação) sem parar na primeira presence “forte” frágil.
 */
object HandLandmarkQuality {
    /** Tips MediaPipe: polegar, indicador, médio, anelar, mindinho. */
    private val TIP_INDICES = intArrayOf(4, 8, 12, 16, 20)

    /**
     * Span normalizado entre tips (0–1). Punho/oclusão → tips colados (baixo);
     * mão aberta → tips espalhados (alto).
     */
    fun tipSpanNorm(points: List<ImageCoordinates.NormPoint>): Float {
        if (points.size < HandLandmarks.MIN_POINTS) return 0f
        var minX = 1f
        var maxX = 0f
        var minY = 1f
        var maxY = 0f
        for (idx in TIP_INDICES) {
            val p = points[idx]
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        return hypot((maxX - minX).toDouble(), (maxY - minY).toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
    }

    /**
     * Score para ranquear variantes: presence + span das tips.
     * Evita preferir enhance com presence alta em landmarks colapsados.
     */
    fun rankingScore(presenceScore: Float, tipSpan: Float): Float {
        val p = presenceScore.coerceIn(0f, 1f)
        val s = tipSpan.coerceIn(0f, 1f)
        return (PRESENCE_WEIGHT * p + SPAN_WEIGHT * s).coerceIn(0f, 1f)
    }

    fun rankingScore(landmarks: HandLandmarks): Float =
        rankingScore(landmarks.presenceScore, tipSpanNorm(landmarks.points))

    /** Só para de buscar variantes com presence bem acima do piso STRONG. */
    fun shouldStopSearching(bestPresence: Float): Boolean =
        bestPresence >= DetectionConfidenceFloor.HAND_PRESENCE_EARLY_STOP

    private const val PRESENCE_WEIGHT = 0.72f
    private const val SPAN_WEIGHT = 0.28f
}
