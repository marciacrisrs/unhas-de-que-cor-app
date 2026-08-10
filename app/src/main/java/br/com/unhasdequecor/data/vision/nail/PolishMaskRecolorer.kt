package br.com.unhasdequecor.data.vision.nail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.scale
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
        val scaledMask = scaledMaskOrSelf(mask, source.width, source.height)
        val out = source.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val target = polishColor.toArgb()
        val tr = channelRed(target)
        val tg = channelGreen(target)
        val tb = channelBlue(target)

        val width = out.width
        val height = out.height
        val pixels = IntArray(width * height)
        val maskPixels = IntArray(width * height)
        out.getPixels(pixels, 0, width, 0, 0, width, height)
        scaledMask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        val meanNailLum = meanNailLuminance(pixels, maskPixels) ?: run {
            recycleIfScaled(scaledMask, mask)
            if (!out.isRecycled) out.recycle()
            return null
        }

        applyPolish(pixels, maskPixels, tr, tg, tb, meanNailLum)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        recycleIfScaled(scaledMask, mask)
        return out
    }

    internal fun scaledMaskOrSelf(mask: Bitmap, width: Int, height: Int): Bitmap =
        if (mask.width == width && mask.height == height) {
            mask
        } else {
            mask.scale(width, height, filter = true)
        }

    internal fun recycleIfScaled(scaledMask: Bitmap, original: Bitmap) {
        if (scaledMask !== original) {
            scaledMask.recycle()
        }
    }

    /** Luminância média ponderada; null se máscara vazia ou cobrindo demais a imagem. */
    internal fun meanNailLuminance(pixels: IntArray, maskPixels: IntArray): Float? {
        var maskWeightSum = 0f
        var maskedLumSum = 0f
        var coveredCount = 0
        for (i in pixels.indices) {
            val coverage = maskCoverage(maskPixels[i])
            if (coverage < MIN_COVERAGE) continue
            coveredCount += 1
            val src = pixels[i]
            val lum = luminance(channelRed(src), channelGreen(src), channelBlue(src))
            maskWeightSum += coverage
            maskedLumSum += lum * coverage
        }
        val coverageRatio = coveredCount.toFloat() / pixels.size.toFloat()
        if (maskWeightSum <= 0f || coverageRatio > MAX_MASK_COVERAGE_RATIO) {
            return null
        }
        return (maskedLumSum / maskWeightSum).coerceAtLeast(1f)
    }

    internal fun applyPolish(
        pixels: IntArray,
        maskPixels: IntArray,
        tr: Int,
        tg: Int,
        tb: Int,
        meanNailLum: Float,
    ) {
        for (i in pixels.indices) {
            val coverage = maskCoverage(maskPixels[i])
            if (coverage < MIN_COVERAGE) continue
            pixels[i] = polishPixel(pixels[i], coverage, tr, tg, tb, meanNailLum)
        }
    }

    internal fun polishPixel(
        src: Int,
        coverage: Float,
        tr: Int,
        tg: Int,
        tb: Int,
        meanNailLum: Float,
    ): Int {
        val sr = channelRed(src)
        val sg = channelGreen(src)
        val sb = channelBlue(src)
        val sa = channelAlpha(src)
        val lum = luminance(sr, sg, sb)

        val shade = (lum / meanNailLum).coerceIn(MIN_SHADE, MAX_SHADE)
        var nr = (tr * shade).toInt().coerceIn(0, 255)
        var ng = (tg * shade).toInt().coerceIn(0, 255)
        var nb = (tb * shade).toInt().coerceIn(0, 255)

        val specular = specularAmount(lum, meanNailLum)
        if (specular > 0f) {
            nr = mixChannel(nr, 255, specular)
            ng = mixChannel(ng, 255, specular)
            nb = mixChannel(nb, 255, specular)
        }

        val vivid = vividize(nr, ng, nb, tr, tg, tb, 0.18f)
        nr = vivid[0]
        ng = vivid[1]
        nb = vivid[2]

        val blend = coverage.pow(0.85f).coerceIn(0f, 1f)
        return packArgb(
            sa,
            mixChannel(sr, nr, blend),
            mixChannel(sg, ng, blend),
            mixChannel(sb, nb, blend),
        )
    }

    /** Helpers puros (testáveis sem Robolectric). */
    internal fun packArgb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    internal fun channelAlpha(color: Int): Int = (color ushr 24) and 0xFF
    internal fun channelRed(color: Int): Int = (color shr 16) and 0xFF
    internal fun channelGreen(color: Int): Int = (color shr 8) and 0xFF
    internal fun channelBlue(color: Int): Int = color and 0xFF

    internal fun maskCoverage(maskPixel: Int): Float {
        val alpha = channelAlpha(maskPixel)
        val gray = channelRed(maskPixel)
            .coerceAtLeast(channelGreen(maskPixel))
            .coerceAtLeast(channelBlue(maskPixel))
        // PNG L (cinza): BitmapFactory costuma devolver alpha=255 em TODOS os pixels.
        // Usar max(alpha, gray) pintava a imagem inteira. min() trata preto como 0.
        return (minOf(alpha, gray) / 255f).coerceIn(0f, 1f)
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

    private const val MIN_COVERAGE = 0.08f
    private const val MAX_MASK_COVERAGE_RATIO = 0.18f
    private const val MIN_SHADE = 0.42f
    private const val MAX_SHADE = 1.65f
    private const val SPECULAR_LUMA_START = 188f
    private const val SPECULAR_LUMA_RANGE = 67f
}
