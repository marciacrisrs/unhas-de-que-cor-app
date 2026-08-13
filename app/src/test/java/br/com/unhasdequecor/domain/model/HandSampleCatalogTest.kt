package br.com.unhasdequecor.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HandSampleCatalogTest {

    @Test
    fun `catalog exposes curated samples with retinta training first`() {
        assertThat(HandSampleCatalog.options).hasSize(6)
        assertThat(HandSampleCatalog.options.map { it.id }).containsExactly(
            "retinta_vinho",
            "retinta_polegar",
            "morena_nude",
            "clara_vermelho",
            "morena_clara_coral",
            "media_rosa",
        ).inOrder()
        val diverse = HandSampleCatalog.findById("retinta_polegar")
        assertThat(diverse?.title).isEqualTo("Pele retinta")
        assertThat(diverse?.detailLabel).contains("pose diversa")
        assertThat(diverse?.deepSkinPriority).isTrue()
        assertThat(diverse?.assetPath).endsWith("hand_sample_retinta_polegar.webp")
    }

    @Test
    fun `deepSkinOptions prioritizes retinta training hands`() {
        assertThat(HandSampleCatalog.deepSkinOptions.map { it.id })
            .containsExactly("retinta_vinho", "retinta_polegar")
            .inOrder()
        assertThat(HandSampleCatalog.deepSkinOptions).hasSize(2)
    }

    @Test
    fun `findById resolves known sample by skin tone title`() {
        val option = HandSampleCatalog.findById("clara_vermelho")
        assertThat(option?.title).isEqualTo("Pele clara")
        assertThat(option?.detailLabel).contains("Máscara")
        assertThat(option?.assetPath).endsWith("hand_sample_clara_vermelho.webp")
    }

    @Test
    fun `default sample is curated catalog option`() {
        assertThat(HandSampleCatalog.DEFAULT_ID).isEqualTo("clara_vermelho")
        assertThat(HandSampleCatalog.defaultOption.id).isEqualTo("clara_vermelho")
    }
}
