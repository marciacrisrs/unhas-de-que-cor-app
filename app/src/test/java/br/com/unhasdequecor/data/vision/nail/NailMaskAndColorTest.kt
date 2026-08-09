package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import com.google.common.truth.Truth.assertThat
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
}
