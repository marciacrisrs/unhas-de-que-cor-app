package br.com.unhasdequecor.ui.result

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import br.com.unhasdequecor.data.vision.nail.NailTryOnPipeline
import br.com.unhasdequecor.domain.model.NailColor
import br.com.unhasdequecor.ui.components.ErrorContent
import br.com.unhasdequecor.ui.components.HandTryOnPreview
import br.com.unhasdequecor.ui.components.InfoTag
import br.com.unhasdequecor.ui.components.LoadingContent
import br.com.unhasdequecor.ui.components.NailPolishMark
import br.com.unhasdequecor.ui.components.NailSwatch
import br.com.unhasdequecor.ui.components.PrimaryCtaButton
import br.com.unhasdequecor.ui.components.SecondaryCtaButton
import br.com.unhasdequecor.ui.theme.SoftSurfaceShape

private const val FAVORITE_BUTTON_WEIGHT = 1.2f
private const val SHARE_BUTTON_WEIGHT = 1f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenHandReference: () -> Unit,
    onOpenLiveTryOn: (String) -> Unit,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ResultTopBar(onBack)
        when {
            state.isLoading -> LoadingContent()
            state.errorMessage != null -> ErrorContent(state.errorMessage.orEmpty(), viewModel::recommendAgain)
            else -> {
                val recommendation = state.recommendation ?: return
                ResultSuccessContent(
                    state = state,
                    nailTryOnPipeline = viewModel.nailTryOnPipeline,
                    onOpenHandReference = onOpenHandReference,
                    onOpenLiveTryOn = { onOpenLiveTryOn(recommendation.color.id) },
                    onToggleFavorite = viewModel::onToggleFavorite,
                    onSelectColor = viewModel::selectColor,
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
        title = { Text("Sua cor ideal", style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
        actions = { NailPolishMark(modifier = Modifier.padding(end = 12.dp), markSize = 36.dp, decorative = true) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun ResultSuccessContent(
    state: ResultUiState,
    nailTryOnPipeline: NailTryOnPipeline,
    onOpenHandReference: () -> Unit,
    onOpenLiveTryOn: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSelectColor: (String) -> Unit,
    onRecommendAgain: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val recommendation = checkNotNull(state.recommendation)
    val color = recommendation.color
    val context = LocalContext.current
    AnimatedVisibility(true, enter = fadeIn() + slideInVertically { it / 5 }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ResultTryOnHero(state, color, nailTryOnPipeline, onToggleFavorite, onOpenHandReference)
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(16.dp))
                Text(color.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(6.dp))
                Text(color.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    color.tags.take(3).forEach { InfoTag(label = it.displayName) }
                }
                Spacer(Modifier.height(14.dp))
                PrimaryCtaButton(text = "Experimentar ao vivo", onClick = onOpenLiveTryOn, modifier = Modifier.fillMaxWidth())
                if (!state.hasHandReference || state.isSampleHand) {
                    Spacer(Modifier.height(10.dp))
                    SecondaryCtaButton(text = "Usar minha mão", onClick = onOpenHandReference)
                }
                Spacer(modifier = Modifier.height(12.dp))
                SecondaryCtaButton(
                    text = "Experimentar ao vivo",
                    onClick = onOpenLiveTryOn,
                    modifier = Modifier.semantics {
                        contentDescription =
                            "Experimentar a cor ${color.name} ao vivo na câmera"
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
                ResultPrimaryActions(
                    isFavorite = state.isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onShare = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Minha cor do momento no Unhas de Que Cor? é ${color.name}.")
                        }
                        context.startActivity(Intent.createChooser(share, "Compartilhar"))
                    },
                )
                Spacer(Modifier.height(14.dp))
                ResultTip(color.tip)
                Spacer(Modifier.height(8.dp))
                Text(recommendation.rationale, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                SimilarColorsSection(color, recommendation.similarColors, onSelectColor)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onRecommendAgain, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Filled.Bookmark, null); Spacer(Modifier.width(6.dp)); Text("Quero outra sugestão")
                }
                TextButton(onClick = onOpenHistory, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Ver histórico") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ResultTryOnHero(state: ResultUiState, color: NailColor, nailTryOnPipeline: NailTryOnPipeline, onToggleFavorite: () -> Unit, onOpenHandReference: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        val handPath = state.handLocalPath
        if (handPath != null) {
            HandTryOnPreview(
                imagePath = handPath,
                revision = state.handRevision,
                polishColor = Color(color.hex),
                colorName = color.name,
                sampleId = state.handSampleId.takeIf { state.isSampleHand },
                nailPipeline = nailTryOnPipeline,
                onImprovePhoto = onOpenHandReference.takeUnless { state.isSampleHand },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(SoftSurfaceShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), CircleShape)) {
            Icon(if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (state.isFavorite) "Remover dos favoritos" else "Salvar nos favoritos", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ResultPrimaryActions(isFavorite: Boolean, onToggleFavorite: () -> Unit, onShare: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryCtaButton(text = if (isFavorite) "Salvo" else "Salvar nos favoritos", onClick = onToggleFavorite, modifier = Modifier.weight(FAVORITE_BUTTON_WEIGHT))
        OutlinedButton(onClick = onShare, modifier = Modifier.weight(SHARE_BUTTON_WEIGHT).height(52.dp), shape = SoftSurfaceShape) {
            Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(6.dp)); Text("Compartilhar", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ResultTip(tip: String) {
    Surface(shape = SoftSurfaceShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
        Text("Dica: $tip", Modifier.padding(horizontal = 18.dp, vertical = 14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SimilarColorsSection(primary: NailColor, similar: List<NailColor>, onSelectColor: (String) -> Unit) {
    val palette = listOf(primary) + similar
    val names = palette.joinToString { it.name }
    Text("CORES PARECIDAS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
    Text("Toque para ver no try-on", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = "Cores parecidas. Toque para trocar o esmalte no try-on: $names" },
    ) {
        palette.forEach { item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NailSwatch(colorHex = item.hex, colorName = item.name, width = 48.dp, height = 72.dp, selected = item.id == primary.id, onClick = { onSelectColor(item.id) })
                Spacer(Modifier.height(6.dp)); Text(item.name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
