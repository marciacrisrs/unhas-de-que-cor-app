package br.com.unhasdequecor.data.vision

import br.com.unhasdequecor.data.vision.nail.DetectionConfidenceFloor
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates
import kotlin.math.hypot

/**
 * Qualidade geométrica dos landmarks para escolher entre variantes de inferência
 * (contraluz / enhance / rotação) sem parar na primeira presence “forte” frágil.
 */
object HandLandmarkQuality {
    /**
     * Span mínimo para considerar tips “não colapsadas” (ranking / STRONG).
     * Punho / oclusão tipicamente fica abaixo.
     */
    const val MIN_TIP_SPAN_FOR_EARLY_STOP = 0.18f

    /**
     * Span de mão aberta: early-stop e claim STRONG exigem pelo menos isto
     * (evita parar em semi-punho com presence alta).
     */
    const val GOOD_OPEN_TIP_SPAN = 0.28f

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
        for (idx in HandLandmarks.TIP_INDICES) {
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
     * Score para ranquear variantes.
     * Soft-gate no span: presence alta com tips colapsadas não vence mão aberta.
     * `p * (0.45 + 0.55 * s)` → span 0 limita o teto a 0.45·p.
     */
    fun rankingScore(presenceScore: Float, tipSpan: Float): Float {
        val p = presenceScore.coerceIn(0f, 1f)
        val s = tipSpan.coerceIn(0f, 1f)
        return (p * (SPAN_SOFT_BASE + SPAN_SOFT_WEIGHT * s)).coerceIn(0f, 1f)
    }

    fun rankingScore(landmarks: HandLandmarks): Float =
        rankingScore(landmarks.presenceScore, tipSpanNorm(landmarks.points))

    /**
     * Para de buscar variantes só com presence forte **e** tip-span de mão aberta.
     * Presence alta com span médio (0.18) ainda continua — flash recovery pode melhorar.
     */
    fun shouldStopSearching(presenceScore: Float, tipSpan: Float): Boolean {
        val p = presenceScore.coerceIn(0f, 1f)
        val s = tipSpan.coerceIn(0f, 1f)
        return p >= DetectionConfidenceFloor.HAND_PRESENCE_STRONG &&
            s >= GOOD_OPEN_TIP_SPAN
    }

    fun shouldStopSearching(landmarks: HandLandmarks): Boolean =
        shouldStopSearching(landmarks.presenceScore, tipSpanNorm(landmarks.points))

    /**
     * Considera um candidato vs o melhor atual (testável sem MediaPipe).
     * @return novo melhor + se deve parar a busca.
     */
    fun consider(
        currentBest: HandLandmarks?,
        candidate: HandLandmarks?,
    ): Pair<HandLandmarks?, Boolean> {
        if (candidate == null ||
            !DetectionConfidenceFloor.acceptsHandPresence(candidate.presenceScore)
        ) {
            val stop = currentBest != null && shouldStopSearching(currentBest)
            return currentBest to stop
        }
        val next =
            if (currentBest == null ||
                rankingScore(candidate) > rankingScore(currentBest)
            ) {
                candidate
            } else {
                currentBest
            }
        return next to shouldStopSearching(next)
    }

    private const val SPAN_SOFT_BASE = 0.45f
    private const val SPAN_SOFT_WEIGHT = 0.55f
}
