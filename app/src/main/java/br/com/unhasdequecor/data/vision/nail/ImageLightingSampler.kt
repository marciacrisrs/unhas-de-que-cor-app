package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import br.com.unhasdequecor.data.vision.HandInferenceEnhancer

/**
 * Amostra luminância / highlights sem alocar `IntArray(w*h)`.
 * Usa [Bitmap.getPixel] em grade (step) — adequado a Default dispatcher.
 */
object ImageLightingSampler {
    data class Stats(
        val meanLuminance: Float,
        val highlightShare: Float,
    )

    fun sample(bitmap: Bitmap, step: Int = DEFAULT_STEP): Stats? {
        if (bitmap.isRecycled) return null
        // Mocks JVM sem Config real → pula (não inventa TooDark com getPixel=0).
        runCatching { bitmap.config }.getOrNull() ?: return null
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val stride = step.coerceAtLeast(1)
        return runCatching {
            var sum = 0L
            var bright = 0
            var n = 0
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    val lum = HandInferenceEnhancer.luminance(bitmap.getPixel(x, y))
                    sum += lum
                    if (lum >= HandInferenceEnhancer.HIGHLIGHT_GATE_LUM) bright += 1
                    n += 1
                    x += stride
                }
                y += stride
            }
            when {
                n == 0 -> null
                // getPixel=0 em todos os pontos → mock JVM ou buffer inválido.
                sum == 0L && bright == 0 -> null
                else -> Stats(
                    meanLuminance = sum.toFloat() / n.toFloat(),
                    highlightShare = bright.toFloat() / n.toFloat(),
                )
            }
        }.getOrNull()
    }

    const val DEFAULT_STEP = 16
}
