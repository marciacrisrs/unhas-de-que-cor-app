package br.com.unhasdequecor.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color

/**
 * Pinta esmalte na foto própria a partir das âncoras MediaPipe:
 * máscara elíptica suave por unha + [PolishMaskRecolorer]
 * (mesma lógica visual das amostras com máscara).
 */
object DetectedNailPolishApplier {
    fun apply(
        source: Bitmap,
        anchors: List<NailOverlayAnchor>,
        polishColor: Color,
    ): Bitmap? {
        if (anchors.isEmpty() || source.width <= 0 || source.height <= 0) return null
        val mask = buildSoftEllipseMask(source.width, source.height, anchors) ?: return null
        val painted = PolishMaskRecolorer.recolor(source, mask, polishColor)
        if (!mask.isRecycled) {
            mask.recycle()
        }
        return painted
    }

    private fun buildSoftEllipseMask(
        width: Int,
        height: Int,
        anchors: List<NailOverlayAnchor>,
    ): Bitmap? {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) ?: return null
        val canvas = Canvas(mask)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        for (anchor in anchors) {
            val cx = anchor.centerX * width
            val cy = anchor.centerY * height
            // Elipse de unha (um pouco mais longa que larga).
            val rx = (anchor.width * width * 0.50f).coerceAtLeast(4f)
            val ry = (anchor.height * height * 0.52f).coerceAtLeast(5f)
            paint.shader = RadialGradient(
                0f,
                0f,
                1f,
                intArrayOf(
                    android.graphics.Color.WHITE,
                    android.graphics.Color.WHITE,
                    android.graphics.Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.70f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(anchor.rotationDegrees)
            canvas.scale(rx, ry)
            canvas.drawCircle(0f, 0f, 1f, paint)
            canvas.restore()
        }
        paint.shader = null
        return soften(mask)
    }

    private fun soften(mask: Bitmap): Bitmap {
        val w = mask.width
        val h = mask.height
        val smallW = (w / 2).coerceAtLeast(1)
        val smallH = (h / 2).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(mask, smallW, smallH, true)
        val soft = Bitmap.createScaledBitmap(small, w, h, true)
        if (small !== mask && !small.isRecycled) {
            small.recycle()
        }
        if (soft !== mask) {
            mask.recycle()
        }
        return soft
    }
}
