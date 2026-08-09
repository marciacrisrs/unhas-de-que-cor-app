package br.com.unhasdequecor.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrações versionadas do Room. Sem fallback destrutivo: usuários
 * com histórico/favoritos sobrevivem a upgrades de schema.
 */
object DatabaseMigrations {

    const val SQL_1_2_ADD_IDEMPOTENCY_KEY =
        "ALTER TABLE history ADD COLUMN idempotencyKey TEXT"

    const val SQL_1_2_INDEX_IDEMPOTENCY_KEY =
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_history_idempotencyKey` " +
            "ON `history` (`idempotencyKey`)"

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(SQL_1_2_ADD_IDEMPOTENCY_KEY)
            db.execSQL(SQL_1_2_INDEX_IDEMPOTENCY_KEY)
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
