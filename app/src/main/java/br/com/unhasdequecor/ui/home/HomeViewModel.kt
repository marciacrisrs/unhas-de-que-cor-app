package br.com.unhasdequecor.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceSource
import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.domain.model.NailColor
import br.com.unhasdequecor.domain.model.UserPreferences
import br.com.unhasdequecor.domain.repository.ColorCatalogRepository
import br.com.unhasdequecor.domain.usecase.ObserveHandReferenceUseCase
import br.com.unhasdequecor.domain.usecase.ObserveHistoryUseCase
import br.com.unhasdequecor.domain.usecase.ObservePreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val displayName: String = "",
    val recentColors: List<HistoryEntry> = emptyList(),
    val inspiration: NailColor? = null,
    val showHandInvite: Boolean = true,
    val isSampleHand: Boolean = false,
    val flashMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observePreferences: ObservePreferencesUseCase,
    observeHistory: ObserveHistoryUseCase,
    observeHandReference: ObserveHandReferenceUseCase,
    catalogRepository: ColorCatalogRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val inspiration = catalogRepository.getById("malva_suave")
        ?: catalogRepository.getAll().firstOrNull()

    private val flashMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        observePreferences(),
        observeHistory(),
        observeHandReference(),
        flashMessage,
    ) { preferences: UserPreferences,
        history: List<HistoryEntry>,
        hand: HandReference?,
        flash: String?,
        ->
        val isSample = hand?.source == HandReferenceSource.SAMPLE
        HomeUiState(
            displayName = preferences.displayName,
            recentColors = history.distinctBy { it.colorId }.take(4),
            inspiration = inspiration,
            showHandInvite = hand == null || isSample,
            isSampleHand = isSample,
            flashMessage = flash,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(inspiration = inspiration),
    )

    init {
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>(FLASH_MESSAGE_KEY, null).collect { flash ->
                if (flash != null) {
                    flashMessage.value = flash
                    savedStateHandle[FLASH_MESSAGE_KEY] = null
                }
            }
        }
    }

    fun consumeFlashMessage() {
        flashMessage.update { null }
    }

    companion object {
        const val FLASH_MESSAGE_KEY = "flash_message"
    }
}
