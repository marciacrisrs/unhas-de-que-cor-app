package br.com.unhasdequecor.ui.style

import app.cash.turbine.test
import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.domain.model.UserPreferences
import br.com.unhasdequecor.domain.usecase.ObservePreferencesUseCase
import br.com.unhasdequecor.domain.usecase.UpdatePreferredStylesUseCase
import br.com.unhasdequecor.testing.FakePreferencesRepository
import br.com.unhasdequecor.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StyleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakePreferencesRepository(
        UserPreferences(preferredStyles = setOf(NailStyle.CLASSICO)),
    )

    private fun viewModel() = StyleViewModel(
        observePreferences = ObservePreferencesUseCase(preferences),
        updatePreferredStyles = UpdatePreferredStylesUseCase(preferences),
    )

    @Test
    fun `rapid toggles of two styles keep both plus the existing selection`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertThat(expectMostRecentItem().selectedStyles).containsExactly(NailStyle.CLASSICO)

            // Toques em chips distintos sem esperar o round-trip do DataStore.
            viewModel.toggleStyle(NailStyle.DELICADO)
            viewModel.toggleStyle(NailStyle.ELEGANTE)
            advanceUntilIdle()

            assertThat(expectMostRecentItem().selectedStyles).containsExactly(
                NailStyle.CLASSICO,
                NailStyle.DELICADO,
                NailStyle.ELEGANTE,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling the selected style turns it off`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.toggleStyle(NailStyle.CLASSICO)
            advanceUntilIdle()
            assertThat(expectMostRecentItem().selectedStyles).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
