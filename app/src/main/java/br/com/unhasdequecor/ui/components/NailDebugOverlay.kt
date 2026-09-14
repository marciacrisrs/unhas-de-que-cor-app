package br.com.unhasdequecor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.nail.DetectedNail
import br.com.unhasdequecor.data.vision.nail.NailMaskDiagnostic
import br.com.unhasdequecor.data.vision.nail.TryOnPipelineMetrics
import br.com.unhasdequecor.data.vision.nail.TryOnPipelineMetricsSnapshot

/** Debug-only overlay. Never changes segmentation or production rendering. */
@Composable
fun NailDebugOverlay(
    landmarks: HandLandmarks?,
    nails: List<DetectedNail>,
    diagnostics: List<NailMaskDiagnostic> = emptyList(),
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
                    Color.Cyan.copy(alpha = 0.85f),
                    4f,
                    Offset(p.x * size.width, p.y * size.height),
                )
            }
            nails.forEach { nail ->
                drawNailMask(nail, sx, sy)
                val b = nail.roi.bounds
                drawRect(
                    Color.Yellow.copy(alpha = 0.7f),
                    Offset(b.left * sx, b.top * sy),
                    Size(b.width() * sx, b.height() * sy),
                    style = Stroke(width = 2f),
                )
                drawPolygon(nail.roi.polygon, sx, sy, Color.Blue.copy(alpha = 0.75f), 2f)
                nail.mask.boundaryPolygon?.let {
                    drawPolygon(it, sx, sy, Color(0xFF7CFF00).copy(alpha = 0.95f), 2.5f)
                }
            }
        }

        Column(
            modifier = Modifier
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            if (metrics.sampleCount > 0) {
                Text(
                    "proc ${metrics.effectiveFps.toInt()} FPS | " +
                        "p95 ${metrics.p95Ms.toInt()}ms | max ${metrics.maxMs.toInt()}ms",
                    color = Color.White,
                )
                Text(
                    "MP ${metrics.mediaPipeMs.toInt()}ms | " +
                        "seg ${metrics.segmentationMs.toInt()}ms | " +
                        "track ${metrics.trackingMs.toInt()}ms",
                    color = Color.White,
                )
                Text(
                    "pred ${metrics.predictionFrames} | " +
                        "recovery ${metrics.recoveryFrames} | fail ${metrics.rejectedFrames}",
                    color = Color.White,
                )
                Text(
                    "last nails ${metrics.lastNailsDetected} | " +
                        "barrier ${metrics.lastRejectionBarrier.name}",
                    color = Color.White,
                )
                Text(
                    "diag ${metrics.lastDiagnosticCount} | " +
                        "roi ${metrics.lastRoiRejected} | " +
                        "seg ${metrics.lastSegmentationRejected} | " +
                        "guard ${metrics.lastGuardRejected} | " +
                        "conf ${metrics.lastConfidenceRejected}",
                    color = Color.White,
                )
                if (metrics.lastFailureReason != null) {
                    Text(
                        "reason ${metrics.lastFailureReason.logCode}",
                        color = Color.White,
                    )
                }
            }
            if (diagnostics.isEmpty() && metrics.lastDiagnosticCount == 0) {
                Text("DIAG EMPTY — no candidate diagnostics reached UI", color = Color.Yellow)
            }
            diagnostics.sortedBy { it.finger.ordinal }.forEach { diagnostic ->
                Text(diagnosticLine(diagnostic), color = Color.White)
            }
        }
    }
}

private fun diagnosticLine(d: NailMaskDiagnostic): String {
    val finger = when (d.finger.name) {
        "THUMB" -> "P"
        "INDEX" -> "I"
        "MIDDLE" -> "M"
        "RING" -> "A"
        "PINKY" -> "Mi"
        else -> d.finger.name.take(2)
    }
    val detail = d.rejectionReason ?: "ok"
    return "$finger ${d.stage.name} geo=${"%.2f".format(d.geometricConfidence)} " +
        "seg=${"%.2f".format(d.segmentationConfidence)} " +
        "fill=${"%.3f".format(d.postGuardFilledRatio)} $detail"
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolygon(
    points: List<br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint>,
    sx: Float,
    sy: Float,
    color: Color,
    strokeWidth: Float,
) {
    if (points.size < 3) return
    val scaled = points.map { Offset(it.x * sx, it.y * sy) }
    val path = Path()
    path.moveTo(scaled.first().x, scaled.first().y)
    for (index in scaled.indices) {
        val current = scaled[index]
        val next = scaled[(index + 1) % scaled.size]
        val midpoint = Offset((current.x + next.x) * 0.5f, (current.y + next.y) * 0.5f)
        path.quadraticTo(current.x, current.y, midpoint.x, midpoint.y)
    }
    path.close()
    drawPath(path, color, style = Stroke(width = strokeWidth))
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
                topLeft = Offset(originX + start * pixelW, originY + y * pixelH),
                size = Size((x - start) * pixelW, pixelH),
            )
        }
    }
}

private const val MASK_VISIBLE_THRESHOLD = 16
private const val MASK_MIN_ALPHA = 0.16f
private const val MASK_MAX_EXTRA_ALPHA = 0.44f
