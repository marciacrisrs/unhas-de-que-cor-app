package br.com.unhasdequecor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.nail.DetectedNail

/**
 * Overlay de debug (somente quando NailTryOnPipeline.debugEnabled = true).
 */
@Composable
fun NailDebugOverlay(
    landmarks: HandLandmarks?,
    nails: List<DetectedNail>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        landmarks?.points?.forEach { p ->
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.85f),
                radius = 4f,
                center = Offset(p.x * size.width, p.y * size.height),
            )
        }
        nails.forEach { nail ->
            val b = nail.roi.bounds
            val imgW = landmarks?.imageWidth?.toFloat()?.coerceAtLeast(1f) ?: size.width
            val imgH = landmarks?.imageHeight?.toFloat()?.coerceAtLeast(1f) ?: size.height
            val sx = size.width / imgW
            val sy = size.height / imgH
            drawRect(
                color = Color.Yellow.copy(alpha = 0.7f),
                topLeft = Offset(b.left * sx, b.top * sy),
                size = Size(b.width() * sx, b.height() * sy),
                style = Stroke(width = 2f),
            )
            val path = Path()
            nail.roi.polygon.forEachIndexed { index, p ->
                val o = Offset(p.x * sx, p.y * sy)
                if (index == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            path.close()
            drawPath(
                path,
                color = Color(0xFF7CFF00).copy(alpha = 0.9f),
                style = Stroke(width = 2.5f),
            )
        }
    }
}
