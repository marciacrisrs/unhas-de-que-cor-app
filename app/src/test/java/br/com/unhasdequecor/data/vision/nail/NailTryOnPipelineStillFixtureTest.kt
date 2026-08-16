package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import br.com.unhasdequecor.data.vision.HandLandmarkProcessor
import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.OrientedHandLandmarks
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.NormPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

/**
 * Fixture-backed integration gate for the one-shot STILL detection path.
 *
 * This intentionally does not exercise MediaPipe itself: the hand landmarks
 * are deterministic, while the real geometric segmenter runs against synthetic
 * skin/plate scenes. The goal is to protect the detect -> ROI -> mask ->
 * confidence contract without making CI dependent on camera/model inference.
 */
class NailTryOnPipelineStillFixtureTest {

    private val landmarkProcessor = mockk<HandLandmarkProcessor>()
    private val roiEstimator = mockk<NailRoiEstimator>()
    private val colorApplier = mockk<NailColorApplier>()
    private val tracker = NailTracker()
    private val segmenter = GeometricNailSegmenter()

    private val pipeline = NailTryOnPipeline(
        landmarkProcessor = landmarkProcessor,
        roiEstimator = roiEstimator,
        segmenter = segmenter,
        colorApplier = colorApplier,
        tracker = tracker,
    )

    @Test
    fun `STILL fixture path produces paintable masks across skin-tone scenes`() {
        HandTrainingScenes.varieties
            .filterNot { it.id == "retinta_underexposed" }
            .forEach { scene ->
                val image = fixtureBitmap(scene)
                val landmarks = openHandLandmarks()
                val rois = fixtureRois()

                every {
                    landmarkProcessor.detectLandmarksWithOrientationFallback(image)
                } returns OrientedHandLandmarks(bitmap = image, landmarks = landmarks)
                every { roiEstimator.estimateAll(landmarks) } returns rois

                val snapshot = pipeline.detect(image, stabilize = false)

                assertThat(snapshot).isNotNull()
                assertThat(snapshot!!.reliability).isEqualTo(TryOnReliability.STRONG)
                assertThat(snapshot.nails).hasSize(3)
                assertThat(snapshot.failureReason).isNull()

                snapshot.nails.forEach { detected ->
                    assertThat(detected.confidence)
                        .isAtLeast(DetectionConfidenceFloor.NAIL_FULL_MIN)
                    assertThat(detected.mask.width)
                        .isEqualTo(detected.roi.bounds.width())
                    assertThat(detected.mask.height)
                        .isEqualTo(detected.roi.bounds.height())
                    assertThat(detected.mask.originX)
                        .isEqualTo(detected.roi.bounds.left)
                    assertThat(detected.mask.originY)
                        .isEqualTo(detected.roi.bounds.top)
                    assertThat(detected.mask.filledRatio()).isGreaterThan(0.04f)
                }

                // A STILL frame must not turn into a temporal prediction.
                assertThat(tracker.lastPredictionReport.predictionApplied).isFalse()
                assertThat(tracker.lastPredictionReport.predictionReason)
                    .isEqualTo(NailPredictionReason.STABLE)
            }
    }

    private fun fixtureBitmap(scene: HandTrainingScenes.Scene): Bitmap {
        val cropWidth = 60
        val cropHeight = 80
        val pixels = HandTrainingScenes.fillNailCrop(
            rw = cropWidth,
            rh = cropHeight,
            skin = scene.skin,
            plate = scene.plate,
        )
        return mockk(relaxed = true) {
            every { width } returns 120
            every { height } returns 160
            every { isRecycled } returns false
            every {
                getPixels(any(), any(), any(), any(), any(), any(), any())
            } answers {
                val destination = firstArg<IntArray>()
                pixels.copyInto(destination, endIndex = minOf(destination.size, pixels.size))
            }
        }
    }

    private fun openHandLandmarks(): HandLandmarks {
        val points = List(21) { index ->
            val column = index % 5
            val row = index / 5
            NormPoint(
                x = (0.18f + column * 0.16f).coerceAtMost(0.90f),
                y = (0.16f + row * 0.16f).coerceAtMost(0.90f),
            )
        }
        return HandLandmarks(
            points = points,
            imageWidth = 120,
            imageHeight = 160,
            presenceScore = 0.90f,
        )
    }

    private fun fixtureRois(): List<NailRoi> =
        listOf(
            sampleRoi(Finger.INDEX),
            sampleRoi(Finger.MIDDLE),
            sampleRoi(Finger.RING),
        )

    private fun sampleRoi(finger: Finger): NailRoi =
        NailRoi(
            finger = finger,
            bounds = PixelRect(left = 30, top = 30, right = 90, bottom = 110),
            polygon = listOf(
                PixelPoint(60f, 35f),
                PixelPoint(78f, 55f),
                PixelPoint(75f, 95f),
                PixelPoint(45f, 95f),
                PixelPoint(42f, 55f),
                PixelPoint(60f, 35f),
            ),
            axisFromDip = PixelPoint(60f, 95f),
            axisToTip = PixelPoint(60f, 35f),
            lengthPx = 60f,
            widthPx = 36f,
            rotationDegrees = 0f,
            geometricConfidence = 0.90f,
        )
}
