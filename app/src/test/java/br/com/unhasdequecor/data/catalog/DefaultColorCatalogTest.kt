package br.com.unhasdequecor.data.catalog

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultColorCatalogTest {

    @Test
    fun `catalog has unique ids and non-blank names`() {
        val colors = DefaultColorCatalog.colors
        assertThat(colors).isNotEmpty()
        assertThat(colors.map { it.id }.toSet()).hasSize(colors.size)
        colors.forEach { color ->
            assertThat(color.name).isNotEmpty()
            assertThat(color.description).isNotEmpty()
            assertThat(color.tip).isNotEmpty()
            assertThat(color.tags).isNotEmpty()
        }
    }

    @Test
    fun `similar color ids point to existing catalog entries`() {
        val ids = DefaultColorCatalog.colors.map { it.id }.toSet()
        DefaultColorCatalog.colors.forEach { color ->
            color.similarColorIds.forEach { similarId ->
                assertThat(ids).contains(similarId)
            }
        }
    }
}
