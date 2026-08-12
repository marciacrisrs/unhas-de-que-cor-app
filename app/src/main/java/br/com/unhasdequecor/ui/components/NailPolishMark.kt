package br.com.unhasdequecor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.unhasdequecor.ui.theme.BrandAction
import br.com.unhasdequecor.ui.theme.BrandFun

/**
 * Mark compacto para toolbars/listas: frasco + anel quebrado + sparkles em chip circular.
 * O lockup oficial completo fica em [BrandLogoLockup] / [BrandHeader] (Home/Perfil).
 */
@Composable
fun NailPolishMark(
    modifier: Modifier = Modifier,
    markSize: Dp = 48.dp,
    polishColor: Color? = null,
    decorative: Boolean = false,
) {
    val resolvedPolish = polishColor ?: BrandFun
    val outline = MaterialTheme.colorScheme.onBackground
    val framed = polishColor == null
    val markModifier = if (decorative) {
        modifier.size(markSize).clearAndSetSemantics { }
    } else {
        modifier
            .size(markSize)
            .semantics { contentDescription = "Ícone do app Unhas de Que Cor" }
    }

    Box(
        modifier = markModifier.then(
            if (framed) {
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f),
                        shape = CircleShape,
                    )
                    .padding(4.dp)
            } else {
                Modifier
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawTintedPolishMark(polishColor = resolvedPolish, outline = outline)
        }
    }
}

private fun DrawScope.drawTintedPolishMark(
    polishColor: Color,
    outline: Color,
) {
    val stroke = Stroke(width = size.minDimension * 0.04f, cap = StrokeCap.Round)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = size.minDimension * 0.42f
    val ring = Brush.sweepGradient(listOf(BrandFun, BrandAction, BrandFun))
    val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
    val topLeft = Offset(cx - radius, cy - radius)

    drawArc(
        brush = ring,
        startAngle = -35f,
        sweepAngle = 250f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = stroke,
    )
    drawArc(
        color = outline.copy(alpha = 0.45f),
        startAngle = 230f,
        sweepAngle = 50f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = stroke,
    )

    val bottleTop = cy - radius * 0.55f
    val bottleBottom = cy + radius * 0.52f
    val bottleHalf = radius * 0.28f
    val bottlePath = Path().apply {
        moveTo(cx - bottleHalf * 0.55f, bottleTop)
        lineTo(cx + bottleHalf * 0.55f, bottleTop)
        lineTo(cx + bottleHalf * 0.7f, bottleTop + radius * 0.2f)
        lineTo(cx + bottleHalf, bottleTop + radius * 0.28f)
        lineTo(cx + bottleHalf, bottleBottom)
        quadraticTo(cx, bottleBottom + radius * 0.12f, cx - bottleHalf, bottleBottom)
        lineTo(cx - bottleHalf, bottleTop + radius * 0.28f)
        lineTo(cx - bottleHalf * 0.7f, bottleTop + radius * 0.2f)
        close()
    }
    drawPath(
        path = bottlePath,
        brush = Brush.verticalGradient(
            colors = listOf(polishColor.copy(alpha = 0.55f), polishColor),
            startY = bottleTop,
            endY = bottleBottom,
        ),
    )
    drawPath(path = bottlePath, color = outline, style = stroke)
    drawMarkSparkles(cx = cx, cy = cy, radius = radius)
}

private fun DrawScope.drawMarkSparkles(
    cx: Float,
    cy: Float,
    radius: Float,
) {
    listOf(
        Offset(cx + radius * 0.55f, cy - radius * 0.35f) to radius * 0.11f,
        Offset(cx - radius * 0.58f, cy + radius * 0.08f) to radius * 0.08f,
        Offset(cx + radius * 0.18f, cy + radius * 0.52f) to radius * 0.07f,
        Offset(cx - radius * 0.18f, cy - radius * 0.52f) to radius * 0.06f,
    ).forEach { (center, s) ->
        val sparklePath = Path().apply {
            moveTo(center.x, center.y - s)
            lineTo(center.x + s * 0.22f, center.y - s * 0.22f)
            lineTo(center.x + s, center.y)
            lineTo(center.x + s * 0.22f, center.y + s * 0.22f)
            lineTo(center.x, center.y + s)
            lineTo(center.x - s * 0.22f, center.y + s * 0.22f)
            lineTo(center.x - s, center.y)
            lineTo(center.x - s * 0.22f, center.y - s * 0.22f)
            close()
        }
        drawPath(path = sparklePath, color = BrandFun)
    }
}
