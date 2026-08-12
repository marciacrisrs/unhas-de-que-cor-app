package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import br.com.unhasdequecor.data.vision.HandLandmarkProcessor
import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.OrientedHandLandmarks
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.NormPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class NailTryOnPipelineTest {

    private val landmarkProcessor = mockk<HandLandmarkProcessor>()
    private val roiEstimator = mockk<NailRoiEstimator>()
    private val segmenter = mockk<NailSegmenter>()
    private val colorApplier = mockk<NailColorApplier>()
    private val tracker = NailTracker()

    private val pipeline = NailTryOnPipeline(
        landmarkProcessor = landmarkProcessor,
        roiEstimator = roiEstimator,
        segmenter = segmenter,
        colorApplier = colorApplier,
        tracker = tracker,
    )

    @Test
    fun `process returns null when landmarks are missing`() {
        val image = mockk<Bitmap>(relaxed = true)
        every { landmarkProcessor.detectLandmarksWithOrientationFallback(image) } returns null

        val result = pipeline.process(image, Color.Red)

        assertThat(result).isNull()
        verify(exactly = 0) { colorApplier.apply(any(), any(), any()) }
    }

    @Test
    fun `process paints nails when segmentation succeeds`() {
        val image = mockk<Bitmap>(relaxed = true) {
            every { width } returns 200
            every { height } returns 300
            every { isRecycled } returns false
        }
        val painted = mockk<Bitmap>(relaxed = true)
        val landmarks = HandLandmarks(
            points = List(21) { NormPoint(0.5f, 0.5f) },
            imageWidth = 200,
            imageHeight = 300,
        )
        every {
            landmarkProcessor.detectLandmarksWithOrientationFallback(image)
        } returns OrientedHandLandmarks(bitmap = image, landmarks = landmarks)

        val roi = NailRoi(
            finger = Finger.MIDDLE,
            bounds = PixelRect(left = 90, top = 40, right = 110, bottom = 80),
            polygon = listOf(
                PixelPoint(90f, 40f),
                PixelPoint(110f, 40f),
                PixelPoint(110f, 80f),
                PixelPoint(90f, 80f),
            ),
            axisFromDip = PixelPoint(100f, 70f),
            axisToTip = PixelPoint(100f, 45f),
            lengthPx = 40f,
            widthPx = 20f,
            rotationDegrees = 0f,
            geometricConfidence = 0.9f,
        )
        every { roiEstimator.estimateAll(landmarks) } returns listOf(roi)

        val mask = NailMask(
            width = 20,
            height = 40,
            alpha = ByteArray(20 * 40) { 255.toByte() },
            originX = 90,
            originY = 40,
        )
        every { segmenter.segment(image, roi) } returns mask
        every { colorApplier.apply(image, any(), Color.Red) } returns painted

        val result = pipeline.process(image, Color.Red, stabilize = false)

        assertThat(result).isNotNull()
        assertThat(result!!.bitmap).isSameInstanceAs(painted)
        assertThat(result.nails).hasSize(1)
        assertThat(result.nails.first().finger).isEqualTo(Finger.MIDDLE)
    }

    @Test
    fun `detect then recolor avoids second landmark pass`() {
        val image = mockk<Bitmap>(relaxed = true) {
            every { width } returns 200
            every { height } returns 300
            every { isRecycled } returns false
        }
        val paintedRed = mockk<Bitmap>(relaxed = true)
        val paintedBlue = mockk<Bitmap>(relaxed = true)
        val landmarks = HandLandmarks(
            points = List(21) { NormPoint(0.5f, 0.5f) },
            imageWidth = 200,
            imageHeight = 300,
        )
        every {
            landmarkProcessor.detectLandmarksWithOrientationFallback(image)
        } returns OrientedHandLandmarks(bitmap = image, landmarks = landmarks)

        val roi = NailRoi(
            finger = Finger.MIDDLE,
            bounds = PixelRect(left = 90, top = 40, right = 110, bottom = 80),
            polygon = listOf(
                PixelPoint(90f, 40f),
                PixelPoint(110f, 40f),
                PixelPoint(110f, 80f),
                PixelPoint(90f, 80f),
            ),
            axisFromDip = PixelPoint(100f, 70f),
            axisToTip = PixelPoint(100f, 45f),
            lengthPx = 40f,
            widthPx = 20f,
            rotationDegrees = 0f,
            geometricConfidence = 0.9f,
        )
        every { roiEstimator.estimateAll(landmarks) } returns listOf(roi)
        val mask = NailMask(
            width = 20,
            height = 40,
            alpha = ByteArray(20 * 40) { 255.toByte() },
            originX = 90,
            originY = 40,
        )
        every { segmenter.segment(image, roi) } returns mask
        every { colorApplier.apply(image, any(), Color.Red) } returns paintedRed
        every { colorApplier.apply(image, any(), Color.Blue) } returns paintedBlue

        val snapshot = pipeline.detect(image, stabilize = false)
        assertThat(snapshot).isNotNull()
        val red = pipeline.recolor(checkNotNull(snapshot), Color.Red)
        val blue = pipeline.recolor(snapshot, Color.Blue)

        assertThat(red.bitmap).isSameInstanceAs(paintedRed)
        assertThat(blue.bitmap).isSameInstanceAs(paintedBlue)
        verify(exactly = 1) { landmarkProcessor.detectLandmarksWithOrientationFallback(image) }
        verify(exactly = 1) { segmenter.segment(image, roi) }
    }

    @Test
    fun `recolor with empty nails returns working when ellipse cannot run`() {
        val image = mockk<Bitmap>(relaxed = true) {
            every { width } returns 200
            every { height } returns 300
            every { isRecycled } returns false
        }
        // Landmarks nas bordas → mapper rejeita → ellipse null → devolve working.
        val landmarks = HandLandmarks(
            points = List(21) { NormPoint(0.01f, 0.01f) },
            imageWidth = 200,
            imageHeight = 300,
        )

        val result = pipeline.recolor(
            NailDetectionSnapshot(
                workingBitmap = image,
                nails = emptyList(),
                landmarks = landmarks,
                ownsWorkingBitmap = false,
                reliability = TryOnReliability.STRONG,
            ),
            Color.Red,
        )

        assertThat(result.bitmap).isSameInstanceAs(image)
        verify(exactly = 0) { colorApplier.apply(any(), any(), any()) }
    }

    @Test
    fun `detect returns null when presence is below reliability floor`() {
        val source = mockk<Bitmap>(relaxed = true) {
            every { width } returns 200
            every { height } returns 300
            every { isRecycled } returns false
        }
        val rotated = mockk<Bitmap>(relaxed = true) {
            every { width } returns 200
            every { height } returns 300
            every { isRecycled } returns false
        }
        val landmarks = HandLandmarks(
            points = List(21) { NormPoint(0.5f, 0.5f) },
            imageWidth = 200,
            imageHeight = 300,
            presenceScore = 0.10f,
        )
        every {
            landmarkProcessor.detectLandmarksWithOrientationFallback(source)
        } returns OrientedHandLandmarks(bitmap = rotated, landmarks = landmarks)
        every { roiEstimator.estimateAll(landmarks) } returns emptyList()

        val snapshot = pipeline.detect(source, stabilize = false)

        assertThat(snapshot).isNull()
        verify(exactly = 1) { rotated.recycle() }
        verify(exactly = 0) { roiEstimator.estimateAll(any()) }
        verify(exactly = 0) { segmenter.segment(any(), any()) }
        verify(exactly = 0) { colorApplier.apply(any(), any(), any()) }
    }

    @Test
    fun `detect rejected with same working bitmap never recycles user photo`() {
        val source = mockk<Bitmap>(relaxed = true) {
            every { width } returns 200
            every { height } returns 300
            every { isRecycled } returns false
        }
        val landmarks = HandLandmarks(
            points = List(21) { NormPoint(0.5f, 0.5f) },
            imageWidth = 200,
            imageHeight = 300,
            presenceScore = 0.05f,
        )
        every {
            landmarkProcessor.detectLandmarksWithOrientationFallback(source)
        } returns OrientedHandLandmarks(bitmap = source, landmarks = landmarks)

        assertThat(pipeline.detect(source, stabilize = false)).isNull()
        verify(exactly = 0) { source.recycle() }
        verify(exactly = 0) { roiEstimator.estimateAll(any()) }
    }

    @Test
    fun `detect marks weak reliability when presence is mid-range`() {
        val image = mockk<Bitmap>(relaxed = true) {
            every { width } returns 200
            every { height } returns 300
            every { isRecycled } returns false
        }
        val landmarks = HandLandmarks(
            points = List(21) { NormPoint(0.5f, 0.5f) },
            imageWidth = 200,
            imageHeight = 300,
            presenceScore = 0.40f,
        )
        every {
            landmarkProcessor.detectLandmarksWithOrientationFallback(image)
        } returns OrientedHandLandmarks(bitmap = image, landmarks = landmarks)
        every { roiEstimator.estimateAll(landmarks) } returns emptyList()

        val snapshot = pipeline.detect(image, stabilize = false)

        assertThat(snapshot).isNotNull()
        assertThat(snapshot!!.reliability).isEqualTo(TryOnReliability.WEAK)
        assertThat(snapshot.nails).isEmpty()
    }

    @Test
    fun `detect marks strong reliability when presence is high`() {
        val image = mockk<Bitmap>(relaxed = true) {
            every { width } returns 200
            every { height } returns 300
            every { isRecycled } returns false
        }
        val landmarks = HandLandmarks(
            points = List(21) { NormPoint(0.5f, 0.5f) },
            imageWidth = 200,
            imageHeight = 300,
            presenceScore = 0.80f,
        )
        every {
            landmarkProcessor.detectLandmarksWithOrientationFallback(image)
        } returns OrientedHandLandmarks(bitmap = image, landmarks = landmarks)
        every { roiEstimator.estimateAll(landmarks) } returns emptyList()

        val snapshot = pipeline.detect(image, stabilize = false)

        assertThat(snapshot).isNotNull()
        assertThat(snapshot!!.reliability).isEqualTo(TryOnReliability.STRONG)
    }

    @Test
    fun `detect drops roi below geometric confidence floor`() {
        val image = mockk<Bitmap>(relaxed = true) {
            every { width } returns 200
            every { height } returns 300
            every { isRecycled } returns false
        }
        val landmarks = HandLandmarks(
            points = List(21) { NormPoint(0.5f, 0.5f) },
            imageWidth = 200,
            imageHeight = 300,
            presenceScore = 0.80f,
        )
        every {
            landmarkProcessor.detectLandmarksWithOrientationFallback(image)
        } returns OrientedHandLandmarks(bitmap = image, landmarks = landmarks)
        val roi = sampleRoi(
            geometricConfidence = DetectionConfidenceFloor.ROI_GEOMETRIC_MIN - 0.01f,
        )
        every { roiEstimator.estimateAll(landmarks) } returns listOf(roi)

        val snapshot = pipeline.detect(image, stabilize = false)

        assertThat(snapshot).isNotNull()
        assertThat(snapshot!!.nails).isEmpty()
        verify(exactly = 0) { segmenter.segment(any(), any()) }
    }

    @Test
    fun `detect drops nail below combined confidence floor`() {
        val image = mockk<Bitmap>(relaxed = true) {
            every { width } returns 200
            every { height } returns 300
            every { isRecycled } returns false
        }
        val landmarks = HandLandmarks(
            points = List(21) { NormPoint(0.5f, 0.5f) },
            imageWidth = 200,
            imageHeight = 300,
            presenceScore = 0.80f,
        )
        every {
            landmarkProcessor.detectLandmarksWithOrientationFallback(image)
        } returns OrientedHandLandmarks(bitmap = image, landmarks = landmarks)
        val roi = sampleRoi(geometricConfidence = DetectionConfidenceFloor.ROI_GEOMETRIC_MIN)
        every { roiEstimator.estimateAll(landmarks) } returns listOf(roi)
        // Máscara vazia → segScore muito baixo → confiança combinada < NAIL_COMBINED_MIN.
        every { segmenter.segment(image, roi) } returns NailMask(
            width = 20,
            height = 40,
            alpha = ByteArray(20 * 40) { 0 },
            originX = 90,
            originY = 40,
        )

        val snapshot = pipeline.detect(image, stabilize = false)

        assertThat(snapshot).isNotNull()
        assertThat(snapshot!!.nails).isEmpty()
    }

    @Test
    fun `detect keeps paintable nail below full quality floor`() {
        val image = mockk<Bitmap>(relaxed = true) {
            every { width } returns 200
            every { height } returns 300
            every { isRecycled } returns false
        }
        val landmarks = HandLandmarks(
            points = List(21) { NormPoint(0.5f, 0.5f) },
            imageWidth = 200,
            imageHeight = 300,
            presenceScore = 0.80f,
        )
        every {
            landmarkProcessor.detectLandmarksWithOrientationFallback(image)
        } returns OrientedHandLandmarks(bitmap = image, landmarks = landmarks)
        val roi = sampleRoi(geometricConfidence = 0.30f)
        every { roiEstimator.estimateAll(landmarks) } returns listOf(roi)
        // filled≈0.10 → SCORE_LOW; geo 0.30 → conf ~0.37 (paintable, não FULL).
        val alpha = ByteArray(20 * 40) { i -> if (i < 80) 255.toByte() else 0 }
        every { segmenter.segment(image, roi) } returns NailMask(
            width = 20,
            height = 40,
            alpha = alpha,
            originX = 90,
            originY = 40,
        )

        val snapshot = pipeline.detect(image, stabilize = false)

        assertThat(snapshot).isNotNull()
        assertThat(snapshot!!.nails).hasSize(1)
        val conf = snapshot.nails.first().confidence
        assertThat(conf).isAtLeast(DetectionConfidenceFloor.NAIL_COMBINED_MIN)
        assertThat(conf).isLessThan(DetectionConfidenceFloor.NAIL_FULL_MIN)
        assertThat(DetectionConfidenceFloor.meetsFullNailFloor(snapshot.nails)).isFalse()
    }

    private fun sampleRoi(geometricConfidence: Float): NailRoi = NailRoi(
        finger = Finger.MIDDLE,
        bounds = PixelRect(left = 90, top = 40, right = 110, bottom = 80),
        polygon = listOf(
            PixelPoint(90f, 40f),
            PixelPoint(110f, 40f),
            PixelPoint(110f, 80f),
            PixelPoint(90f, 80f),
        ),
        axisFromDip = PixelPoint(100f, 70f),
        axisToTip = PixelPoint(100f, 45f),
        lengthPx = 40f,
        widthPx = 20f,
        rotationDegrees = 0f,
        geometricConfidence = geometricConfidence,
    )
}
