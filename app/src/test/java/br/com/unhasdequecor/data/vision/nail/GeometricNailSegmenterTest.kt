package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class GeometricNailSegmenterTest {

    private val segmenter = GeometricNailSegmenter()

    @Test
    fun `segments almond roi into non-empty mask`() {
        val rw = 60
        val rh = 80
        val skin = argb(210, 170, 150)
        val plate = argb(235, 210, 195)
        val pixels = IntArray(rw * rh) { skin }
        for (y in 15 until 55) {
            for (x in 18 until 42) {
                pixels[y * rw + x] = plate
            }
        }
        val image = mockk<Bitmap>(relaxed = true) {
            every { width } returns 120
            every { height } returns 160
            every { getPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
                val dest = firstArg<IntArray>()
                System.arraycopy(pixels, 0, dest, 0, pixels.size)
            }
        }
        val roi = NailRoi(
            finger = Finger.MIDDLE,
            bounds = PixelRect(left = 30, top = 30, right = 90, bottom = 110),
            polygon = listOf(
                PixelPoint(60f, 35f),
                PixelPoint(78f, 55f),
                PixelPoint(75f, 95f),
                PixelPoint(45f, 95f),
                PixelPoint(42f, 55f),
                PixelPoint(60f, 35f),
            ),
            axisFromDip = PixelPoint(60f, 95f),
            axisToTip = PixelPoint(60f, 35f),
            lengthPx = 60f,
            widthPx = 36f,
            rotationDegrees = 0f,
            geometricConfidence = 0.9f,
        )

        val mask = segmenter.segment(image, roi)

        assertThat(mask).isNotNull()
        assertThat(mask!!.filledRatio()).isGreaterThan(0.05f)
        assertThat(mask.width).isEqualTo(rw)
        assertThat(mask.height).isEqualTo(rh)
    }

    @Test
    fun `rejects tiny bounds`() {
        val image = mockk<Bitmap>(relaxed = true) {
            every { width } returns 40
            every { height } returns 40
        }
        val roi = NailRoi(
            finger = Finger.PINKY,
            bounds = PixelRect(left = 10, top = 10, right = 12, bottom = 12),
            polygon = listOf(
                PixelPoint(11f, 10f),
                PixelPoint(12f, 11f),
                PixelPoint(11f, 12f),
                PixelPoint(10f, 11f),
            ),
            axisFromDip = PixelPoint(11f, 12f),
            axisToTip = PixelPoint(11f, 10f),
            lengthPx = 4f,
            widthPx = 3f,
            rotationDegrees = 0f,
            geometricConfidence = 0.5f,
        )
        assertThat(segmenter.segment(image, roi)).isNull()
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
