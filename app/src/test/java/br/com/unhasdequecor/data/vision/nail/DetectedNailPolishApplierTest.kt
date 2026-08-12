package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class DetectedNailPolishApplierTest {

    @Test
    fun apply_whenAnchorsEmpty_returnsNull() {
        val source = mockk<Bitmap>(relaxed = true) {
            every { width } returns 100
            every { height } returns 100
        }

        val result = DetectedNailPolishApplier.apply(source, emptyList(), Color.Red)

        assertThat(result).isNull()
    }

    @Test
    fun apply_whenSourceWidthInvalid_returnsNull() {
        val source = mockk<Bitmap>(relaxed = true) {
            every { width } returns 0
            every { height } returns 100
        }
        val anchors = listOf(
            NailOverlayAnchor(
                centerX = 0.5f,
                centerY = 0.5f,
                width = 0.1f,
                height = 0.12f,
                rotationDegrees = 0f,
            ),
        )

        val result = DetectedNailPolishApplier.apply(source, anchors, Color.Red)

        assertThat(result).isNull()
    }

    @Test
    fun apply_whenSourceHeightInvalid_returnsNull() {
        val source = mockk<Bitmap>(relaxed = true) {
            every { width } returns 100
            every { height } returns -1
        }
        val anchors = listOf(
            NailOverlayAnchor(
                centerX = 0.5f,
                centerY = 0.5f,
                width = 0.1f,
                height = 0.12f,
                rotationDegrees = 0f,
            ),
        )

        val result = DetectedNailPolishApplier.apply(source, anchors, Color.Blue)

        assertThat(result).isNull()
    }
}
