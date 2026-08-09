package br.com.unhasdequecor.domain.model

data class HistoryEntry(
    val id: Long = 0,
    val colorId: String,
    val colorName: String,
    val colorHex: Long,
    val tags: List<NailStyle>,
    val source: RecommendationSource,
    val occasion: Occasion?,
    val mood: Mood?,
    val createdAtEpochMs: Long,
    val isFavorite: Boolean = false,
    val idempotencyKey: String? = null,
)
