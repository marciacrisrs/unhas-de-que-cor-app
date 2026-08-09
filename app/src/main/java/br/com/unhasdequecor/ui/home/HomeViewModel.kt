package br.com.unhasdequecor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.domain.model.UserPreferences
import br.com.unhasdequecor.domain.usecase.ObserveHistoryUseCase
import br.com.unhasdequecor.domain.usecase.ObservePreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val displayName: String = "Márcia",
    val recentColors: List<HistoryEntry> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observePreferences: ObservePreferencesUseCase,
    observeHistory: ObserveHistoryUseCase,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        observePreferences(),
        observeHistory(),
    ) { preferences: UserPreferences, history: List<HistoryEntry> ->
        HomeUiState(
            displayName = preferences.displayName,
            recentColors = history.distinctBy { it.colorId }.take(4),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )
}
