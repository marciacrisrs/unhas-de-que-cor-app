package br.com.unhasdequecor.ui.components

import android.graphics.BitmapFactory
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.unhasdequecor.ui.theme.SoftSurfaceShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HandTryOnPreview(
    imagePath: String,
    revision: Long,
    polishColor: Color,
    colorName: String,
    sampleId: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState(
        initialValue = null as android.graphics.Bitmap?,
        imagePath,
        revision,
    ) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(imagePath)
        }
    }
    val anchors = NailOverlayAnchors.forSample(sampleId)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(SoftSurfaceShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .semantics {
                contentDescription = "Prévia da cor $colorName na sua mão"
            },
    ) {
        val preview = bitmap
        if (preview != null) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                anchors.forEach { anchor ->
                    drawPolishNail(anchor = anchor, polishColor = polishColor)
                }
            }
        }
        Text(
            text = "Prévia aproximada",
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolishNail(
    anchor: NailOverlayAnchor,
    polishColor: Color,
) {
    val nailWidth = size.width * anchor.width
    val nailHeight = size.height * anchor.height
    val center = Offset(size.width * anchor.centerX, size.height * anchor.centerY)
    rotate(degrees = anchor.rotationDegrees, pivot = center) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    polishColor.copy(alpha = 0.72f),
                    polishColor.copy(alpha = 0.95f),
                ),
            ),
            topLeft = Offset(center.x - nailWidth / 2f, center.y - nailHeight / 2f),
            size = Size(nailWidth, nailHeight),
            cornerRadius = CornerRadius(nailWidth / 2f, nailHeight / 2f),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.32f),
            topLeft = Offset(
                center.x - nailWidth * 0.12f,
                center.y - nailHeight * 0.28f,
            ),
            size = Size(nailWidth * 0.22f, nailHeight * 0.42f),
            cornerRadius = CornerRadius(8f, 8f),
        )
    }
}
