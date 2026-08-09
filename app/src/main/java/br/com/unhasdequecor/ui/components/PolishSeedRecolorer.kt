package br.com.unhasdequecor.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Recolore pixels parecidos com o esmalte já pintado na foto de exemplo.
 * Coloca a cor nova exatamente onde está o esmalte original (sem âncoras).
 */
object PolishSeedRecolorer {
    fun recolor(source: Bitmap, sampleId: String, polishColor: Color): Bitmap? {
        val seed = SEEDS[sampleId] ?: return null
        if (source.width <= 0 || source.height <= 0) return null
        val out = source.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val target = polishColor.toArgb()
        val tr = AndroidColor.red(target)
        val tg = AndroidColor.green(target)
        val tb = AndroidColor.blue(target)

        val width = out.width
        val height = out.height
        val pixels = IntArray(width * height)
        out.getPixels(pixels, 0, width, 0, 0, width, height)

        var weightSum = 0f
        var lumSum = 0f
        val coverage = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val src = pixels[i]
            val sr = AndroidColor.red(src)
            val sg = AndroidColor.green(src)
            val sb = AndroidColor.blue(src)
            val match = seedMatch(sr, sg, sb, seed)
            coverage[i] = match
            if (match < 0.08f) continue
            val lum = luminance(sr, sg, sb)
            weightSum += match
            lumSum += lum * match
        }
        if (weightSum < 80f) return null
        val meanLum = (lumSum / weightSum).coerceAtLeast(1f)

        for (i in pixels.indices) {
            val match = coverage[i]
            if (match < 0.08f) continue
            val src = pixels[i]
            val sr = AndroidColor.red(src)
            val sg = AndroidColor.green(src)
            val sb = AndroidColor.blue(src)
            val sa = AndroidColor.alpha(src)
            val lum = luminance(sr, sg, sb)
            val shade = (lum / meanLum).coerceIn(0.42f, 1.65f)
            var nr = (tr * shade).toInt().coerceIn(0, 255)
            var ng = (tg * shade).toInt().coerceIn(0, 255)
            var nb = (tb * shade).toInt().coerceIn(0, 255)
            val specular = max(
                ((lum - 188f) / 67f).coerceIn(0f, 1f),
                (((lum / meanLum) - 1.12f) / 0.55f).coerceIn(0f, 1f) * 0.75f,
            )
            if (specular > 0f) {
                nr = mix(nr, 255, specular)
                ng = mix(ng, 255, specular)
                nb = mix(nb, 255, specular)
            }
            val blend = match.coerceIn(0f, 1f)
            pixels[i] = AndroidColor.argb(
                sa,
                mix(sr, nr, blend),
                mix(sg, ng, blend),
                mix(sb, nb, blend),
            )
        }
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    private fun seedMatch(r: Int, g: Int, b: Int, seed: PolishSeed): Float {
        val dist = sqrt(
            ((r - seed.r) * (r - seed.r) + (g - seed.g) * (g - seed.g) + (b - seed.b) * (b - seed.b))
                .toFloat(),
        )
        if (dist > seed.maxRgbDist) return 0f
        val (_, s, v) = rgbToHsv(r, g, b)
        if (s < seed.minSaturation || v < seed.minValue || v > seed.maxValue) return 0f
        val soft = 1f - (dist / seed.maxRgbDist)
        return soft * soft
    }

    private fun rgbToHsv(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val maxC = max(rf, max(gf, bf))
        val minC = min(rf, min(gf, bf))
        val delta = maxC - minC
        val s = if (maxC == 0f) 0f else delta / maxC
        return floatArrayOf(0f, s, maxC)
    }

    private fun mix(from: Int, to: Int, t: Float): Int =
        (from + (to - from) * t).toInt().coerceIn(0, 255)

    private fun luminance(r: Int, g: Int, b: Int): Float =
        0.299f * r + 0.587f * g + 0.114f * b

    private data class PolishSeed(
        val r: Int,
        val g: Int,
        val b: Int,
        val maxRgbDist: Float,
        val minSaturation: Float,
        val minValue: Float,
        val maxValue: Float,
    )

    private val SEEDS = mapOf(
        "clara_vermelho" to PolishSeed(187, 25, 30, 78f, 0.35f, 0.18f, 0.98f),
        "media_rosa" to PolishSeed(158, 82, 101, 72f, 0.14f, 0.22f, 0.90f),
        "morena_nude" to PolishSeed(140, 77, 48, 58f, 0.18f, 0.28f, 0.90f),
        "retinta_vinho" to PolishSeed(62, 10, 12, 68f, 0.22f, 0.06f, 0.55f),
        "morena_clara_coral" to PolishSeed(248, 105, 70, 70f, 0.28f, 0.30f, 0.99f),
    )
}
