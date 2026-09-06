package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import kotlin.math.hypot

/** Builds a smooth, rounded plate contour from calibrated anatomical anchors. */
object NailPlateContour {
    fun build(plate: NailPlateCalibration.PlateGeometry): List<PixelPoint> {
        val e = NailPlateCalibration.almondExtents(plate)
        val tip = PixelPoint(e.tipX, e.tipY)
        val cuticle = PixelPoint(e.cuticleX, e.cuticleY)
        val rightTip = offset(e.tipX, e.tipY, e.px, e.py, e.tipHalfW)
        val rightMid = offset(e.midX, e.midY, e.px, e.py, e.midHalfW)
        val rightCuticle = offset(e.cuticleX, e.cuticleY, e.px, e.py, e.cuticleHalfW)
        val leftCuticle = offset(e.cuticleX, e.cuticleY, e.px, e.py, -e.cuticleHalfW)
        val leftMid = offset(e.midX, e.midY, e.px, e.py, -e.midHalfW)
        val leftTip = offset(e.tipX, e.tipY, e.px, e.py, -e.tipHalfW)
        val contour = ArrayList<PixelPoint>(SIDE_SAMPLES * 2 + CAP_SAMPLES + CUTICLE_SAMPLES)
        addQuadratic(contour, rightTip, tip, leftTip, CAP_SAMPLES)
        addQuadratic(contour, leftTip, leftMid, leftCuticle, SIDE_SAMPLES)
        addQuadratic(contour, leftCuticle, cuticle, rightCuticle, CUTICLE_SAMPLES)
        addQuadratic(contour, rightCuticle, rightMid, rightTip, SIDE_SAMPLES)
        return contour
    }

    /** Legacy six-point contour smoother retained for geometry safety checks. */
    fun densify(polygon: List<PixelPoint>): List<PixelPoint> {
        if (polygon.size != SIX_POINTS) return polygon
        val tipCenter = midpoint(polygon[0], polygon[5])
        val cuticleCenter = midpoint(polygon[2], polygon[3])
        val axisX = tipCenter.x - cuticleCenter.x
        val axisY = tipCenter.y - cuticleCenter.y
        val axisLength = hypot(axisX.toDouble(), axisY.toDouble()).toFloat().coerceAtLeast(1f)
        val ux = axisX / axisLength
        val uy = axisY / axisLength
        val cuticleControl = PixelPoint(
            cuticleCenter.x + ux * axisLength * CUTICLE_INSET,
            cuticleCenter.y + uy * axisLength * CUTICLE_INSET,
        )
        val result = ArrayList<PixelPoint>(SIDE_SAMPLES * 2 + CAP_SAMPLES + CUTICLE_SAMPLES)
        addLine(result, polygon[0], polygon[1], SIDE_SAMPLES)
        addLine(result, polygon[1], polygon[2], SIDE_SAMPLES)
        addQuadratic(result, polygon[2], cuticleControl, polygon[3], CUTICLE_SAMPLES)
        addLine(result, polygon[3], polygon[4], SIDE_SAMPLES)
        addLine(result, polygon[4], polygon[5], SIDE_SAMPLES)
        addQuadratic(result, polygon[5], tipCenter, polygon[0], CAP_SAMPLES)
        return result
    }

    private fun offset(x: Float, y: Float, px: Float, py: Float, distance: Float): PixelPoint =
        PixelPoint(x + px * distance, y + py * distance)

    private fun addQuadratic(out: MutableList<PixelPoint>, from: PixelPoint, control: PixelPoint, to: PixelPoint, samples: Int) {
        for (i in 0 until samples) {
            val t = i.toFloat() / samples
            val u = 1f - t
            out += PixelPoint(
                u * u * from.x + 2f * u * t * control.x + t * t * to.x,
                u * u * from.y + 2f * u * t * control.y + t * t * to.y,
            )
        }
    }

    private fun addLine(out: MutableList<PixelPoint>, from: PixelPoint, to: PixelPoint, samples: Int) {
        for (i in 0 until samples) {
            val t = i.toFloat() / samples
            out += PixelPoint(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
        }
    }

    private fun midpoint(a: PixelPoint, b: PixelPoint): PixelPoint =
        PixelPoint((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)

    private const val SIX_POINTS = 6
    private const val SIDE_SAMPLES = 8
    private const val CAP_SAMPLES = 14
    private const val CUTICLE_SAMPLES = 8
    private const val CUTICLE_INSET = 0.055f
}
