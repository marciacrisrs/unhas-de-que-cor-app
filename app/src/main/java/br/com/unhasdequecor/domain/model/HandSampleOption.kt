package br.com.unhasdequecor.domain.model

data class HandSampleOption(
    val id: String,
    val skinLabel: String,
    /** Detalhe visual secundário — a escolha é pelo tom de pele. */
    val detailLabel: String,
    val assetPath: String,
    /** Prioridade para treino / pele profunda (mãe, pele retinta). */
    val deepSkinPriority: Boolean = false,
) {
    val title: String get() = skinLabel
}

object HandSampleCatalog {
    /**
     * Amostra padrão quando a usuária ainda não cadastrou a própria mão.
     * `clara_vermelho` mantém máscara calibrada (paint garantido no 1º uso).
     * Pele retinta lidera o picker / treino ([deepSkinOptions]).
     */
    const val DEFAULT_ID = "clara_vermelho"

    private const val REFERENCE_DETAIL = "Referência"
    private const val RETINTA_TRAINING_DETAIL = "Treino · pele retinta"

    val defaultOption: HandSampleOption
        get() = checkNotNull(findById(DEFAULT_ID))

    /**
     * Ordem: retinta primeiro (prioridade mãe / pele profunda), depois demais tons.
     */
    val options: List<HandSampleOption> = listOf(
        HandSampleOption(
            id = "retinta_vinho",
            skinLabel = "Pele retinta",
            detailLabel = RETINTA_TRAINING_DETAIL,
            assetPath = "hand_samples/hand_sample_retinta_vinho.webp",
            deepSkinPriority = true,
        ),
        HandSampleOption(
            id = "retinta_polegar",
            skinLabel = "Pele retinta",
            detailLabel = "Treino · pose diversa",
            assetPath = "hand_samples/hand_sample_retinta_polegar.webp",
            deepSkinPriority = true,
        ),
        HandSampleOption(
            id = "morena_nude",
            skinLabel = "Pele morena",
            detailLabel = REFERENCE_DETAIL,
            assetPath = "hand_samples/hand_sample_morena_nude.webp",
        ),
        HandSampleOption(
            id = "clara_vermelho",
            skinLabel = "Pele clara",
            detailLabel = "Máscara calibrada",
            assetPath = "hand_samples/hand_sample_clara_vermelho.webp",
        ),
        HandSampleOption(
            id = "morena_clara_coral",
            skinLabel = "Pele morena clara",
            detailLabel = "Unhas curtas",
            assetPath = "hand_samples/hand_sample_morena_clara_coral.webp",
        ),
        HandSampleOption(
            id = "media_rosa",
            skinLabel = "Pele média",
            detailLabel = "Unhas longas",
            assetPath = "hand_samples/hand_sample_media_rosa.webp",
        ),
    )

    /** Subconjunto para treino / QA focado em pele profunda. */
    val deepSkinOptions: List<HandSampleOption>
        get() = options.filter { it.deepSkinPriority }

    fun findById(id: String): HandSampleOption? = options.firstOrNull { it.id == id }
}
