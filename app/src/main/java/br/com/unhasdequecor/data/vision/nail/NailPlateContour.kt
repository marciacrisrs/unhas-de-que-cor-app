package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import kotlin.math.hypot

/**
 * Converts the six anatomical almond anchors into a dense curved contour for rasterization.
 * The landmark-derived anchors remain the source of truth; this only removes polygonal/flat edges.
 */
object NailPlateContour {
    fun densify(polygon: List<PixelPoint>): List<PixelPoint> {
        if (polygon.size != ALMOND_POINTS) return polygon
        val tipCenter = midpoint(polygon[TIP_LEFT], polygon[TIP_RIGHT])
        val cuticleCenter = midpoint(polygon[CUTICLE_RIGHT], polygon[CUTICLE_LEFT])
        val axisX = tipCenter.x - cuticleCenter.x
        val axisY = tipCenter.y - cuticleCenter.y
        val axisLength = hypot(axisX.toDouble(), axisY.toDouble()).toFloat().coerceAtLeast(1f)
        val ux = axisX / axisLength
        val uy = axisY / axisLength
        val cuticleControl = PixelPoint(
            cuticleCenter.x + ux * axisLength * CUTICLE_INSET,
            cuticleCenter.y + uy * axisLength * CUTICLE_INSET,
        )
        val result = ArrayList<PixelPoint>(SIDE_SAMPLES * 4 + CAP_SAMPLES + 2)

        // Tip-left -> mid-right -> cuticle-right.
        addLine(result, polygon[TIP_LEFT], polygon[MID_RIGHT], SIDE_SAMPLES)
        addLine(result, polygon[MID_RIGHT], polygon[CUTICLE_RIGHT], SIDE_SAMPLES)
        // Conservative shallow U at the cuticle: the control point moves toward the tip.
        addQuadratic(result, polygon[CUTICLE_RIGHT], cuticleControl, polygon[CUTICLE_LEFT], CUTICLE_SAMPLES)
        // Cuticle-left -> mid-left -> tip-right.
        addLine(result, polygon[CUTICLE_LEFT], polygon[MID_LEFT], SIDE_SAMPLES)
        addLine(result, polygon[MID_LEFT], polygon[TIP_RIGHT], SIDE_SAMPLES)
        // Rounded free edge: control point is the observed tip center, never beyond it.
        addQuadratic(result, polygon[TIP_RIGHT], tipCenter, polygon[TIP_LEFT], CAP_SAMPLES)
        return result
    }

    private fun addLine(out: MutableList<PixelPoint>, from: PixelPoint, to: PixelPoint, samples: Int) {
        for (i in 0 until samples) {
            val t = i.toFloat() / samples
            out += interpolate(from, to, t)
        }
    }

    private fun addQuadratic(
        out: MutableList<PixelPoint>,
        from: PixelPoint,
        control: PixelPoint,
        to: PixelPoint,
        samples: Int,
    ) {
        for (i in 0 until samples) {
            val t = i.toFloat() / samples
            val oneMinus = 1f - t
            out += PixelPoint(
                oneMinus * oneMinus * from.x + 2f * oneMinus * t * control.x + t * t * to.x,
                oneMinus * oneMinus * from.y + 2f * oneMinus * t * control.y + t * t * to.y,
            )
        }
    }

    private fun interpolate(from: PixelPoint, to: PixelPoint, t: Float): PixelPoint =
        PixelPoint(
            from.x + (to.x - from.x) * t,
            from.y + (to.y - from.y) * t,
        )

    private fun midpoint(a: PixelPoint, b: PixelPoint): PixelPoint =
        PixelPoint((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)

    private const val ALMOND_POINTS = 6
    private const val TIP_LEFT = 0
    private const val MID_RIGHT = 1
    private const val CUTICLE_RIGHT = 2
    private const val CUTICLE_LEFT = 3
    private const val MID_LEFT = 4
    private const val TIP_RIGHT = 5
    private const val SIDE_SAMPLES = 4
    private const val CAP_SAMPLES = 10
    private const val CUTICLE_SAMPLES = 6
    private const val CUTICLE_INSET = 0.055f
}
