package br.com.unhasdequecor.data.vision

import android.graphics.Bitmap
import android.graphics.Matrix
import br.com.unhasdequecor.data.local.hand.OrientedBitmapDecoder
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates

/**
 * Variante de inferência: [inferenceBitmap] vai ao MediaPipe;
 * [displayBitmap] é o frame em que o try-on pinta (landmarks já remapeados).
 */
internal data class HandInferenceVariant(
    val inferenceBitmap: Bitmap,
    val displayBitmap: Bitmap,
    val remapPoint: (ImageCoordinates.NormPoint) -> ImageCoordinates.NormPoint = { it },
)

/**
 * Tentativas extras quando a foto “crua” não detecta mão
 * (contraluz, espelho, orientação).
 */
internal object HandInferenceVariants {

    private const val SHADOW_LIFT_GAMMA = 0.65f
    private val ROTATION_DEGREES = floatArrayOf(90f, 270f, 180f)

    fun forSource(source: Bitmap): Sequence<HandInferenceVariant> = sequence {
        yield(HandInferenceVariant(source, source))
        enhance(source, stretch = true)?.let { yield(HandInferenceVariant(it, source)) }
        enhance(source, gamma = SHADOW_LIFT_GAMMA)?.let { yield(HandInferenceVariant(it, source)) }

        val mirrored = mirrorX(source)
        if (mirrored != null) {
            val remap: (ImageCoordinates.NormPoint) -> ImageCoordinates.NormPoint = { p ->
                ImageCoordinates.NormPoint(
                    x = HandInferenceEnhancer.mirrorXNormalized(p.x),
                    y = p.y,
                )
            }
            yield(HandInferenceVariant(mirrored, source, remap))
            enhance(mirrored, stretch = true)?.let {
                yield(HandInferenceVariant(it, source, remap))
            }
        }

        for (degrees in ROTATION_DEGREES) {
            val rotated = OrientedBitmapDecoder.rotate(source, degrees)
            if (rotated === source) continue
            yield(HandInferenceVariant(rotated, rotated))
            enhance(rotated, stretch = true)?.let {
                yield(HandInferenceVariant(it, rotated))
            }
        }
    }

    private fun enhance(
        source: Bitmap,
        stretch: Boolean = false,
        gamma: Float? = null,
    ): Bitmap? {
        if (source.width <= 0 || source.height <= 0) return null
        return runCatching {
            val out = source.copy(Bitmap.Config.ARGB_8888, true) ?: return null
            val pixels = IntArray(out.width * out.height)
            out.getPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
            if (stretch) HandInferenceEnhancer.contrastStretchArgb(pixels)
            if (gamma != null) HandInferenceEnhancer.applyGammaArgb(pixels, gamma)
            out.setPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
            out
        }.getOrNull()
    }

    private fun mirrorX(source: Bitmap): Bitmap? {
        if (source.width <= 0 || source.height <= 0) return null
        return runCatching {
            val matrix = Matrix().apply { preScale(-1f, 1f) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }.getOrNull()
    }
}
