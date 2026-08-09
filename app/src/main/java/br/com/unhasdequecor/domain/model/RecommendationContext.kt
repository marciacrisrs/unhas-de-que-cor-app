package br.com.unhasdequecor.domain.model

data class RecommendationContext(
    val occasion: Occasion? = null,
    val mood: Mood? = null,
    val season: Season? = null,
    val preferredStyles: Set<NailStyle> = emptySet(),
)
