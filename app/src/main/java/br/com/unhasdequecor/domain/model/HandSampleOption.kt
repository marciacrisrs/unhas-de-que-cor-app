package br.com.unhasdequecor.domain.model

data class HandSampleOption(
    val id: String,
    val skinLabel: String,
    /** Detalhe visual secundário — a escolha é pelo tom de pele. */
    val detailLabel: String,
    val assetPath: String,
) {
    val title: String get() = skinLabel
}

object HandSampleCatalog {
    /**
     * Amostra padrão quando a usuária ainda não cadastrou a própria mão.
     * `clara_vermelho` tem máscara de unha calibrada para o try-on.
     */
    const val DEFAULT_ID = "clara_vermelho"

    private const val REFERENCE_DETAIL = "Referência"

    val defaultOption: HandSampleOption
        get() = checkNotNull(findById(DEFAULT_ID))

    val options: List<HandSampleOption> = listOf(
        HandSampleOption(
            id = "retinta_vinho",
            skinLabel = "Pele retinta",
            detailLabel = REFERENCE_DETAIL,
            assetPath = "hand_samples/hand_sample_retinta_vinho.webp",
        ),
        HandSampleOption(
            id = "retinta_polegar",
            skinLabel = "Pele retinta",
            detailLabel = "Pose diversa",
            assetPath = "hand_samples/hand_sample_retinta_polegar.webp",
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
            detailLabel = REFERENCE_DETAIL,
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

    fun findById(id: String): HandSampleOption? = options.firstOrNull { it.id == id }
}
