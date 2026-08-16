package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import br.com.unhasdequecor.data.vision.HandLandmarkProcessor
import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.OrientedHandLandmarks
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.NormPoint
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class NailTryOnPipelineLivePathTest {
    private val landmarkProcessor = mockk<HandLandmarkProcessor>()
    private val roiEstimator = mockk<NailRoiEstimator>(relaxed = true)
    private val segmenter = mockk<NailSegmenter>(relaxed = true)
    private val colorApplier = mockk<NailColorApplier>(relaxed = true)
    private val pipeline = NailTryOnPipeline(
        landmarkProcessor = landmarkProcessor,
        roiEstimator = roiEstimator,
        segmenter = segmenter,
        colorApplier = colorApplier,
        tracker = NailTracker(),
    )

    @Test
    fun `live detection uses live processor path`() {
        val image = mockk<Bitmap>(relaxed = true) {
            every { width } returns 200
            every { height } returns 300
            every { isRecycled } returns false
        }
        val landmarks = HandLandmarks(
            points = openHandPoints(),
            imageWidth = 200,
            imageHeight = 300,
            presenceScore = 0.80f,
        )
        every { landmarkProcessor.detectLandmarksForLive(image) } returns
            OrientedHandLandmarks(image, landmarks)
        every { roiEstimator.estimateAll(landmarks) } returns emptyList()

        pipeline.detect(image, stabilize = true)

        verify(exactly = 1) { landmarkProcessor.detectLandmarksForLive(image) }
        verify(exactly = 0) { landmarkProcessor.detectLandmarksWithOrientationFallback(image) }
    }

    @Test
    fun `still detection keeps orientation fallback path`() {
        val image = mockk<Bitmap>(relaxed = true) {
            every { width } returns 200
            every { height } returns 300
            every { isRecycled } returns false
        }
        val landmarks = HandLandmarks(
            points = openHandPoints(),
            imageWidth = 200,
            imageHeight = 300,
            presenceScore = 0.80f,
        )
        every { landmarkProcessor.detectLandmarksWithOrientationFallback(image) } returns
            OrientedHandLandmarks(image, landmarks)
        every { roiEstimator.estimateAll(landmarks) } returns emptyList()

        pipeline.detect(image, stabilize = false)

        verify(exactly = 1) { landmarkProcessor.detectLandmarksWithOrientationFallback(image) }
        verify(exactly = 0) { landmarkProcessor.detectLandmarksForLive(image) }
    }

    private fun openHandPoints(): List<NormPoint> = listOf(
        NormPoint(0.50f, 0.72f),
        NormPoint(0.50f, 0.60f),
        NormPoint(0.42f, 0.52f),
        NormPoint(0.34f, 0.45f),
        NormPoint(0.28f, 0.38f),
        NormPoint(0.56f, 0.50f),
        NormPoint(0.58f, 0.40f),
        NormPoint(0.58f, 0.30f),
        NormPoint(0.58f, 0.22f),
        NormPoint(0.58f, 0.14f),
        NormPoint(0.64f, 0.52f),
        NormPoint(0.66f, 0.42f),
        NormPoint(0.66f, 0.32f),
        NormPoint(0.66f, 0.24f),
        NormPoint(0.66f, 0.16f),
        NormPoint(0.72f, 0.55f),
        NormPoint(0.74f, 0.46f),
        NormPoint(0.74f, 0.38f),
        NormPoint(0.74f, 0.30f),
        NormPoint(0.74f, 0.23f),
        NormPoint(0.38f, 0.58f),
    )

    @Suppress("unused")
    private fun sanity(value: Any?) = assertThat(value).isNotNull()
}
