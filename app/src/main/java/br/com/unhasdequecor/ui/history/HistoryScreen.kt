package br.com.unhasdequecor.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.unhasdequecor.ui.components.FilterTab
import br.com.unhasdequecor.ui.components.HistoryRow
import br.com.unhasdequecor.ui.components.NailPolishMark
import br.com.unhasdequecor.ui.theme.SoftSurfaceShape

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
    ) {
        HistoryHeader()
        Spacer(modifier = Modifier.height(16.dp))
        HistoryFilterTabs(
            filter = state.filter,
            onFilterSelected = viewModel::onFilterSelected,
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (state.isEmpty) {
            HistoryEmptyMessage(filter = state.filter)
        } else {
            HistoryList(
                state = state,
                onToggleFavorite = viewModel::onToggleFavorite,
            )
        }
    }
}

@Composable
private fun HistoryHeader() {
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Histórico", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = "Suas escolhas recentes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NailPolishMark(markSize = 40.dp, decorative = true)
    }
}

@Composable
private fun HistoryFilterTabs(
    filter: HistoryFilter,
    onFilterSelected: (HistoryFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterTab(
            label = "Todas",
            selected = filter == HistoryFilter.ALL,
            onClick = { onFilterSelected(HistoryFilter.ALL) },
        )
        FilterTab(
            label = "Favoritas",
            selected = filter == HistoryFilter.FAVORITES,
            onClick = { onFilterSelected(HistoryFilter.FAVORITES) },
            leadingIcon = {
                Icon(
                    Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = if (filter == HistoryFilter.FAVORITES) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    modifier = Modifier.size(16.dp),
                )
            },
        )
    }
}

@Composable
private fun HistoryEmptyMessage(filter: HistoryFilter) {
    Text(
        text = if (filter == HistoryFilter.FAVORITES) {
            "Nenhuma favorita ainda. Salve uma recomendação com o coração."
        } else {
            "Seu histórico aparece aqui depois da primeira recomendação."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun HistoryList(
    state: HistoryUiState,
    onToggleFavorite: (HistoryRowUi) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        state.groups.forEach { group ->
            item(key = "header-${group.monthLabel}") {
                Text(
                    text = group.monthLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
            }
            item(key = "card-${group.monthLabel}") {
                HistoryMonthCard(group = group, onToggleFavorite = onToggleFavorite)
            }
        }
        item {
            HistoryStatsBanner(distinctColorCount = state.distinctColorCount)
        }
    }
}

@Composable
private fun HistoryMonthCard(
    group: HistoryMonthGroupUi,
    onToggleFavorite: (HistoryRowUi) -> Unit,
) {
    Surface(
        shape = SoftSurfaceShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            group.entries.forEach { entry ->
                HistoryRow(
                    colorName = entry.colorName,
                    colorHex = entry.colorHex,
                    tags = entry.tags,
                    dateLabel = entry.dateLabel,
                    isFavorite = entry.isFavorite,
                    onFavoriteClick = { onToggleFavorite(entry) },
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun HistoryStatsBanner(distinctColorCount: Int) {
    Spacer(modifier = Modifier.height(4.dp))
    Surface(
        shape = SoftSurfaceShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = buildAnnotatedString {
                    append("Você já explorou ")
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        ),
                    ) {
                        append("$distinctColorCount")
                    }
                    append(" cores diferentes. Continue explorando e descubra novas combinações!")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Spacer(modifier = Modifier.height(88.dp))
}
