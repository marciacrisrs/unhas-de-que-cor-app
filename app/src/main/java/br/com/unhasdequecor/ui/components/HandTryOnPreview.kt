package br.com.unhasdequecor.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.unhasdequecor.BuildConfig
import br.com.unhasdequecor.data.local.hand.OrientedBitmapDecoder
import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.nail.DetectedNail
import br.com.unhasdequecor.data.vision.nail.NailLandmarkMapper
import br.com.unhasdequecor.data.vision.nail.NailOverlayAnchor
import br.com.unhasdequecor.data.vision.nail.NailOverlayAnchors
import br.com.unhasdequecor.data.vision.nail.NailTryOnPipeline
import br.com.unhasdequecor.data.vision.nail.PolishMaskRecolorer
import br.com.unhasdequecor.di.NailPipelineEntryPoint
import br.com.unhasdequecor.ui.theme.SoftSurfaceShape
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class TryOnPreviewData(
    val bitmap: Bitmap,
    val anchors: List<NailOverlayAnchor>,
    val mode: TryOnMode,
    val nails: List<DetectedNail> = emptyList(),
    val landmarks: HandLandmarks? = null,
    val showDebug: Boolean = false,
)

private enum class TryOnMode {
    MASK,
    DETECTED,
    APPROXIMATE,
}

