package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import br.com.unhasdequecor.data.vision.HandLandmarkProcessor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NailTryOnPipelineStillIsolationTest {

    @Test
    fun `still detection clears live tracker state before missing-landmark early return`() {
        val landmarkProcessor = mockk<HandLandmarkProcessor>()
        val roiEstimator = mockk<NailRoiEstimator>()
        val segmenter = mockk<NailSegmenter>()
        val colorApplier = mockk<NailColorApplier>()
        val tracker = NailTracker()
        val pipeline = NailTryOnPipeline(
            landmarkProcessor = landmarkProcessor,
            roiEstimator = roiEstimator,
            segmenter = segmenter,
            colorApplier = colorApplier,
            tracker = tracker,
        )

        val image = mockk<Bitmap>(relaxed = true) {
            every { width } returns 200
            every { height } returns 300
            every { isRecycled } returns false
        }
        every { landmarkProcessor.detectLandmarksWithOrientationFallback(image) } returns null

        val valid = nail(confidence = 0.9f, x = 100f)
        val lowConfidence = nail(confidence = 0.2f, x = 111f)

        tracker.stabilize(listOf(valid))
        assertTrue(tracker.stabilize(listOf(lowConfidence)).isNotEmpty())

        val result = pipeline.detect(image, stabilize = false)

        assertEquals(null, result)
        val afterStill = tracker.stabilize(listOf(lowConfidence))
        assertTrue(afterStill.isNotEmpty())
        assertFalse(tracker.lastPredictionReport.predictionApplied)
        assertEquals(NailPredictionReason.STABLE, tracker.lastPredictionReport.predictionReason)
        verify(exactly = 1) { landmarkProcessor.detectLandmarksWithOrientationFallback(image) }
    }

    private fun nail(confidence: Float, x: Float): DetectedNail {
        val roi = NailRoi(
            finger = Finger.INDEX,
            bounds = ImageCoordinates.PixelRect(x.toInt(), 100, x.toInt() + 20, 140),
            polygon = listOf(
                ImageCoordinates.PixelPoint(x, 100f),
                ImageCoordinates.PixelPoint(x + 20f, 100f),
                ImageCoordinates.PixelPoint(x + 20f, 140f),
            ),
            axisFromDip = ImageCoordinates.PixelPoint(x, 140f),
            axisToTip = ImageCoordinates.PixelPoint(x, 100f),
            lengthPx = 40f,
            widthPx = 20f,
            rotationDegrees = 0f,
            geometricConfidence = 0.95f,
        )
        val mask = NailMask(
            width = 20,
            height = 40,
            alpha = ByteArray(20 * 40) { (-1).toByte() },
            originX = x.toInt(),
            originY = 100,
        )
        return DetectedNail(
            finger = Finger.INDEX,
            roi = roi,
            mask = mask,
            confidence = confidence,
        )
    }
}
