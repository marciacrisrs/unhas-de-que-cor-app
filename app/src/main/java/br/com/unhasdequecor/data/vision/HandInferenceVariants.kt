package br.com.unhasdequecor.data.vision

import android.graphics.Bitmap
import android.graphics.Matrix
import br.com.unhasdequecor.data.local.hand.OrientedBitmapDecoder
import br.com.unhasdequecor.data.vision.nail.DetectionConfidenceFloor
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
 * (contraluz, **flash/overexposure**, pele retinta, espelho, orientação).
 *
 * Ordem: raw → flash (se cena estourada) → **lift pele retinta/subexposta** →
 * stretch → contraluz → espelho → rotação.
 */
internal object HandInferenceVariants {

    private const val SHADOW_LIFT_GAMMA = 0.65f
    private const val STRONG_SHADOW_GAMMA = 0.50f
    /** Gamma &gt; 1 recupera midtones estourados pelo flash. */
    private const val FLASH_GAMMA_MILD = 1.45f
    private const val FLASH_EXPOSURE = 0.70f
    private const val FLASH_HIGHLIGHT_COMPRESS = 0.60f
    /** Só gasta variantes de flash se ≥ este share de highlights. */
    const val FLASH_HIGHLIGHT_SHARE_MIN = 0.12f
    /**
     * Faixa de luminância média onde pele retinta bem iluminada ainda
     * precisa de lift (não é noite total).
     */
    const val DEEP_SKIN_LUMA_MIN = 35f
    const val DEEP_SKIN_LUMA_MAX = 145f
    private const val DEEP_SKIN_BRIGHTNESS = 0.36f
    private const val DEEP_SKIN_GAMMA = 0.55f
    private const val DEEP_SKIN_MILD_BRIGHTNESS = 0.22f
    private const val EMPTY_SCENE_MEAN_LUMA = 128f
    private const val SAMPLE_STEP = 17
    private val ROTATION_DEGREES = floatArrayOf(90f, 270f, 180f)

    fun forSource(source: Bitmap): Sequence<HandInferenceVariant> = sequence {
        yield(HandInferenceVariant(source, source))
        if (shouldTryFlashRecovery(source)) {
            yieldAll(flashRecovery(source, display = source))
        }
        if (shouldTryDeepSkinLift(source)) {
            yieldAll(deepSkinLift(source, display = source))
        }
        yieldAll(baseEnhancements(source))
        yieldAll(shadowRecovery(source, display = source))
        yieldAll(mirroredVariants(source))
        yieldAll(rotatedVariants(source))
    }

    /** Cena com highlights estourados (flash / overexposure). */
    fun shouldTryFlashRecovery(source: Bitmap): Boolean {
        if (source.width <= 0 || source.height <= 0) return false
        return runCatching {
            sceneHighlightShare(source) >= FLASH_HIGHLIGHT_SHARE_MIN
        }.getOrDefault(false)
    }

    /**
     * Pele retinta / subexposição moderada: lift cedo (antes só do stretch genérico),
     * sem confundir com flash.
     */
    fun shouldTryDeepSkinLift(source: Bitmap): Boolean {
        if (source.width <= 0 || source.height <= 0) return false
        return runCatching {
            val share = sceneHighlightShare(source)
            if (share >= FLASH_HIGHLIGHT_SHARE_MIN) return@runCatching false
            val mean = sceneMeanLuminance(source)
            mean in DEEP_SKIN_LUMA_MIN..DEEP_SKIN_LUMA_MAX
        }.getOrDefault(false)
    }

    private fun deepSkinLift(
        inferenceBase: Bitmap,
        display: Bitmap,
        remap: (ImageCoordinates.NormPoint) -> ImageCoordinates.NormPoint = { it },
    ): Sequence<HandInferenceVariant> = sequence {
        enhance(inferenceBase, brightness = DEEP_SKIN_BRIGHTNESS)?.let {
            yield(HandInferenceVariant(it, display, remap))
        }
        enhance(inferenceBase, stretch = true, brightness = DEEP_SKIN_BRIGHTNESS)?.let {
            yield(HandInferenceVariant(it, display, remap))
        }
        enhance(inferenceBase, gamma = DEEP_SKIN_GAMMA, brightness = DEEP_SKIN_MILD_BRIGHTNESS)?.let {
            yield(HandInferenceVariant(it, display, remap))
        }
    }

    private fun sceneHighlightShare(source: Bitmap): Float {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        return HandInferenceEnhancer.highlightShareArgb(
            pixels = pixels,
            threshold = HandInferenceEnhancer.HIGHLIGHT_GATE_LUM,
            sampleStep = SAMPLE_STEP,
        )
    }

