package br.com.unhasdequecor.testing

import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeHistoryRepository : HistoryRepository {
    private val entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private val favorites = mutableSetOf<String>()
    private var nextId = 1L

    override fun observeHistory(): Flow<List<HistoryEntry>> = entries

    override fun observeFavorites(): Flow<List<HistoryEntry>> =
        entries.map { list -> list.filter { it.colorId in favorites }.distinctBy { it.colorId } }

    override suspend fun save(entry: HistoryEntry): Long {
        val key = entry.idempotencyKey
        if (key != null) {
            val existing = entries.value.firstOrNull { it.idempotencyKey == key }
            if (existing != null) return existing.id
        }
        val id = if (entry.id == 0L) nextId++ else entry.id
        val saved = entry.copy(id = id, isFavorite = entry.colorId in favorites)
        entries.value = listOf(saved) + entries.value.filterNot { it.id == id }
        return id
    }

    override suspend fun setFavorite(colorId: String, isFavorite: Boolean) {
        if (isFavorite) favorites += colorId else favorites -= colorId
        entries.value = entries.value.map {
            if (it.colorId == colorId) it.copy(isFavorite = isFavorite) else it
        }
    }

    override suspend fun isFavorite(colorId: String): Boolean = colorId in favorites

    override suspend fun recentColorIds(limit: Int): Set<String> =
        entries.value.take(limit).map { it.colorId }.toSet()

    override suspend fun distinctColorCount(): Int =
        entries.value.map { it.colorId }.toSet().size
}
