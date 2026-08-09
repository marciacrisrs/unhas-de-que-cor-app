package br.com.unhasdequecor.ui.context

import androidx.lifecycle.ViewModel
import br.com.unhasdequecor.domain.model.Mood
import br.com.unhasdequecor.domain.model.Occasion
import br.com.unhasdequecor.domain.recommendation.RecommendationSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ContextChoiceUiState(
    val selectedOccasion: Occasion? = null,
    val selectedMood: Mood? = null,
) {
    val canContinue: Boolean get() = selectedOccasion != null && selectedMood != null
}

@HiltViewModel
class ContextChoiceViewModel @Inject constructor(
    private val session: RecommendationSession,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ContextChoiceUiState(
            selectedOccasion = session.occasion,
            selectedMood = session.mood,
        ),
    )
    val uiState: StateFlow<ContextChoiceUiState> = _uiState.asStateFlow()

    fun selectOccasion(occasion: Occasion) {
        _uiState.update { it.copy(selectedOccasion = occasion) }
        session.occasion = occasion
    }

    fun selectMood(mood: Mood) {
        _uiState.update { it.copy(selectedMood = mood) }
        session.mood = mood
    }
}