    private fun sceneMeanLuminance(source: Bitmap): Float {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        var sum = 0L
        var n = 0
        var i = 0
        while (i < pixels.size) {
            sum += HandInferenceEnhancer.luminance(pixels[i])
            n += 1
            i += SAMPLE_STEP
        }
        return if (n == 0) EMPTY_SCENE_MEAN_LUMA else sum.toFloat() / n.toFloat()
    }

    private fun baseEnhancements(source: Bitmap): Sequence<HandInferenceVariant> = sequence {
        enhance(source, stretch = true)?.let { yield(HandInferenceVariant(it, source)) }
    }

    private fun flashRecovery(
        inferenceBase: Bitmap,
        display: Bitmap,
        remap: (ImageCoordinates.NormPoint) -> ImageCoordinates.NormPoint = { it },
    ): Sequence<HandInferenceVariant> = sequence {
        // Receitas enxutas: gamma, exposure, highlights+stretch.
        enhance(inferenceBase, gamma = FLASH_GAMMA_MILD)?.let {
            yield(HandInferenceVariant(it, display, remap))
        }
        enhance(inferenceBase, exposure = FLASH_EXPOSURE)?.let {
            yield(HandInferenceVariant(it, display, remap))
        }
        enhance(inferenceBase, highlights = FLASH_HIGHLIGHT_COMPRESS, stretch = true)?.let {
            yield(HandInferenceVariant(it, display, remap))
        }
    }

    private fun shadowRecovery(
        inferenceBase: Bitmap,
        display: Bitmap,
        remap: (ImageCoordinates.NormPoint) -> ImageCoordinates.NormPoint = { it },
    ): Sequence<HandInferenceVariant> = sequence {
        enhance(inferenceBase, gamma = SHADOW_LIFT_GAMMA)?.let {
            yield(HandInferenceVariant(it, display, remap))
        }
        enhance(inferenceBase, stretch = true, gamma = SHADOW_LIFT_GAMMA)?.let {
            yield(HandInferenceVariant(it, display, remap))
        }
        enhance(inferenceBase, gamma = STRONG_SHADOW_GAMMA)?.let {
            yield(HandInferenceVariant(it, display, remap))
        }
        enhance(inferenceBase, brightness = 0.28f)?.let {
            yield(HandInferenceVariant(it, display, remap))
        }
    }

    private fun mirroredVariants(source: Bitmap): Sequence<HandInferenceVariant> = sequence {
        val mirrored = mirrorX(source) ?: return@sequence
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
        if (shouldTryFlashRecovery(source)) {
            yieldAll(flashRecovery(mirrored, display = source, remap = remap).take(2))
        }
        enhance(mirrored, stretch = true, gamma = SHADOW_LIFT_GAMMA)?.let {
            yield(HandInferenceVariant(it, source, remap))
        }
    }

    private fun rotatedVariants(source: Bitmap): Sequence<HandInferenceVariant> = sequence {
        for (degrees in ROTATION_DEGREES) {
            yieldAll(variantsForRotation(source, degrees))
        }
    }

    private fun variantsForRotation(
        source: Bitmap,
        degrees: Float,
    ): Sequence<HandInferenceVariant> = sequence {
        val rotated = OrientedBitmapDecoder.rotate(source, degrees)
        if (rotated === source) return@sequence
        yield(HandInferenceVariant(rotated, rotated))
        enhance(rotated, stretch = true)?.let {
            yield(HandInferenceVariant(it, rotated))
        }
        if (shouldTryFlashRecovery(source)) {
            yieldAll(flashRecovery(rotated, display = rotated).take(2))
        }
        enhance(rotated, stretch = true, gamma = SHADOW_LIFT_GAMMA)?.let {
            yield(HandInferenceVariant(it, rotated))
        }
        val rotatedMirror = mirrorX(rotated) ?: return@sequence
        val remap: (ImageCoordinates.NormPoint) -> ImageCoordinates.NormPoint = { p ->
            ImageCoordinates.NormPoint(
                x = HandInferenceEnhancer.mirrorXNormalized(p.x),
                y = p.y,
            )
        }
        yield(HandInferenceVariant(rotatedMirror, rotated, remap))
    }

    private fun enhance(
        source: Bitmap,
        stretch: Boolean = false,
        gamma: Float? = null,
        brightness: Float? = null,
        exposure: Float? = null,
        highlights: Float? = null,
    ): Bitmap? {
        if (source.width <= 0 || source.height <= 0) return null
        return runCatching {
            val out = source.copy(Bitmap.Config.ARGB_8888, true) ?: return null
            val pixels = IntArray(out.width * out.height)
            out.getPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
            if (exposure != null) HandInferenceEnhancer.scaleExposureArgb(pixels, exposure)
            if (highlights != null) HandInferenceEnhancer.compressHighlightsArgb(pixels, highlights)
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
        DetectionConfidenceFloor.acceptsHandPresence(score)
}
