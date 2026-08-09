package br.com.unhasdequecor.ui.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.unhasdequecor.domain.model.ColorRecommendation
import br.com.unhasdequecor.domain.usecase.ObservePreferencesUseCase
import br.com.unhasdequecor.domain.usecase.RecommendByContextUseCase
import br.com.unhasdequecor.domain.usecase.RecommendForMeUseCase
import br.com.unhasdequecor.domain.usecase.SaveRecommendationUseCase
import br.com.unhasdequecor.domain.usecase.ToggleFavoriteUseCase
import br.com.unhasdequecor.domain.recommendation.RecommendationSession
import br.com.unhasdequecor.ui.navigation.ResultSources
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResultUiState(
    val isLoading: Boolean = true,
    val recommendation: ColorRecommendation? = null,
    val isFavorite: Boolean = false,
    val savedToHistory: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recommendByContext: RecommendByContextUseCase,
    private val recommendForMe: RecommendForMeUseCase,
    private val saveRecommendation: SaveRecommendationUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val observePreferences: ObservePreferencesUseCase,
    private val session: RecommendationSession,
) : ViewModel() {

    private val source: String = checkNotNull(savedStateHandle["source"])

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    init {
        loadRecommendation()
    }

    private fun loadRecommendation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                when (source) {
                    ResultSources.FOR_ME -> recommendForMe()
                    else -> {
                        val styles = observePreferences().first().preferredStyles
                        recommendByContext(session.toContext(styles))
                    }
                }
            }.onSuccess { recommendation ->
                session.lastRecommendation = recommendation
                saveRecommendation(recommendation)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        recommendation = recommendation,
                        savedToHistory = true,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Não foi possível recomendar agora.",
                    )
                }
            }
        }
    }

    fun onToggleFavorite() {
        val recommendation = _uiState.value.recommendation ?: return
        viewModelScope.launch {
            val currentlyFavorite = _uiState.value.isFavorite
            toggleFavorite(recommendation.color.id, currentlyFavorite)
            _uiState.update { it.copy(isFavorite = !currentlyFavorite) }
        }
    }

    fun recommendAgain() = loadRecommendation()
}
