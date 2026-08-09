package br.com.unhasdequecor.ui.context

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WineBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.unhasdequecor.domain.model.Mood
import br.com.unhasdequecor.domain.model.Occasion
import br.com.unhasdequecor.ui.components.NailPolishMark
import br.com.unhasdequecor.ui.components.PrimaryCtaButton
import br.com.unhasdequecor.ui.components.ProgressSteps
import br.com.unhasdequecor.ui.theme.SoftSurfaceShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextChoiceScreen(
    viewModel: ContextChoiceViewModel,
    onContinue: (Occasion, Mood) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Por contexto",
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
            },
            actions = {
                NailPolishMark(modifier = Modifier.padding(end = 12.dp), markSize = 36.dp, decorative = true)
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = "Conte o momento — a gente sugere a cor com calma.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))
            ProgressSteps(current = 1, total = 2)
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Qual é a ocasião principal?",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OccasionGrid(
                selected = state.selectedOccasion,
                onSelect = viewModel::selectOccasion,
            )

            Spacer(modifier = Modifier.height(36.dp))
            Text(
                text = "Como você está se sentindo?",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(16.dp))
            MoodRow(
                selected = state.selectedMood,
                onSelect = viewModel::selectMood,
            )
            Spacer(modifier = Modifier.height(28.dp))
        }

        PrimaryCtaButton(
            text = "Continuar",
            onClick = {
                val occasion = state.selectedOccasion
                val mood = state.selectedMood
                if (occasion != null && mood != null) {
                    onContinue(occasion, mood)
                }
            },
            enabled = state.canContinue,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun OccasionGrid(
    selected: Occasion?,
    onSelect: (Occasion) -> Unit,
) {
    val items = listOf(
        Occasion.DIA_A_DIA to Icons.Outlined.LocalCafe,
        Occasion.ENCONTRO to Icons.Outlined.WineBar,
        Occasion.TRABALHO to Icons.Outlined.Work,
        Occasion.FESTA to Icons.Outlined.Celebration,
        Occasion.VIAGEM to Icons.Outlined.BeachAccess,
        Occasion.EM_CASA to Icons.Outlined.Home,
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (occasion, icon) ->
                    SelectableCard(
                        label = occasion.displayName,
                        icon = icon,
                        selected = selected == occasion,
                        onClick = { onSelect(occasion) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MoodRow(
    selected: Mood?,
    onSelect: (Mood) -> Unit,
) {
    val items = listOf(
        Mood.ROMANTICA to Icons.Outlined.Favorite,
        Mood.TRANQUILA to Icons.Outlined.Spa,
        Mood.CRIATIVA to Icons.Outlined.Star,
        Mood.ENERGETICA to Icons.Outlined.WbSunny,
        Mood.NEUTRA to Icons.Outlined.Cloud,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (mood, icon) ->
            SelectableCard(
                label = mood.displayName,
                icon = icon,
                selected = selected == mood,
                onClick = { onSelect(mood) },
                modifier = Modifier.weight(1f),
                compact = true,
            )
        }
    }
}

@Composable
private fun SelectableCard(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val shape = SoftSurfaceShape
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(if (compact) 12.dp else 18.dp)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = label
            },
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(if (compact) 22.dp else 28.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = if (compact) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.labelLarge
                },
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
