package br.com.unhasdequecor.ui.result

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.unhasdequecor.ui.components.InfoTag
import br.com.unhasdequecor.ui.components.NailHandIllustration
import br.com.unhasdequecor.ui.components.NailPolishMark
import br.com.unhasdequecor.ui.components.NailSwatch
import br.com.unhasdequecor.ui.components.PrimaryCtaButton
import br.com.unhasdequecor.ui.components.ProgressSteps
import br.com.unhasdequecor.ui.theme.FunChipShape
import br.com.unhasdequecor.ui.theme.RecommendationCardShape
import br.com.unhasdequecor.ui.theme.SoftSurfaceShape
import android.content.Intent

private const val FAVORITE_BUTTON_WEIGHT = 1.2f
private const val SHARE_BUTTON_WEIGHT = 1f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
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
                NailPolishMark(modifier = Modifier.padding(end = 12.dp), markSize = 36.dp, decorative = true)
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            state.errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.errorMessage.orEmpty())
                    Spacer(modifier = Modifier.height(16.dp))
                    PrimaryCtaButton(text = "Tentar de novo", onClick = viewModel::recommendAgain)
                }
            }
            else -> {
                val recommendation = state.recommendation ?: return
                val color = recommendation.color
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

                        Surface(
                            shape = RecommendationCardShape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    NailHandIllustration(
                                        polishColor = Color(color.hex),
                                        colorName = color.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp),
                                    )
                                    IconButton(
                                        onClick = viewModel::onToggleFavorite,
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
                                    text = recommendation.rationale,
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

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PrimaryCtaButton(
                                text = if (state.isFavorite) "Salvo" else "Salvar nos favoritos",
                                onClick = viewModel::onToggleFavorite,
                                modifier = Modifier.weight(FAVORITE_BUTTON_WEIGHT),
                            )
                            OutlinedButton(
                                onClick = {
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Minha cor do momento no Unhas de Que Cor? é ${color.name}.",
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(share, "Compartilhar"))
                                },
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

                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = SoftSurfaceShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        ) {
                            Text(
                                text = "Dica: ${color.tip}",
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "CORES PARECIDAS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                text = "VER TODAS ›",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            (listOf(color) + recommendation.similarColors).forEach { item ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    NailSwatch(
                                        colorHex = item.hex,
                                        colorName = item.name,
                                        width = 48.dp,
                                        height = 72.dp,
                                        modifier = Modifier.border(
                                            width = if (item.id == color.id) 2.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = FunChipShape,
                                        ),
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(item.name, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = viewModel::recommendAgain,
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
        }
    }
}
