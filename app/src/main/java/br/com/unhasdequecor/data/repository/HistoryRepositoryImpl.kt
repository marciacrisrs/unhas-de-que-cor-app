package br.com.unhasdequecor.data.repository

import br.com.unhasdequecor.data.local.db.dao.FavoriteDao
import br.com.unhasdequecor.data.local.db.dao.HistoryDao
import br.com.unhasdequecor.data.local.db.entity.FavoriteEntity
import br.com.unhasdequecor.data.local.db.toDomain
import br.com.unhasdequecor.data.local.db.toEntity
import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val historyDao: HistoryDao,
    private val favoriteDao: FavoriteDao,
) : HistoryRepository {

    override fun observeHistory(): Flow<List<HistoryEntry>> =
        historyDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeFavorites(): Flow<List<HistoryEntry>> =
        historyDao.observeFavorites().map { list -> list.map { it.toDomain() } }

    override suspend fun save(entry: HistoryEntry): Long {
        val favorite = favoriteDao.isFavorite(entry.colorId)
        return historyDao.insert(entry.copy(isFavorite = favorite).toEntity())
    }

    override suspend fun setFavorite(colorId: String, isFavorite: Boolean) {
        if (isFavorite) {
            favoriteDao.upsert(
                FavoriteEntity(
                    colorId = colorId,
                    favoritedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        } else {
            favoriteDao.delete(colorId)
        }
        historyDao.updateFavoriteForColor(colorId, isFavorite)
    }

    override suspend fun isFavorite(colorId: String): Boolean = favoriteDao.isFavorite(colorId)

    override suspend fun recentColorIds(limit: Int): Set<String> =
        historyDao.recentColorIds(limit).toSet()

    override suspend fun distinctColorCount(): Int = historyDao.distinctColorCount()
}
