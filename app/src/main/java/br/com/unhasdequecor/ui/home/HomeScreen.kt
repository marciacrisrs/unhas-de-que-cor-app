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
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.ui.components.BrandHeader
import br.com.unhasdequecor.ui.components.NailPolishMark
import br.com.unhasdequecor.ui.theme.RecommendationCardShape
import br.com.unhasdequecor.ui.theme.SoftSurfaceShape

private val RecentSwatchOverlap = 10.dp

@Composable
fun HomeScreen(
    onChooseByContext: () -> Unit,
    onChooseForMe: () -> Unit,
    onOpenStyle: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHandReference: () -> Unit,
    onOpenInspiration: (colorId: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(state.flashMessage) {
        val text = state.flashMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeFlashMessage()
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(550),
        label = "homeFade",
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Compacto no primeiro viewport; scroll só se a altura for insuficiente
                // (fonte grande / convite de mão + recentes).
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .alpha(alpha),
        ) {
            BrandHeader(lockupHeight = 64.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HeroActionCard(
                    title = "Escolher minha cor",
                    subtitle = "Receba a cor ideal para o seu momento",
                    icon = Icons.Outlined.Palette,
                    emphasized = true,
                    onClick = onChooseByContext,
                    modifier = Modifier.weight(1f),
                )
                HeroActionCard(
                    title = "Escolha por mim",
                    subtitle = "Surpreenda-se com uma cor incrível",
                    icon = Icons.Outlined.AutoAwesome,
                    emphasized = false,
                    onClick = onChooseForMe,
                    modifier = Modifier.weight(1f),
                )
            }

            state.inspiration?.let { inspiration ->
                Spacer(modifier = Modifier.height(12.dp))
                InspirationCard(
                    title = inspiration.name,
                    subtitle = inspiration.description,
                    polishColor = Color(inspiration.hex),
                    onClick = { onOpenInspiration(inspiration.id) },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "CONTINUE EXPLORANDO",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExploreTile("Por contexto", Icons.Outlined.Event, onChooseByContext, Modifier.weight(1f))
                ExploreTile("Estilo", Icons.Outlined.Checkroom, onOpenStyle, Modifier.weight(1f))
                ExploreTile("Favoritos", Icons.Outlined.FavoriteBorder, onOpenFavorites, Modifier.weight(1f))
                ExploreTile("Histórico", Icons.Outlined.History, onOpenHistory, Modifier.weight(1f))
            }

            if (state.showHandInvite) {
                Spacer(modifier = Modifier.height(10.dp))
                HandReferenceInviteCard(
                    isSampleHand = state.isSampleHand,
                    onClick = onOpenHandReference,
                )
            }

            if (state.recentColors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                RecentChoicesCard(
                    recentColors = state.recentColors,
                    onOpenHistory = onOpenHistory,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RecentChoicesCard(
    recentColors: List<HistoryEntry>,
    onOpenHistory: () -> Unit,
) {
    Surface(
        shape = SoftSurfaceShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenHistory)
            .semantics {
                role = Role.Button
                contentDescription = "Ver suas últimas escolhas"
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Suas últimas escolhas",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Veja e inspire-se novamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(-RecentSwatchOverlap)) {
                recentColors.forEach { entry ->
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(entry.colorHex))
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .semantics {
                                contentDescription = "Cor recente ${entry.colorName}"
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    emphasized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RecommendationCardShape
    val background = if (emphasized) {
        Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.primary,
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surface,
            ),
        )
    }
    val contentColor = if (emphasized) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = modifier
            .height(136.dp)
            .clip(shape)
            .background(background)
            .then(
                if (!emphasized) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
            .semantics {
                role = Role.Button
                contentDescription = title
            },
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(contentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.92f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (emphasized) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun InspirationCard(
    title: String,
    subtitle: String,
    polishColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = SoftSurfaceShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription =
                    "Inspiração do dia: $title. $subtitle. Toque para ver o try-on."
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "INSPIRAÇÃO DO DIA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NailPolishMark(markSize = 48.dp, polishColor = polishColor, decorative = true)
        }
    }
}

@Composable
private fun HandReferenceInviteCard(
    isSampleHand: Boolean,
    onClick: () -> Unit,
) {
    val title = if (isSampleHand) {
        "Troque o exemplo pela sua mão"
    } else {
        "Cadastrar minha mão"
    }
    val subtitle = if (isSampleHand) {
        "Assim o try-on fica mais fiel a você."
    } else {
        "Salve uma foto da sua mão para o try-on virtual."
    }
    Surface(
        shape = SoftSurfaceShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "$title. $subtitle"
            },
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
