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
    /** Dica única APPROXIMATE / NOT_DETECTED (banner + TalkBack). */
    const val LIGHTING_HINT =
        "luz frontal sem estouro (evite flash direto e contraluz)"

    fun status(claim: TryOnPreviewClaim): String = when (claim) {
        TryOnPreviewClaim.LOADING -> "Preparando prévia"
        TryOnPreviewClaim.SAMPLE_MASK -> "Prévia na mão de exemplo"
        TryOnPreviewClaim.FULL_USER -> "Prévia na sua mão"
        TryOnPreviewClaim.APPROXIMATE ->
            "Prévia aproximada — unhas à mostra, $LIGHTING_HINT, dedos abertos"
        TryOnPreviewClaim.NOT_DETECTED ->
            "Mão não detectada — unhas à mostra, $LIGHTING_HINT, dedos abertos"
    }

    /**
     * Content description do frame: nunca afirma “na sua mão” se o modo não for FULL.
     * Mantém tip curto (não repete o status inteiro).
     */
    fun contentDescription(colorName: String, claim: TryOnPreviewClaim): String = when (claim) {
        TryOnPreviewClaim.LOADING ->
            "Preparando prévia da cor $colorName"
        TryOnPreviewClaim.SAMPLE_MASK ->
            "Prévia da cor $colorName na mão de exemplo"
        TryOnPreviewClaim.FULL_USER ->
            "Prévia da cor $colorName na sua mão"
        TryOnPreviewClaim.APPROXIMATE ->
            "Prévia aproximada da cor $colorName. $LIGHTING_HINT"
        TryOnPreviewClaim.NOT_DETECTED ->
            "Prévia da cor $colorName. Mão não detectada. $LIGHTING_HINT"
    }
}
