package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.HandLandmarks

/**
 * Confiabilidade do try-on na **mão real** (foto da usuária).
 *
 * MediaPipe pode devolver landmarks fracos (limiar baixo / contraluz).
 * Esta camada evita rotular elipse frágil como “Prévia na sua mão”.
 *
 * Pisos numéricos: [DetectionConfidenceFloor].
 */
enum class TryOnReliability {
    /** Descartar — tratar como mão não detectada. */
    REJECTED,
    /** Landmarks usable só como prévia aproximada. */
    WEAK,
    /** Presence forte — claim FULL só se também houver máscaras de qualidade. */
    STRONG,
}

enum class UserTryOnRenderMode {
    /** Máscaras/recolor confiáveis → “Prévia na sua mão”. */
    FULL,
    /** Elipse/âncoras ou detecção fraca → “Prévia aproximada”. */
    APPROXIMATE,
    /** Sem landmarks utilizáveis → “Mão não detectada”, zero overlay. */
    NONE,
}

data class UserTryOnRenderPlan(
    val mode: UserTryOnRenderMode,
    val useNailMasks: Boolean,
    val useEllipsePaint: Boolean,
    val useCanvasAnchors: Boolean,
)

object TryOnHandReliability {
    const val MIN_PRESENCE_ACCEPT = DetectionConfidenceFloor.HAND_PRESENCE_ACCEPT
    const val MIN_PRESENCE_STRONG = DetectionConfidenceFloor.HAND_PRESENCE_STRONG
    const val MIN_MASKS_FOR_FULL = DetectionConfidenceFloor.MIN_MASKS_FOR_FULL

    /**
     * Classifica só por presence. Máscaras **não** elevam para STRONG —
     * mid presence + muitas máscaras continua WEAK → APPROXIMATE (nunca FULL).
     */
    fun classify(presenceScore: Float): TryOnReliability {
        val score = presenceScore.coerceIn(0f, 1f)
        if (!DetectionConfidenceFloor.acceptsHandPresence(score)) {
            return TryOnReliability.REJECTED
        }
        if (DetectionConfidenceFloor.isStrongHandPresence(score)) {
            return TryOnReliability.STRONG
        }
        return TryOnReliability.WEAK
    }

    fun classify(landmarks: HandLandmarks): TryOnReliability =
        classify(landmarks.presenceScore)

    /**
     * Plano de renderização honesto (modo ≡ qualidade).
     * FULL exige STRONG **e** ≥ [MIN_MASKS_FOR_FULL] unhas com confiança ≥ [DetectionConfidenceFloor.NAIL_FULL_MIN].
     * Elipse sozinha nunca é FULL.
     *
     * @param paintableNailCount unhas ≥ [DetectionConfidenceFloor.NAIL_COMBINED_MIN]
     * @param fullQualityNailCount unhas ≥ [DetectionConfidenceFloor.NAIL_FULL_MIN]
     */
    fun planRender(
        reliability: TryOnReliability?,
        paintableNailCount: Int,
        hasMappableAnchors: Boolean,
        fullQualityNailCount: Int = paintableNailCount,
    ): UserTryOnRenderPlan {
        if (reliability == null || reliability == TryOnReliability.REJECTED) {
            return nonePlan()
        }
        val masksOk = fullQualityNailCount >= MIN_MASKS_FOR_FULL
        val anyMask = paintableNailCount > 0
        return when (reliability) {
            TryOnReliability.STRONG -> when {
                masksOk -> UserTryOnRenderPlan(
                    mode = UserTryOnRenderMode.FULL,
                    useNailMasks = true,
                    useEllipsePaint = true,
                    useCanvasAnchors = false,
                )
                anyMask -> UserTryOnRenderPlan(
                    mode = UserTryOnRenderMode.APPROXIMATE,
                    useNailMasks = true,
                    useEllipsePaint = true,
                    useCanvasAnchors = !hasMappableAnchors,
                )
                hasMappableAnchors -> UserTryOnRenderPlan(
                    mode = UserTryOnRenderMode.APPROXIMATE,
                    useNailMasks = false,
                    useEllipsePaint = true,
                    useCanvasAnchors = true,
                )
                else -> nonePlan()
            }
            TryOnReliability.WEAK -> when {
                hasMappableAnchors || anyMask -> UserTryOnRenderPlan(
                    mode = UserTryOnRenderMode.APPROXIMATE,
                    useNailMasks = anyMask,
                    useEllipsePaint = true,
                    useCanvasAnchors = hasMappableAnchors && !anyMask,
                )
                else -> nonePlan()
            }
            TryOnReliability.REJECTED -> error("handled above")
        }
    }

    fun planRender(
        reliability: TryOnReliability?,
        nails: List<DetectedNail>,
        hasMappableAnchors: Boolean,
    ): UserTryOnRenderPlan = planRender(
        reliability = reliability,
        paintableNailCount = DetectionConfidenceFloor.countPaintable(nails),
        hasMappableAnchors = hasMappableAnchors,
        fullQualityNailCount = DetectionConfidenceFloor.countFullQuality(nails),
    )

    private fun nonePlan() = UserTryOnRenderPlan(
        mode = UserTryOnRenderMode.NONE,
        useNailMasks = false,
        useEllipsePaint = false,
        useCanvasAnchors = false,
    )
}
