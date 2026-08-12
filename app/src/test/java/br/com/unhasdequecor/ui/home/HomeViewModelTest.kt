package br.com.unhasdequecor.ui.home

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceSource
import br.com.unhasdequecor.domain.usecase.ObserveHandReferenceUseCase
import br.com.unhasdequecor.domain.usecase.ObserveHistoryUseCase
import br.com.unhasdequecor.domain.usecase.ObservePreferencesUseCase
import br.com.unhasdequecor.testing.FakeColorCatalogRepository
import br.com.unhasdequecor.testing.FakeHandReferenceRepository
import br.com.unhasdequecor.testing.FakeHistoryRepository
import br.com.unhasdequecor.testing.FakePreferencesRepository
import br.com.unhasdequecor.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakePreferencesRepository()
    private val history = FakeHistoryRepository()
    private val handReference = FakeHandReferenceRepository()
    private val catalog = FakeColorCatalogRepository()

    private fun viewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): HomeViewModel = HomeViewModel(
        observePreferences = ObservePreferencesUseCase(preferences),
        observeHistory = ObserveHistoryUseCase(history),
        observeHandReference = ObserveHandReferenceUseCase(handReference),
        catalogRepository = catalog,
        savedStateHandle = savedStateHandle,
    )

    @Test
    fun `reads flash message from saved state and clears the key`() = runTest {
        val handle = SavedStateHandle(
            mapOf(HomeViewModel.FLASH_MESSAGE_KEY to "Mão cadastrada com sucesso."),
        )
        val viewModel = viewModel(handle)

        viewModel.uiState.test {
            advanceUntilIdle()
            val withFlash = expectMostRecentItem()
            assertThat(withFlash.flashMessage).isEqualTo("Mão cadastrada com sucesso.")
            assertThat(handle.get<String?>(HomeViewModel.FLASH_MESSAGE_KEY)).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `consumeFlashMessage clears snackbar text`() = runTest {
        val handle = SavedStateHandle(
            mapOf(HomeViewModel.FLASH_MESSAGE_KEY to "Exemplo salvo."),
        )
        val viewModel = viewModel(handle)

        viewModel.uiState.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem().flashMessage).isNotNull()

            viewModel.consumeFlashMessage()
            advanceUntilIdle()
            assertThat(expectMostRecentItem().flashMessage).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showHandInvite true when hand is sample`() = runTest {
        handReference.emit(
            HandReference(
                localPath = "/files/hand.jpg",
                capturedAtEpochMs = 1L,
                source = HandReferenceSource.SAMPLE,
                sampleId = "clara_vermelho",
            ),
        )
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.showHandInvite).isTrue()
            assertThat(state.isSampleHand).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
