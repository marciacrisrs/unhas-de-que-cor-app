package br.com.unhasdequecor.data.vision.nail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NailMaskDiagnosticsTest {
    @Test
    fun `diagnostic remains unverified without ground truth`() {
        val roi = NailRoi(
            finger = Finger.INDEX,
            polygon = listOf(
                ImageCoordinates.PixelPoint(10f, 10f),
                ImageCoordinates.PixelPoint(30f, 10f),
                ImageCoordinates.PixelPoint(30f, 70f),
                ImageCoordinates.PixelPoint(10f, 70f),
            ),
            bounds = ImageCoordinates.PixelRect(10, 10, 30, 70),
            axisFromDip = ImageCoordinates.PixelPoint(20f, 10f),
            axisToTip = ImageCoordinates.PixelPoint(20f, 70f),
            widthPx = 20f,
            lengthPx = 60f,
            geometricConfidence = 0.9f,
        )
        val mask = NailMask(
            width = 20,
            height = 60,
            alpha = ByteArray(20 * 60) { 255.toByte() },
            originX = 10,
            originY = 10,
        )
        val nail = DetectedNail(Finger.INDEX, roi, mask, confidence = 0.9f)

        val diagnostic = NailMaskDiagnostics.from(nail)

        assertThat(diagnostic.filledRatio).isEqualTo(1f)
        assertThat(diagnostic.coverageRatio).isEqualTo(1f)
        assertThat(diagnostic.classification).isEqualTo(MaskDiagnosticClassification.UNVERIFIED)
        assertThat(diagnostic.hasBoundaryPolygon).isFalse()
    }
}
