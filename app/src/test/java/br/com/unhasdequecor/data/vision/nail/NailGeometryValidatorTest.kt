package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NailGeometryValidatorTest {
    @Test
    fun `valid geometry is accepted`() {
        val result = NailGeometryValidator.validate(nail())

        assertTrue(result.valid)
        assertEquals(NailGeometryValidator.Reason.VALID, result.reason)
    }

    @Test
    fun `invalid nail dimensions are rejected`() {
        val result = NailGeometryValidator.validate(nail(length = 13f))

        assertFalse(result.valid)
        assertEquals(NailGeometryValidator.Reason.NAIL_DIMENSIONS_INVALID, result.reason)
    }

    @Test
    fun `small roi is rejected`() {
        val result = NailGeometryValidator.validate(
            nail(boundsWidth = 9, boundsHeight = 13),
        )

        assertFalse(result.valid)
        assertEquals(NailGeometryValidator.Reason.ROI_TOO_SMALL, result.reason)
    }

    @Test
    fun `large roi is rejected`() {
        val result = NailGeometryValidator.validate(
            nail(boundsWidth = 221, boundsHeight = 40),
        )

        assertFalse(result.valid)
        assertEquals(NailGeometryValidator.Reason.ROI_TOO_LARGE, result.reason)
    }

    @Test
    fun `extreme roi aspect ratio is rejected`() {
        val result = NailGeometryValidator.validate(
            nail(boundsWidth = 200, boundsHeight = 20),
        )

        assertFalse(result.valid)
        assertEquals(NailGeometryValidator.Reason.ROI_ASPECT_INVALID, result.reason)
    }

    @Test
    fun `short roi axis is rejected`() {
        val result = NailGeometryValidator.validate(
            nail(axisLength = 9f),
        )

        assertFalse(result.valid)
        assertEquals(NailGeometryValidator.Reason.AXIS_TOO_SHORT, result.reason)
    }

    @Test
    fun `small mask is rejected`() {
        val result = NailGeometryValidator.validate(
            nail(maskWidth = 7, maskHeight = 40),
        )

        assertFalse(result.valid)
        assertEquals(NailGeometryValidator.Reason.MASK_INVALID, result.reason)
    }

    @Test
    fun `mask and roi origin mismatch is rejected`() {
        val result = NailGeometryValidator.validate(
            nail(maskX = 102),
        )

        assertFalse(result.valid)
        assertEquals(NailGeometryValidator.Reason.MASK_ROI_MISMATCH, result.reason)
    }

    private fun nail(
        length: Float = 40f,
        width: Float = 20f,
        boundsWidth: Int = 20,
        boundsHeight: Int = 40,
        axisLength: Float = 40f,
        maskWidth: Int = 20,
        maskHeight: Int = 40,
        maskX: Int = 100,
        maskY: Int = 100,
    ): DetectedNail {
        val roi = NailRoi(
            finger = Finger.INDEX,
            bounds = PixelRect(100, 100, 100 + boundsWidth, 100 + boundsHeight),
            polygon = listOf(
                PixelPoint(100f, 100f),
                PixelPoint(100f + width, 100f),
                PixelPoint(100f + width, 100f + length),
            ),
            axisFromDip = PixelPoint(100f, 100f + axisLength),
            axisToTip = PixelPoint(100f, 100f),
            lengthPx = length,
            widthPx = width,
            rotationDegrees = 0f,
            geometricConfidence = 0.95f,
        )
        val mask = NailMask(
            width = maskWidth,
            height = maskHeight,
            alpha = ByteArray(maskWidth * maskHeight) { (-1).toByte() },
            originX = maskX,
            originY = maskY,
        )
        return DetectedNail(
            finger = Finger.INDEX,
            roi = roi,
            mask = mask,
            confidence = 0.9f,
        )
    }
}
