package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NailPlateContourTest {
    @Test
    fun densify_roundsFreeEdgeWithoutPassingTip() {
        val polygon = listOf(
            PixelPoint(94f, 20f),
            PixelPoint(92f, 45f),
            PixelPoint(93f, 60f),
            PixelPoint(107f, 60f),
            PixelPoint(108f, 45f),
            PixelPoint(106f, 20f),
        )
        val contour = NailPlateContour.densify(polygon)
        val tip = PixelPoint(100f, 20f)
        assertThat(contour.size).isAtLeast(50)
        assertThat(contour.maxOf { tip.y - it.y }).isAtMost(0.001f)
        assertThat(contour.any { it.x == tip.x && it.y == tip.y }).isTrue()
    }

    @Test
    fun densify_keepsAnatomicalSixPointPolygonUnchangedWhenNotAlmond() {
        val polygon = listOf(
            PixelPoint(0f, 0f), PixelPoint(1f, 0f), PixelPoint(2f, 0f),
            PixelPoint(2f, 1f), PixelPoint(1f, 1f), PixelPoint(0f, 1f),
        )
        assertThat(NailPlateContour.densify(polygon)).isNotEmpty()
    }
}
