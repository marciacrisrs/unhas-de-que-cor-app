package br.com.unhasdequecor.testing

import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.domain.model.RecommendationSource
import br.com.unhasdequecor.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class FakeHistoryRepository : HistoryRepository {
    private val entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private val favorites = MutableStateFlow<Set<String>>(emptySet())
    private var nextId = 1L

    override fun observeHistory(): Flow<List<HistoryEntry>> = combine(entries, favorites) { list, favs ->
        list.map { it.copy(isFavorite = it.colorId in favs) }
    }

    override fun observeFavorites(): Flow<List<HistoryEntry>> =
        combine(entries, favorites) { list, favs ->
            val fromHistory = list.filter { it.colorId in favs }.distinctBy { it.colorId }
                .map { it.copy(isFavorite = true) }
            val historyIds = fromHistory.map { it.colorId }.toSet()
            val orphans = favs.filter { it !in historyIds }.map { colorId ->
                HistoryEntry(
                    colorId = colorId,
                    colorName = colorId,
                    colorHex = 0L,
                    tags = emptyList(),
                    source = RecommendationSource.FOR_ME,
                    occasion = null,
                    mood = null,
                    createdAtEpochMs = 0L,
                    isFavorite = true,
                )
            }
            fromHistory + orphans
        }

    override suspend fun save(entry: HistoryEntry): Long {
        val key = entry.idempotencyKey
        if (key != null) {
            val existing = entries.value.firstOrNull { it.idempotencyKey == key }
            if (existing != null) return existing.id
        }
        val id = if (entry.id == 0L) nextId++ else entry.id
        val saved = entry.copy(id = id, isFavorite = entry.colorId in favorites.value)
        entries.value = listOf(saved) + entries.value.filterNot { it.id == id }
        return id
    }

    override suspend fun setFavorite(colorId: String, isFavorite: Boolean) {
        favorites.value = if (isFavorite) favorites.value + colorId else favorites.value - colorId
        entries.value = entries.value.map {
            if (it.colorId == colorId) it.copy(isFavorite = isFavorite) else it
        }
    }

    override suspend fun isFavorite(colorId: String): Boolean = colorId in favorites.value

    override suspend fun recentColorIds(limit: Int): Set<String> =
        entries.value.take(limit).map { it.colorId }.toSet()

    override suspend fun distinctColorCount(): Int =
        entries.value.map { it.colorId }.toSet().size
}
