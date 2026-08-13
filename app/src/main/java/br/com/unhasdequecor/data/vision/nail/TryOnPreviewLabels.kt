package br.com.unhasdequecor.data.vision.nail

/**
 * Rótulos honestos do try-on (modo ≡ qualidade).
 * Extraídos da UI para testes JVM e para o TalkBack não mentir “na sua mão”.
 */
enum class TryOnPreviewClaim {
    LOADING,
    SAMPLE_MASK,
    FULL_USER,
    APPROXIMATE,
    NOT_DETECTED,
}

object TryOnPreviewLabels {
    /** Dica genérica APPROXIMATE / NOT_DETECTED quando não há motivo tipado. */
    const val LIGHTING_HINT =
        "luz frontal sem estouro (evite flash direto e contraluz)"

    /** CTA curto para nova foto (banner / botão). */
    const val RETRY_HINT = "Tentar outra foto"

    fun status(
        claim: TryOnPreviewClaim,
        reason: DetectionFailureReason? = null,
    ): String = when (claim) {
        TryOnPreviewClaim.LOADING -> "Preparando prévia"
        TryOnPreviewClaim.SAMPLE_MASK -> "Prévia na mão de exemplo"
        TryOnPreviewClaim.FULL_USER -> "Prévia na sua mão"
        TryOnPreviewClaim.APPROXIMATE -> approximateStatus(reason)
        TryOnPreviewClaim.NOT_DETECTED -> notDetectedStatus(reason)
    }

    /**
     * Content description do frame: nunca afirma “na sua mão” se o modo não for FULL.
     * Inclui o motivo tipado quando disponível (TalkBack).
     */
    fun contentDescription(
        colorName: String,
        claim: TryOnPreviewClaim,
        reason: DetectionFailureReason? = null,
    ): String = when (claim) {
        TryOnPreviewClaim.LOADING ->
            "Preparando prévia da cor $colorName"
        TryOnPreviewClaim.SAMPLE_MASK ->
            "Prévia da cor $colorName na mão de exemplo"
        TryOnPreviewClaim.FULL_USER ->
            "Prévia da cor $colorName na sua mão"
        TryOnPreviewClaim.APPROXIMATE ->
            "Prévia aproximada da cor $colorName. ${hintFor(reason)}"
        TryOnPreviewClaim.NOT_DETECTED ->
            "Prévia da cor $colorName. ${notDetectedStatus(reason)}"
    }

    private fun approximateStatus(reason: DetectionFailureReason?): String {
        val tip = when (reason) {
            null, DetectionFailureReason.Generic ->
                "unhas à mostra, $LIGHTING_HINT, dedos abertos"
            else -> reason.userMessage
        }
        return "Prévia aproximada — $tip"
    }

    private fun notDetectedStatus(reason: DetectionFailureReason?): String {
        return when (reason) {
            null, DetectionFailureReason.Generic ->
                "Mão não detectada — unhas à mostra, $LIGHTING_HINT, dedos abertos"
            else -> reason.userMessage
        }
    }

    private fun hintFor(reason: DetectionFailureReason?): String =
        when (reason) {
            null, DetectionFailureReason.Generic -> LIGHTING_HINT
            else -> reason.userMessage
        }
}
