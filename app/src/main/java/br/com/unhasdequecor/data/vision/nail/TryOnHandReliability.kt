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
     * Plano de renderização a partir das unhas detectadas (caminho de produção).
     * FULL = STRONG ∧ [DetectionConfidenceFloor.meetsFullNailFloor].
     */
    fun planRender(
        reliability: TryOnReliability?,
        nails: List<DetectedNail>,
        hasMappableAnchors: Boolean,
    ): UserTryOnRenderPlan {
        if (reliability == null || reliability == TryOnReliability.REJECTED) {
            return nonePlan()
        }
        val paintable = DetectionConfidenceFloor.countPaintable(nails)
        val fullOk = DetectionConfidenceFloor.meetsFullNailFloor(nails)
        return planFromCounts(
            reliability = reliability,
            paintableNailCount = paintable,
            fullQualityOk = fullOk,
            hasMappableAnchors = hasMappableAnchors,
        )
    }

    /**
     * Variante por contagens (testes). [fullQualityNailCount] é **obrigatório** —
     * não defaulta para paintable (evita FULL indevido).
     */
    fun planRender(
        reliability: TryOnReliability?,
        paintableNailCount: Int,
        fullQualityNailCount: Int,
        hasMappableAnchors: Boolean,
    ): UserTryOnRenderPlan {
        if (reliability == null || reliability == TryOnReliability.REJECTED) {
            return nonePlan()
        }
        return planFromCounts(
            reliability = reliability,
            paintableNailCount = paintableNailCount,
            fullQualityOk = fullQualityNailCount >= DetectionConfidenceFloor.MIN_MASKS_FOR_FULL,
            hasMappableAnchors = hasMappableAnchors,
        )
    }

    private fun planFromCounts(
        reliability: TryOnReliability,
        paintableNailCount: Int,
        fullQualityOk: Boolean,
        hasMappableAnchors: Boolean,
    ): UserTryOnRenderPlan {
        val anyMask = paintableNailCount > 0
        return when (reliability) {
            TryOnReliability.STRONG -> when {
                fullQualityOk -> UserTryOnRenderPlan(
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
