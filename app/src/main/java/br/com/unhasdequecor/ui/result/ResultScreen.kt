package br.com.unhasdequecor.ui.result

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.unhasdequecor.domain.model.ColorRecommendation
import br.com.unhasdequecor.domain.model.NailColor
import br.com.unhasdequecor.ui.components.EmptyContent
import br.com.unhasdequecor.ui.components.ErrorContent
import br.com.unhasdequecor.ui.components.HandTryOnPreview
import br.com.unhasdequecor.ui.components.InfoTag
import br.com.unhasdequecor.ui.components.LoadingContent
import br.com.unhasdequecor.ui.components.NailPolishMark
import br.com.unhasdequecor.ui.components.NailSwatch
import br.com.unhasdequecor.ui.components.PrimaryCtaButton
import br.com.unhasdequecor.ui.components.ProgressSteps
import br.com.unhasdequecor.ui.components.SecondaryCtaButton
import br.com.unhasdequecor.ui.theme.FunChipShape
import br.com.unhasdequecor.ui.theme.RecommendationCardShape
import br.com.unhasdequecor.ui.theme.SoftSurfaceShape

private const val FAVORITE_BUTTON_WEIGHT = 1.2f
private const val SHARE_BUTTON_WEIGHT = 1f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenHandReference: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ResultTopBar(onBack = onBack)
        when {
            state.isLoading -> LoadingContent()
            state.errorMessage != null -> ErrorContent(
                message = state.errorMessage.orEmpty(),
                onRetry = viewModel::recommendAgain,
            )
            else -> {
                val recommendation = state.recommendation ?: return
                ResultSuccessContent(
                    state = state,
                    recommendation = recommendation,
                    onOpenHandReference = onOpenHandReference,
                    onToggleFavorite = viewModel::onToggleFavorite,
                    onRecommendAgain = viewModel::recommendAgain,
                    onOpenHistory = onOpenHistory,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text("Sua cor ideal", style = MaterialTheme.typography.headlineSmall)
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
        },
        actions = {
            NailPolishMark(modifier = Modifier.padding(end = 12.dp), markSize = 40.dp, decorative = true)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

@Composable
private fun ResultSuccessContent(
    state: ResultUiState,
    recommendation: ColorRecommendation,
    onOpenHandReference: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRecommendAgain: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val color = recommendation.color
    val context = LocalContext.current
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically { it / 5 },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            ProgressSteps(current = 2, total = 2)
            Spacer(modifier = Modifier.height(20.dp))
            ResultHeroCard(
                state = state,
                color = color,
                rationale = recommendation.rationale,
                onToggleFavorite = onToggleFavorite,
                onOpenHandReference = onOpenHandReference,
            )
            Spacer(modifier = Modifier.height(16.dp))
            ResultPrimaryActions(
                isFavorite = state.isFavorite,
                onToggleFavorite = onToggleFavorite,
                onShare = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Minha cor do momento no Unhas de Que Cor? é ${color.name}.",
                        )
                    }
                    context.startActivity(Intent.createChooser(share, "Compartilhar"))
                },
            )
            Spacer(modifier = Modifier.height(14.dp))
            ResultTip(tip = color.tip)
            Spacer(modifier = Modifier.height(24.dp))
            SimilarColorsSection(
                primary = color,
                similar = recommendation.similarColors,
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = onRecommendAgain,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Icon(Icons.Filled.Bookmark, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Quero outra sugestão")
            }
            TextButton(
                onClick = onOpenHistory,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Ver histórico")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ResultHeroCard(
    state: ResultUiState,
    color: NailColor,
    rationale: String,
    onToggleFavorite: () -> Unit,
    onOpenHandReference: () -> Unit,
) {
    Surface(
        shape = RecommendationCardShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            ResultHandPreview(
                state = state,
                polishColor = Color(color.hex),
                colorName = color.name,
                onToggleFavorite = onToggleFavorite,
            )
            if (!state.hasHandReference || state.isSampleHand) {
                Spacer(modifier = Modifier.height(12.dp))
                SecondaryCtaButton(
                    text = "Usar minha mão",
                    onClick = onOpenHandReference,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Sua cor do momento é",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = color.name,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                color.tags.take(3).forEach { tag ->
                    InfoTag(label = tag.displayName)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = color.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = rationale,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            NailPolishMark(
                markSize = 64.dp,
                polishColor = Color(color.hex),
                decorative = true,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun ResultHandPreview(
    state: ResultUiState,
    polishColor: Color,
    colorName: String,
    onToggleFavorite: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        val handPath = state.handLocalPath
        if (handPath != null) {
            HandTryOnPreview(
                imagePath = handPath,
                revision = state.handRevision,
                polishColor = polishColor,
                colorName = colorName,
                sampleId = state.handSampleId.takeIf { state.isSampleHand },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .aspectRatio(3f / 4f)
                    .clip(SoftSurfaceShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    CircleShape,
                ),
        ) {
            Icon(
                imageVector = if (state.isFavorite) {
                    Icons.Filled.Favorite
                } else {
                    Icons.Filled.FavoriteBorder
                },
                contentDescription = if (state.isFavorite) {
                    "Remover dos favoritos"
                } else {
                    "Salvar nos favoritos"
                },
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ResultPrimaryActions(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PrimaryCtaButton(
            text = if (isFavorite) "Salvo" else "Salvar nos favoritos",
            onClick = onToggleFavorite,
            modifier = Modifier.weight(FAVORITE_BUTTON_WEIGHT),
        )
        OutlinedButton(
            onClick = onShare,
            modifier = Modifier
                .weight(SHARE_BUTTON_WEIGHT)
                .height(52.dp),
            shape = SoftSurfaceShape,
        ) {
            Icon(Icons.Outlined.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Compartilhar", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ResultTip(tip: String) {
    Surface(
        shape = SoftSurfaceShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Text(
            text = "Dica: $tip",
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SimilarColorsSection(
    primary: NailColor,
    similar: List<NailColor>,
) {
    val names = (listOf(primary) + similar).joinToString { it.name }
    Text(
        text = "CORES PARECIDAS",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .semantics {
                contentDescription = "Cores parecidas (visualização): $names"
            },
    ) {
        (listOf(primary) + similar).forEach { item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NailSwatch(
                    colorHex = item.hex,
                    colorName = item.name,
                    width = 48.dp,
                    height = 72.dp,
                    decorative = true,
                    modifier = Modifier.border(
                        width = if (item.id == primary.id) 2.dp else 0.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = FunChipShape,
                    ),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(item.name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
