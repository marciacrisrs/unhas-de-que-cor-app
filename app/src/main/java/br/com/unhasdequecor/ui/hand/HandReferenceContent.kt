package br.com.unhasdequecor.ui.hand

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.unhasdequecor.ui.components.PrimaryCtaButton
import br.com.unhasdequecor.ui.components.SecondaryCtaButton

@Composable
internal fun UserPhotoConfirmContent(
    path: String,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Text(
            text = "É esta a mão que você quer usar?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Confira se as unhas aparecem bem e a luz está boa.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        HandPreview(
            path = path,
            revision = path.hashCode().toLong(),
            isSample = false,
            sampleTitle = null,
        )
        Spacer(modifier = Modifier.height(20.dp))
        PrimaryCtaButton(
            text = "OK, usar esta",
            onClick = onConfirm,
            enabled = enabled,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SecondaryCtaButton(text = "Escolher outra", onClick = onDiscard)
    }
}

@Composable
internal fun HandReferenceContent(
    state: HandReferenceUiState,
    onOpenSamplePicker: () -> Unit,
    onOpenReplaceSheet: () -> Unit,
    onOpenRemoveConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Text(
            text = "Cadastre uma foto da sua mão para o try-on virtual.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when {
                !state.hasReference -> "Carregando a mão de referência…"
                state.isSample ->
                    "Você está com uma mão de exemplo. Troque pela sua para o try-on ficar mais fiel."
                else -> "Esta é a mão que vai experimentar as cores."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        HandPreview(
            path = state.reference?.localPath,
            revision = state.reference?.capturedAtEpochMs ?: 0L,
            isSample = state.isSample,
            sampleTitle = state.sampleTitle,
        )
        Spacer(modifier = Modifier.height(20.dp))
        HandReferenceActionButtons(
            hasReference = state.hasReference,
            isSample = state.isSample,
            enabled = !state.isSaving,
            onOpenSamplePicker = onOpenSamplePicker,
            onOpenReplaceSheet = onOpenReplaceSheet,
            onOpenRemoveConfirm = onOpenRemoveConfirm,
        )
    }
}

@Composable
internal fun HandReferenceActionButtons(
    hasReference: Boolean,
    isSample: Boolean,
    enabled: Boolean,
    onOpenSamplePicker: () -> Unit,
    onOpenReplaceSheet: () -> Unit,
    onOpenRemoveConfirm: () -> Unit,
) {
    if (!hasReference) {
        // Sem empty state: enquanto a amostra padrão materializa, não oferece CTAs vazios.
        return
    }
    if (isSample) {
        PrimaryCtaButton(
            text = "Usar minha mão",
            onClick = onOpenReplaceSheet,
            enabled = enabled,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SecondaryCtaButton(text = "Trocar exemplo", onClick = onOpenSamplePicker)
    } else {
        PrimaryCtaButton(
            text = "Trocar foto",
            onClick = onOpenReplaceSheet,
            enabled = enabled,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    SecondaryCtaButton(
        text = if (isSample) "Restaurar exemplo padrão" else "Voltar para exemplo",
        onClick = onOpenRemoveConfirm,
    )
}
