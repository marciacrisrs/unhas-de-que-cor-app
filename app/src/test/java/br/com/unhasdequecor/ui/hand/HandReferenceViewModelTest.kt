package br.com.unhasdequecor.ui.hand

import android.content.Context
import br.com.unhasdequecor.data.local.hand.HandReferenceFileStore
import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceRejection
import br.com.unhasdequecor.domain.model.HandReferenceSaveOutcome
import br.com.unhasdequecor.domain.model.HandReferenceSource
import br.com.unhasdequecor.domain.usecase.ClearHandReferenceUseCase
import br.com.unhasdequecor.domain.usecase.ObserveHandReferenceUseCase
import br.com.unhasdequecor.domain.usecase.SaveHandReferenceUseCase
import br.com.unhasdequecor.domain.usecase.UseSampleHandReferenceUseCase
import br.com.unhasdequecor.testing.FakeHandReferenceRepository
import br.com.unhasdequecor.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HandReferenceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeHandReferenceRepository()
    private val context = mockk<Context>(relaxed = true)
    private val fileStore = mockk<HandReferenceFileStore>(relaxed = true)

    private fun viewModel(): HandReferenceViewModel = HandReferenceViewModel(
        context = context,
        observeHandReference = ObserveHandReferenceUseCase(repository),
        saveHandReference = SaveHandReferenceUseCase(repository) { FIXED_NOW_MS },
        useSampleHandReference = UseSampleHandReferenceUseCase(repository) { FIXED_NOW_MS },
        clearHandReference = ClearHandReferenceUseCase(repository),
        fileStore = fileStore,
    )

    @Test
    fun `import from camera capture updates state and message`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.importFromCameraCapture(File("/tmp/capture.jpg"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.reference).isNotNull()
        assertThat(viewModel.uiState.value.message).contains("sucesso")
        assertThat(viewModel.uiState.value.isSaving).isFalse()
        assertThat(repository.lastSource).isEqualTo(HandReferenceSource.USER)
    }

    @Test
    fun `confirm pending sample persists selection`() = runTest {
        every { fileStore.copySampleAssetToCache(any()) } returns File("/tmp/sample.webp")
        val viewModel = viewModel()

        viewModel.openSamplePicker()
        viewModel.selectPendingSample("retinta_vinho")
        assertThat(viewModel.uiState.value.pendingSampleId).isEqualTo("retinta_vinho")

        viewModel.confirmPendingSample()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.reference?.source).isEqualTo(HandReferenceSource.SAMPLE)
        assertThat(viewModel.uiState.value.reference?.sampleId).isEqualTo("retinta_vinho")
        assertThat(viewModel.uiState.value.message).contains("Pele retinta")
        assertThat(repository.lastSampleId).isEqualTo("retinta_vinho")
        assertThat(viewModel.uiState.value.showSamplePicker).isFalse()
        assertThat(viewModel.uiState.value.pendingSampleId).isNull()
    }

    @Test
    fun `camera after sample clears sample source`() = runTest {
        every { fileStore.copySampleAssetToCache(any()) } returns File("/tmp/sample.webp")
        val viewModel = viewModel()
        viewModel.useSampleHand("morena_nude")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.reference?.source).isEqualTo(HandReferenceSource.SAMPLE)

        viewModel.importFromCameraCapture(File("/tmp/capture.jpg"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.reference?.source).isEqualTo(HandReferenceSource.USER)
        assertThat(viewModel.uiState.value.reference?.sampleId).isNull()
        assertThat(repository.lastSource).isEqualTo(HandReferenceSource.USER)
        assertThat(repository.lastSampleId).isNull()
    }

    @Test
    fun `open and dismiss sample picker`() = runTest {
        val viewModel = viewModel()
        viewModel.openSamplePicker()
        assertThat(viewModel.uiState.value.showSamplePicker).isTrue()
        viewModel.dismissSamplePicker()
        assertThat(viewModel.uiState.value.showSamplePicker).isFalse()
        assertThat(viewModel.uiState.value.pendingSampleId).isNull()
    }

    @Test
    fun `rejected save surfaces friendly message`() = runTest {
        repository.reject(HandReferenceRejection.TOO_LARGE)
        val viewModel = viewModel()

        viewModel.importFromCameraCapture(File("/tmp/huge.jpg"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.reference).isNull()
        assertThat(viewModel.uiState.value.message).contains("15 MB")
    }

    @Test
    fun `clear removes reference`() = runTest {
        repository.emit(
            HandReference(
                localPath = "/files/hand_reference/hand.jpg",
                capturedAtEpochMs = 1L,
            ),
        )
        val viewModel = viewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.reference).isNotNull()

        viewModel.clear()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.reference).isNull()
        assertThat(viewModel.uiState.value.message).contains("removida")
    }

    @Test
    fun `createCameraCaptureFile delegates to file store`() {
        val expected = File("/cache/hand_capture/capture.jpg")
        every { fileStore.createCameraCaptureFile() } returns expected

        assertThat(viewModel().createCameraCaptureFile()).isEqualTo(expected)
    }

    @Test
    fun `consumeMessage clears snackbar text`() = runTest {
        repository.nextOutcome = HandReferenceSaveOutcome.Saved(
            HandReference("/files/hand.jpg", 1L),
        )
        val viewModel = viewModel()
        viewModel.importFromCameraCapture(File("/tmp/ok.jpg"))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.message).isNotNull()

        viewModel.consumeMessage()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.message).isNull()
    }

    private companion object {
        const val FIXED_NOW_MS = 99L
    }
}
