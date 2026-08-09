package br.com.unhasdequecor.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.unhasdequecor.data.local.db.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE colorId = :colorId")
    suspend fun delete(colorId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE colorId = :colorId)")
    suspend fun isFavorite(colorId: String): Boolean

    @Query("SELECT colorId FROM favorites")
    fun observeFavoriteIds(): Flow<List<String>>
}
