package br.com.unhasdequecor.ui.components

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
}
