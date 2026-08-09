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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.unhasdequecor.ui.components.NailPolishMark
import br.com.unhasdequecor.ui.components.NailSwatch
import br.com.unhasdequecor.ui.components.PrimaryCtaButton
import br.com.unhasdequecor.ui.components.ProgressSteps
import br.com.unhasdequecor.ui.components.SecondaryCtaButton
import br.com.unhasdequecor.ui.components.InfoTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                        .padding(24.dp),
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
                    enter = fadeIn() + slideInVertically { it / 4 },
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
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(
                                        modifier = Modifier
                                            .width(120.dp)
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        Color(color.hex).copy(alpha = 0.55f),
                                                        Color(color.hex),
                                                    ),
                                                ),
                                            )
                                            .semantics {
                                                contentDescription = "Visualização da cor ${color.name}"
                                            },
                                    ) {
                                        IconButton(
                                            onClick = viewModel::onToggleFavorite,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                                    shape = androidx.compose.foundation.shape.CircleShape,
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
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Sua cor do momento é",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = color.name,
                                            style = MaterialTheme.typography.displayMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        ) {
                                            color.tags.take(3).forEach { tag ->
                                                InfoTag(label = tag.displayName)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = recommendation.rationale,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                NailPolishMark(
                                    markSize = 72.dp,
                                    polishColor = Color(color.hex),
                                    modifier = Modifier.align(Alignment.End),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        ) {
                            Text(
                                text = "Dica: ${color.tip}",
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "CORES PARECIDAS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            (listOf(color) + recommendation.similarColors).forEach { item ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    NailSwatch(
                                        colorHex = item.hex,
                                        colorName = item.name,
                                        width = 44.dp,
                                        height = 64.dp,
                                        modifier = Modifier.border(
                                            width = if (item.id == color.id) 2.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(50),
                                        ),
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        PrimaryCtaButton(
                            text = if (state.isFavorite) "Salvo nos favoritos" else "Salvar nos favoritos",
                            onClick = viewModel::onToggleFavorite,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SecondaryCtaButton(
                            text = "Ver histórico",
                            onClick = onOpenHistory,
                        )
                        TextButton(
                            onClick = viewModel::recommendAgain,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Icon(Icons.Filled.Bookmark, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Quero outra sugestão")
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
