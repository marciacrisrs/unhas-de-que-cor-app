package br.com.unhasdequecor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.nail.DetectedNail
import br.com.unhasdequecor.data.vision.nail.TryOnPipelineMetrics
import br.com.unhasdequecor.data.vision.nail.TryOnPipelineMetricsSnapshot

/**
 * Overlay de debug (somente quando NailTryOnPipeline.debugEnabled = true).
 *
 * A máscara atual é desenhada diretamente por cima da foto, sem alterar a
 * segmentação. Isso permite separar visualmente:
 * - amarelo: ROI estimada;
 * - verde: polígono geométrico/prior;
 * - vermelho: NailMask efetivamente entregue ao renderizador.
 */
@Composable
fun NailDebugOverlay(
    landmarks: HandLandmarks?,
    nails: List<DetectedNail>,
    metrics: TryOnPipelineMetricsSnapshot = TryOnPipelineMetrics.latestDebugSnapshot,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val imgW = landmarks?.imageWidth?.toFloat()?.coerceAtLeast(1f) ?: size.width
            val imgH = landmarks?.imageHeight?.toFloat()?.coerceAtLeast(1f) ?: size.height
            val sx = size.width / imgW
            val sy = size.height / imgH

            landmarks?.points?.forEach { p ->
                drawCircle(
                    color = Color.Cyan.copy(alpha = 0.85f),
                    radius = 4f,
                    center = Offset(p.x * size.width, p.y * size.height),
                )
            }

            nails.forEach { nail ->
                // Máscara efetiva: esta é a forma que o NailColorApplier recebe.
                drawNailMask(nail, sx, sy)

                val b = nail.roi.bounds
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

        if (metrics.sampleCount > 0) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "proc ${metrics.effectiveFps.toInt()} FPS | " +
                        "p95 ${metrics.p95Ms.toInt()}ms | " +
                        "max ${metrics.maxMs.toInt()}ms",
                    color = Color.White,
                )
                Text(
                    text = "MP ${metrics.mediaPipeMs.toInt()}ms | " +
                        "seg ${metrics.segmentationMs.toInt()}ms | " +
                        "track ${metrics.trackingMs.toInt()}ms",
                    color = Color.White,
                )
                Text(
                    text = "pred ${metrics.predictionFrames} | " +
                        "recovery ${metrics.recoveryFrames} | " +
                        "fail ${metrics.rejectedFrames}",
                    color = Color.White,
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNailMask(
    nail: DetectedNail,
    sx: Float,
    sy: Float,
) {
    val mask = nail.mask
    val originX = mask.originX * sx
    val originY = mask.originY * sy
    val pixelW = sx.coerceAtLeast(0.5f)
    val pixelH = sy.coerceAtLeast(0.5f)

    // Agrupa pixels contíguos de cada linha para manter o overlay leve em debug.
    for (y in 0 until mask.height) {
        var x = 0
        while (x < mask.width) {
            while (x < mask.width && mask.coverageAt(x, y) < MASK_VISIBLE_THRESHOLD) x++
            if (x >= mask.width) break
            val start = x
            var maxAlpha = 0
            while (x < mask.width) {
                val alpha = mask.coverageAt(x, y)
                if (alpha < MASK_VISIBLE_THRESHOLD) break
                maxAlpha = maxOf(maxAlpha, alpha)
                x++
            }
            drawRect(
                color = Color.Red.copy(
                    alpha = MASK_MIN_ALPHA + MASK_MAX_EXTRA_ALPHA * (maxAlpha / 255f),
                ),
                topLeft = Offset(
                    originX + start * pixelW,
                    originY + y * pixelH,
                ),
                size = Size(
                    (x - start) * pixelW,
                    pixelH,
                ),
            )
        }
    }
}

private const val MASK_VISIBLE_THRESHOLD = 16
private const val MASK_MIN_ALPHA = 0.16f
private const val MASK_MAX_EXTRA_ALPHA = 0.44f
