package br.com.unhasdequecor.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.unhasdequecor.data.local.db.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query(
        """
        SELECT h.* FROM history h
        INNER JOIN (
            SELECT colorId, MAX(createdAtEpochMs) AS maxCreated
            FROM history
            GROUP BY colorId
        ) latest ON h.colorId = latest.colorId AND h.createdAtEpochMs = latest.maxCreated
        WHERE h.isFavorite = 1
        ORDER BY h.createdAtEpochMs DESC
        """,
    )
    fun observeFavorites(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HistoryEntity): Long

    @Query("UPDATE history SET isFavorite = :isFavorite WHERE colorId = :colorId")
    suspend fun updateFavoriteForColor(colorId: String, isFavorite: Boolean)

    @Query("SELECT colorId FROM history ORDER BY createdAtEpochMs DESC LIMIT :limit")
    suspend fun recentColorIds(limit: Int): List<String>

    @Query("SELECT COUNT(DISTINCT colorId) FROM history")
    suspend fun distinctColorCount(): Int
}
