package br.com.unhasdequecor.data.local.db

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Valida o SQL da migração 1→2 sem Instrumentation (CI unitário).
 */
class DatabaseMigrationsTest {

    @Test
    fun `migration 1 to 2 adds unique idempotencyKey`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE history (
                      id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      colorId TEXT NOT NULL,
                      colorName TEXT NOT NULL,
                      colorHex INTEGER NOT NULL,
                      tagsCsv TEXT NOT NULL,
                      source TEXT NOT NULL,
                      occasion TEXT,
                      mood TEXT,
                      createdAtEpochMs INTEGER NOT NULL,
                      isFavorite INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.execute(DatabaseMigrations.SQL_1_2_ADD_IDEMPOTENCY_KEY)
                statement.execute(DatabaseMigrations.SQL_1_2_INDEX_IDEMPOTENCY_KEY)

                statement.execute(
                    """
                    INSERT INTO history (
                      colorId, colorName, colorHex, tagsCsv, source,
                      createdAtEpochMs, isFavorite, idempotencyKey
                    ) VALUES ('a', 'A', 1, 'ELEGANTE', 'FOR_ME', 1, 0, 'session-1')
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    INSERT INTO history (
                      colorId, colorName, colorHex, tagsCsv, source,
                      createdAtEpochMs, isFavorite, idempotencyKey
                    ) VALUES ('b', 'B', 2, 'ELEGANTE', 'FOR_ME', 2, 0, NULL)
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    INSERT INTO history (
                      colorId, colorName, colorHex, tagsCsv, source,
                      createdAtEpochMs, isFavorite, idempotencyKey
                    ) VALUES ('c', 'C', 3, 'ELEGANTE', 'FOR_ME', 3, 0, NULL)
                    """.trimIndent(),
                )
            }

            val count = connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM history").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
            assertThat(count).isEqualTo(3)

            var duplicateBlocked = false
            try {
                connection.createStatement().use { statement ->
                    statement.execute(
                        """
                        INSERT INTO history (
                          colorId, colorName, colorHex, tagsCsv, source,
                          createdAtEpochMs, isFavorite, idempotencyKey
                        ) VALUES ('d', 'D', 4, 'ELEGANTE', 'FOR_ME', 4, 0, 'session-1')
                        """.trimIndent(),
                    )
                }
            } catch (_: SQLException) {
                duplicateBlocked = true
            }
            assertThat(duplicateBlocked).isTrue()
        }
    }

    @Test
    fun `migration object targets versions 1 to 2`() {
        assertThat(DatabaseMigrations.MIGRATION_1_2.startVersion).isEqualTo(1)
        assertThat(DatabaseMigrations.MIGRATION_1_2.endVersion).isEqualTo(2)
        assertThat(DatabaseMigrations.ALL).hasLength(1)
    }
}
