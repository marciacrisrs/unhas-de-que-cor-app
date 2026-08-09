package br.com.unhasdequecor.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.domain.usecase.GetDistinctColorCountUseCase
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

data class HistoryMonthGroup(
    val monthLabel: String,
    val entries: List<HistoryEntry>,
)

data class HistoryUiState(
    val favoritesOnly: Boolean = false,
    val groups: List<HistoryMonthGroup> = emptyList(),
    val distinctColorCount: Int = 0,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val observeHistory: ObserveHistoryUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val getDistinctColorCount: GetDistinctColorCountUseCase,
) : ViewModel() {

    private val favoritesOnly = MutableStateFlow(false)
    private val distinctCount = MutableStateFlow(0)

    val uiState: StateFlow<HistoryUiState> = combine(
        favoritesOnly,
        observeHistory(false),
        observeHistory(true),
        distinctCount,
    ) { onlyFavorites, all, favorites, count ->
        val source = if (onlyFavorites) favorites else all
        HistoryUiState(
            favoritesOnly = onlyFavorites,
            groups = groupByMonth(source),
            distinctColorCount = count,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    init {
        viewModelScope.launch {
            distinctCount.value = getDistinctColorCount()
        }
    }

    fun setFavoritesOnly(value: Boolean) {
        favoritesOnly.value = value
        viewModelScope.launch {
            distinctCount.value = getDistinctColorCount()
        }
    }

    fun onToggleFavorite(entry: HistoryEntry) {
        viewModelScope.launch {
            toggleFavorite(entry.colorId, entry.isFavorite)
            distinctCount.value = getDistinctColorCount()
        }
    }

    fun formatDate(epochMs: Long): String =
        DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withLocale(Locale("pt", "BR"))
            .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

    private fun groupByMonth(entries: List<HistoryEntry>): List<HistoryMonthGroup> {
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale("pt", "BR"))
        return entries
            .groupBy {
                Instant.ofEpochMilli(it.createdAtEpochMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .withDayOfMonth(1)
            }
            .toSortedMap(compareByDescending { it })
            .map { (month, items) ->
                HistoryMonthGroup(
                    monthLabel = month.format(formatter).replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString()
                    },
                    entries = items,
                )
            }
    }
}
