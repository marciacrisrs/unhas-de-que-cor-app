package br.com.unhasdequecor.ui.components

import android.content.Context
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
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
import br.com.unhasdequecor.data.local.hand.OrientedBitmapDecoder
import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.nail.DetectedNail
import br.com.unhasdequecor.data.vision.nail.DetectedNailPolishApplier
import br.com.unhasdequecor.data.vision.nail.NailDetectionSnapshot
import br.com.unhasdequecor.data.vision.nail.NailLandmarkMapper
import br.com.unhasdequecor.data.vision.nail.NailOverlayAnchor
import br.com.unhasdequecor.data.vision.nail.NailOverlayAnchors
import br.com.unhasdequecor.data.vision.nail.NailTryOnPipeline
import br.com.unhasdequecor.data.vision.nail.PolishMaskRecolorer
import br.com.unhasdequecor.data.vision.nail.TryOnHandReliability
import br.com.unhasdequecor.data.vision.nail.UserTryOnRenderMode
import br.com.unhasdequecor.ui.theme.SoftSurfaceShape
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

/** Cache de detecção/máscara independente da cor do esmalte. */
private data class TryOnBaseAssets(
    val decoded: Bitmap,
    val snapshot: NailDetectionSnapshot? = null,
    val sampleMask: Bitmap? = null,
    val sampleId: String? = null,
)

private enum class TryOnMode {
    MASK,
    DETECTED,
    APPROXIMATE,
    /** Sem landmarks utilizáveis / presence rejeitada — sem overlay de unha. */
    NOT_DETECTED,
}

@Composable
fun HandTryOnPreview(
    imagePath: String,
    revision: Long,
    polishColor: Color,
    colorName: String,
    sampleId: String?,
    nailPipeline: NailTryOnPipeline,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val base by rememberTryOnBaseAssets(imagePath, revision, sampleId, nailPipeline, context)
    DisposeTryOnBaseAssets(base)
    val preview by rememberPaintedPreview(base, polishColor, nailPipeline)
    DisposeTryOnPreview(preview)
    TryOnPreviewFrame(
        preview = preview,
        polishColor = polishColor,
        colorName = colorName,
        sampleId = sampleId,
        modifier = modifier,
    )
}

@Composable
private fun rememberTryOnBaseAssets(
    imagePath: String,
    revision: Long,
    sampleId: String?,
    pipeline: NailTryOnPipeline,
    context: Context,
): State<TryOnBaseAssets?> = produceState(
    initialValue = null,
    imagePath,
    revision,
    sampleId,
    pipeline,
) {
    value = withContext(Dispatchers.Default) {
        loadTryOnBaseAssets(
            imagePath = imagePath,
            sampleId = sampleId,
            pipeline = pipeline,
            appContext = context.applicationContext,
        )
    }
}

@Composable
private fun rememberPaintedPreview(
    base: TryOnBaseAssets?,
    polishColor: Color,
    pipeline: NailTryOnPipeline,
): State<TryOnPreviewData?> = produceState(
    initialValue = null,
    base,
    polishColor,
) {
    val assets = base
    if (assets == null) {
        value = null
        return@produceState
    }
    value = withContext(Dispatchers.Default) {
        paintPreview(assets, polishColor, pipeline)
    }
}

@Composable
private fun DisposeTryOnBaseAssets(base: TryOnBaseAssets?) {
    DisposableEffect(base) {
        val held = base
        onDispose { recycleTryOnBaseAssets(held) }
    }
}

@Composable
private fun DisposeTryOnPreview(preview: TryOnPreviewData?) {
    DisposableEffect(preview) {
        val held = preview
        onDispose { recycleQuietly(held?.bitmap) }
    }
}

