package br.com.unhasdequecor.domain.repository

import br.com.unhasdequecor.domain.model.HistoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * Persistência do histórico de recomendações e dos favoritos.
 * Favoritos têm SoT na tabela `favorites` e aparecem mesmo sem linha de histórico
 * (ex.: coração no Result aberto pela inspiração do dia).
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
