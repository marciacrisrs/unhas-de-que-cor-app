package br.com.unhasdequecor.ui.style

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.domain.usecase.ObservePreferencesUseCase
import br.com.unhasdequecor.domain.usecase.UpdatePreferredStylesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StyleUiState(
    val selectedStyles: Set<NailStyle> = emptySet(),
    val availableStyles: List<NailStyle> = listOf(
        NailStyle.CLASSICO,
        NailStyle.DELICADO,
        NailStyle.ELEGANTE,
        NailStyle.DIVERTIDO,
        NailStyle.OUSADO,
        NailStyle.MINIMALISTA,
        NailStyle.ROMANTICO,
        NailStyle.FASHIONISTA,
    ),
)

@HiltViewModel
class StyleViewModel @Inject constructor(
    observePreferences: ObservePreferencesUseCase,
    private val updatePreferredStyles: UpdatePreferredStylesUseCase,
) : ViewModel() {

    val uiState: StateFlow<StyleUiState> = observePreferences()
        .map { prefs -> StyleUiState(selectedStyles = prefs.preferredStyles) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StyleUiState(),
        )

    fun toggleStyle(style: NailStyle) {
        val current = uiState.value.selectedStyles.toMutableSet()
        if (!current.add(style)) {
            current.remove(style)
        }
        viewModelScope.launch {
            updatePreferredStyles(current)
        }
    }
}
