package br.com.unhasdequecor.data.vision

import br.com.unhasdequecor.data.vision.nail.DetectionConfidenceFloor

/**
 * Combina score de handedness MediaPipe com presence média das tips.
 *
 * Flash / glare nas pontas pode zerar tip-presence mesmo com a mão bem classificada —
 * mantém a detecção acima do piso de aceite, mas **não** eleva sozinho a STRONG/FULL.
 */
object HandPresenceScoring {
    /** Abaixo disso tip-presence é tratada como glare (não confiar para claim forte). */
    const val TIP_GLARE_MAX = 0.15f

    private const val HANDEDNESS_WEIGHT = 0.35f
    private const val TIP_WEIGHT = 0.65f
    /** Logo abaixo de STRONG — aceita mão, bloqueia FULL por handedness+glare. */
    private val MAX_WHEN_TIP_GLARE =
        DetectionConfidenceFloor.HAND_PRESENCE_STRONG - 0.01f

    fun score(handednessScore: Float, tipPresence: Float): Float {
        val hand = handednessScore.coerceIn(0f, 1f)
        val rawTip = tipPresence.coerceIn(0f, 1f)
        if (rawTip <= 0f) {
            // IMAGE mode sem tip-presence → confia no handedness.
            return hand
        }
        val blended = hand * HANDEDNESS_WEIGHT + rawTip * TIP_WEIGHT
        if (rawTip < TIP_GLARE_MAX) {
            val capped = minOf(hand, MAX_WHEN_TIP_GLARE)
            return maxOf(blended, capped).coerceIn(0f, 1f)
        }
        return maxOf(blended, rawTip).coerceIn(0f, 1f)
    }
}
