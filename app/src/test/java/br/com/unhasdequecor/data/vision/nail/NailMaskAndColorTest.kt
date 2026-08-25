package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/** Testes de máscara / confiança / cor (sem framework de Bitmap). */
class NailMaskAndColorTest {

    @Test
    fun `mask filled ratio and coverage bounds`() {
        val alpha = ByteArray(4) { 0 }
        alpha[0] = 255.toByte()
        alpha[1] = 200.toByte()
        val mask = NailMask(width = 2, height = 2, alpha = alpha, originX = 10, originY = 20)
        assertThat(mask.coverageAt(0, 0)).isEqualTo(255)
        assertThat(mask.coverageAt(1, 0)).isEqualTo(200)
        assertThat(mask.coverageAt(0, 1)).isEqualTo(0)
        assertThat(mask.coverageAt(5, 5)).isEqualTo(0)
        assertThat(mask.filledRatio()).isEqualTo(0.5f)
    }

    @Test
    fun `empty mask has zero fill`() {
        val mask = NailMask(2, 2, ByteArray(4), 0, 0)
        assertThat(mask.filledRatio()).isEqualTo(0f)
    }

    @Test
    fun `mask stays within declared roi size`() {
        val alpha = ByteArray(6 * 8) { 255.toByte() }
        val mask = NailMask(width = 6, height = 8, alpha = alpha, originX = 12, originY = 4)
        assertThat(mask.width).isEqualTo(6)
        assertThat(mask.height).isEqualTo(8)
        assertThat(mask.alpha.size).isEqualTo(48)
        assertThat(mask.coverageAt(-1, 0)).isEqualTo(0)
        assertThat(mask.coverageAt(6, 0)).isEqualTo(0)
        assertThat(mask.coverageAt(0, 8)).isEqualTo(0)
    }

    @Test
    fun `low confidence nail is skipped by color applier gate`() {
        val roi = NailRoi(
            finger = Finger.INDEX,
            bounds = PixelRect(0, 0, 10, 10),
            polygon = listOf(
                PixelPoint(1f, 1f),
                PixelPoint(9f, 1f),
                PixelPoint(9f, 9f),
                PixelPoint(1f, 9f),
            ),
            axisFromDip = PixelPoint(5f, 8f),
            axisToTip = PixelPoint(5f, 2f),
            lengthPx = 8f,
            widthPx = 5f,
            rotationDegrees = 0f,
            geometricConfidence = 0.2f,
        )
        val nail = DetectedNail(
            finger = Finger.INDEX,
            roi = roi,
            mask = NailMask(2, 2, ByteArray(4) { 255.toByte() }),
            confidence = 0.1f,
        )
        assertThat(nail.confidence).isLessThan(NailColorApplier.MIN_CONFIDENCE)
    }

    @Test
    fun `color transform leaves pixels outside mask unchanged`() {
        val src = 0xFF8090A0.toInt()
        val out = NailColorApplier.transformPixel(
            srcArgb = src,
            maskAlpha = 0,
            targetR = 200,
            targetG = 40,
            targetB = 80,
            meanLum = 120f,
        )
        assertThat(out).isEqualTo(src)
    }

    @Test
    fun `color transform changes pixels inside mask toward polish`() {
        val src = 0xFFB0B0B0.toInt()
        val out = NailColorApplier.transformPixel(
            srcArgb = src,
            maskAlpha = 255,
            targetR = 200,
            targetG = 40,
            targetB = 80,
            meanLum = 176f,
        )
        assertThat(out).isNotEqualTo(src)
        val r = (out shr 16) and 0xFF
        val g = (out shr 8) and 0xFF
        val b = out and 0xFF
        assertThat(r).isGreaterThan(g)
        assertThat(r).isGreaterThan(b)
    }

    @Test
    fun `roi estimator rejects degenerate tiny finger`() {
        val pts = MutableList(21) { ImageCoordinates.NormPoint(0.5f, 0.5f) }
        val hand = HandLandmarks(
            points = pts,
            imageWidth = 200,
            imageHeight = 200,
            presenceScore = 0.2f,
        )
        val rois = NailRoiEstimator().estimateAll(hand)
        rois.forEach {
            assertThat(it.geometricConfidence).isLessThan(0.5f)
        }
    }

    @Test
    fun `apply recycles output when mask weight is below minimum`() {
        val source = mockk<Bitmap>(relaxed = true)
        val out = mockk<Bitmap>(relaxed = true)
        every { source.copy(any(), any()) } returns out
        every { out.isRecycled } returns false
        every { out.getPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
            firstArg<IntArray>().fill(0xFF808080.toInt())
        }
        val roi = NailRoi(
            finger = Finger.INDEX,
            bounds = PixelRect(0, 0, 2, 2),
            polygon = listOf(
                PixelPoint(0f, 0f),
                PixelPoint(2f, 0f),
                PixelPoint(2f, 2f),
                PixelPoint(0f, 2f),
            ),
            axisFromDip = PixelPoint(1f, 1.5f),
            axisToTip = PixelPoint(1f, 0.5f),
            lengthPx = 2f,
            widthPx = 2f,
            rotationDegrees = 0f,
            geometricConfidence = 0.9f,
        )
        val nail = DetectedNail(
            finger = Finger.INDEX,
            roi = roi,
            mask = NailMask(2, 2, ByteArray(4) { 0 }, originX = 0, originY = 0),
            confidence = 0.9f,
        )

        val result = NailColorApplier().apply(source, listOf(nail), Color.Red)

        assertThat(result).isNull()
        verify(exactly = 1) { out.recycle() }
    }

