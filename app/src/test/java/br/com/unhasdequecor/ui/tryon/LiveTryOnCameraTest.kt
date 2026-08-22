package br.com.unhasdequecor.ui.tryon

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveTryOnCameraTest {

    @Test
    fun `prefers front camera when both lenses exist`() {
        assertThat(LiveTryOnCamera.lens(hasFront = true, hasBack = true))
            .isEqualTo(LiveTryOnLens.FRONT)
        assertThat(LiveTryOnCamera.shouldMirror(LiveTryOnLens.FRONT)).isTrue()
    }

    @Test
    fun `falls back to back camera when there is no selfie lens`() {
        assertThat(LiveTryOnCamera.lens(hasFront = false, hasBack = true))
            .isEqualTo(LiveTryOnLens.BACK)
        assertThat(LiveTryOnCamera.shouldMirror(LiveTryOnLens.BACK)).isFalse()
    }

    @Test
    fun `returns null when the device has no camera`() {
        assertThat(LiveTryOnCamera.lens(hasFront = false, hasBack = false)).isNull()
    }
}
