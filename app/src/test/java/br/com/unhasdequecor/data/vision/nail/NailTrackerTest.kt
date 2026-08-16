package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NailTrackerTest {

    @Test
    fun `confident translational frame keeps mask and roi origins coherent`() {
        val tracker = NailTracker()
        val first = nail(x = 100f, y = 100f, maskX = 100, maskY = 100)
        val second = nail(x = 110f, y = 106f, maskX = 110, maskY = 106)

        tracker.stabilize(listOf(first))
        val result = tracker.stabilize(listOf(second)).single()

        assertEquals(result.roi.bounds.left, result.mask.originX)
        assertEquals(result.roi.bounds.top, result.mask.originY)
        assertEquals(NailPredictionReason.STABLE, tracker.lastPredictionReport.predictionReason)
        assertFalse(tracker.lastPredictionReport.predictionApplied)
    }

    @Test
    fun `low confidence translational frame blends roi and mask as one unit`() {
        val tracker = NailTracker()
        val first = nail(x = 100f, y = 100f, maskX = 100, maskY = 100, confidence = 0.9f)
        val next = nail(x = 110f, y = 106f, maskX = 110, maskY = 106, confidence = 0.5f)

        tracker.stabilize(listOf(first))
        val result = tracker.stabilize(listOf(next)).single()

        assertEquals(result.roi.bounds.left, result.mask.originX)
        assertEquals(result.roi.bounds.top, result.mask.originY)
        assertEquals(105, result.mask.originX)
        assertEquals(103, result.mask.originY)
        assertEquals(105.5f, result.roi.axisToTip.x, 0.01f)
        assertEquals(103.3f, result.roi.axisToTip.y, 0.01f)
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
        assertFalse(tracker.lastPredictionReport.predictionApplied)
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
        assertFalse(tracker.lastPredictionReport.predictionApplied)
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
        assertEquals(100f, result.roi.axisToTip.y, 0.01f)
    }

    @Test
    fun `low confidence rotation does not apply translational prediction`() {
        val tracker = NailTracker()
        val first = nail(x = 100f, y = 100f, rotation = 0f, confidence = 0.9f)
        val rotated = nail(x = 110f, y = 100f, rotation = 25f, confidence = 0.2f)

        tracker.stabilize(listOf(first))
        val result = tracker.stabilize(listOf(rotated))

        assertTrue(result.isEmpty())
        assertFalse(tracker.lastPredictionReport.predictionApplied)
        assertEquals(NailPredictionReason.RECOVERY, tracker.lastPredictionReport.predictionReason)
    }

    @Test
    fun `low confidence scale does not apply translational prediction`() {
        val tracker = NailTracker()
        val first = nail(x = 100f, y = 100f, length = 40f, width = 20f, confidence = 0.9f)
        val scaled = nail(x = 110f, y = 100f, length = 60f, width = 30f, confidence = 0.2f)

        tracker.stabilize(listOf(first))
        val result = tracker.stabilize(listOf(scaled))

        assertTrue(result.isEmpty())
        assertFalse(tracker.lastPredictionReport.predictionApplied)
        assertEquals(NailPredictionReason.RECOVERY, tracker.lastPredictionReport.predictionReason)
    }

    @Test
    fun `confidence recovery resumes normal tracking after predicted frame`() {
        val tracker = NailTracker()
        val first = nail(x = 100f, y = 100f, confidence = 0.9f)
        val second = nail(x = 110f, y = 100f, confidence = 0.9f)
        val low = nail(x = 111f, y = 100f, confidence = 0.2f)
        val recovered = nail(x = 130f, y = 100f, confidence = 0.9f)

        tracker.stabilize(listOf(first))
        tracker.stabilize(listOf(second))
        tracker.stabilize(listOf(low))
        val result = tracker.stabilize(listOf(recovered)).single()

        assertEquals(130f, result.roi.axisToTip.x, 0.01f)
        assertEquals(NailPredictionReason.STABLE, tracker.lastPredictionReport.predictionReason)
        assertFalse(tracker.lastPredictionReport.predictionApplied)
    }

    @Test
    fun `empty frame reports recovery without retaining a stale nail`() {
        val tracker = NailTracker()
        tracker.stabilize(listOf(nail(x = 100f, y = 100f)))

        val result = tracker.stabilize(emptyList())

        assertTrue(result.isEmpty())
        assertEquals(NailPredictionReason.RECOVERY, tracker.lastPredictionReport.predictionReason)
        assertFalse(tracker.lastPredictionReport.predictionApplied)
    }

    @Test
    fun `same roi shape with different mask shape keeps next mask`() {
        val tracker = NailTracker()
        val first = nail(x = 100f, y = 100f, maskWidth = 20, maskHeight = 40, confidence = 0.9f)
        val next = nail(x = 110f, y = 106f, maskX = 110, maskY = 106, maskWidth = 18, maskHeight = 38, confidence = 0.5f)

        tracker.stabilize(listOf(first))
        val result = tracker.stabilize(listOf(next)).single()

        assertEquals(next.mask, result.mask)
        assertEquals(result.roi.bounds.left, result.mask.originX)
        assertEquals(result.roi.bounds.top, result.mask.originY)
        assertEquals(NailPredictionReason.STABLE, tracker.lastPredictionReport.predictionReason)
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
        assertFalse(tracker.lastPredictionReport.predictionApplied)
    }

    private fun nail(
        x: Float,
        y: Float,
        maskX: Int = x.toInt(),
        maskY: Int = y.toInt(),
        rotation: Float = 0f,
        length: Float = 40f,
        width: Float = 20f,
        maskWidth: Int = width.toInt(),
        maskHeight: Int = length.toInt(),
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
            confidence = confidence,
        )
    }
}
