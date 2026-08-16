package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NailTrackerTest {

    @Test
    fun `translational blend keeps mask and roi origins coherent`() {
        val tracker = NailTracker()
        val first = nail(x = 100f, y = 100f, maskX = 100, maskY = 100)
        val second = nail(x = 110f, y = 106f, maskX = 110, maskY = 106)

        tracker.stabilize(listOf(first))
        val result = tracker.stabilize(listOf(second)).single()

        assertEquals(result.roi.bounds.left, result.mask.originX)
        assertEquals(result.roi.bounds.top, result.mask.originY)
        assertEquals(NailPredictionReason.STABLE, tracker.lastPredictionReport.predictionReason)
    }

    @Test
    fun `rotation uses next mask and roi together instead of hybrid blend`() {
        val tracker = NailTracker()
        val first = nail(x = 100f, y = 100f, rotation = 0f, length = 40f)
        val rotated = nail(x = 101f, y = 101f, rotation = 25f, length = 40f)

        tracker.stabilize(listOf(first))
        val result = tracker.stabilize(listOf(rotated)).single()

        assertEquals(rotated.mask, result.mask)
        assertEquals(rotated.roi, result.roi)
        assertEquals(NailPredictionReason.ROTATION, tracker.lastPredictionReport.predictionReason)
    }

    @Test
    fun `scale change uses next mask and roi together`() {
        val tracker = NailTracker()
        val first = nail(x = 100f, y = 100f, length = 40f, width = 20f)
        val scaled = nail(x = 101f, y = 101f, length = 60f, width = 30f)

        tracker.stabilize(listOf(first))
        val result = tracker.stabilize(listOf(scaled)).single()

        assertEquals(scaled.mask, result.mask)
        assertEquals(scaled.roi, result.roi)
        assertEquals(NailPredictionReason.SCALE, tracker.lastPredictionReport.predictionReason)
    }

    @Test
    fun `low confidence translational frame can use previous velocity as prediction`() {
        val tracker = NailTracker()
        val first = nail(x = 100f, y = 100f, confidence = 0.9f)
        val second = nail(x = 110f, y = 100f, confidence = 0.9f)
        val low = nail(x = 111f, y = 100f, confidence = 0.2f)

        tracker.stabilize(listOf(first))
        tracker.stabilize(listOf(second))
        val result = tracker.stabilize(listOf(low)).single()

        assertTrue(tracker.lastPredictionReport.predictionApplied)
        assertEquals(NailPredictionReason.APPLIED, tracker.lastPredictionReport.predictionReason)
        assertEquals(120f, result.roi.axisToTip.x, 0.01f)
    }

    @Test
    fun `reset forgets previous state and prediction`() {
        val tracker = NailTracker()
        tracker.stabilize(listOf(nail(x = 100f, y = 100f)))
        tracker.stabilize(listOf(nail(x = 110f, y = 100f)))
        tracker.reset()

        val result = tracker.stabilize(listOf(nail(x = 200f, y = 200f))).single()

        assertEquals(NailPredictionReason.STABLE, tracker.lastPredictionReport.predictionReason)
        assertEquals(200f, result.roi.axisToTip.x, 0.01f)
        assertEquals(200f, result.roi.axisToTip.y, 0.01f)
    }

    private fun nail(
        x: Float,
        y: Float,
        maskX: Int = x.toInt(),
        maskY: Int = y.toInt(),
        rotation: Float = 0f,
        length: Float = 40f,
        width: Float = 20f,
        confidence: Float = 0.9f,
    ): DetectedNail {
        val roi = NailRoi(
            finger = Finger.INDEX,
            bounds = PixelRect(maskX, maskY, maskX + width.toInt(), maskY + length.toInt()),
            polygon = listOf(
                PixelPoint(x, y),
                PixelPoint(x + width, y),
                PixelPoint(x + width, y + length),
            ),
            axisFromDip = PixelPoint(x, y + length),
            axisToTip = PixelPoint(x, y),
            lengthPx = length,
            widthPx = width,
            rotationDegrees = rotation,
            geometricConfidence = 0.95f,
        )
        val mask = NailMask(
            width = width.toInt(),
            height = length.toInt(),
            alpha = ByteArray(width.toInt() * length.toInt()) { (-1).toByte() },
            originX = maskX,
            originY = maskY,
        )
        return DetectedNail(
            finger = Finger.INDEX,
            roi = roi,
            mask = mask,
            confidence = confidence,
        )
    }
}
