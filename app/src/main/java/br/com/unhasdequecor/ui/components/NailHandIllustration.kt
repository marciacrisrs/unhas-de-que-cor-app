package br.com.unhasdequecor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.unhasdequecor.ui.theme.BrandFun
import br.com.unhasdequecor.ui.theme.BrandInk
import br.com.unhasdequecor.ui.theme.BrandSoftSurface

/**
 * Ilustração vetorial leve de mão/unhas — parte da experiência visual
 * até entrarem assets finais de marca.
 */
@Composable
fun NailHandIllustration(
    polishColor: Color,
    modifier: Modifier = Modifier,
    colorName: String? = null,
) {
    val description = colorName?.let {
        "Ilustração de unhas na cor $it"
    } ?: "Ilustração de mão com unhas"

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .semantics { contentDescription = description },
    ) {
        val w = size.width
        val h = size.height
        val ink = BrandInk.copy(alpha = 0.88f)
        val soft = BrandSoftSurface.copy(alpha = 0.55f)
        val skin = Color(0xFFF3D4C8)
        val skinShadow = Color(0xFFE2B8A8)

        // Aura suave atrás da mão
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(polishColor.copy(alpha = 0.28f), Color.Transparent),
                center = Offset(w * 0.52f, h * 0.48f),
                radius = w * 0.48f,
            ),
            radius = w * 0.48f,
            center = Offset(w * 0.52f, h * 0.48f),
        )
        drawRoundRect(
            color = soft,
            topLeft = Offset(w * 0.08f, h * 0.12f),
            size = Size(w * 0.84f, h * 0.76f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
        )

        val palm = Path().apply {
            moveTo(w * 0.30f, h * 0.78f)
            cubicTo(w * 0.22f, h * 0.62f, w * 0.22f, h * 0.42f, w * 0.34f, h * 0.34f)
            cubicTo(w * 0.42f, h * 0.30f, w * 0.58f, h * 0.30f, w * 0.66f, h * 0.36f)
            cubicTo(w * 0.78f, h * 0.44f, w * 0.80f, h * 0.62f, w * 0.72f, h * 0.78f)
            close()
        }
        drawPath(
            path = palm,
            brush = Brush.verticalGradient(listOf(skin, skinShadow)),
        )
        drawPath(path = palm, color = ink, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

        data class Finger(
            val baseX: Float,
            val tipX: Float,
            val tipY: Float,
            val width: Float,
        )

        val fingers = listOf(
            Finger(w * 0.38f, w * 0.34f, h * 0.18f, w * 0.055f),
            Finger(w * 0.48f, w * 0.47f, h * 0.12f, w * 0.058f),
            Finger(w * 0.58f, w * 0.60f, h * 0.14f, w * 0.056f),
            Finger(w * 0.66f, w * 0.72f, h * 0.22f, w * 0.050f),
        )

        fingers.forEach { finger ->
            val fingerPath = Path().apply {
                moveTo(finger.baseX - finger.width, h * 0.38f)
                cubicTo(
                    finger.baseX - finger.width * 1.1f,
                    h * 0.28f,
                    finger.tipX - finger.width,
                    finger.tipY + h * 0.08f,
                    finger.tipX - finger.width * 0.75f,
                    finger.tipY,
                )
                quadraticTo(
                    finger.tipX,
                    finger.tipY - h * 0.02f,
                    finger.tipX + finger.width * 0.75f,
                    finger.tipY,
                )
                cubicTo(
                    finger.tipX + finger.width,
                    finger.tipY + h * 0.08f,
                    finger.baseX + finger.width * 1.1f,
                    h * 0.28f,
                    finger.baseX + finger.width,
                    h * 0.38f,
                )
                close()
            }
            drawPath(
                path = fingerPath,
                brush = Brush.verticalGradient(listOf(skin, skinShadow)),
            )
            drawPath(
                path = fingerPath,
                color = ink,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
            )

            // Unha
            val nailWidth = finger.width * 1.35f
            val nailHeight = h * 0.055f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(polishColor.copy(alpha = 0.75f), polishColor),
                ),
                topLeft = Offset(finger.tipX - nailWidth / 2f, finger.tipY - nailHeight * 0.15f),
                size = Size(nailWidth, nailHeight),
                cornerRadius = CornerRadius(nailWidth / 2f, nailHeight / 2f),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.28f),
                topLeft = Offset(
                    finger.tipX - nailWidth * 0.18f,
                    finger.tipY + nailHeight * 0.08f,
                ),
                size = Size(nailWidth * 0.22f, nailHeight * 0.45f),
                cornerRadius = CornerRadius(8f, 8f),
            )
        }

        // Sparkles divertidos
        val sparkle = BrandFun
        listOf(
            Offset(w * 0.18f, h * 0.22f) to w * 0.025f,
            Offset(w * 0.84f, h * 0.30f) to w * 0.02f,
            Offset(w * 0.78f, h * 0.58f) to w * 0.018f,
        ).forEach { (center, s) ->
            val star = Path().apply {
                moveTo(center.x, center.y - s)
                lineTo(center.x + s * 0.25f, center.y - s * 0.25f)
                lineTo(center.x + s, center.y)
                lineTo(center.x + s * 0.25f, center.y + s * 0.25f)
                lineTo(center.x, center.y + s)
                lineTo(center.x - s * 0.25f, center.y + s * 0.25f)
                lineTo(center.x - s, center.y)
                lineTo(center.x - s * 0.25f, center.y - s * 0.25f)
                close()
            }
            drawPath(path = star, color = sparkle)
        }
    }
}
