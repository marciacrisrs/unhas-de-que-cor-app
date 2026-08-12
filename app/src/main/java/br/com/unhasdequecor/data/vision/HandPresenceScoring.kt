package br.com.unhasdequecor.data.vision

/**
 * Combina score de handedness MediaPipe com presence média das tips.
 *
 * Flash / glare nas pontas pode zerar tip-presence mesmo com a mão bem classificada —
 * não deixar as tips sozinhas derrubarem a detecção abaixo do piso de aceite.
 */
object HandPresenceScoring {
    private const val HANDEDNESS_WEIGHT = 0.35f
    private const val TIP_WEIGHT = 0.65f

    fun score(handednessScore: Float, tipPresence: Float): Float {
        val hand = handednessScore.coerceIn(0f, 1f)
        val tip = tipPresence.coerceIn(0f, 1f).takeIf { it > 0f } ?: hand
        val blended = hand * HANDEDNESS_WEIGHT + tip * TIP_WEIGHT
        // Piso: se o modelo já apontou uma mão, tip glare não pode anular.
        return maxOf(blended, tip, hand).coerceIn(0f, 1f)
    }
}
