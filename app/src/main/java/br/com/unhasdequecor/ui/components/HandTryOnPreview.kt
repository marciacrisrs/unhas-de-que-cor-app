package br.com.unhasdequecor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import br.com.unhasdequecor.data.vision.nail.NailTryOnPipeline
import br.com.unhasdequecor.di.NailPipelineEntryPoint
import br.com.unhasdequecor.ui.theme.SoftSurfaceShape
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class TryOnPreviewData(
    val bitmap: android.graphics.Bitmap,
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
            val bitmap = OrientedBitmapDecoder.decodeFile(imagePath, maxEdge = 2048)
                ?: return@withContext null
            resolvePreview(
                context = context.applicationContext,
                bitmap = bitmap,
                polishColor = polishColor,
                sampleId = sampleId,
                pipeline = pipeline,
            )
        }
    }

    val aspect = preview?.bitmap?.let { bmp ->
        if (bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else NailLandmarkMapper.PREVIEW_ASPECT
    } ?: NailLandmarkMapper.PREVIEW_ASPECT

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(SoftSurfaceShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .semantics {
                contentDescription = "Prévia da cor $colorName na sua mão"
            },
    ) {
        val data = preview
        if (data != null) {
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
        Text(
            text = when {
                preview?.mode == TryOnMode.MASK -> "Prévia na mão de exemplo"
                preview?.mode == TryOnMode.DETECTED -> "Prévia na sua mão"
                preview?.mode == TryOnMode.APPROXIMATE && sampleId == null ->
                    "Mão não detectada — unhas à mostra, boa luz, dedos abertos"
                else -> "Prévia aproximada"
            },
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

private fun resolvePreview(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    polishColor: Color,
    sampleId: String?,
    pipeline: NailTryOnPipeline,
): TryOnPreviewData {
    val isUserPhoto = sampleId == null
    return when {
        isUserPhoto -> {
            val result = pipeline.process(
                image = bitmap,
                polishColor = polishColor,
                stabilize = false,
            )
            if (result != null && result.nails.isNotEmpty()) {
                TryOnPreviewData(
                    bitmap = result.bitmap,
                    anchors = emptyList(),
                    mode = TryOnMode.DETECTED,
                    nails = result.nails,
                    landmarks = result.landmarks,
                    showDebug = result.debugEnabled,
                )
            } else {
                TryOnPreviewData(
                    bitmap = result?.bitmap ?: bitmap,
                    anchors = emptyList(),
                    mode = TryOnMode.APPROXIMATE,
                    nails = result?.nails.orEmpty(),
                    landmarks = result?.landmarks,
                    showDebug = result?.debugEnabled == true,
                )
            }
        }
        else -> resolveSamplePreview(
            context = context,
            bitmap = bitmap,
            polishColor = polishColor,
            sampleId = checkNotNull(sampleId),
        )
    }
}

private fun resolveSamplePreview(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    polishColor: Color,
    sampleId: String,
): TryOnPreviewData {
    if (NailOverlayAnchors.hasMaskAsset(sampleId)) {
        val mask = PolishMaskRecolorer.loadMask(context, sampleId)
        val recolored = mask?.let { PolishMaskRecolorer.recolor(bitmap, it, polishColor) }
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
