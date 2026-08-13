package br.com.unhasdequecor.ui.hand

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.unhasdequecor.domain.model.HandSampleOption
import br.com.unhasdequecor.data.vision.nail.HandCaptureGuidance
import br.com.unhasdequecor.ui.components.PrimaryCtaButton
import br.com.unhasdequecor.ui.components.SecondaryCtaButton
import br.com.unhasdequecor.ui.theme.SoftSurfaceShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReplaceHandSheet(
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onSample: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Trocar foto",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = HandCaptureGuidance.REPLACE_SHEET_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryCtaButton(text = "Galeria", onClick = onGallery)
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryCtaButton(text = "Câmera", onClick = onCamera)
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryCtaButton(text = "Mão de exemplo", onClick = onSample)
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryCtaButton(text = "Cancelar", onClick = onDismiss)
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HandSamplePickerSheet(
    options: List<HandSampleOption>,
    pendingSampleId: String?,
    onSelectPending: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Escolha pelo tom de pele",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = HandCaptureGuidance.SAMPLE_PICKER_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
            ) {
                items(options, key = { it.id }) { option ->
                    HandSampleCard(
                        option = option,
                        selected = option.id == pendingSampleId,
                        onClick = { onSelectPending(option.id) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryCtaButton(
                text = "OK, usar esta",
                onClick = onConfirm,
                enabled = pendingSampleId != null,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SecondaryCtaButton(text = "Cancelar", onClick = onDismiss)
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
internal fun HandSampleCard(
    option: HandSampleOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap by produceState(initialValue = null as android.graphics.Bitmap?, option.assetPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(option.assetPath).use(BitmapFactory::decodeStream)
            }.getOrNull()
        }
    }
    RecycleBitmapOnDispose(bitmap)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Column(
        modifier = Modifier
            .clip(SoftSurfaceShape)
            .border(if (selected) 2.5.dp else 1.dp, borderColor, SoftSurfaceShape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = handSampleContentDescription(option.title, selected)
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            BitmapPreview(bitmap)
            if (selected) {
                Box(modifier = Modifier.align(Alignment.TopEnd)) { SelectedCheckBadge() }
            }
        }
        Column(modifier = Modifier.padding(10.dp)) {
            Text(option.skinLabel, style = MaterialTheme.typography.labelLarge)
            Text(
                text = option.detailLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
