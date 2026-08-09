package br.com.unhasdequecor.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history",
    indices = [Index(value = ["colorId"]), Index(value = ["createdAtEpochMs"])],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val colorId: String,
    val colorName: String,
    val colorHex: Long,
    val tagsCsv: String,
    val source: String,
    val occasion: String?,
    val mood: String?,
    val createdAtEpochMs: Long,
    val isFavorite: Boolean,
)
