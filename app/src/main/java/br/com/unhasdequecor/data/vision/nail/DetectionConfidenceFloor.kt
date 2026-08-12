package br.com.unhasdequecor.data.vision.nail

/**
 * Pisos de confiança da **detecção** (mão + unhas) — fonte única.
 *
 * Evita claim FULL / máscaras com landmarks ou ROIs fracos (contraluz, falso positivo
 * pós-enhance). Valores alinhados a MediaPipe IMAGE e à anatomia da placa.
 */
object DetectionConfidenceFloor {
    /** Limiar MediaPipe Hand Landmarker (IMAGE). */
    const val MEDIAPIPE_MIN = 0.08f

    /** Abaixo: tratar como mão não detectada (ruído / enhance frágil). */
    const val HAND_PRESENCE_ACCEPT = 0.12f

    /** Acima: presence forte o bastante para FULL (com máscaras de qualidade). */
    const val HAND_PRESENCE_STRONG = 0.55f

    /** ROI geométrico mínimo para tentar segmentar. */
    const val ROI_GEOMETRIC_MIN = 0.24f

    /** Confiança combinada (geo+seg) mínima para pintar / contar máscara. */
    const val NAIL_COMBINED_MIN = 0.32f

    /**
     * Confiança mínima por unha para contar no claim FULL
     * (“Prévia na sua mão”) — acima do piso de pintura.
     */
    const val NAIL_FULL_MIN = 0.45f

    const val MIN_MASKS_FOR_FULL = 3

    fun acceptsHandPresence(presenceScore: Float): Boolean =
        presenceScore.coerceIn(0f, 1f) >= HAND_PRESENCE_ACCEPT

    fun isStrongHandPresence(presenceScore: Float): Boolean =
        presenceScore.coerceIn(0f, 1f) >= HAND_PRESENCE_STRONG

    fun acceptsRoi(geometricConfidence: Float): Boolean =
        geometricConfidence >= ROI_GEOMETRIC_MIN

    fun acceptsNail(confidence: Float): Boolean =
        confidence >= NAIL_COMBINED_MIN

    fun isFullQualityNail(confidence: Float): Boolean =
        confidence >= NAIL_FULL_MIN

    fun countPaintable(nails: List<DetectedNail>): Int =
        nails.count { acceptsNail(it.confidence) }

    fun countFullQuality(nails: List<DetectedNail>): Int =
        nails.count { isFullQualityNail(it.confidence) }

    /** TRUE se há ≥ [MIN_MASKS_FOR_FULL] unhas acima de [NAIL_FULL_MIN]. */
    fun meetsFullNailFloor(nails: List<DetectedNail>): Boolean =
        countFullQuality(nails) >= MIN_MASKS_FOR_FULL
}
