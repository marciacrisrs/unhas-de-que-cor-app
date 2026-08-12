package br.com.unhasdequecor.ui.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.unhasdequecor.domain.model.ColorRecommendation
import br.com.unhasdequecor.domain.model.HandReferenceSource
import br.com.unhasdequecor.domain.model.RecommendationContext
import br.com.unhasdequecor.domain.usecase.GenerateAndSaveRecommendationUseCase
import br.com.unhasdequecor.domain.usecase.ObserveHandReferenceUseCase
import br.com.unhasdequecor.domain.usecase.RestoreRecommendationUseCase
import br.com.unhasdequecor.domain.usecase.ToggleFavoriteUseCase
import br.com.unhasdequecor.ui.navigation.ResultSources
import br.com.unhasdequecor.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ResultUiState(
    val isLoading: Boolean = true,
    val recommendation: ColorRecommendation? = null,
    val isFavorite: Boolean = false,
    val savedToHistory: Boolean = false,
    val errorMessage: String? = null,
    val handLocalPath: String? = null,
    val handRevision: Long = 0L,
    val isSampleHand: Boolean = false,
    val handSampleId: String? = null,
) {
    val hasHandReference: Boolean get() = !handLocalPath.isNullOrBlank()
}

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val generateAndSave: GenerateAndSaveRecommendationUseCase,
    private val restoreRecommendation: RestoreRecommendationUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    observeHandReference: ObserveHandReferenceUseCase,
) : ViewModel() {

    private val source = ResultSources.toDomain(checkNotNull(savedStateHandle["source"]))
    private val occasion = Routes.parseOccasion(checkNotNull(savedStateHandle["occasion"]))
    private val mood = Routes.parseMood(checkNotNull(savedStateHandle["mood"]))
    private val recommendationContext = RecommendationContext(
        occasion = occasion,
        mood = mood,
    )

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeHandReference().collect { hand ->
                _uiState.update {
                    it.copy(
                        handLocalPath = hand?.localPath,
                        handRevision = hand?.capturedAtEpochMs ?: 0L,
                        isSampleHand = hand?.source == HandReferenceSource.SAMPLE,
                        handSampleId = hand?.sampleId,
                    )
                }
            }
        }

        val navColorId = Routes.parseColorId(savedStateHandle.get<String>("colorId") ?: Routes.NONE)
        when {
            navColorId != null -> {
                savedStateHandle[KEY_COLOR_ID] = navColorId
                restoreCached(navColorId)
            }
            savedStateHandle.get<String>(KEY_COLOR_ID) != null -> {
                restoreCached(checkNotNull(savedStateHandle.get(KEY_COLOR_ID)))
            }
            else -> generateFresh()
        }
    }

    private fun restoreCached(colorId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                restoreRecommendation(
                    colorId = colorId,
                    source = source,
                    context = recommendationContext,
                ) ?: error("Recomendação não encontrada.")
            }.onSuccess { generated ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        recommendation = generated.recommendation,
                        isFavorite = generated.isFavorite,
                        savedToHistory = true,
                    )
                }
            }.onFailure {
                clearSessionCache()
                generateFresh()
            }
        }
    }

    private fun generateFresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val idempotencyKey = sessionIdempotencyKey()
            runCatching {
                generateAndSave(
                    source = source,
                    context = recommendationContext,
                    idempotencyKey = idempotencyKey,
                )
            }.onSuccess { generated ->
                savedStateHandle[KEY_COLOR_ID] = generated.recommendation.color.id
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

    fun recommendAgain() {
        clearSessionCache()
        generateFresh()
    }

    private fun sessionIdempotencyKey(): String {
        val existing = savedStateHandle.get<String>(KEY_IDEMPOTENCY)
        if (existing != null) return existing
        val created = UUID.randomUUID().toString()
        savedStateHandle[KEY_IDEMPOTENCY] = created
        return created
    }

    private fun clearSessionCache() {
        savedStateHandle.remove<String>(KEY_COLOR_ID)
        savedStateHandle.remove<String>(KEY_IDEMPOTENCY)
        savedStateHandle["colorId"] = Routes.NONE
    }

    private companion object {
        const val KEY_COLOR_ID = "result_cached_color_id"
        const val KEY_IDEMPOTENCY = "result_idempotency_key"
    }
}
