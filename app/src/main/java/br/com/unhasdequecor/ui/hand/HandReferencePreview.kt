package br.com.unhasdequecor.ui.hand

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.unhasdequecor.data.local.hand.OrientedBitmapDecoder
import br.com.unhasdequecor.ui.theme.SoftSurfaceShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun HandPreview(
    path: String?,
    revision: Long,
    isSample: Boolean,
    sampleTitle: String?,
) {
    val bitmap by produceState(
        initialValue = null as android.graphics.Bitmap?,
        path,
        revision,
    ) {
        value = path?.takeIf { it.isNotBlank() }?.let {
            withContext(Dispatchers.IO) {
                OrientedBitmapDecoder.decodeFile(it, maxEdge = 1280)
            }
        }
    }
    RecycleBitmapOnDispose(bitmap)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(SoftSurfaceShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .semantics {
                contentDescription = handPreviewContentDescription(
                    hasBitmap = bitmap != null,
                    isSample = isSample,
                    sampleTitle = sampleTitle,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val preview = bitmap
        if (preview != null && !preview.isRecycled) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isSample) {
                Box(modifier = Modifier.align(Alignment.TopStart)) {
                    SampleHandBadge(sampleTitle = sampleTitle)
                }
            }
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

internal fun handSampleContentDescription(title: String, selected: Boolean): String =
    if (selected) "Exemplo $title, selecionada" else "Exemplo $title"

private fun handPreviewContentDescription(
    hasBitmap: Boolean,
    isSample: Boolean,
    sampleTitle: String?,
): String = when {
    hasBitmap && isSample ->
        sampleTitle?.let { "Mão de exemplo cadastrada: $it" } ?: "Mão de exemplo cadastrada"
    hasBitmap -> "Pré-visualização da mão cadastrada"
    else -> "Carregando foto da mão"
}

@Composable
internal fun RecycleBitmapOnDispose(bitmap: android.graphics.Bitmap?) {
    DisposableEffect(bitmap) {
        val held = bitmap
        onDispose {
            if (held != null && !held.isRecycled) {
                runCatching { held.recycle() }
            }
        }
    }
}

@Composable
internal fun BitmapPreview(bitmap: android.graphics.Bitmap?) {
    val preview = bitmap
    if (preview != null && !preview.isRecycled) {
        Image(
            bitmap = preview.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun SelectedCheckBadge() {
    Box(
        modifier = Modifier
            .padding(10.dp)
            .size(28.dp)
            .clip(SoftSurfaceShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun SampleHandBadge(sampleTitle: String?) {
    Text(
        text = sampleTitle?.let { "Exemplo · $it" } ?: "Mão de exemplo",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .padding(12.dp)
            .clip(SoftSurfaceShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
