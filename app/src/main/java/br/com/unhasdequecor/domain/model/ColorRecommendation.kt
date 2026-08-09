package br.com.unhasdequecor.domain.model

data class ColorRecommendation(
    val color: NailColor,
    val similarColors: List<NailColor>,
    val source: RecommendationSource,
    val context: RecommendationContext,
    val rationale: String,
)
