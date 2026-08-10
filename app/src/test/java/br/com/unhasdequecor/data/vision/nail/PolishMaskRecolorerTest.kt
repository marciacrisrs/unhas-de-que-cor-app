package br.com.unhasdequecor.data.vision.nail

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

class PolishMaskRecolorerTest {

    @Test
    fun `polishPixel tints mid-tone source toward target polish`() {
        val src = PolishMaskRecolorer.packArgb(255, 120, 100, 90)
        val out = PolishMaskRecolorer.polishPixel(
            src = src,
            coverage = 1f,
            tr = 200,
            tg = 40,
            tb = 80,
            meanNailLum = 110f,
        )
        assertThat(PolishMaskRecolorer.channelRed(out))
            .isGreaterThan(PolishMaskRecolorer.channelGreen(out))
        assertThat(PolishMaskRecolorer.channelRed(out))
            .isGreaterThan(PolishMaskRecolorer.channelBlue(out))
        assertThat(PolishMaskRecolorer.channelAlpha(out)).isEqualTo(255)
    }

    @Test
    fun `polishPixel with low coverage stays closer to source`() {
        val src = PolishMaskRecolorer.packArgb(255, 10, 20, 30)
        val full = PolishMaskRecolorer.polishPixel(src, 1f, 220, 30, 60, 50f)
        val soft = PolishMaskRecolorer.polishPixel(src, 0.15f, 220, 30, 60, 50f)
        val fullDelta = abs(PolishMaskRecolorer.channelRed(full) - 10)
        val softDelta = abs(PolishMaskRecolorer.channelRed(soft) - 10)
        assertThat(softDelta).isLessThan(fullDelta)
    }

    @Test
    fun `polishPixel specular highlight pushes channels toward white`() {
        val dark = PolishMaskRecolorer.polishPixel(
            src = PolishMaskRecolorer.packArgb(255, 80, 70, 60),
            coverage = 1f,
            tr = 180,
            tg = 40,
            tb = 70,
            meanNailLum = 100f,
        )
        val bright = PolishMaskRecolorer.polishPixel(
            src = PolishMaskRecolorer.packArgb(255, 240, 235, 230),
            coverage = 1f,
            tr = 180,
            tg = 40,
            tb = 70,
            meanNailLum = 100f,
        )
        // Highlight absoluto (luma alta) deve clarear canais vs tom médio.
        assertThat(PolishMaskRecolorer.channelRed(bright))
            .isAtLeast(PolishMaskRecolorer.channelRed(dark))
        assertThat(PolishMaskRecolorer.channelGreen(bright))
            .isGreaterThan(PolishMaskRecolorer.channelGreen(dark))
    }

    @Test
    fun `maskCoverage uses min of alpha and gray`() {
        val opaqueBlack = PolishMaskRecolorer.packArgb(255, 0, 0, 0)
        val opaqueWhite = PolishMaskRecolorer.packArgb(255, 255, 255, 255)
        val softGray = PolishMaskRecolorer.packArgb(255, 128, 128, 128)
        assertThat(PolishMaskRecolorer.maskCoverage(opaqueBlack)).isEqualTo(0f)
        assertThat(PolishMaskRecolorer.maskCoverage(opaqueWhite)).isEqualTo(1f)
        assertThat(PolishMaskRecolorer.maskCoverage(softGray)).isWithin(0.01f).of(128f / 255f)
    }

    @Test
    fun `meanNailLuminance returns null when mask is empty`() {
        val pixels = IntArray(100) { PolishMaskRecolorer.packArgb(255, 100, 100, 100) }
        val mask = IntArray(100) { PolishMaskRecolorer.packArgb(255, 0, 0, 0) }
        assertThat(PolishMaskRecolorer.meanNailLuminance(pixels, mask)).isNull()
    }

    @Test
    fun `meanNailLuminance returns null when mask covers too much of the image`() {
        // 25/100 = 25% > MAX_MASK_COVERAGE_RATIO (18%)
        val pixels = IntArray(100) { PolishMaskRecolorer.packArgb(255, 120, 110, 100) }
        val mask = IntArray(100) { i ->
            if (i < 25) {
                PolishMaskRecolorer.packArgb(255, 255, 255, 255)
            } else {
                PolishMaskRecolorer.packArgb(255, 0, 0, 0)
            }
        }
        assertThat(PolishMaskRecolorer.meanNailLuminance(pixels, mask)).isNull()
    }

    @Test
    fun `meanNailLuminance returns weighted mean for small nail region`() {
        val pixels = IntArray(100) { PolishMaskRecolorer.packArgb(255, 100, 100, 100) }
        val mask = IntArray(100) { i ->
            if (i < 10) {
                PolishMaskRecolorer.packArgb(255, 255, 255, 255)
            } else {
                PolishMaskRecolorer.packArgb(255, 0, 0, 0)
            }
        }
        val mean = PolishMaskRecolorer.meanNailLuminance(pixels, mask)
        assertThat(mean).isNotNull()
        assertThat(mean!!).isWithin(1f).of(100f)
    }

    @Test
    fun `applyPolish tints only covered pixels`() {
        val base = PolishMaskRecolorer.packArgb(255, 90, 90, 90)
        val pixels = IntArray(20) { base }
        val mask = IntArray(20) { i ->
            if (i < 3) {
                PolishMaskRecolorer.packArgb(255, 255, 255, 255)
            } else {
                PolishMaskRecolorer.packArgb(255, 0, 0, 0)
            }
        }
        PolishMaskRecolorer.applyPolish(
            pixels = pixels,
            maskPixels = mask,
            tr = 200,
            tg = 30,
            tb = 60,
            meanNailLum = 90f,
        )
        assertThat(pixels[0]).isNotEqualTo(base)
        assertThat(PolishMaskRecolorer.channelRed(pixels[0]))
            .isGreaterThan(PolishMaskRecolorer.channelGreen(pixels[0]))
        assertThat(pixels[10]).isEqualTo(base)
    }
}
