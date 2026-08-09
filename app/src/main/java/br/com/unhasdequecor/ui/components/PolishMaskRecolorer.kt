package br.com.unhasdequecor.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow

/**
 * Recolore a região da máscara de unha como esmalte:
 * - usa alpha da máscara (bordas suaves)
 * - preserva sombreamento da foto (luminância)
 * - preserva brilho especular
 * - não mistura o tom do esmalte original (evita “lama”)
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
        val targetLum = luminance(tr, tg, tb).coerceAtLeast(8f)

        val width = out.width
        val height = out.height
        val pixels = IntArray(width * height)
        val maskPixels = IntArray(width * height)
        out.getPixels(pixels, 0, width, 0, 0, width, height)
        scaledMask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        var maskWeightSum = 0f
        var maskedLumSum = 0f
        for (i in pixels.indices) {
            val coverage = maskCoverage(maskPixels[i])
            if (coverage < 0.02f) continue
            val src = pixels[i]
            val lum = luminance(
                AndroidColor.red(src),
                AndroidColor.green(src),
                AndroidColor.blue(src),
            )
            maskWeightSum += coverage
            maskedLumSum += lum * coverage
        }
        val meanNailLum = if (maskWeightSum > 0f) {
            (maskedLumSum / maskWeightSum).coerceAtLeast(1f)
        } else {
            targetLum
        }

        for (i in pixels.indices) {
            val coverage = maskCoverage(maskPixels[i])
            if (coverage < 0.02f) continue

            val src = pixels[i]
            val sr = AndroidColor.red(src)
            val sg = AndroidColor.green(src)
            val sb = AndroidColor.blue(src)
            val sa = AndroidColor.alpha(src)
            val lum = luminance(sr, sg, sb)

            // Sombreamento relativo ao brilho médio da unha na foto.
            val shade = (lum / meanNailLum).coerceIn(MIN_SHADE, MAX_SHADE)
            var nr = (tr * shade).toInt().coerceIn(0, 255)
            var ng = (tg * shade).toInt().coerceIn(0, 255)
            var nb = (tb * shade).toInt().coerceIn(0, 255)

            // Specular: pixels claros viram brilho de esmalte (quase branco).
            val specular = specularAmount(lum, meanNailLum)
            if (specular > 0f) {
                nr = mixChannel(nr, 255, specular)
                ng = mixChannel(ng, 255, specular)
                nb = mixChannel(nb, 255, specular)
            }

            // Leve saturação do tom alvo para parecer camada de esmalte.
            val vivid = vividize(nr, ng, nb, tr, tg, tb, 0.18f)
            nr = vivid[0]
            ng = vivid[1]
            nb = vivid[2]

            val blend = coverage.pow(0.85f).coerceIn(0f, 1f)
            val outR = mixChannel(sr, nr, blend)
            val outG = mixChannel(sg, ng, blend)
            val outB = mixChannel(sb, nb, blend)
            pixels[i] = AndroidColor.argb(sa, outR, outG, outB)
        }
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        if (scaledMask !== mask) {
            scaledMask.recycle()
        }
        return out
    }

    private fun maskCoverage(maskPixel: Int): Float {
        val alpha = AndroidColor.alpha(maskPixel)
        val gray = AndroidColor.red(maskPixel)
            .coerceAtLeast(AndroidColor.green(maskPixel))
            .coerceAtLeast(AndroidColor.blue(maskPixel))
        val strength = maxOf(alpha, gray)
        return (strength / 255f).coerceIn(0f, 1f)
    }

    private fun specularAmount(lum: Float, meanNailLum: Float): Float {
        val absolute = ((lum - SPECULAR_LUMA_START) / SPECULAR_LUMA_RANGE).coerceIn(0f, 1f)
        val relative = (((lum / meanNailLum) - 1.12f) / 0.55f).coerceIn(0f, 1f)
        return maxOf(absolute, relative * 0.75f)
    }

    private fun vividize(
        r: Int,
        g: Int,
        b: Int,
        tr: Int,
        tg: Int,
        tb: Int,
        amount: Float,
    ): IntArray {
        val mixedR = mixChannel(r, tr, amount * 0.35f)
        val mixedG = mixChannel(g, tg, amount * 0.35f)
        val mixedB = mixChannel(b, tb, amount * 0.35f)
        return intArrayOf(mixedR, mixedG, mixedB)
    }

    private fun mixChannel(from: Int, to: Int, t: Float): Int =
        (from + (to - from) * t).toInt().coerceIn(0, 255)

    private fun luminance(r: Int, g: Int, b: Int): Float =
        0.299f * r + 0.587f * g + 0.114f * b

    private const val MIN_SHADE = 0.42f
    private const val MAX_SHADE = 1.65f
    private const val SPECULAR_LUMA_START = 188f
    private const val SPECULAR_LUMA_RANGE = 67f
}
