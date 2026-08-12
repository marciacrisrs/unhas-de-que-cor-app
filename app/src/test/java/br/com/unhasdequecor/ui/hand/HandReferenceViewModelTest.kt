package br.com.unhasdequecor.ui.hand

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HandReferenceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeHandReferenceRepository()

    @Before
    fun setUp() {
        // Espelha UnhasDeQueCorApp.onCreate → ensureDefaultHandReference.
        runBlocking { repository.ensureDefaultSample() }
    }

    private fun viewModel(): HandReferenceViewModel = HandReferenceViewModel(
        observeHandReference = ObserveHandReferenceUseCase(repository),
        saveHandReference = SaveHandReferenceUseCase(repository) { FIXED_NOW_MS },
        useSampleHandReference = UseSampleHandReferenceUseCase(repository) { FIXED_NOW_MS },
        clearHandReference = ClearHandReferenceUseCase(repository),
        repository = repository,
    )

    @Test
    fun `camera stages photo until user confirms`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.reference?.source).isEqualTo(HandReferenceSource.SAMPLE)

        viewModel.importFromCameraCapture(File("/tmp/capture.jpg"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.pendingUserPreviewPath).isEqualTo("/tmp/capture.jpg")
        assertThat(viewModel.uiState.value.reference?.source).isEqualTo(HandReferenceSource.SAMPLE)

        viewModel.confirmPendingUserPhoto()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.reference?.source).isEqualTo(HandReferenceSource.USER)
        assertThat(viewModel.uiState.value.pendingUserPreviewPath).isNull()
        assertThat(viewModel.uiState.value.navigateHome).isTrue()
        assertThat(repository.lastSource).isEqualTo(HandReferenceSource.USER)
    }

    @Test
    fun `discard pending user photo does not persist`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.importFromCameraCapture(File("/tmp/capture.jpg"))
        advanceUntilIdle()

        viewModel.discardPendingUserPhoto()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.pendingUserPreviewPath).isNull()
        assertThat(viewModel.uiState.value.reference?.source).isEqualTo(HandReferenceSource.SAMPLE)
        assertThat(repository.lastSource).isEqualTo(HandReferenceSource.SAMPLE)
    }

    @Test
    fun `confirm pending sample persists selection`() = runTest {
        val viewModel = viewModel()

        viewModel.openSamplePicker()
        viewModel.selectPendingSample("retinta_vinho")
        assertThat(viewModel.uiState.value.pendingSampleId).isEqualTo("retinta_vinho")

        viewModel.confirmPendingSample()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.reference?.source).isEqualTo(HandReferenceSource.SAMPLE)
        assertThat(viewModel.uiState.value.reference?.sampleId).isEqualTo("retinta_vinho")
        assertThat(viewModel.uiState.value.isSample).isTrue()
        assertThat(viewModel.uiState.value.sampleTitle).isEqualTo("Pele retinta")
        assertThat(viewModel.uiState.value.navigateHome).isTrue()
        assertThat(viewModel.uiState.value.showSamplePicker).isFalse()
    }

    @Test
    fun `confirm camera after sample clears sample source`() = runTest {
        val viewModel = viewModel()
        viewModel.useSampleHand("morena_nude")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isSample).isTrue()

        viewModel.importFromCameraCapture(File("/tmp/capture.jpg"))
        advanceUntilIdle()
        viewModel.confirmPendingUserPhoto()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.reference?.source).isEqualTo(HandReferenceSource.USER)
        assertThat(viewModel.uiState.value.reference?.sampleId).isNull()
        assertThat(repository.lastSampleId).isNull()
    }

    @Test
    fun `replace sheet and remove confirm toggles`() = runTest {
        val viewModel = viewModel()
        viewModel.openReplaceSheet()
        assertThat(viewModel.uiState.value.showReplaceSheet).isTrue()
        viewModel.dismissReplaceSheet()
        assertThat(viewModel.uiState.value.showReplaceSheet).isFalse()

        viewModel.openRemoveConfirm()
        assertThat(viewModel.uiState.value.showRemoveConfirm).isTrue()
        viewModel.dismissRemoveConfirm()
        assertThat(viewModel.uiState.value.showRemoveConfirm).isFalse()
    }

    @Test
    fun `confirm remove restores default sample reference`() = runTest {
        repository.emit(
            HandReference(
                localPath = "/files/hand_reference/hand.jpg",
                capturedAtEpochMs = 1L,
                source = HandReferenceSource.USER,
            ),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openRemoveConfirm()
        viewModel.confirmRemove()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.reference?.source).isEqualTo(HandReferenceSource.SAMPLE)
        assertThat(viewModel.uiState.value.reference?.sampleId).isEqualTo("clara_vermelho")
        assertThat(viewModel.uiState.value.message).contains("referência")
        assertThat(viewModel.uiState.value.showRemoveConfirm).isFalse()
    }

    @Test
    fun `rejected save surfaces friendly message after confirm`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        repository.reject(HandReferenceRejection.TOO_LARGE)

        viewModel.importFromCameraCapture(File("/tmp/huge.jpg"))
        advanceUntilIdle()
        viewModel.confirmPendingUserPhoto()
        advanceUntilIdle()

        // Mantém a amostra padrão; a foto pendente fica para tentar de novo.
        assertThat(viewModel.uiState.value.reference?.source).isEqualTo(HandReferenceSource.SAMPLE)
        assertThat(viewModel.uiState.value.message).contains("15 MB")
        assertThat(viewModel.uiState.value.pendingUserPreviewPath).isEqualTo("/tmp/huge.jpg")
    }

    @Test
    fun `createCameraCaptureFile delegates to repository`() {
        assertThat(viewModel().createCameraCaptureFile()).isEqualTo(File("/tmp/capture.jpg"))
    }

    @Test
    fun `consumeNavigateHome clears home navigation flag`() = runTest {
        repository.nextOutcome = HandReferenceSaveOutcome.Saved(
            HandReference("/files/hand.jpg", 1L),
        )
        val viewModel = viewModel()
        viewModel.importFromCameraCapture(File("/tmp/ok.jpg"))
        advanceUntilIdle()
        viewModel.confirmPendingUserPhoto()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.navigateHome).isTrue()

        viewModel.consumeNavigateHome()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.navigateHome).isFalse()
    }

    @Test
    fun `consumeMessage clears snackbar text`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        repository.reject(HandReferenceRejection.IO_ERROR)

        viewModel.importFromCameraCapture(File("/tmp/bad.jpg"))
        advanceUntilIdle()
        viewModel.confirmPendingUserPhoto()
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
