package br.com.unhasdequecor.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.unhasdequecor.domain.usecase.ObserveHistoryUseCase
import br.com.unhasdequecor.domain.usecase.ToggleFavoriteUseCase
import br.com.unhasdequecor.ui.history.HistoryRowUi
import br.com.unhasdequecor.ui.history.HistoryUiState
import br.com.unhasdequecor.ui.history.toHistoryUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    observeHistory: ObserveHistoryUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = observeHistory(favoritesOnly = true)
        .map { entries -> entries.toHistoryUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(),
        )

    fun onToggleFavorite(entry: HistoryRowUi) {
        viewModelScope.launch {
            toggleFavorite(entry.colorId, entry.isFavorite)
        }
    }
}
