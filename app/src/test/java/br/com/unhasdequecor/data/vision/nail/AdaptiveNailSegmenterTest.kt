package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class AdaptiveNailSegmenterTest {

    @Test
    fun `expands a conservative geometric seed toward the visible nail plate`() {
        val width = 100
        val height = 120
        val skin = argb(132, 86, 74)
        val nail = argb(210, 194, 188)
        val pixels = IntArray(width * height) { skin }

        for (y in 30 until 92) {
            val halfWidth = when {
                y < 38 -> 6
                y < 48 -> 13
                y < 82 -> 17
                else -> 13
            }
            for (x in 50 - halfWidth..50 + halfWidth) {
                pixels[y * width + x] = nail
            }
        }

        val image = mockk<Bitmap>(relaxed = true) {
            every { this@mockk.width } returns width
            every { this@mockk.height } returns height
            every { getPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
                val args = invocation.args
                val dest = args[0] as IntArray
                val destOffset = args[1] as Int
                val stride = args[2] as Int
                val srcX = args[3] as Int
                val srcY = args[4] as Int
                val w = args[5] as Int
                val h = args[6] as Int
                for (row in 0 until h) {
                    for (col in 0 until w) {
                        dest[destOffset + row * stride + col] =
                            pixels[(srcY + row) * width + srcX + col]
                    }
                }
            }
        }

        val roi = NailRoi(
            finger = Finger.MIDDLE,
            bounds = PixelRect(25, 20, 75, 105),
            polygon = listOf(
                PixelPoint(45f, 43f),
                PixelPoint(55f, 43f),
                PixelPoint(55f, 78f),
                PixelPoint(45f, 78f),
            ),
            axisFromDip = PixelPoint(50f, 82f),
            axisToTip = PixelPoint(50f, 28f),
            lengthPx = 35f,
            widthPx = 10f,
            rotationDegrees = 0f,
            geometricConfidence = 0.95f,
        )

        val mask = AdaptiveNailSegmenter(EvidenceNailSegmenter()).segment(image, roi)

        assertThat(mask).isNotNull()
        assertThat(mask!!.boundaryPolygon).isNotNull()
        assertThat(mask.filledRatio()).isGreaterThan(0.10f)
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
