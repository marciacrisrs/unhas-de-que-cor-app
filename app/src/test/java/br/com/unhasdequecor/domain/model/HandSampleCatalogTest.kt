package br.com.unhasdequecor.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HandSampleCatalogTest {

    @Test
    fun `catalog exposes five curated samples`() {
        assertThat(HandSampleCatalog.options).hasSize(5)
        assertThat(HandSampleCatalog.options.map { it.id }).containsExactly(
            "retinta_vinho",
            "morena_nude",
            "clara_vermelho",
            "morena_clara_coral",
            "media_rosa",
        ).inOrder()
    }

    @Test
    fun `findById resolves known sample by skin tone title`() {
        val option = HandSampleCatalog.findById("clara_vermelho")
        assertThat(option?.title).isEqualTo("Pele clara")
        assertThat(option?.detailLabel).isEqualTo("Referência")
        assertThat(option?.assetPath).endsWith("hand_sample_clara_vermelho.webp")
    }

    @Test
    fun `default sample is curated catalog option`() {
        assertThat(HandSampleCatalog.DEFAULT_ID).isEqualTo("morena_nude")
        assertThat(HandSampleCatalog.defaultOption.id).isEqualTo("morena_nude")
    }
}
