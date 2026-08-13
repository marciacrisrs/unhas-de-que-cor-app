package br.com.unhasdequecor.data.vision.nail

/**
 * Motivo tipado de falha / qualidade fraca do try-on (ISSUE 005 / #54).
 *
 * Mensagens amigáveis em PT-BR; [logCode] só para debug/analytics (não na UI).
 */
sealed class DetectionFailureReason {
    abstract val userMessage: String
    abstract val logCode: String

    /** Mão pequena no frame / longe da câmera. */
    data object HandTooFar : DetectionFailureReason() {
        override val userMessage =
            "Aproxime a mão da câmera para que eu consiga analisar melhor."
        override val logCode = "hand_too_far"
    }

    /** Cena escura / subexposta. */
    data object TooDark : DetectionFailureReason() {
        override val userMessage =
            "Tente luz na frente da mão (janela ou lâmpada). Em pele retinta, evite sombra e contraluz."
        override val logCode = "too_dark"
    }

    /** Flash / highlights estourados nas unhas. */
    data object ExcessiveGlare : DetectionFailureReason() {
        override val userMessage =
            "Evite reflexos fortes na luz. Tente um ângulo diferente."
        override val logCode = "excessive_glare"
    }

    /** Palma/unhas não de frente, punho ou tip-span colapsado. */
    data object BadAngle : DetectionFailureReason() {
        override val userMessage =
            "Mostre a palma e as unhas de frente para a câmera."
        override val logCode = "bad_angle"
    }

    /** Mão ok, mas sem unha paintable / landmarks de placa. */
    data object NoNailVisible : DetectionFailureReason() {
        override val userMessage =
            "Não consegui identificar uma unha nesta foto. Verifique se as unhas estão visíveis."
        override val logCode = "no_nail_visible"
    }

    /** Presence rejeitada por clutter / várias mãos (heurística). */
    data object ClutteredScene : DetectionFailureReason() {
        override val userMessage =
            "Tente tirar apenas a foto da mão. Remova objetos do fundo."
        override val logCode = "cluttered_scene"
    }

    /** Sem diagnóstico específico. */
    data object Generic : DetectionFailureReason() {
        override val userMessage =
            "Não consegui processar essa imagem. Tente outra foto."
        override val logCode = "generic"
    }

    companion object {
        /**
         * Lazy: evitar nulls por ordem de init do companion vs data objects.
         */
        val all: List<DetectionFailureReason> by lazy {
            listOf(
                HandTooFar,
                TooDark,
                ExcessiveGlare,
                BadAngle,
                NoNailVisible,
                ClutteredScene,
                Generic,
            )
        }
    }
}