@Composable
fun HandTryOnPreview(
    imagePath: String,
    revision: Long,
    polishColor: Color,
    colorName: String,
    sampleId: String?,
    modifier: Modifier = Modifier,
    nailPipeline: NailTryOnPipeline? = null,
) {
    val context = LocalContext.current
    val pipeline = nailPipeline ?: remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            NailPipelineEntryPoint::class.java,
        ).nailTryOnPipeline().also {
            // Debug só em builds debug; não polui a UI de release.
            it.debugEnabled = BuildConfig.DEBUG && BuildConfig.DEBUG_NAIL_OVERLAY
        }
    }
    val preview by produceState<TryOnPreviewData?>(
        initialValue = null,
        imagePath,
        revision,
        sampleId,
        polishColor,
        pipeline,
    ) {
        value = withContext(Dispatchers.Default) {
            var decoded: Bitmap? = null
            try {
                decoded = OrientedBitmapDecoder.decodeFile(imagePath, maxEdge = 1280)
                    ?: return@withContext null
                val resolved = resolvePreview(
                    context = context.applicationContext,
                    bitmap = decoded,
                    polishColor = polishColor,
                    sampleId = sampleId,
                    pipeline = pipeline,
                )
                // decodeFile sempre cria bitmap nova; se o pipeline devolveu outra, libera a fonte.
                if (resolved.bitmap !== decoded) {
                    recycleQuietly(decoded)
                    decoded = null
                }
                resolved
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                recycleQuietly(decoded)
                throw cancelled
            }
        }
    }

    // Recicla bitmap ao trocar de prévia ou ao sair da composição.
    DisposableEffect(preview) {
        val held = preview
        onDispose {
            recycleQuietly(held?.bitmap)
        }
    }

    val aspect = preview?.bitmap?.let { bmp ->
        if (bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else NailLandmarkMapper.PREVIEW_ASPECT
    } ?: NailLandmarkMapper.PREVIEW_ASPECT
    val statusLabel = previewStatusLabel(mode = preview?.mode, isUserPhoto = sampleId == null)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(SoftSurfaceShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .semantics {
                contentDescription = if (preview == null) {
                    "Preparando prévia da cor $colorName na sua mão"
                } else {
                    "Prévia da cor $colorName na sua mão. $statusLabel"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (preview == null) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        } else {
            TryOnPreviewContent(data = preview, polishColor = polishColor)
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .clip(SoftSurfaceShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.88f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun TryOnPreviewContent(
    data: TryOnPreviewData?,
    polishColor: Color,
) {
    if (data == null || data.bitmap.isRecycled) return
    Image(
        bitmap = data.bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.fillMaxSize(),
    )
    if (data.mode != TryOnMode.MASK && data.anchors.isNotEmpty()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            data.anchors.forEach { anchor ->
                drawPolishNail(anchor = anchor, polishColor = polishColor)
            }
        }
    }
    if (data.showDebug) {
        NailDebugOverlay(
            landmarks = data.landmarks,
            nails = data.nails,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun previewStatusLabel(mode: TryOnMode?, isUserPhoto: Boolean): String = when {
    mode == TryOnMode.MASK -> "Prévia na mão de exemplo"
    mode == TryOnMode.DETECTED -> "Prévia na sua mão"
    mode == TryOnMode.APPROXIMATE && isUserPhoto ->
        "Mão não detectada — unhas à mostra, boa luz, dedos abertos"
    else -> "Prévia aproximada"
}

private fun resolvePreview(
    context: android.content.Context,
    bitmap: Bitmap,
    polishColor: Color,
    sampleId: String?,
    pipeline: NailTryOnPipeline,
): TryOnPreviewData {
    val isUserPhoto = sampleId == null
    return when {
        isUserPhoto -> resolveUserPreview(
            bitmap = bitmap,
            polishColor = polishColor,
            pipeline = pipeline,
        )
        else -> resolveSamplePreview(
            context = context,
            bitmap = bitmap,
            polishColor = polishColor,
            sampleId = checkNotNull(sampleId),
        )
    }
}

private fun resolveUserPreview(
    bitmap: Bitmap,
    polishColor: Color,
    pipeline: NailTryOnPipeline,
): TryOnPreviewData {
    val result = pipeline.process(
        image = bitmap,
        polishColor = polishColor,
        stabilize = false,
    )
    if (result != null && result.nails.isNotEmpty()) {
        return TryOnPreviewData(
            bitmap = result.bitmap,
            anchors = emptyList(),
            mode = TryOnMode.DETECTED,
            nails = result.nails,
            landmarks = result.landmarks,
            showDebug = result.debugEnabled,
        )
    }
    // Sem detecção confiável: overlay DEFAULT (mão aberta) — melhor que zero pintura.
    val displayBitmap = result?.bitmap ?: bitmap
    return TryOnPreviewData(
        bitmap = displayBitmap,
        anchors = NailOverlayAnchors.DEFAULT,
        mode = TryOnMode.APPROXIMATE,
        nails = result?.nails.orEmpty(),
        landmarks = result?.landmarks,
        showDebug = result?.debugEnabled == true,
    )
}

private fun resolveSamplePreview(
    context: android.content.Context,
    bitmap: Bitmap,
    polishColor: Color,
    sampleId: String,
): TryOnPreviewData {
    if (NailOverlayAnchors.hasMaskAsset(sampleId)) {
        val mask = PolishMaskRecolorer.loadMask(context, sampleId)
        val recolored = mask?.let { PolishMaskRecolorer.recolor(bitmap, it, polishColor) }
        if (mask != null && mask !== recolored && !mask.isRecycled) {
            // loadMask devolve bitmap própria; recolor escala/copia — libera a máscara.
            recycleQuietly(mask)
        }
        if (recolored != null) {
            return TryOnPreviewData(
                bitmap = recolored,
                anchors = emptyList(),
                mode = TryOnMode.MASK,
            )
        }
    }
    return TryOnPreviewData(
        bitmap = bitmap,
        anchors = NailOverlayAnchors.forSample(sampleId),
        mode = TryOnMode.APPROXIMATE,
    )
}

private fun recycleQuietly(bitmap: Bitmap?) {
    if (bitmap != null && !bitmap.isRecycled) {
        runCatching { bitmap.recycle() }
    }
}

private fun DrawScope.drawPolishNail(
    anchor: NailOverlayAnchor,
    polishColor: Color,
) {
    val nailWidth = size.width * anchor.width
    val nailHeight = size.height * anchor.height
    val center = Offset(size.width * anchor.centerX, size.height * anchor.centerY)
    val topLeft = Offset(center.x - nailWidth / 2f, center.y - nailHeight / 2f)
    val nailSize = Size(nailWidth, nailHeight)
    val radius = CornerRadius(nailWidth * 0.48f, nailHeight * 0.42f)
    rotate(degrees = anchor.rotationDegrees, pivot = center) {
        drawRoundRect(
            color = polishColor.copy(alpha = 0.55f),
            topLeft = topLeft,
            size = nailSize,
            cornerRadius = radius,
            blendMode = BlendMode.Multiply,
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    polishColor.copy(alpha = 0.58f),
                    polishColor.copy(alpha = 0.86f),
                    polishColor.copy(alpha = 0.72f),
                ),
            ),
            topLeft = topLeft,
            size = nailSize,
            cornerRadius = radius,
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.34f),
                    Color.White.copy(alpha = 0.05f),
                    Color.Transparent,
                ),
            ),
            topLeft = Offset(
                center.x - nailWidth * 0.12f,
                center.y - nailHeight * 0.42f,
            ),
            size = Size(nailWidth * 0.22f, nailHeight * 0.72f),
            cornerRadius = CornerRadius(nailWidth * 0.16f, nailHeight * 0.2f),
            blendMode = BlendMode.Screen,
        )
    }
}
