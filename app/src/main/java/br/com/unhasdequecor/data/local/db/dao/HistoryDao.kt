package br.com.unhasdequecor.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.unhasdequecor.data.local.db.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query(
        """
        SELECT * FROM history
        WHERE colorId IN (SELECT colorId FROM favorites)
        ORDER BY createdAtEpochMs DESC
        """,
    )
    fun observeForFavorites(): Flow<List<HistoryEntity>>

    /**
     * IGNORE: se [HistoryEntity.idempotencyKey] já existir, não duplica a linha.
     * Retorna -1 quando o insert é ignorado por conflito.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: HistoryEntity): Long

    @Query("SELECT id FROM history WHERE idempotencyKey = :key LIMIT 1")
    suspend fun findIdByIdempotencyKey(key: String): Long?

    @Query("UPDATE history SET isFavorite = :isFavorite WHERE colorId = :colorId")
    suspend fun updateFavoriteForColor(colorId: String, isFavorite: Boolean)

    @Query("SELECT colorId FROM history ORDER BY createdAtEpochMs DESC LIMIT :limit")
    suspend fun recentColorIds(limit: Int): List<String>

    @Query("SELECT COUNT(DISTINCT colorId) FROM history")
    suspend fun distinctColorCount(): Int
}
