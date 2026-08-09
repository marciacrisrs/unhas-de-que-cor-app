package br.com.unhasdequecor.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val colorId: String,
    val favoritedAtEpochMs: Long,
)
