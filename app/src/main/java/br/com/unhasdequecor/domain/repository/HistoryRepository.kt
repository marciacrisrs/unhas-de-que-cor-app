package br.com.unhasdequecor.domain.repository

import br.com.unhasdequecor.domain.model.HistoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * Persistência do histórico de recomendações e dos favoritos derivados dele.
 */
interface HistoryRepository {
    fun observeHistory(): Flow<List<HistoryEntry>>
    fun observeFavorites(): Flow<List<HistoryEntry>>
    suspend fun save(entry: HistoryEntry): Long
    suspend fun setFavorite(colorId: String, isFavorite: Boolean)
    suspend fun isFavorite(colorId: String): Boolean
    suspend fun recentColorIds(limit: Int = 8): Set<String>
    suspend fun distinctColorCount(): Int
}
