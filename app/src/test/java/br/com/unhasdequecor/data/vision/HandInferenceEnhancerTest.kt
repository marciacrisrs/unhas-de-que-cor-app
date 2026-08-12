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
}
