package br.com.unhasdequecor.data.repository

import br.com.unhasdequecor.data.local.db.dao.FavoriteDao
import br.com.unhasdequecor.data.local.db.dao.HistoryDao
import br.com.unhasdequecor.data.local.db.entity.FavoriteEntity
import br.com.unhasdequecor.data.local.db.toDomain
import br.com.unhasdequecor.data.local.db.toEntity
import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.domain.repository.HistoryRepository
import br.com.unhasdequecor.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Histórico em Room; favoritos têm SoT na tabela `favorites`.
 * O flag `isFavorite` nas linhas de histórico é derivado na leitura.
 * Saves com a mesma [HistoryEntry.idempotencyKey] são idempotentes.
 */
@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val historyDao: HistoryDao,
    private val favoriteDao: FavoriteDao,
    private val clock: Clock,
) : HistoryRepository {

    override fun observeHistory(): Flow<List<HistoryEntry>> =
        combine(
            historyDao.observeAll(),
            favoriteDao.observeFavoriteIds(),
        ) { history, favoriteIds ->
            val favorites = favoriteIds.toSet()
            history.map { entity ->
                entity.toDomain().copy(isFavorite = entity.colorId in favorites)
            }
        }

    override fun observeFavorites(): Flow<List<HistoryEntry>> =
        combine(
            historyDao.observeAll(),
            favoriteDao.observeFavoriteIds(),
        ) { history, favoriteIds ->
            val favorites = favoriteIds.toSet()
            history
                .filter { it.colorId in favorites }
                .distinctBy { it.colorId }
                .map { entity ->
                    entity.toDomain().copy(isFavorite = true)
                }
        }

    override suspend fun save(entry: HistoryEntry): Long {
        val favorite = favoriteDao.isFavorite(entry.colorId)
        val insertedId = historyDao.insert(entry.copy(isFavorite = favorite).toEntity())
        if (insertedId != -1L) return insertedId
        val key = entry.idempotencyKey ?: return insertedId
        return historyDao.findIdByIdempotencyKey(key) ?: insertedId
    }

    override suspend fun setFavorite(colorId: String, isFavorite: Boolean) {
        if (isFavorite) {
            favoriteDao.upsert(
                FavoriteEntity(
                    colorId = colorId,
                    favoritedAtEpochMs = clock.now(),
                ),
            )
        } else {
            favoriteDao.delete(colorId)
        }
        // Mantém coluna denormalizada alinhada para queries legadas/export.
        historyDao.updateFavoriteForColor(colorId, isFavorite)
    }

    override suspend fun isFavorite(colorId: String): Boolean = favoriteDao.isFavorite(colorId)

    override suspend fun recentColorIds(limit: Int): Set<String> =
        historyDao.recentColorIds(limit).toSet()

    override suspend fun distinctColorCount(): Int = historyDao.distinctColorCount()
}