@Composable
private fun TryOnPreviewFrame(
    preview: TryOnPreviewData?,
    polishColor: Color,
    colorName: String,
    sampleId: String?,
    modifier: Modifier,
) {
    val aspect = previewAspect(preview)
    val statusLabel = previewStatusLabel(data = preview, isUserPhoto = sampleId == null)
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

private fun previewAspect(preview: TryOnPreviewData?): Float {
    val bmp = preview?.bitmap ?: return NailLandmarkMapper.PREVIEW_ASPECT
    return if (bmp.height > 0) {
        bmp.width.toFloat() / bmp.height.toFloat()
    } else {
        NailLandmarkMapper.PREVIEW_ASPECT
    }
}

private fun previewStatusLabel(mode: TryOnMode?, isUserPhoto: Boolean): String = when {
    mode == TryOnMode.MASK -> "Prévia na mão de exemplo"
    mode == TryOnMode.DETECTED -> "Prévia na sua mão"
    mode == TryOnMode.NOT_DETECTED && isUserPhoto ->
        "Mão não detectada — frente à câmera, boa luz na mão (evite contraluz)"
    mode == TryOnMode.APPROXIMATE && isUserPhoto ->
        "Prévia aproximada — unhas à mostra, luz na mão (evite contraluz)"
    mode == TryOnMode.APPROXIMATE -> "Prévia aproximada"
    else -> "Prévia aproximada"
}

/** Rótulo alinhado ao modo ≡ qualidade (FULL / aproximado / não detectado). */
private fun previewStatusLabel(
    data: TryOnPreviewData?,
    isUserPhoto: Boolean,
): String = previewStatusLabel(data?.mode, isUserPhoto)

private fun loadTryOnBaseAssets(
    imagePath: String,
    sampleId: String?,
    pipeline: NailTryOnPipeline,
    appContext: Context,
): TryOnBaseAssets? {
    var decoded: Bitmap? = null
    var sampleMask: Bitmap? = null
    return try {
        decoded = OrientedBitmapDecoder.decodeFile(imagePath, maxEdge = 1280) ?: return null
        if (sampleId != null && NailOverlayAnchors.hasMaskAsset(sampleId)) {
            sampleMask = PolishMaskRecolorer.loadMask(appContext, sampleId)
        }
        val snapshot = if (sampleId == null) {
            pipeline.detect(decoded, stabilize = false)
        } else {
            null
        }
        TryOnBaseAssets(
            decoded = decoded,
            snapshot = snapshot,
            sampleMask = sampleMask,
            sampleId = sampleId,
        ).also { decoded = null }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        recycleQuietly(decoded)
        recycleQuietly(sampleMask)
        throw cancelled
    }
}

private fun recycleTryOnBaseAssets(held: TryOnBaseAssets?) {
    held?.snapshot?.let { snap ->
        if (snap.ownsWorkingBitmap &&
            snap.workingBitmap !== held.decoded &&
            !snap.workingBitmap.isRecycled
        ) {
            recycleQuietly(snap.workingBitmap)
        }
    }
    recycleQuietly(held?.sampleMask)
    recycleQuietly(held?.decoded)
}

/**
 * Produz bitmap pintado a partir do cache de detecção/máscara.
 * O bitmap devolvido é sempre uma cópia/pintura nova (ou a decoded se APPROXIMATE sem paint).
 */
private fun paintPreview(
    assets: TryOnBaseAssets,
    polishColor: Color,
    pipeline: NailTryOnPipeline,
): TryOnPreviewData {
    val sampleId = assets.sampleId
    return if (sampleId != null) {
        paintSamplePreview(assets, polishColor, sampleId)
    } else {
        paintUserPreview(assets, polishColor, pipeline)
    }
}

private fun paintSamplePreview(
    assets: TryOnBaseAssets,
    polishColor: Color,
    sampleId: String,
): TryOnPreviewData {
    val mask = assets.sampleMask
    if (mask != null) {
        val recolored = PolishMaskRecolorer.recolor(assets.decoded, mask, polishColor)
        if (recolored != null) {
            return TryOnPreviewData(
                bitmap = recolored,
                anchors = emptyList(),
                mode = TryOnMode.MASK,
            )
        }
    }
    val display = ownedPreviewBitmap(
        candidate = assets.decoded,
        protected = listOf(assets.decoded),
    )
    return TryOnPreviewData(
        bitmap = display,
        anchors = NailOverlayAnchors.forSample(sampleId),
        mode = TryOnMode.APPROXIMATE,
    )
}

private fun paintUserPreview(
    assets: TryOnBaseAssets,
    polishColor: Color,
    pipeline: NailTryOnPipeline,
): TryOnPreviewData {
    val snapshot = assets.snapshot
    val landmarks = snapshot?.landmarks
    val mappedAnchors = landmarks?.let {
        NailLandmarkMapper.fromNormalizedLandmarks(
            landmarks = it.points.map { p -> NailLandmarkMapper.NormalizedPoint(p.x, p.y) },
            imageWidth = it.imageWidth,
            imageHeight = it.imageHeight,
        )
    }
    val plan = TryOnHandReliability.planRender(
        reliability = snapshot?.reliability,
        nailCount = snapshot?.nails?.size ?: 0,
        hasMappableAnchors = mappedAnchors != null,
    )

    when (plan.mode) {
        UserTryOnRenderMode.NONE -> {
            val displaySource = snapshot?.workingBitmap ?: assets.decoded
            val display = ownedPreviewBitmap(
                candidate = displaySource,
                protected = listOfNotNull(snapshot?.workingBitmap, assets.decoded),
            )
            return TryOnPreviewData(
                bitmap = display,
                anchors = emptyList(),
                mode = TryOnMode.NOT_DETECTED,
                nails = emptyList(),
                landmarks = null,
                showDebug = pipeline.debugEnabled,
            )
        }
        UserTryOnRenderMode.FULL -> {
            checkNotNull(snapshot)
            val result = pipeline.recolor(snapshot, polishColor)
            val painted = ownedPreviewBitmap(
                candidate = result.bitmap,
                protected = listOfNotNull(snapshot.workingBitmap, assets.decoded),
            )
            return TryOnPreviewData(
                bitmap = painted,
                anchors = emptyList(),
                mode = TryOnMode.DETECTED,
                nails = result.nails,
                landmarks = result.landmarks,
                showDebug = result.debugEnabled,
            )
        }
        UserTryOnRenderMode.APPROXIMATE -> {
            checkNotNull(snapshot)
            if (plan.useNailMasks && snapshot.nails.isNotEmpty()) {
                val result = pipeline.recolor(snapshot, polishColor)
                val painted = ownedPreviewBitmap(
                    candidate = result.bitmap,
                    protected = listOfNotNull(snapshot.workingBitmap, assets.decoded),
                )
                return TryOnPreviewData(
                    bitmap = painted,
                    anchors = emptyList(),
                    mode = TryOnMode.APPROXIMATE,
                    nails = result.nails,
                    landmarks = result.landmarks,
                    showDebug = result.debugEnabled,
                )
            }
            if (plan.useEllipsePaint && landmarks != null && mappedAnchors != null) {
                val painted = DetectedNailPolishApplier.apply(
                    source = snapshot.workingBitmap,
                    anchors = mappedAnchors,
                    polishColor = polishColor,
                )
                if (painted != null) {
                    return TryOnPreviewData(
                        bitmap = painted,
                        anchors = emptyList(),
                        mode = TryOnMode.APPROXIMATE,
                        landmarks = landmarks,
                        showDebug = pipeline.debugEnabled,
                    )
                }
                val display = ownedPreviewBitmap(
                    candidate = snapshot.workingBitmap,
                    protected = listOfNotNull(snapshot.workingBitmap, assets.decoded),
                )
                return TryOnPreviewData(
                    bitmap = display,
                    anchors = if (plan.useCanvasAnchors) mappedAnchors else emptyList(),
                    mode = TryOnMode.APPROXIMATE,
                    landmarks = landmarks,
                    showDebug = pipeline.debugEnabled,
                )
            }
            val display = ownedPreviewBitmap(
                candidate = snapshot.workingBitmap,
                protected = listOfNotNull(snapshot.workingBitmap, assets.decoded),
            )
            return TryOnPreviewData(
                bitmap = display,
                anchors = emptyList(),
                mode = TryOnMode.APPROXIMATE,
                landmarks = landmarks,
                showDebug = pipeline.debugEnabled,
            )
        }
    }
}

/** Garante bitmap que a UI pode reciclar sem tocar no cache de detecção. */
private fun ownedPreviewBitmap(
    candidate: Bitmap,
    protected: List<Bitmap>,
): Bitmap {
    if (protected.none { it === candidate }) return candidate
    return candidate.copy(Bitmap.Config.ARGB_8888, false) ?: candidate
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
