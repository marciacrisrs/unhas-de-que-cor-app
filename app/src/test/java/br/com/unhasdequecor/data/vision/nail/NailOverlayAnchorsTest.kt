package br.com.unhasdequecor.data.vision.nail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NailOverlayAnchorsTest {

    @Test
    fun `default layout has five nails inside unit square`() {
        assertThat(NailOverlayAnchors.DEFAULT).hasSize(5)
        NailOverlayAnchors.DEFAULT.forEach { nail ->
            assertThat(nail.centerX).isAtLeast(0f)
            assertThat(nail.centerX).isAtMost(1f)
            assertThat(nail.centerY).isAtLeast(0f)
            assertThat(nail.centerY).isAtMost(1f)
            assertThat(nail.width).isGreaterThan(0f)
            assertThat(nail.height).isGreaterThan(0f)
        }
    }

    @Test
    fun `known sample ids resolve dedicated layouts`() {
        val clara = NailOverlayAnchors.forSample("clara_vermelho")
        assertThat(clara).isNotEqualTo(NailOverlayAnchors.DEFAULT)
        assertThat(clara).hasSize(5)
        assertThat(NailOverlayAnchors.forSample(null)).isEqualTo(NailOverlayAnchors.DEFAULT)
        assertThat(NailOverlayAnchors.forSample("unknown")).isEqualTo(NailOverlayAnchors.DEFAULT)
    }

    @Test
    fun `mask assets exist for calibrated samples`() {
        assertThat(NailOverlayAnchors.hasMaskAsset("clara_vermelho")).isTrue()
        assertThat(NailOverlayAnchors.hasMaskAsset("media_rosa")).isTrue()
        assertThat(NailOverlayAnchors.hasMaskAsset("morena_nude")).isTrue()
        assertThat(NailOverlayAnchors.hasMaskAsset("retinta_vinho")).isTrue()
        assertThat(NailOverlayAnchors.hasMaskAsset("retinta_polegar")).isTrue()
        assertThat(NailOverlayAnchors.hasMaskAsset("morena_clara_coral")).isTrue()
        assertThat(NailOverlayAnchors.forSample("retinta_polegar")).hasSize(5)
        assertThat(NailOverlayAnchors.forSample("retinta_polegar"))
            .isNotEqualTo(NailOverlayAnchors.DEFAULT)
        assertThat(NailOverlayAnchors.hasMaskAsset(null)).isFalse()
        assertThat(NailOverlayAnchors.hasMaskAsset("unknown")).isFalse()
    }
}
