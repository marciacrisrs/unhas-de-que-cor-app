package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class EvidenceNailSegmenterTest {

    private val segmenter = EvidenceNailSegmenter()

    @Test
    fun `recovers visible nail boundaries instead of returning the geometric rectangle`() {
        val width = 80
        val height = 100
        val skin = argb(188, 132, 112)
        val nail = argb(226, 207, 198)
        val pixels = IntArray(width * height) { skin }

        // Rounded/almond plate with a narrower tip and a visible cuticle line.
        for (y in 22 until 78) {
            val halfWidth = when {
                y < 30 -> 5
                y < 38 -> 9
                y < 66 -> 14
                else -> 11
            }
            for (x in 40 - halfWidth..40 + halfWidth) {
                pixels[y * width + x] = nail
            }
        }

        val image = mockk<Bitmap>(relaxed = true) {
            every { this@mockk.width } returns 120
            every { this@mockk.height } returns 140
            every { getPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
                val dest = firstArg<IntArray>()
                System.arraycopy(pixels, 0, dest, 0, pixels.size)
            }
        }

        val roi = NailRoi(
            finger = Finger.MIDDLE,
            bounds = PixelRect(20, 20, 100, 120),
            polygon = listOf(
                PixelPoint(40f, 22f),
                PixelPoint(56f, 40f),
                PixelPoint(54f, 78f),
                PixelPoint(26f, 78f),
                PixelPoint(24f, 40f),
                PixelPoint(40f, 22f),
            ),
            axisFromDip = PixelPoint(40f, 78f),
            axisToTip = PixelPoint(40f, 22f),
            lengthPx = 56f,
            widthPx = 28f,
            rotationDegrees = 0f,
            geometricConfidence = 0.95f,
        )

        val mask = segmenter.segment(image, roi)

        assertThat(mask).isNotNull()
        assertThat(mask!!.boundaryPolygon).isNotNull()
        assertThat(mask.filledRatio()).isGreaterThan(0.10f)
    }

    @Test
    fun `rejects uniform skin instead of inventing a nail`() {
        val width = 60
        val height = 80
        val skin = argb(188, 132, 112)
        val pixels = IntArray(width * height) { skin }
        val image = mockk<Bitmap>(relaxed = true) {
            every { this@mockk.width } returns 100
            every { this@mockk.height } returns 100
            every { getPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
                val dest = firstArg<IntArray>()
                System.arraycopy(pixels, 0, dest, 0, pixels.size)
            }
        }
        val roi = NailRoi(
            finger = Finger.INDEX,
            bounds = PixelRect(10, 10, 70, 90),
            polygon = listOf(
                PixelPoint(40f, 14f),
                PixelPoint(54f, 34f),
                PixelPoint(52f, 84f),
                PixelPoint(28f, 84f),
                PixelPoint(26f, 34f),
            ),
            axisFromDip = PixelPoint(40f, 84f),
            axisToTip = PixelPoint(40f, 14f),
            lengthPx = 70f,
            widthPx = 24f,
            rotationDegrees = 0f,
            geometricConfidence = 0.9f,
        )

        assertThat(segmenter.segment(image, roi)).isNull()
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
