package br.com.unhasdequecor.ui.components

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
        val media = NailOverlayAnchors.forSample("media_rosa")
        assertThat(media).isNotEqualTo(NailOverlayAnchors.DEFAULT)
        assertThat(media).hasSize(5)
        assertThat(NailOverlayAnchors.forSample(null)).isEqualTo(NailOverlayAnchors.DEFAULT)
        assertThat(NailOverlayAnchors.forSample("unknown")).isEqualTo(NailOverlayAnchors.DEFAULT)
    }
}
