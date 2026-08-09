package br.com.unhasdequecor.ui.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.unhasdequecor.domain.model.ColorRecommendation
import br.com.unhasdequecor.domain.model.RecommendationContext
import br.com.unhasdequecor.domain.usecase.GenerateAndSaveRecommendationUseCase
import br.com.unhasdequecor.domain.usecase.ToggleFavoriteUseCase
import br.com.unhasdequecor.ui.navigation.ResultSources
import br.com.unhasdequecor.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val generateAndSave: GenerateAndSaveRecommendationUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val source = ResultSources.toDomain(checkNotNull(savedStateHandle["source"]))
    private val occasion = Routes.parseOccasion(checkNotNull(savedStateHandle["occasion"]))
    private val mood = Routes.parseMood(checkNotNull(savedStateHandle["mood"]))

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    init {
        loadRecommendation()
    }

    private fun loadRecommendation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                generateAndSave(
                    source = source,
                    context = RecommendationContext(
                        occasion = occasion,
                        mood = mood,
                    ),
                )
            }.onSuccess { generated ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        recommendation = generated.recommendation,
                        isFavorite = generated.isFavorite,
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
            runCatching {
                toggleFavorite(recommendation.color.id, currentlyFavorite)
            }.onSuccess {
                _uiState.update { it.copy(isFavorite = !currentlyFavorite) }
            }
        }
    }

    fun recommendAgain() = loadRecommendation()
}
