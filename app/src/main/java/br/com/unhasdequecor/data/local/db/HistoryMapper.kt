package br.com.unhasdequecor.data.local.db

import br.com.unhasdequecor.data.local.db.entity.HistoryEntity
import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.domain.model.Mood
import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.domain.model.Occasion
import br.com.unhasdequecor.domain.model.RecommendationSource

fun HistoryEntity.toDomain(): HistoryEntry = HistoryEntry(
    id = id,
    colorId = colorId,
    colorName = colorName,
    colorHex = colorHex,
    tags = tagsCsv.split(',')
        .filter { it.isNotBlank() }
        .mapNotNull { runCatching { NailStyle.valueOf(it) }.getOrNull() },
    source = RecommendationSource.valueOf(source),
    occasion = occasion?.let { runCatching { Occasion.valueOf(it) }.getOrNull() },
    mood = mood?.let { runCatching { Mood.valueOf(it) }.getOrNull() },
    createdAtEpochMs = createdAtEpochMs,
    isFavorite = isFavorite,
    idempotencyKey = idempotencyKey,
)

fun HistoryEntry.toEntity(): HistoryEntity = HistoryEntity(
    id = id,
    colorId = colorId,
    colorName = colorName,
    colorHex = colorHex,
    tagsCsv = tags.joinToString(",") { it.name },
    source = source.name,
    occasion = occasion?.name,
    mood = mood?.name,
    createdAtEpochMs = createdAtEpochMs,
    isFavorite = isFavorite,
    idempotencyKey = idempotencyKey,
)
