package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.HandLandmarks

/**
 * Confiabilidade do try-on na **mão real** (foto da usuária).
 *
 * MediaPipe pode devolver landmarks fracos (limiar baixo / contraluz).
 * Esta camada evita rotular elipse frágil como “Prévia na sua mão”.
 */
enum class TryOnReliability {
    /** Descartar — tratar como mão não detectada. */
    REJECTED,
    /** Landmarks usable só como prévia aproximada. */
    WEAK,
    /** Presence forte — claim FULL só se também houver máscaras suficientes. */
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
    /** Abaixo disso: ruído típico — descartar. Alinhado ao limiar MediaPipe (~0.08). */
    const val MIN_PRESENCE_ACCEPT = 0.12f

    /** Acima disso: claim FULL permitido (com ≥ [MIN_MASKS_FOR_FULL] máscaras). */
    const val MIN_PRESENCE_STRONG = 0.55f

    const val MIN_MASKS_FOR_FULL = 3

    /**
     * Classifica só por presence. Máscaras **não** elevam para STRONG —
     * mid presence + muitas máscaras continua WEAK → APPROXIMATE (nunca FULL).
     */
    fun classify(presenceScore: Float): TryOnReliability {
        val score = presenceScore.coerceIn(0f, 1f)
        if (score < MIN_PRESENCE_ACCEPT) return TryOnReliability.REJECTED
        if (score >= MIN_PRESENCE_STRONG) return TryOnReliability.STRONG
        return TryOnReliability.WEAK
    }

    fun classify(landmarks: HandLandmarks): TryOnReliability =
        classify(landmarks.presenceScore)

    /**
     * Plano de renderização honesto (modo ≡ qualidade).
     * FULL exige STRONG **e** ≥ [MIN_MASKS_FOR_FULL] máscaras. Elipse sozinha nunca é FULL.
     */
    fun planRender(
        reliability: TryOnReliability?,
        nailCount: Int,
        hasMappableAnchors: Boolean,
    ): UserTryOnRenderPlan {
        if (reliability == null || reliability == TryOnReliability.REJECTED) {
            return nonePlan()
        }
        val masksOk = nailCount >= MIN_MASKS_FOR_FULL
        val anyMask = nailCount > 0
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

    private fun nonePlan() = UserTryOnRenderPlan(
        mode = UserTryOnRenderMode.NONE,
        useNailMasks = false,
        useEllipsePaint = false,
        useCanvasAnchors = false,
    )
}
