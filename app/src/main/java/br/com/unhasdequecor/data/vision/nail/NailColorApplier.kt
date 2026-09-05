package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * Aplica cor de esmalte só nos pixels da [NailMask], preservando luminância/brilho.
 * Núcleo de pixel compartilhado com [PolishMaskRecolorer.polishPixel].
 */
@Singleton
class NailColorApplier @Inject constructor() {

    fun apply(
        source: Bitmap,
        nails: List<DetectedNail>,
        polishColor: Color,
    ): Bitmap? {
        if (nails.isEmpty()) return null
        val out = source.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val target = polishColor.toArgb()
        val tr = red(target)
        val tg = green(target)
        val tb = blue(target)

        var weight = 0f
        var lumSum = 0f
        for (nail in nails) {
            if (nail.confidence < MIN_CONFIDENCE) continue
            val window = visibleWindow(out, nail.mask)
            if (window != null) {
                val buf = readWindow(out, window)
                forEachMaskedPixel(nail.mask, window, buf) { _, color, alpha ->
                    val coverage = alpha / FULL_ALPHA
                    lumSum += luminance(red(color), green(color), blue(color)) * coverage
                    weight += coverage
                }
            }
        }
        if (weight < MIN_WEIGHT) {
            if (!out.isRecycled) out.recycle()
            return null
        }
        val meanLum = (lumSum / weight).coerceAtLeast(1f)

        for (nail in nails) {
            if (nail.confidence < MIN_CONFIDENCE) continue
            paintNail(out, nail.mask, tr, tg, tb, meanLum)
        }
        return out
    }

    private fun paintNail(
        out: Bitmap,
        mask: NailMask,
        tr: Int,
        tg: Int,
        tb: Int,
        meanLum: Float,
    ) {
        val window = visibleWindow(out, mask) ?: return
        val buf = readWindow(out, window)
        forEachMaskedPixel(mask, window, buf) { index, color, alpha ->
            buf[index] = transformPixel(
                srcArgb = color,
                maskAlpha = alpha,
                targetR = tr,
                targetG = tg,
                targetB = tb,
                meanLum = meanLum,
            )
        }
        out.setPixels(
            buf,
            0,
            window.width,
            window.left,
            window.top,
            window.width,
            window.height,
        )
    }

    /**
     * O tracker Live pode deslocar a origem da máscara para fora do frame.
     * [Bitmap.getPixels] lança se o retângulo sai dos limites — e o bitmap
     * copiado vazava a cada frame.
     */
    private fun visibleWindow(bitmap: Bitmap, mask: NailMask): VisibleMaskWindow? {
        val left = mask.originX.coerceAtLeast(0)
        val top = mask.originY.coerceAtLeast(0)
        val right = (mask.originX + mask.width).coerceAtMost(bitmap.width)
        val bottom = (mask.originY + mask.height).coerceAtMost(bitmap.height)
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return null
        return VisibleMaskWindow(
            left = left,
            top = top,
            width = width,
            height = height,
            maskOffsetX = left - mask.originX,
            maskOffsetY = top - mask.originY,
        )
    }

    private fun readWindow(bitmap: Bitmap, window: VisibleMaskWindow): IntArray {
        val buf = IntArray(window.pixelCount)
        bitmap.getPixels(
            buf,
            0,
            window.width,
            window.left,
            window.top,
            window.width,
            window.height,
        )
        return buf
    }

    private inline fun forEachMaskedPixel(
        mask: NailMask,
        window: VisibleMaskWindow,
        buf: IntArray,
        action: (index: Int, color: Int, alpha: Int) -> Unit,
    ) {
        for (row in 0 until window.height) {
            val maskRow = (window.maskOffsetY + row) * mask.width + window.maskOffsetX
            val bufRow = row * window.width
            for (col in 0 until window.width) {
                val alpha = mask.alpha[maskRow + col].toInt() and 0xFF
                if (alpha < MIN_MASK_ALPHA) continue
                val index = bufRow + col
                action(index, buf[index], alpha)
            }
        }
    }

