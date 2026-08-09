package br.com.unhasdequecor.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history",
    indices = [
        Index(value = ["colorId"]),
        Index(value = ["createdAtEpochMs"]),
        Index(value = ["idempotencyKey"], unique = true),
    ],
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
    /**
     * Chave de idempotência da sessão de Result. NULL em linhas antigas;
     * UNIQUE permite vários NULLs no SQLite e bloqueia saves duplicados
     * da mesma regeneração/process-death.
     */
    val idempotencyKey: String? = null,
)
