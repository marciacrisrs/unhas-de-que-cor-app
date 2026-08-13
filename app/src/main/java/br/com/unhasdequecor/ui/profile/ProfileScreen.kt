package br.com.unhasdequecor.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.unhasdequecor.ui.components.BrandLogoLockup
import br.com.unhasdequecor.ui.theme.SoftSurfaceShape

@Composable
fun ProfileScreen(
    onOpenStyle: () -> Unit,
    onOpenHandReference: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val stylesLabel = if (state.preferredStyles.isEmpty()) {
        "Toque para escolher clássico, delicado, ousado e mais"
    } else {
        state.preferredStyles.joinToString(" · ") { it.displayName }
    }
    val handLabel = when {
        state.isSampleHand && state.sampleTitle != null ->
            "Exemplo: ${state.sampleTitle} · toque para trocar"
        state.isSampleHand -> "Usando foto de exemplo · toque para trocar pela sua"
        state.hasHandReference -> "Foto cadastrada neste aparelho · toque para trocar"
        else -> "Toque para cadastrar a foto da sua mão"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        BrandLogoLockup(height = 96.dp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Perfil",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Personalize como o app te recomenda cores.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        ProfileCard(
            title = "Meu estilo",
            subtitle = stylesLabel,
            onClick = onOpenStyle,
            contentDescription = "Definir meu estilo",
        )
        Spacer(modifier = Modifier.height(12.dp))
        ProfileCard(
            title = "Minha mão",
            subtitle = handLabel,
            onClick = onOpenHandReference,
            contentDescription = "Cadastrar minha mão",
        )
        Spacer(modifier = Modifier.height(12.dp))
        ProfileCard(
            title = "Suas cores",
            subtitle = if (state.distinctColorCount == 0) {
                "Ainda sem recomendações no histórico"
            } else {
                "Você já explorou ${state.distinctColorCount} cores diferentes"
            },
            onClick = onOpenHistory,
            contentDescription = "Abrir histórico de cores",
        )
        Spacer(modifier = Modifier.height(12.dp))
        ProfileCard(
            title = "Sobre o app",
            subtitle = "Unhas de Que Cor? · versão ${state.appVersion}\n" +
                "Offline · try-on · histórico no aparelho",
            onClick = onOpenAbout,
            contentDescription = "Abrir sobre o app",
        )
        Spacer(modifier = Modifier.height(88.dp))
    }
}

@Composable
private fun ProfileCard(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
) {
    val clickableModifier = if (onClick != null) {
        Modifier
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription ?: title
            }
    } else {
        Modifier
    }
    Surface(
        shape = SoftSurfaceShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