    @Test
    fun `apply clips mask origin that tracking pushed past the left and top edges`() {
        val source = mockk<Bitmap>(relaxed = true)
        val out = bitmapWithSize(32, 32)
        every { source.copy(any(), any()) } returns out
        val nail = paintableNail(
            originX = -8,
            originY = -4,
            maskWidth = 20,
            maskHeight = 20,
        )

        val result = NailColorApplier().apply(source, listOf(nail), Color.Red)

        assertThat(result).isSameInstanceAs(out)
        verify { out.getPixels(any(), any(), any(), 0, 0, 12, 16) }
        verify { out.setPixels(any(), any(), any(), 0, 0, 12, 16) }
        verify(exactly = 0) { out.recycle() }
    }

    @Test
    fun `apply clips mask that extends past the right and bottom edges`() {
        val source = mockk<Bitmap>(relaxed = true)
        val out = bitmapWithSize(32, 32)
        every { source.copy(any(), any()) } returns out
        val nail = paintableNail(
            originX = 24,
            originY = 20,
            maskWidth = 20,
            maskHeight = 20,
        )

        val result = NailColorApplier().apply(source, listOf(nail), Color.Red)

        assertThat(result).isSameInstanceAs(out)
        verify { out.getPixels(any(), any(), any(), 24, 20, 8, 12) }
        verify { out.setPixels(any(), any(), any(), 24, 20, 8, 12) }
    }

    @Test
    fun `apply recycles output when tracked mask is completely outside the frame`() {
        val source = mockk<Bitmap>(relaxed = true)
        val out = bitmapWithSize(32, 32)
        every { source.copy(any(), any()) } returns out
        val nail = paintableNail(
            originX = -40,
            originY = 0,
            maskWidth = 20,
            maskHeight = 20,
        )

        val result = NailColorApplier().apply(source, listOf(nail), Color.Red)

        assertThat(result).isNull()
        verify(exactly = 0) { out.getPixels(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { out.recycle() }
    }

    private fun bitmapWithSize(width: Int, height: Int): Bitmap {
        val bitmap = mockk<Bitmap>(relaxed = true)
        every { bitmap.width } returns width
        every { bitmap.height } returns height
        every { bitmap.isRecycled } returns false
        every { bitmap.getPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
            val x = invocation.args[3] as Int
            val y = invocation.args[4] as Int
            val w = invocation.args[5] as Int
            val h = invocation.args[6] as Int
            require(x >= 0 && y >= 0 && x + w <= width && y + h <= height) {
                "getPixels out of bounds: x=$x y=$y w=$w h=$h on ${width}x$height"
            }
            firstArg<IntArray>().fill(0xFF808080.toInt())
        }
        every { bitmap.setPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
            val x = invocation.args[3] as Int
            val y = invocation.args[4] as Int
            val w = invocation.args[5] as Int
            val h = invocation.args[6] as Int
            require(x >= 0 && y >= 0 && x + w <= width && y + h <= height) {
                "setPixels out of bounds: x=$x y=$y w=$w h=$h on ${width}x$height"
            }
        }
        return bitmap
    }

    private fun paintableNail(
        originX: Int,
        originY: Int,
        maskWidth: Int,
        maskHeight: Int,
    ): DetectedNail {
        val roi = NailRoi(
            finger = Finger.INDEX,
            bounds = PixelRect(
                originX,
                originY,
                originX + maskWidth,
                originY + maskHeight,
            ),
            polygon = listOf(
                PixelPoint(originX.toFloat(), originY.toFloat()),
                PixelPoint((originX + maskWidth).toFloat(), originY.toFloat()),
                PixelPoint((originX + maskWidth).toFloat(), (originY + maskHeight).toFloat()),
                PixelPoint(originX.toFloat(), (originY + maskHeight).toFloat()),
            ),
            axisFromDip = PixelPoint(originX + maskWidth / 2f, originY + maskHeight.toFloat()),
            axisToTip = PixelPoint(originX + maskWidth / 2f, originY.toFloat()),
            lengthPx = maskHeight.toFloat(),
            widthPx = maskWidth.toFloat(),
            rotationDegrees = 0f,
            geometricConfidence = 0.9f,
        )
        return DetectedNail(
            finger = Finger.INDEX,
            roi = roi,
            mask = NailMask(
                width = maskWidth,
                height = maskHeight,
                alpha = ByteArray(maskWidth * maskHeight) { 255.toByte() },
                originX = originX,
                originY = originY,
            ),
            confidence = 0.9f,
        )
    }
}
