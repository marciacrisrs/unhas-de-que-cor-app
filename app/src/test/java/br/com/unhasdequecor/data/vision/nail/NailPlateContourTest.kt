package br.com.unhasdequecor.data.vision.nail

import com.google.common.truth.Truth.assertThat
import kotlin.math.hypot
import org.junit.Test

class NailPlateContourTest {
    @Test
    fun build_hasDenseRoundedFreeEdgeWithTipLandmarkAsOutermostPoint() {
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

        val ux = plate.ux
        val uy = plate.uy
        val tipProjection = contour.maxOf { (it.x - plate.tipX) * ux + (it.y - plate.tipY) * uy }
        assertThat(tipProjection).isAtMost(0.001f)

        val center = contour.minBy { hypot((it.x - plate.tipX).toDouble(), (it.y - plate.tipY).toDouble()) }
        assertThat(center.x).isWithin(0.5f).of(plate.tipX)
        assertThat(center.y).isWithin(0.5f).of(plate.tipY)
    }

    @Test
    fun build_shortPlate_hasLessPointedTipThanLongPlate() {
        val shortPlate = NailPlateCalibration.plateFromPixels(
            finger = Finger.INDEX,
            tipX = 100f,
            tipY = 50f,
            dipX = 100f,
            dipY = 80f,
            pipX = 100f,
            pipY = 120f,
            mcpX = 100f,
            mcpY = 180f,
        )
        val longPlate = NailPlateCalibration.plateFromPixels(
            finger = Finger.INDEX,
            tipX = 100f,
            tipY = 20f,
            dipX = 100f,
            dipY = 80f,
            pipX = 100f,
            pipY = 140f,
            mcpX = 100f,
            mcpY = 220f,
        )
        val shortContour = NailPlateContour.build(shortPlate)
        val longContour = NailPlateContour.build(longPlate)
        val shortTipWidth = shortContour.maxOf { kotlin.math.abs((it.x - shortPlate.tipX) * -shortPlate.uy + (it.y - shortPlate.tipY) * shortPlate.ux) }
        val longTipWidth = longContour.maxOf { kotlin.math.abs((it.x - longPlate.tipX) * -longPlate.uy + (it.y - longPlate.tipY) * longPlate.ux) }
        assertThat(shortTipWidth).isGreaterThan(longTipWidth * 0.9f)
    }
}
