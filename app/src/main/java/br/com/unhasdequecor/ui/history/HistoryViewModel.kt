package br.com.unhasdequecor.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.domain.usecase.ObserveHistoryUseCase
import br.com.unhasdequecor.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

enum class HistoryFilter {
    ALL,
    FAVORITES,
}

data class HistoryRowUi(
    val id: Long,
    val colorId: String,
    val colorName: String,
    val colorHex: Long,
    val tags: List<NailStyle>,
    val dateLabel: String,
    val isFavorite: Boolean,
)

data class HistoryMonthGroupUi(
    val monthLabel: String,
    val entries: List<HistoryRowUi>,
)

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter.ALL,
    val groups: List<HistoryMonthGroupUi> = emptyList(),
    val distinctColorCount: Int = 0,
    val isEmpty: Boolean = true,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeHistory: ObserveHistoryUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    val uiState: StateFlow<HistoryUiState> = combine(
        observeHistory(favoritesOnly = false),
        observeHistory(favoritesOnly = true),
        filter,
    ) { all, favorites, selected ->
        val source = if (selected == HistoryFilter.FAVORITES) favorites else all
        source.toHistoryUiState(selected)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    fun onFilterSelected(value: HistoryFilter) {
        filter.value = value
    }

    fun onToggleFavorite(entry: HistoryRowUi) {
        viewModelScope.launch {
            toggleFavorite(entry.colorId, entry.isFavorite)
        }
    }
}

internal fun List<HistoryEntry>.toHistoryUiState(
    filter: HistoryFilter = HistoryFilter.ALL,
): HistoryUiState {
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy").withLocale(Locale("pt", "BR"))
    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale("pt", "BR"))
    val monthGroups = groupBy {
        Instant.ofEpochMilli(it.createdAtEpochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .withDayOfMonth(1)
    }
        .toSortedMap(compareByDescending { it })
        .map { (month, items) ->
            HistoryMonthGroupUi(
                monthLabel = month.format(monthFormatter).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString()
                },
                entries = items.map { entry ->
                    HistoryRowUi(
                        id = entry.id,
                        colorId = entry.colorId,
                        colorName = entry.colorName,
                        colorHex = entry.colorHex,
                        tags = entry.tags,
                        dateLabel = dateFormatter.format(
                            Instant.ofEpochMilli(entry.createdAtEpochMs)
                                .atZone(ZoneId.systemDefault()),
                        ),
                        isFavorite = entry.isFavorite,
                    )
                },
            )
        }
    return HistoryUiState(
        filter = filter,
        groups = monthGroups,
        distinctColorCount = map { it.colorId }.toSet().size,
        isEmpty = isEmpty(),
    )
}
