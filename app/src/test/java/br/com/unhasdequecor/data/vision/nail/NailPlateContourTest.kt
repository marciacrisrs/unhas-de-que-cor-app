package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import com.google.common.truth.Truth.assertThat
import kotlin.math.hypot
import org.junit.Test

class NailPlateContourTest {
    @Test
    fun build_createsWideRoundedPlateWithTipAtLandmark() {
        val plate = NailPlateCalibration.plateFromPixels(
            finger = Finger.INDEX,
            tipX = 100f,
            tipY = 20f,
            dipX = 100f,
            dipY = 70f,
            pipX = 100f,
            pipY = 110f,
            mcpX = 100f,
            mcpY = 180f,
        )
        val contour = NailPlateContour.build(plate)
        assertThat(contour.size).isAtLeast(30)

        val tipDistance = contour.minOf {
            hypot((it.x - plate.tipX).toDouble(), (it.y - plate.tipY).toDouble())
        }
        assertThat(tipDistance).isWithin(0.01).of(0.0)

        val tipBand = contour.filter { it.y <= plate.tipY + 4f }
        val tipWidth = tipBand.maxOf {
            kotlin.math.abs((it.x - plate.tipX) * -plate.uy + (it.y - plate.tipY) * plate.ux)
        }
        assertThat(tipWidth).isGreaterThan(plate.widthPx * 0.25f)
    }

    @Test
    fun densify_roundsLegacySixPointContour() {
        val polygon = listOf(
            PixelPoint(94f, 20f),
            PixelPoint(92f, 45f),
            PixelPoint(93f, 60f),
            PixelPoint(107f, 60f),
            PixelPoint(108f, 45f),
            PixelPoint(106f, 20f),
        )
        val contour = NailPlateContour.densify(polygon)
        assertThat(contour.size).isAtLeast(30)
        assertThat(contour.minOf { it.y }).isAtLeast(20f)
    }
}
