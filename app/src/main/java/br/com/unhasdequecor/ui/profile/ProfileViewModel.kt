package br.com.unhasdequecor.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.unhasdequecor.BuildConfig
import br.com.unhasdequecor.domain.model.HandReferenceSource
import br.com.unhasdequecor.domain.model.HandSampleCatalog
import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.domain.usecase.GetDistinctColorCountUseCase
import br.com.unhasdequecor.domain.usecase.ObserveHandReferenceUseCase
import br.com.unhasdequecor.domain.usecase.ObservePreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ProfileUiState(
    val preferredStyles: Set<NailStyle> = emptySet(),
    val distinctColorCount: Int = 0,
    val hasHandReference: Boolean = false,
    val isSampleHand: Boolean = false,
    val sampleTitle: String? = null,
    val appVersion: String = BuildConfig.VERSION_NAME,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    observePreferences: ObservePreferencesUseCase,
    observeHandReference: ObserveHandReferenceUseCase,
    getDistinctColorCount: GetDistinctColorCountUseCase,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        observePreferences(),
        observeHandReference(),
    ) { preferences, hand ->
        preferences to hand
    }.mapLatest { (preferences, hand) ->
        val sampleTitle = hand?.sampleId
            ?.let { HandSampleCatalog.findById(it)?.title }
        ProfileUiState(
            preferredStyles = preferences.preferredStyles,
            distinctColorCount = getDistinctColorCount(),
            hasHandReference = hand != null,
            isSampleHand = hand?.source == HandReferenceSource.SAMPLE,
            sampleTitle = sampleTitle,
            appVersion = BuildConfig.VERSION_NAME,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(),
    )
}
