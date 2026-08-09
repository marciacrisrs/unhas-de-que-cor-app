package br.com.unhasdequecor.domain.model

data class HandSampleOption(
    val id: String,
    val skinLabel: String,
    val polishLabel: String,
    val assetPath: String,
) {
    val title: String get() = "$skinLabel + $polishLabel"
}

object HandSampleCatalog {
    val options: List<HandSampleOption> = listOf(
        HandSampleOption(
            id = "retinta_vinho",
            skinLabel = "Pele retinta",
            polishLabel = "Vinho",
            assetPath = "hand_samples/hand_sample_retinta_vinho.webp",
        ),
        HandSampleOption(
            id = "morena_nude",
            skinLabel = "Pele morena",
            polishLabel = "Nude",
            assetPath = "hand_samples/hand_sample_morena_nude.webp",
        ),
        HandSampleOption(
            id = "clara_vermelho",
            skinLabel = "Pele clara",
            polishLabel = "Vermelho",
            assetPath = "hand_samples/hand_sample_clara_vermelho.webp",
        ),
        HandSampleOption(
            id = "morena_clara_coral",
            skinLabel = "Pele morena clara",
            polishLabel = "Coral · curtas",
            assetPath = "hand_samples/hand_sample_morena_clara_coral.webp",
        ),
        HandSampleOption(
            id = "media_rosa",
            skinLabel = "Pele média",
            polishLabel = "Rosa queimado · longas",
            assetPath = "hand_samples/hand_sample_media_rosa.webp",
        ),
    )

    fun findById(id: String): HandSampleOption? = options.firstOrNull { it.id == id }
}