    companion object {
        const val MIN_CONFIDENCE = DetectionConfidenceFloor.NAIL_COMBINED_MIN
        private const val MIN_MASK_ALPHA = 16
        private const val MIN_WEIGHT = 8f
        private const val FULL_ALPHA = 255f
        private const val SHADE_MIN = 0.42f
        private const val SHADE_MAX = 1.65f
        private const val SPECULAR_LUMA_START = 188f
        private const val SPECULAR_LUMA_RANGE = 67f
        private const val SPECULAR_REL_START = 1.12f
        private const val SPECULAR_REL_RANGE = 0.55f
        private const val SPECULAR_REL_WEIGHT = 0.75f
        private const val BLEND_GAMMA = 0.85f
        private const val VIVID_AMOUNT = 0.12f
        private const val LUMA_R = 0.299f
        private const val LUMA_G = 0.587f
        private const val LUMA_B = 0.114f

        /**
         * Transformação pura (testável sem Bitmap):
         * maskAlpha baixo → pixel inalterado; caso contrário, recolor com luminância.
         */
        fun transformPixel(
            srcArgb: Int,
            maskAlpha: Int,
            targetR: Int,
            targetG: Int,
            targetB: Int,
            meanLum: Float,
        ): Int {
            if (maskAlpha < MIN_MASK_ALPHA) return srcArgb
            val sr = red(srcArgb)
            val sg = green(srcArgb)
            val sb = blue(srcArgb)
            val sa = alpha(srcArgb)
            val lum = luminance(sr, sg, sb)
            val safeMean = meanLum.coerceAtLeast(1f)
            val shade = (lum / safeMean).coerceIn(SHADE_MIN, SHADE_MAX)
            var nr = (targetR * shade).toInt().coerceIn(0, 255)
            var ng = (targetG * shade).toInt().coerceIn(0, 255)
            var nb = (targetB * shade).toInt().coerceIn(0, 255)
            val specular = maxOf(
                ((lum - SPECULAR_LUMA_START) / SPECULAR_LUMA_RANGE).coerceIn(0f, 1f),
                (((lum / safeMean) - SPECULAR_REL_START) / SPECULAR_REL_RANGE)
                    .coerceIn(0f, 1f) * SPECULAR_REL_WEIGHT,
            )
            if (specular > 0f) {
                nr = mix(nr, 255, specular)
                ng = mix(ng, 255, specular)
                nb = mix(nb, 255, specular)
            }
            // Mesmo “punch” das máscaras de amostra (PolishMaskRecolorer.vividize).
            nr = mix(nr, targetR, VIVID_AMOUNT)
            ng = mix(ng, targetG, VIVID_AMOUNT)
            nb = mix(nb, targetB, VIVID_AMOUNT)
            val blend = (maskAlpha / FULL_ALPHA).pow(BLEND_GAMMA)
            return argb(
                sa,
                mix(sr, nr, blend),
                mix(sg, ng, blend),
                mix(sb, nb, blend),
            )
        }

        private fun red(c: Int): Int = (c shr 16) and 0xFF
        private fun green(c: Int): Int = (c shr 8) and 0xFF
        private fun blue(c: Int): Int = c and 0xFF
        private fun alpha(c: Int): Int = (c ushr 24) and 0xFF
        private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
            (a shl 24) or (r shl 16) or (g shl 8) or b

        private fun mix(from: Int, to: Int, t: Float): Int =
            (from + (to - from) * t).toInt().coerceIn(0, 255)

        private fun luminance(r: Int, g: Int, b: Int): Float =
            LUMA_R * r + LUMA_G * g + LUMA_B * b
    }
}

private data class VisibleMaskWindow(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val maskOffsetX: Int,
    val maskOffsetY: Int,
) {
    val pixelCount: Int get() = width * height
}
