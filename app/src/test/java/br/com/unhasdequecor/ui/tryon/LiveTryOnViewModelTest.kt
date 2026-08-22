package br.com.unhasdequecor.ui.tryon

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import br.com.unhasdequecor.data.vision.nail.DetectedNail
import br.com.unhasdequecor.data.vision.nail.DetectionConfidenceFloor
import br.com.unhasdequecor.data.vision.nail.DetectionFailureReason
import br.com.unhasdequecor.data.vision.nail.Finger
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import br.com.unhasdequecor.data.vision.nail.NailDetectionSnapshot
import br.com.unhasdequecor.data.vision.nail.NailMask
import br.com.unhasdequecor.data.vision.nail.NailRoi
import br.com.unhasdequecor.data.vision.nail.NailTryOnPipeline
import br.com.unhasdequecor.data.vision.nail.NailTryOnResult
import br.com.unhasdequecor.data.vision.nail.TryOnPreviewClaim
import br.com.unhasdequecor.data.vision.nail.TryOnReliability
import br.com.unhasdequecor.testing.FakeColorCatalogRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class LiveTryOnViewModelTest {

    private val pipeline = mockk<NailTryOnPipeline>(relaxed = true)
    private val catalog = FakeColorCatalogRepository()

    private fun viewModel(colorId: String) = LiveTryOnViewModel(
        savedStateHandle = SavedStateHandle(mapOf("colorId" to colorId)),
        catalog = catalog,
        pipeline = pipeline,
    )

    @Test
    fun `unknown color shows error without starting camera processing`() {
        val frame = bitmap()
        val viewModel = viewModel("cor_inexistente")

        assertThat(viewModel.uiState.value.errorMessage).isNotNull()
        viewModel.consumeFrame(frame)
        verify(exactly = 0) { pipeline.detect(any(), any()) }
        verify { frame.recycle() }
    }

    @Test
    fun `known color loads polish name`() {
        val viewModel = viewModel("festa_vermelha")

        assertThat(viewModel.uiState.value.errorMessage).isNull()
        assertThat(viewModel.uiState.value.colorName).isEqualTo("Vermelho Festa")
        assertThat(viewModel.uiState.value.claim).isEqualTo(TryOnPreviewClaim.LOADING)
    }

    @Test
    fun `missing landmarks clear overlay and do not claim a hand`() {
        val frame = bitmap()
        every { pipeline.detect(frame, stabilize = true) } returns null
        val viewModel = viewModel("festa_vermelha")

        viewModel.consumeFrame(frame)

        assertThat(viewModel.uiState.value.overlay).isNull()
        assertThat(viewModel.uiState.value.claim).isEqualTo(TryOnPreviewClaim.NOT_DETECTED)
        verify { frame.recycle() }
        verify(exactly = 0) { pipeline.recolor(any(), any()) }
    }

    @Test
    fun `rejected snapshot clears overlay and recycles owned working bitmap`() {
        val frame = bitmap()
        val working = bitmap()
        every { pipeline.detect(frame, stabilize = true) } returns NailDetectionSnapshot(
            workingBitmap = working,
            nails = emptyList(),
            landmarks = null,
            ownsWorkingBitmap = true,
            reliability = TryOnReliability.REJECTED,
            failureReason = DetectionFailureReason.Generic,
        )
        val viewModel = viewModel("festa_vermelha")

        viewModel.consumeFrame(frame)

        assertThat(viewModel.uiState.value.claim).isEqualTo(TryOnPreviewClaim.NOT_DETECTED)
        assertThat(viewModel.uiState.value.overlay).isNull()
        verify { working.recycle() }
        verify(exactly = 0) { pipeline.recolor(any(), any()) }
    }

    @Test
    fun `strong full-quality frame publishes overlay with honest full claim`() {
        val frame = bitmap()
        val painted = bitmap()
        val snapshot = strongSnapshot(frame)
        every { pipeline.detect(frame, stabilize = true) } returns snapshot
        every { pipeline.recolor(snapshot, any()) } returns NailTryOnResult(
            bitmap = painted,
            nails = snapshot.nails,
            landmarks = snapshot.landmarks,
            debugEnabled = false,
            paintedViaEllipse = false,
        )
        val viewModel = viewModel("festa_vermelha")

        viewModel.consumeFrame(frame)

        assertThat(viewModel.uiState.value.overlay).isSameInstanceAs(painted)
        assertThat(viewModel.uiState.value.claim).isEqualTo(TryOnPreviewClaim.FULL_USER)
        verify(exactly = 0) { painted.recycle() }
    }

    @Test
    fun `ellipse paint on full path is labeled approximate`() {
        val frame = bitmap()
        val painted = bitmap()
        val snapshot = strongSnapshot(frame)
        every { pipeline.detect(frame, stabilize = true) } returns snapshot
        every { pipeline.recolor(snapshot, any()) } returns NailTryOnResult(
            bitmap = painted,
            nails = snapshot.nails,
            landmarks = snapshot.landmarks,
            debugEnabled = false,
            paintedViaEllipse = true,
        )
        val viewModel = viewModel("festa_vermelha")

        viewModel.consumeFrame(frame)

        assertThat(viewModel.uiState.value.claim).isEqualTo(TryOnPreviewClaim.APPROXIMATE)
        assertThat(viewModel.uiState.value.overlay).isSameInstanceAs(painted)
    }

    @Test
    fun `busy frame is dropped and recycled without a second detect`() {
        val first = bitmap()
        val second = bitmap()
        val snapshot = strongSnapshot(first)
        val viewModel = viewModel("festa_vermelha")
        every { pipeline.detect(first, stabilize = true) } answers {
            viewModel.consumeFrame(second)
            snapshot
        }
        every { pipeline.recolor(snapshot, any()) } returns NailTryOnResult(
            bitmap = first,
            nails = snapshot.nails,
            landmarks = null,
            debugEnabled = false,
        )

        viewModel.consumeFrame(first)

        verify(exactly = 1) { pipeline.detect(first, stabilize = true) }
        verify(exactly = 0) { pipeline.detect(second, stabilize = true) }
        verify { second.recycle() }
    }

    @Test
    fun `releaseSession drops later frames without another detect`() {
        val frame = bitmap()
        val viewModel = viewModel("festa_vermelha")

        viewModel.releaseSession()
        viewModel.consumeFrame(frame)

        verify(exactly = 0) { pipeline.detect(any(), any()) }
        verify { frame.recycle() }
    }

    @Test
    fun `camera unavailable shows error and ignores frames`() {
        val frame = bitmap()
        val viewModel = viewModel("festa_vermelha")

        viewModel.onCameraUnavailable()
        viewModel.consumeFrame(frame)

        assertThat(viewModel.uiState.value.errorMessage).isNotNull()
        verify(exactly = 0) { pipeline.detect(any(), any()) }
        verify { frame.recycle() }
        verify { pipeline.resetTracking() }
    }

    @Test
    fun `camera unavailable does not overwrite a known color error`() {
        val viewModel = viewModel("cor_inexistente")
        val original = viewModel.uiState.value.errorMessage

        viewModel.onCameraUnavailable()

        assertThat(viewModel.uiState.value.errorMessage).isEqualTo(original)
    }

    @Test
    fun `releaseSession is idempotent`() {
        val viewModel = viewModel("festa_vermelha")
        viewModel.releaseSession()
        viewModel.releaseSession()
        verify(exactly = 1) { pipeline.resetTracking() }
    }

    private fun bitmap(): Bitmap = mockk(relaxed = true) {
        every { isRecycled } returns false
    }

    private fun strongSnapshot(frame: Bitmap): NailDetectionSnapshot {
        val nails = listOf(Finger.THUMB, Finger.INDEX, Finger.MIDDLE).map { finger ->
            DetectedNail(
                finger = finger,
                roi = sampleRoi(finger),
                mask = NailMask(
                    width = 20,
                    height = 40,
                    alpha = ByteArray(20 * 40) { 255.toByte() },
                    originX = 90,
                    originY = 40,
                ),
                confidence = DetectionConfidenceFloor.NAIL_FULL_MIN,
            )
        }
        return NailDetectionSnapshot(
            workingBitmap = frame,
            nails = nails,
            landmarks = null,
            ownsWorkingBitmap = false,
            reliability = TryOnReliability.STRONG,
        )
    }

    private fun sampleRoi(finger: Finger) = NailRoi(
        finger = finger,
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
}
