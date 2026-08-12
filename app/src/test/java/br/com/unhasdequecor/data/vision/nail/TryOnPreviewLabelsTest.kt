package br.com.unhasdequecor.data.vision.nail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TryOnPreviewLabelsTest {

    @Test
    fun contentDescription_loading_doesNotClaimUserHand() {
        val cd = TryOnPreviewLabels.contentDescription("Vermelho", TryOnPreviewClaim.LOADING)
        assertThat(cd).doesNotContain("sua mão")
        assertThat(cd).contains("Preparando")
        assertThat(cd).contains("Vermelho")
    }

    @Test
    fun contentDescription_fullUser_claimsUserHand() {
        val cd = TryOnPreviewLabels.contentDescription("Nude", TryOnPreviewClaim.FULL_USER)
        assertThat(cd).contains("sua mão")
    }

    @Test
    fun contentDescription_approximate_doesNotClaimUserHand() {
        val cd = TryOnPreviewLabels.contentDescription("Rosa", TryOnPreviewClaim.APPROXIMATE)
        assertThat(cd).doesNotContain("sua mão")
        assertThat(cd).contains("aproximada")
        assertThat(cd).contains(TryOnPreviewLabels.LIGHTING_HINT)
    }

    @Test
    fun contentDescription_notDetected_doesNotClaimUserHand() {
        val cd = TryOnPreviewLabels.contentDescription("Preto", TryOnPreviewClaim.NOT_DETECTED)
        assertThat(cd).doesNotContain("sua mão")
        assertThat(cd).contains("não detectada")
        assertThat(cd).contains(TryOnPreviewLabels.LIGHTING_HINT)
    }

    @Test
    fun contentDescription_sampleMask_mentionsExampleHand() {
        val cd = TryOnPreviewLabels.contentDescription("Coral", TryOnPreviewClaim.SAMPLE_MASK)
        assertThat(cd).contains("exemplo")
        assertThat(cd).doesNotContain("sua mão")
    }

    @Test
    fun status_approximateAndNotDetected_shareLightingHint() {
        assertThat(TryOnPreviewLabels.status(TryOnPreviewClaim.APPROXIMATE))
            .contains(TryOnPreviewLabels.LIGHTING_HINT)
        assertThat(TryOnPreviewLabels.status(TryOnPreviewClaim.NOT_DETECTED))
            .contains(TryOnPreviewLabels.LIGHTING_HINT)
    }

    @Test
    fun status_modesAreDistinct() {
        val statuses = TryOnPreviewClaim.entries.map { TryOnPreviewLabels.status(it) }.toSet()
        assertThat(statuses).hasSize(TryOnPreviewClaim.entries.size)
    }
}
