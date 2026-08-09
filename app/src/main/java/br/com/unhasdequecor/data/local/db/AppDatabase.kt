package br.com.unhasdequecor.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import br.com.unhasdequecor.data.local.db.dao.FavoriteDao
import br.com.unhasdequecor.data.local.db.dao.HistoryDao
import br.com.unhasdequecor.data.local.db.entity.FavoriteEntity
import br.com.unhasdequecor.data.local.db.entity.HistoryEntity

@Database(
    entities = [HistoryEntity::class, FavoriteEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
}
