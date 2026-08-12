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
    fun status(claim: TryOnPreviewClaim): String = when (claim) {
        TryOnPreviewClaim.LOADING -> "Preparando prévia"
        TryOnPreviewClaim.SAMPLE_MASK -> "Prévia na mão de exemplo"
        TryOnPreviewClaim.FULL_USER -> "Prévia na sua mão"
        TryOnPreviewClaim.APPROXIMATE ->
            "Prévia aproximada — unhas à mostra, luz na mão, dedos abertos"
        TryOnPreviewClaim.NOT_DETECTED ->
            "Mão não detectada — unhas à mostra, boa luz, dedos abertos (evite contraluz)"
    }

    /**
     * Content description do frame: nunca afirma “na sua mão” se o modo não for FULL.
     */
    fun contentDescription(colorName: String, claim: TryOnPreviewClaim): String = when (claim) {
        TryOnPreviewClaim.LOADING ->
            "Preparando prévia da cor $colorName"
        TryOnPreviewClaim.SAMPLE_MASK ->
            "Prévia da cor $colorName na mão de exemplo. ${status(claim)}"
        TryOnPreviewClaim.FULL_USER ->
            "Prévia da cor $colorName na sua mão. ${status(claim)}"
        TryOnPreviewClaim.APPROXIMATE ->
            "Prévia aproximada da cor $colorName. ${status(claim)}"
        TryOnPreviewClaim.NOT_DETECTED ->
            "Prévia da cor $colorName. ${status(claim)}"
    }
}
