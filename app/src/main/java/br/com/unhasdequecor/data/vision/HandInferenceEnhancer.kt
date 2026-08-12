package br.com.unhasdequecor.data.vision

/**
 * Pré-processamento de pixels para MediaPipe em fotos difíceis
 * (contra-luz, flash/overexposure, mão escura, pouco contraste unha–pele).
 *
 * Operações puras (testáveis sem Bitmap Android).
 */
object HandInferenceEnhancer {

    private const val CHANNEL_MAX = 255
    private const val HIST_BINS = 256
    private const val MIN_STRETCH_SPAN = 8
    private const val MIN_GAMMA = 0.05f
    private const val MIN_EXPOSURE_FACTOR = 0.05f
    private const val HIGHLIGHT_MID = 128
    private const val LUM_R = 77
    private const val LUM_G = 150
    private const val LUM_B = 29
    private const val LUM_SHIFT = 8
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BYTE_MASK = 0xFF

    /** Estica luminância pelos percentis [lowPct, highPct] — recupera mão em silhueta. */
    fun contrastStretchArgb(
        pixels: IntArray,
        lowPct: Float = 0.05f,
        highPct: Float = 0.95f,
    ) {
        require(pixels.isNotEmpty())
        require(lowPct in 0f..<highPct && highPct <= 1f)
        val hist = IntArray(HIST_BINS)
        for (p in pixels) {
            hist[luminance(p)] += 1
        }
        val total = pixels.size
        val lowCount = (total * lowPct).toInt().coerceAtLeast(0)
        val highCount = (total * highPct).toInt().coerceAtMost(total - 1)
        var low = 0
        var high = CHANNEL_MAX
        var seen = 0
        for (i in 0 until HIST_BINS) {
            seen += hist[i]
            if (seen >= lowCount) {
                low = i
                break
            }
        }
        seen = 0
        for (i in 0 until HIST_BINS) {
            seen += hist[i]
            if (seen >= highCount) {
                high = i
                break
            }
        }
        if (high <= low + MIN_STRETCH_SPAN) return
        val scale = CHANNEL_MAX.toFloat() / (high - low).toFloat()
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = p ushr ALPHA_SHIFT and BYTE_MASK
            val r = ((p ushr RED_SHIFT and BYTE_MASK) - low) * scale
            val g = ((p ushr GREEN_SHIFT and BYTE_MASK) - low) * scale
            val b = ((p and BYTE_MASK) - low) * scale
            pixels[i] = (a shl ALPHA_SHIFT) or
                (r.toInt().coerceIn(0, CHANNEL_MAX) shl RED_SHIFT) or
                (g.toInt().coerceIn(0, CHANNEL_MAX) shl GREEN_SHIFT) or
                b.toInt().coerceIn(0, CHANNEL_MAX)
        }
    }

    /** Gamma &lt; 1 clareia sombras (mão em contraluz). */
    fun applyGammaArgb(pixels: IntArray, gamma: Float) {
        require(gamma > MIN_GAMMA)
        val table = IntArray(HIST_BINS) { v ->
            (CHANNEL_MAX.toDouble() * Math.pow(v / CHANNEL_MAX.toDouble(), gamma.toDouble()))
                .toInt()
                .coerceIn(0, CHANNEL_MAX)
        }
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = p ushr ALPHA_SHIFT and BYTE_MASK
            val r = table[p ushr RED_SHIFT and BYTE_MASK]
            val g = table[p ushr GREEN_SHIFT and BYTE_MASK]
            val b = table[p and BYTE_MASK]
            pixels[i] = (a shl ALPHA_SHIFT) or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
        }
    }

    /**
     * Clareia linearmente (pele retinta / flash desigual).
     * [amount] em 0..1: quanto puxar cada canal em direção a 255.
     */
    fun liftBrightnessArgb(pixels: IntArray, amount: Float) {
        require(amount in 0f..1f)
        if (amount == 0f) return
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = p ushr ALPHA_SHIFT and BYTE_MASK
            val r = p ushr RED_SHIFT and BYTE_MASK
            val g = p ushr GREEN_SHIFT and BYTE_MASK
            val b = p and BYTE_MASK
            val nr = (r + (CHANNEL_MAX - r) * amount).toInt().coerceIn(0, CHANNEL_MAX)
            val ng = (g + (CHANNEL_MAX - g) * amount).toInt().coerceIn(0, CHANNEL_MAX)
            val nb = (b + (CHANNEL_MAX - b) * amount).toInt().coerceIn(0, CHANNEL_MAX)
            pixels[i] = (a shl ALPHA_SHIFT) or (nr shl RED_SHIFT) or (ng shl GREEN_SHIFT) or nb
        }
    }

    /**
     * Escurece a cena (flash / overexposure). [factor] em (0, 1]: multiplica canais.
     */
    fun scaleExposureArgb(pixels: IntArray, factor: Float) {
        require(factor in MIN_EXPOSURE_FACTOR..1f)
        if (factor == 1f) return
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = p ushr ALPHA_SHIFT and BYTE_MASK
            val r = ((p ushr RED_SHIFT and BYTE_MASK) * factor).toInt().coerceIn(0, CHANNEL_MAX)
            val g = ((p ushr GREEN_SHIFT and BYTE_MASK) * factor).toInt().coerceIn(0, CHANNEL_MAX)
            val b = ((p and BYTE_MASK) * factor).toInt().coerceIn(0, CHANNEL_MAX)
            pixels[i] = (a shl ALPHA_SHIFT) or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
        }
    }

    /**
     * Comprime highlights: canais acima do meio tom são puxados para baixo.
     * [amount] 0..1 — útil quando o flash estoura a pele e as tips.
     */
    fun compressHighlightsArgb(pixels: IntArray, amount: Float) {
        require(amount in 0f..1f)
        if (amount == 0f) return
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = p ushr ALPHA_SHIFT and BYTE_MASK
            val r = compressHighlightChannel(p ushr RED_SHIFT and BYTE_MASK, amount)
            val g = compressHighlightChannel(p ushr GREEN_SHIFT and BYTE_MASK, amount)
            val b = compressHighlightChannel(p and BYTE_MASK, amount)
            pixels[i] = (a shl ALPHA_SHIFT) or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
        }
    }

    private fun compressHighlightChannel(value: Int, amount: Float): Int {
        if (value <= HIGHLIGHT_MID) return value
        return (value - (value - HIGHLIGHT_MID) * amount).toInt().coerceIn(0, CHANNEL_MAX)
    }

    fun luminance(argb: Int): Int {
        val r = argb ushr RED_SHIFT and BYTE_MASK
        val g = argb ushr GREEN_SHIFT and BYTE_MASK
        val b = argb and BYTE_MASK
        return ((LUM_R * r + LUM_G * g + LUM_B * b) shr LUM_SHIFT).coerceIn(0, CHANNEL_MAX)
    }

    fun mirrorXNormalized(x: Float): Float = (1f - x).coerceIn(0f, 1f)
}
