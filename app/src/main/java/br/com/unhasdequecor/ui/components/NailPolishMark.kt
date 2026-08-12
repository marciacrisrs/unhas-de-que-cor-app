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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
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
 * Mark compacto alinhado ao [logo_mark] oficial: tampa alta, corpo bulboso,
 * anel com aberturas e sparkles. Tintável via [polishColor] (inspiração / resultado).
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
            drawOfficialPolishMark(polishColor = resolvedPolish, outline = outline)
        }
    }
}

private fun DrawScope.drawOfficialPolishMark(
    polishColor: Color,
    outline: Color,
) {
    val strokeWidth = size.minDimension * 0.035f
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = size.minDimension * 0.40f
    val ring = Brush.sweepGradient(listOf(BrandFun, BrandAction, BrandFun))
    val arcSize = Size(radius * 2f, radius * 2f)
    val topLeft = Offset(cx - radius, cy - radius)

    // Anel incompleto (aberturas no topo-direita e base-esquerda), como no logo_icone.
    drawArc(
        brush = ring,
        startAngle = -20f,
        sweepAngle = 195f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = stroke,
    )
    drawArc(
        color = outline.copy(alpha = 0.55f),
        startAngle = 200f,
        sweepAngle = 95f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = stroke,
    )
    // Ponto decorativo perto da abertura superior.
    drawCircle(
        color = outline.copy(alpha = 0.7f),
        radius = strokeWidth * 0.85f,
        center = Offset(
            cx + radius * kotlin.math.cos(Math.toRadians(-28.0)).toFloat(),
            cy + radius * kotlin.math.sin(Math.toRadians(-28.0)).toFloat(),
        ),
    )

    val bottle = officialBottlePath(cx = cx, cy = cy, radius = radius)
    val fillTop = cy + radius * 0.02f
    val fillBottom = cy + radius * 0.58f
    drawPath(
        path = bottle,
        brush = Brush.verticalGradient(
            colors = listOf(polishColor.copy(alpha = 0.35f), polishColor),
            startY = fillTop,
            endY = fillBottom,
        ),
    )
    drawPath(path = bottle, color = outline, style = stroke)
    drawOfficialSparkles(cx = cx, cy = cy, radius = radius, color = outline.copy(alpha = 0.85f))
}

private fun officialBottlePath(cx: Float, cy: Float, radius: Float): Path {
    val capHalf = radius * 0.18f
    val capTop = cy - radius * 0.62f
    val capBottom = cy - radius * 0.28f
    val neckY = cy - radius * 0.18f
    val shoulderY = cy - radius * 0.05f
    val bodyBottom = cy + radius * 0.55f
    val bodyHalf = radius * 0.34f
    val shoulderHalf = radius * 0.26f

    return Path().apply {
        // Tampa alta retangular (logo oficial).
        addRoundRect(
            RoundRect(
                left = cx - capHalf,
                top = capTop,
                right = cx + capHalf,
                bottom = capBottom,
                cornerRadius = CornerRadius(capHalf * 0.35f, capHalf * 0.35f),
            ),
        )
        // Pescoço curto.
        moveTo(cx - capHalf * 0.7f, capBottom)
        lineTo(cx + capHalf * 0.7f, capBottom)
        lineTo(cx + capHalf * 0.55f, neckY)
        lineTo(cx - capHalf * 0.55f, neckY)
        close()
        // Corpo bulboso.
        moveTo(cx - shoulderHalf, neckY)
        lineTo(cx + shoulderHalf, neckY)
        quadraticTo(cx + bodyHalf, shoulderY, cx + bodyHalf, cy + radius * 0.15f)
        quadraticTo(cx + bodyHalf * 0.95f, bodyBottom, cx, bodyBottom)
        quadraticTo(cx - bodyHalf * 0.95f, bodyBottom, cx - bodyHalf, cy + radius * 0.15f)
        quadraticTo(cx - bodyHalf, shoulderY, cx - shoulderHalf, neckY)
        close()
    }
}

private fun DrawScope.drawOfficialSparkles(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color,
) {
    listOf(
        Offset(cx + radius * 0.52f, cy - radius * 0.42f) to radius * 0.10f,
        Offset(cx - radius * 0.55f, cy - radius * 0.22f) to radius * 0.08f,
        Offset(cx + radius * 0.58f, cy + radius * 0.12f) to radius * 0.07f,
        Offset(cx - radius * 0.48f, cy + radius * 0.28f) to radius * 0.09f,
        Offset(cx + radius * 0.22f, cy + radius * 0.52f) to radius * 0.06f,
        Offset(cx - radius * 0.12f, cy - radius * 0.55f) to radius * 0.05f,
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
        drawPath(path = sparklePath, color = color)
    }
}
