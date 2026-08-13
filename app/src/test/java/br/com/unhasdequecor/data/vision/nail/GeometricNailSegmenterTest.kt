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
    fun `uses almond tip cuticle axis even when landmark axis differs`() {
        // Landmark axis aponta para o lado; almond é vertical — trim deve seguir o almond.
        val rw = 60
        val rh = 80
        val skin = argb(210, 170, 150)
        val plate = argb(235, 210, 195)
        val pixels = IntArray(rw * rh) { skin }
        // Placa no terço distal do almond (topo do crop).
        for (y in 8 until 28) {
            for (x in 22 until 38) {
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
        val originX = 30
        val originY = 30
        val roi = NailRoi(
            finger = Finger.MIDDLE,
            bounds = PixelRect(left = originX, top = originY, right = originX + rw, bottom = originY + rh),
            polygon = listOf(
                PixelPoint(originX + 30f, originY + 5f),
                PixelPoint(originX + 42f, originY + 30f),
                PixelPoint(originX + 40f, originY + 70f),
                PixelPoint(originX + 20f, originY + 70f),
                PixelPoint(originX + 18f, originY + 30f),
                PixelPoint(originX + 30f, originY + 5f),
            ),
            // Eixo landmark errado (horizontal) — não deve guiar tip/cutícula.
            axisFromDip = PixelPoint(originX + 10f, originY + 40f),
            axisToTip = PixelPoint(originX + 50f, originY + 40f),
            lengthPx = 60f,
            widthPx = 28f,
            rotationDegrees = 0f,
            geometricConfidence = 0.9f,
        )

        val mask = segmenter.segment(image, roi)
        assertThat(mask).isNotNull()
        // Região distal (tip do almond) deve manter cobertura.
        var tipSolid = 0
        for (y in 8 until 24) {
            for (x in 22 until 38) {
                if ((mask!!.alpha[y * rw + x].toInt() and 0xFF) >= 128) tipSolid++
            }
        }
        assertThat(tipSolid).isGreaterThan(40)
    }

    @Test
    fun `segments dark wine polish on retinta skin`() {
        val rw = 60
        val rh = 80
        val scene = HandTrainingScenes.varieties.first { it.id == "retinta_wine_polish" }
        val pixels = HandTrainingScenes.fillNailCrop(rw, rh, scene.skin, scene.plate)
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
        assertThat(mask!!.filledRatio()).isGreaterThan(0.04f)
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
