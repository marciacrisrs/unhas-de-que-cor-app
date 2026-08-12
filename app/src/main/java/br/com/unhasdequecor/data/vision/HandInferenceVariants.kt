package br.com.unhasdequecor.data.vision

import android.graphics.Bitmap
import android.graphics.Matrix
import br.com.unhasdequecor.data.local.hand.OrientedBitmapDecoder
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates
import br.com.unhasdequecor.data.vision.nail.TryOnHandReliability

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
 * (contraluz, pele retinta, espelho, orientação horizontal).
 */
internal object HandInferenceVariants {

    private const val SHADOW_LIFT_GAMMA = 0.65f
    private const val STRONG_SHADOW_GAMMA = 0.50f
    private val ROTATION_DEGREES = floatArrayOf(90f, 270f, 180f)

    fun forSource(source: Bitmap): Sequence<HandInferenceVariant> = sequence {
        yield(HandInferenceVariant(source, source))
        enhance(source, stretch = true)?.let { yield(HandInferenceVariant(it, source)) }
        enhance(source, gamma = SHADOW_LIFT_GAMMA)?.let { yield(HandInferenceVariant(it, source)) }
        enhance(source, stretch = true, gamma = SHADOW_LIFT_GAMMA)?.let {
            yield(HandInferenceVariant(it, source))
        }
        enhance(source, gamma = STRONG_SHADOW_GAMMA)?.let { yield(HandInferenceVariant(it, source)) }
        enhance(source, brightness = 0.28f)?.let { yield(HandInferenceVariant(it, source)) }

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
            enhance(mirrored, stretch = true, gamma = SHADOW_LIFT_GAMMA)?.let {
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
            enhance(rotated, stretch = true, gamma = SHADOW_LIFT_GAMMA)?.let {
                yield(HandInferenceVariant(it, rotated))
            }
            val rotatedMirror = mirrorX(rotated)
            if (rotatedMirror != null) {
                val remap: (ImageCoordinates.NormPoint) -> ImageCoordinates.NormPoint = { p ->
                    ImageCoordinates.NormPoint(
                        x = HandInferenceEnhancer.mirrorXNormalized(p.x),
                        y = p.y,
                    )
                }
                yield(HandInferenceVariant(rotatedMirror, rotated, remap))
            }
        }
    }

    private fun enhance(
        source: Bitmap,
        stretch: Boolean = false,
        gamma: Float? = null,
        brightness: Float? = null,
    ): Bitmap? {
        if (source.width <= 0 || source.height <= 0) return null
        return runCatching {
            val out = source.copy(Bitmap.Config.ARGB_8888, true) ?: return null
            val pixels = IntArray(out.width * out.height)
            out.getPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
            if (stretch) HandInferenceEnhancer.contrastStretchArgb(pixels)
            if (gamma != null) HandInferenceEnhancer.applyGammaArgb(pixels, gamma)
            if (brightness != null) HandInferenceEnhancer.liftBrightnessArgb(pixels, brightness)
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

    /** Presence mínima para aceitar uma variante (alinhada ao piso de confiabilidade). */
    fun isAcceptablePresence(score: Float): Boolean =
        score >= TryOnHandReliability.MIN_PRESENCE_ACCEPT
}
