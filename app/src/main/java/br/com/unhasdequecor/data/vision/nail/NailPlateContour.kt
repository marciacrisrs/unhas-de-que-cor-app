package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import kotlin.math.hypot

/** Builds a calibrated plate contour and exposes a compact six-point ROI polygon. */
object NailPlateContour {
    fun build(plate: NailPlateCalibration.PlateGeometry): List<PixelPoint> =
        densify(buildSixPoint(plate))

    fun buildSixPoint(plate: NailPlateCalibration.PlateGeometry): List<PixelPoint> {
        val e = NailPlateCalibration.almondExtents(plate)
        return listOf(
            offset(e.tipX, e.tipY, e.px, e.py, e.tipHalfW),
            offset(e.midX, e.midY, e.px, e.py, e.midHalfW),
            offset(e.cuticleX, e.cuticleY, e.px, e.py, e.cuticleHalfW),
            offset(e.cuticleX, e.cuticleY, e.px, e.py, -e.cuticleHalfW),
            offset(e.midX, e.midY, e.px, e.py, -e.midHalfW),
            offset(e.tipX, e.tipY, e.px, e.py, -e.tipHalfW),
        )
    }

    /** Densifies the calibrated contour for rasterization and rendering. */
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
            val x = u * u * from.x + 2f * u * t * control.x + t * t * to.x
            val y = u * u * from.y + 2f * u * t * control.y + t * t * to.y
            out += PixelPoint(
                x.coerceIn(minOf(from.x, to.x), maxOf(from.x, to.x)),
                y.coerceIn(minOf(from.y, to.y), maxOf(from.y, to.y)),
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
