package br.com.unhasdequecor.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.unhasdequecor.ui.components.BrandHeader
import br.com.unhasdequecor.ui.theme.RecommendationCardShape
import br.com.unhasdequecor.ui.theme.SoftSurfaceShape

@Composable
fun HomeScreen(
    onChooseByContext: () -> Unit,
    onChooseForMe: () -> Unit,
    onOpenStyle: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFavorites: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600),
        label = "homeFade",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp)
            .alpha(alpha),
    ) {
        BrandHeader()
        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = if (state.displayName.isBlank()) {
                "Qual cor combina com você hoje?"
            } else {
                "Qual cor combina com você hoje, ${state.displayName}?"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Menos dúvida. Mais unha bonita.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        HeroActionCard(
            title = "Escolher minha cor",
            subtitle = "Ocasião e humor em poucos toques — a sugestão respira com você.",
            icon = Icons.Outlined.Palette,
            emphasized = true,
            onClick = onChooseByContext,
        )
        Spacer(modifier = Modifier.height(16.dp))
        HeroActionCard(
            title = "Escolha por mim",
            subtitle = "Surpresa com personalidade. Uma cor, sem julgamento.",
            icon = Icons.Outlined.AutoAwesome,
            emphasized = false,
            onClick = onChooseForMe,
        )

        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = "Continue explorando",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExploreTile("Estilo", Icons.Outlined.Style, onOpenStyle, Modifier.weight(1f))
            ExploreTile("Favoritos", Icons.Outlined.FavoriteBorder, onOpenFavorites, Modifier.weight(1f))
            ExploreTile("Histórico", Icons.Outlined.History, onOpenHistory, Modifier.weight(1f))
        }

        if (state.recentColors.isNotEmpty()) {
            Spacer(modifier = Modifier.height(40.dp))
            Surface(
                shape = SoftSurfaceShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenHistory)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Ver suas últimas escolhas"
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Suas últimas escolhas",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Veja e inspire-se novamente.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                        state.recentColors.forEach { entry ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(entry.colorHex))
                                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                    .semantics {
                                        contentDescription = "Cor recente ${entry.colorName}"
                                    },
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun HeroActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    val shape = RecommendationCardShape
    val background = if (emphasized) {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.95f),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
            ),
        )
    }
    val contentColor = if (emphasized) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 22.dp)
            .semantics {
                role = Role.Button
                contentDescription = title
            },
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(contentColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor)
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.9f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ExploreTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(SoftSurfaceShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
