package br.com.unhasdequecor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.unhasdequecor.R
import br.com.unhasdequecor.ui.theme.BrandAction
import br.com.unhasdequecor.ui.theme.BrandFun

/**
 * Mark da marca (frasco) no canto direito das telas.
 *
 * - Sem [polishColor]: asset oficial `logo_mark` (claro/escuro via night).
 * - Com [polishColor]: frasco tintável (inspiração / resultado).
 */
@Composable
fun NailPolishMark(
    modifier: Modifier = Modifier,
    markSize: Dp = 48.dp,
    polishColor: Color? = null,
    decorative: Boolean = false,
) {
    if (polishColor == null) {
        OfficialBrandMark(
            modifier = modifier,
            markSize = markSize,
            decorative = decorative,
        )
    } else {
        TintablePolishMark(
            modifier = modifier,
            markSize = markSize,
            polishColor = polishColor,
            decorative = decorative,
        )
    }
}

/** Ícone oficial só do esmalte — usado à direita nas toolbars / headers. */
@Composable
fun OfficialBrandMark(
    modifier: Modifier = Modifier,
    markSize: Dp = 40.dp,
    decorative: Boolean = true,
) {
    val markModifier = if (decorative) {
        modifier
            .size(markSize)
            .clearAndSetSemantics { }
    } else {
        modifier
            .size(markSize)
            .semantics { contentDescription = "Ícone do app Unhas de Que Cor" }
    }
    Image(
        painter = painterResource(R.drawable.logo_mark),
        contentDescription = null,
        modifier = markModifier,
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun TintablePolishMark(
    modifier: Modifier,
    markSize: Dp,
    polishColor: Color,
    decorative: Boolean,
) {
    val outline = Color(0xFF400113)
    val markModifier = if (decorative) {
        modifier.size(markSize).clearAndSetSemantics { }
    } else {
        modifier
            .size(markSize)
            .semantics { contentDescription = "Ícone do app Unhas de Que Cor" }
    }
    Canvas(modifier = markModifier) {
        drawOfficialPolishMark(polishColor = polishColor, outline = outline)
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
        addRoundRect(
            RoundRect(
                left = cx - capHalf,
                top = capTop,
                right = cx + capHalf,
                bottom = capBottom,
                cornerRadius = CornerRadius(capHalf * 0.35f, capHalf * 0.35f),
            ),
        )
        moveTo(cx - capHalf * 0.7f, capBottom)
        lineTo(cx + capHalf * 0.7f, capBottom)
        lineTo(cx + capHalf * 0.55f, neckY)
        lineTo(cx - capHalf * 0.55f, neckY)
        close()
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
