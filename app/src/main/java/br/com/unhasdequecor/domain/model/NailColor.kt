package br.com.unhasdequecor.domain.model

data class NailColor(
    val id: String,
    val name: String,
    val hex: Long,
    val tags: List<NailStyle>,
    val description: String,
    val tip: String,
    val occasions: Set<Occasion> = emptySet(),
    val moods: Set<Mood> = emptySet(),
    val seasons: Set<Season> = emptySet(),
    val similarColorIds: List<String> = emptyList(),
)
