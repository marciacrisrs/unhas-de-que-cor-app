package br.com.unhasdequecor.data.vision.nail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NailPlateMaskBoundaryGuardTest {

    @Test
    fun clamp_removesFeatheredAlphaOutsidePlatePolygon() {
        val roi = NailRoi(
            finger = Finger.INDEX,
            bounds = ImageCoordinates.PixelRect(0, 0, 10, 10),
            polygon = listOf(
                ImageCoordinates.PixelPoint(2f, 2f),
                ImageCoordinates.PixelPoint(8f, 2f),
                ImageCoordinates.PixelPoint(8f, 8f),
                ImageCoordinates.PixelPoint(2f, 8f),
            ),
            axisFromDip = ImageCoordinates.PixelPoint(5f, 8f),
            axisToTip = ImageCoordinates.PixelPoint(5f, 2f),
            lengthPx = 6f,
            widthPx = 6f,
            rotationDegrees = 0f,
            geometricConfidence = 1f,
        )
        val alpha = ByteArray(100) { 255.toByte() }
        val mask = NailMask(width = 10, height = 10, alpha = alpha)

        val clamped = NailPlateMaskBoundaryGuard.clamp(mask, roi)

        assertThat(clamped.coverageAt(0, 0)).isEqualTo(0)
        assertThat(clamped.coverageAt(1, 5)).isEqualTo(0)
        assertThat(clamped.coverageAt(5, 1)).isEqualTo(0)
        assertThat(clamped.coverageAt(9, 9)).isEqualTo(0)
        assertThat(clamped.coverageAt(5, 5)).isEqualTo(255)
        assertThat(clamped.filledRatio()).isEqualTo(0.36f)
    }

    @Test
    fun clamp_preservesAlreadySafeMaskInstance() {
        val polygon = listOf(
            ImageCoordinates.PixelPoint(1f, 1f),
            ImageCoordinates.PixelPoint(4f, 1f),
            ImageCoordinates.PixelPoint(4f, 4f),
            ImageCoordinates.PixelPoint(1f, 4f),
        )
        val roi = NailRoi(
            finger = Finger.INDEX,
            bounds = ImageCoordinates.PixelRect(0, 0, 5, 5),
            polygon = polygon,
            axisFromDip = ImageCoordinates.PixelPoint(2f, 4f),
            axisToTip = ImageCoordinates.PixelPoint(2f, 1f),
            lengthPx = 3f,
            widthPx = 3f,
            rotationDegrees = 0f,
            geometricConfidence = 1f,
        )
        val alpha = ByteArray(25)
        for (y in 1..2) {
            for (x in 1..2) {
                alpha[y * 5 + x] = 255.toByte()
            }
        }
        val mask = NailMask(width = 5, height = 5, alpha = alpha)

        val clamped = NailPlateMaskBoundaryGuard.clamp(mask, roi)

        assertThat(clamped).isSameInstanceAs(mask)
    }
}
