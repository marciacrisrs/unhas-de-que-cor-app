package br.com.unhasdequecor.data.vision

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HandInferenceEnhancerTest {

    @Test
    fun `contrast stretch expands dark range toward full scale`() {
        // Quase tudo em tons médios-escuros (simula contraluz).
        val pixels = IntArray(100) { i ->
            val v = 40 + (i % 20)
            0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
        }
        val beforeSpan = pixels.maxOf { HandInferenceEnhancer.luminance(it) } -
            pixels.minOf { HandInferenceEnhancer.luminance(it) }
        HandInferenceEnhancer.contrastStretchArgb(pixels)
        val afterSpan = pixels.maxOf { HandInferenceEnhancer.luminance(it) } -
            pixels.minOf { HandInferenceEnhancer.luminance(it) }
        assertThat(afterSpan).isGreaterThan(beforeSpan)
        assertThat(pixels.maxOf { HandInferenceEnhancer.luminance(it) }).isAtLeast(200)
    }

    @Test
    fun `gamma below one brightens midtones`() {
        val mid = 0xFF808080.toInt()
        val pixels = intArrayOf(mid, mid, mid, mid)
        HandInferenceEnhancer.applyGammaArgb(pixels, gamma = 0.65f)
        assertThat(HandInferenceEnhancer.luminance(pixels[0])).isGreaterThan(0x80)
    }

    @Test
    fun `contrast stretch is no-op when luminance almost flat`() {
        val pixels = IntArray(32) { 0xFF646464.toInt() }
        val before = pixels.copyOf()
        HandInferenceEnhancer.contrastStretchArgb(pixels)
        assertThat(pixels.toList()).isEqualTo(before.toList())
    }

    @Test
    fun `luminance is within channel bounds`() {
        assertThat(HandInferenceEnhancer.luminance(0xFF000000.toInt())).isEqualTo(0)
        assertThat(HandInferenceEnhancer.luminance(0xFFFFFFFF.toInt())).isEqualTo(255)
    }

    @Test
    fun `mirrorXNormalized flips horizontal coordinate`() {
        assertThat(HandInferenceEnhancer.mirrorXNormalized(0.25f)).isWithin(0.001f).of(0.75f)
        assertThat(HandInferenceEnhancer.mirrorXNormalized(0f)).isWithin(0.001f).of(1f)
        assertThat(HandInferenceEnhancer.mirrorXNormalized(1f)).isWithin(0.001f).of(0f)
    }

    @Test
    fun `liftBrightness brightens dark pixels toward white`() {
        val dark = 0xFF202020.toInt()
        val pixels = intArrayOf(dark, dark, dark, dark)
        HandInferenceEnhancer.liftBrightnessArgb(pixels, amount = 0.28f)
        assertThat(HandInferenceEnhancer.luminance(pixels[0])).isGreaterThan(0x20)
        assertThat(HandInferenceEnhancer.luminance(pixels[0])).isLessThan(0xFF)
    }

    @Test
    fun `liftBrightness zero amount is no-op`() {
        val pixels = intArrayOf(0xFF404040.toInt())
        val before = pixels[0]
        HandInferenceEnhancer.liftBrightnessArgb(pixels, amount = 0f)
        assertThat(pixels[0]).isEqualTo(before)
    }

    @Test
    fun `gamma above one darkens midtones for flash recovery`() {
        val mid = 0xFFC0C0C0.toInt()
        val pixels = intArrayOf(mid, mid, mid, mid)
        HandInferenceEnhancer.applyGammaArgb(pixels, gamma = 1.45f)
        assertThat(HandInferenceEnhancer.luminance(pixels[0])).isLessThan(0xC0)
    }

    @Test
    fun `scaleExposure darkens all channels`() {
        val bright = 0xFFE0E0E0.toInt()
        val pixels = intArrayOf(bright, bright)
        HandInferenceEnhancer.scaleExposureArgb(pixels, factor = 0.70f)
        assertThat(HandInferenceEnhancer.luminance(pixels[0])).isLessThan(0xE0)
        assertThat(HandInferenceEnhancer.luminance(pixels[0])).isGreaterThan(0x80)
    }

    @Test
    fun `compressHighlights_pullsOnlyBrightChannels`() {
        val dark = 0xFF404040.toInt()
        val bright = 0xFFF0F0F0.toInt()
        val pixels = intArrayOf(dark, bright)
        HandInferenceEnhancer.compressHighlightsArgb(pixels, amount = 0.60f)
        assertThat(pixels[0]).isEqualTo(dark)
        assertThat(HandInferenceEnhancer.luminance(pixels[1])).isLessThan(0xF0)
        assertThat(HandInferenceEnhancer.luminance(pixels[1])).isGreaterThan(0x80)
    }

    @Test
    fun `scaleExposure factor one is no-op`() {
        val pixels = intArrayOf(0xFFE0E0E0.toInt())
        val before = pixels[0]
        HandInferenceEnhancer.scaleExposureArgb(pixels, factor = 1f)
        assertThat(pixels[0]).isEqualTo(before)
    }

    @Test
    fun `compressHighlights zero amount is no-op`() {
        val pixels = intArrayOf(0xFFF0F0F0.toInt())
        val before = pixels[0]
        HandInferenceEnhancer.compressHighlightsArgb(pixels, amount = 0f)
        assertThat(pixels[0]).isEqualTo(before)
    }

    @Test
    fun `highlightShare detects flashy frames`() {
        val bright = IntArray(100) { 0xFFF0F0F0.toInt() }
        val dark = IntArray(100) { 0xFF202020.toInt() }
        assertThat(
            HandInferenceEnhancer.highlightShareArgb(
                bright,
                threshold = HandInferenceEnhancer.HIGHLIGHT_GATE_LUM,
            ),
        ).isGreaterThan(HandInferenceVariants.FLASH_HIGHLIGHT_SHARE_MIN)
        assertThat(
            HandInferenceEnhancer.highlightShareArgb(
                dark,
                threshold = HandInferenceEnhancer.HIGHLIGHT_GATE_LUM,
            ),
        ).isLessThan(HandInferenceVariants.FLASH_HIGHLIGHT_SHARE_MIN)
    }
}
