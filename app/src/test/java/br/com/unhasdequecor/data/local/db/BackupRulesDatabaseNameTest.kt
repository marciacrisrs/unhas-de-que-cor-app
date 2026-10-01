package br.com.unhasdequecor.data.local.db

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Auto Backup / device-transfer includes are exact filenames under
 * `getDatabasePath()`. A slug that does not match [AppDatabase.FILE_NAME]
 * silently drops history and favorites from backup.
 */
class BackupRulesDatabaseNameTest {

    @Test
    fun `backup xml includes the on-disk Room database name`() {
        val fileName = AppDatabase.FILE_NAME
        val xmlFiles = listOf(
            xmlFile("backup_rules.xml"),
            xmlFile("data_extraction_rules.xml"),
        )
        for (file in xmlFiles) {
            val text = file.readText()
            assertThat(text).contains("path=\"$fileName\"")
            assertThat(text).contains("path=\"$fileName-wal\"")
            assertThat(text).contains("path=\"$fileName-shm\"")
            assertThat(text).doesNotContain("unhas_de_que_cor.db")
        }
    }

    private fun xmlFile(name: String): File {
        val candidates = listOf(
            File("src/main/res/xml/$name"),
            File("app/src/main/res/xml/$name"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Missing res/xml/$name (cwd=${File(".").canonicalPath})")
    }
}
