package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class PaintAwareNailSegmenterTest {
    private val segmenter = PaintAwareNailSegmenter()

    @Test
    fun `follows visible plate instead of undersized roi`() {
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
            for (x in 50 - halfWidth..50 + halfWidth) pixels[y * width + x] = nail
        }

        val image = bitmap(width, height, pixels)
        val roi = NailRoi(
            finger = Finger.MIDDLE,
            bounds = PixelRect(25, 20, 75, 105),
            polygon = listOf(
                PixelPoint(45f, 43f), PixelPoint(55f, 43f),
                PixelPoint(55f, 78f), PixelPoint(45f, 78f),
            ),
            axisFromDip = PixelPoint(50f, 82f),
            axisToTip = PixelPoint(50f, 28f),
            lengthPx = 35f,
            widthPx = 10f,
            rotationDegrees = 0f,
            geometricConfidence = 0.95f,
        )

        val mask = segmenter.segment(image, roi)

        assertThat(mask).isNotNull()
        assertThat(mask!!.boundaryPolygon).isNotNull()
        assertThat(mask.filledRatio()).isGreaterThan(0.10f)
    }

    @Test
    fun `traces beyond narrow geometric prior when visible plate is wider`() {
        val width = 100
        val height = 120
        val skin = argb(132, 86, 74)
        val nail = argb(210, 194, 188)
        val pixels = IntArray(width * height) { skin }
        for (y in 30 until 92) {
            for (x in 33..67) pixels[y * width + x] = nail
        }

        val mask = segmenter.segment(
            bitmap(width, height, pixels),
            NailRoi(
                finger = Finger.MIDDLE,
                bounds = PixelRect(25, 20, 75, 105),
                polygon = listOf(
                    PixelPoint(47f, 43f), PixelPoint(53f, 43f),
                    PixelPoint(53f, 78f), PixelPoint(47f, 78f),
                ),
                axisFromDip = PixelPoint(50f, 82f),
                axisToTip = PixelPoint(50f, 28f),
                lengthPx = 35f,
                widthPx = 6f,
                rotationDegrees = 0f,
                geometricConfidence = 0.95f,
            ),
        )

        assertThat(mask).isNotNull()
        assertThat(mask!!.filledRatio()).isGreaterThan(0.18f)
        val xs = mask.boundaryPolygon!!.map { it.x }
        assertThat(xs.max() - xs.min()).isGreaterThan(20f)
    }

    @Test
    fun `keeps dark polish on deep skin instead of reverting to skin`() {
        val width = 100
        val height = 120
        val skin = argb(72, 48, 38)
        val wine = argb(58, 22, 32)
        val pixels = IntArray(width * height) { skin }
        for (y in 30 until 92) for (x in 37..63) pixels[y * width + x] = wine

        val mask = segmenter.segment(
            bitmap(width, height, pixels),
            NailRoi(
                finger = Finger.MIDDLE,
                bounds = PixelRect(25, 20, 75, 105),
                polygon = listOf(
                    PixelPoint(45f, 43f), PixelPoint(55f, 43f),
                    PixelPoint(55f, 78f), PixelPoint(45f, 78f),
                ),
                axisFromDip = PixelPoint(50f, 82f),
                axisToTip = PixelPoint(50f, 28f),
                lengthPx = 35f,
                widthPx = 10f,
                rotationDegrees = 0f,
                geometricConfidence = 0.95f,
            ),
        )

        assertThat(mask).isNotNull()
        assertThat(mask!!.filledRatio()).isGreaterThan(0.10f)
    }

    @Test
    fun `rejects all skin roi instead of painting the finger`() {
        val width = 80
        val height = 100
        val skin = argb(150, 95, 82)
        val pixels = IntArray(width * height) { skin }
        val image = bitmap(width, height, pixels)
        val roi = NailRoi(
            finger = Finger.INDEX,
            bounds = PixelRect(20, 15, 60, 85),
            polygon = listOf(
                PixelPoint(40f, 20f), PixelPoint(50f, 32f),
                PixelPoint(48f, 70f), PixelPoint(32f, 70f),
                PixelPoint(30f, 32f),
            ),
            axisFromDip = PixelPoint(40f, 72f),
            axisToTip = PixelPoint(40f, 22f),
            lengthPx = 50f,
            widthPx = 18f,
            rotationDegrees = 0f,
            geometricConfidence = 0.9f,
        )

        val mask = segmenter.segment(image, roi)

        assertThat(mask == null || mask.filledRatio() < 0.35f).isTrue()
    }

    @Test
    fun `keeps paint boundary inside safe inset`() {
        val width = 90
        val height = 110
        val skin = argb(140, 92, 80)
        val nail = argb(230, 205, 195)
        val pixels = IntArray(width * height) { skin }
        for (y in 28 until 82) for (x in 31..59) pixels[y * width + x] = nail
        val image = bitmap(width, height, pixels)
        val roi = NailRoi(
            finger = Finger.MIDDLE,
            bounds = PixelRect(20, 15, 70, 95),
            polygon = listOf(
                PixelPoint(42f, 25f), PixelPoint(55f, 30f),
                PixelPoint(60f, 78f), PixelPoint(30f, 78f),
                PixelPoint(35f, 30f),
            ),
            axisFromDip = PixelPoint(45f, 80f),
            axisToTip = PixelPoint(45f, 24f),
            lengthPx = 55f,
            widthPx = 28f,
            rotationDegrees = 0f,
            geometricConfidence = 0.9f,
        )

        val mask = segmenter.segment(image, roi)

        assertThat(mask).isNotNull()
        assertThat(mask!!.boundaryPolygon!!.all { it.x in 20f..69f && it.y in 15f..94f }).isTrue()
    }

    private fun bitmap(width: Int, height: Int, pixels: IntArray): Bitmap = mockk(relaxed = true) {
        every { this@mockk.width } returns width
        every { this@mockk.height } returns height
        every { getPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
            val dest = firstArg<IntArray>()
            val offset = secondArg<Int>()
            val stride = thirdArg<Int>()
            val srcX = fourthArg<Int>()
            val srcY = fifthArg<Int>()
            val w = sixthArg<Int>()
            val h = seventhArg<Int>()
            for (row in 0 until h) for (col in 0 until w) {
                dest[offset + row * stride + col] = pixels[(srcY + row) * width + srcX + col]
            }
        }
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
