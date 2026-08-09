package br.com.unhasdequecor.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Recolore pixels da máscara de unha preservando luminância/brilho do esmalte original.
 */
object PolishMaskRecolorer {
    fun loadMask(context: Context, sampleId: String): Bitmap? = runCatching {
        context.assets.open("hand_nail_masks/$sampleId.png").use { input ->
            BitmapFactory.decodeStream(input)
        }
    }.getOrNull()

    fun recolor(source: Bitmap, mask: Bitmap, polishColor: Color): Bitmap? {
        if (source.width <= 0 || source.height <= 0) return null
        val scaledMask = if (mask.width == source.width && mask.height == source.height) {
            mask
        } else {
            Bitmap.createScaledBitmap(mask, source.width, source.height, true)
        }
        val out = source.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val target = polishColor.toArgb()
        val tr = AndroidColor.red(target)
        val tg = AndroidColor.green(target)
        val tb = AndroidColor.blue(target)
        val targetLum = luminance(tr, tg, tb).coerceAtLeast(1f)

        val width = out.width
        val height = out.height
        val pixels = IntArray(width * height)
        val maskPixels = IntArray(width * height)
        out.getPixels(pixels, 0, width, 0, 0, width, height)
        scaledMask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            if (AndroidColor.alpha(maskPixels[i]) < MASK_ALPHA_THRESHOLD &&
                AndroidColor.red(maskPixels[i]) < MASK_ALPHA_THRESHOLD
            ) {
                continue
            }
            val src = pixels[i]
            val sr = AndroidColor.red(src)
            val sg = AndroidColor.green(src)
            val sb = AndroidColor.blue(src)
            val lum = luminance(sr, sg, sb)
            val factor = (lum / targetLum).coerceIn(MIN_FACTOR, MAX_FACTOR)
            val nr = (tr * factor).toInt().coerceIn(0, 255)
            val ng = (tg * factor).toInt().coerceIn(0, 255)
            val nb = (tb * factor).toInt().coerceIn(0, 255)
            // Mantém um pouco do highlight original.
            val mixR = (nr * POLISH_WEIGHT + sr * HIGHLIGHT_WEIGHT).toInt().coerceIn(0, 255)
            val mixG = (ng * POLISH_WEIGHT + sg * HIGHLIGHT_WEIGHT).toInt().coerceIn(0, 255)
            val mixB = (nb * POLISH_WEIGHT + sb * HIGHLIGHT_WEIGHT).toInt().coerceIn(0, 255)
            pixels[i] = AndroidColor.argb(255, mixR, mixG, mixB)
        }
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        if (scaledMask !== mask) {
            scaledMask.recycle()
        }
        return out
    }

    private fun luminance(r: Int, g: Int, b: Int): Float =
        0.299f * r + 0.587f * g + 0.114f * b

    private const val MASK_ALPHA_THRESHOLD = 40
    private const val MIN_FACTOR = 0.4f
    private const val MAX_FACTOR = 1.8f
    private const val POLISH_WEIGHT = 0.82f
    private const val HIGHLIGHT_WEIGHT = 0.18f
}
