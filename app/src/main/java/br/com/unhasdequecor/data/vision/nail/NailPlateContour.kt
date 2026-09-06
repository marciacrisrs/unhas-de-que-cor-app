package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import kotlin.math.sin

/** Builds a dense, conservative contour that follows the visible nail plate. */
object NailPlateContour {
    fun build(plate: NailPlateCalibration.PlateGeometry): List<PixelPoint> {
        val extents = NailPlateCalibration.almondExtents(plate)
        val halfWidth = plate.widthPx * 0.5f
        val aspect = plate.lengthPx / plate.widthPx.coerceAtLeast(1f)
        val short = aspect < NailPlateCalibration.SHORT_PLATE_ASPECT
        val sideEnd = if (short) SHORT_SIDE_END else LONG_SIDE_END
        val sideTipWidth = if (short) SHORT_TIP_WIDTH else LONG_TIP_WIDTH
        val cuticleWidth = CUTICLE_WIDTH
        val points = ArrayList<PixelPoint>(SAMPLES * 2 + CAP_SAMPLES + 2)

        // Positive side: cuticle -> near the free edge.
        for (i in 0..SAMPLES) {
            val s = sideEnd * i.toFloat() / SAMPLES
            points += sidePoint(extents, halfWidth, s, true, short, cuticleWidth, sideTipWidth)
        }

        // Rounded free edge: quadratic Bezier, with the tip landmark as the outermost point.
        val left = points.last()
        val tip = PixelPoint(extents.tipX, extents.tipY)
        val right = sidePoint(extents, halfWidth, sideEnd, false, short, cuticleWidth, sideTipWidth)
        for (i in 1..CAP_SAMPLES) {
            val t = i.toFloat() / CAP_SAMPLES
            val oneMinus = 1f - t
            val x = oneMinus * oneMinus * left.x + 2f * oneMinus * t * tip.x + t * t * right.x
            val y = oneMinus * oneMinus * left.y + 2f * oneMinus * t * tip.y + t * t * right.y
            points += PixelPoint(x, y)
        }

        // Negative side: free edge -> cuticle.
        for (i in SAMPLES downTo 0) {
            val s = sideEnd * i.toFloat() / SAMPLES
            points += sidePoint(extents, halfWidth, s, false, short, cuticleWidth, sideTipWidth)
        }
        return points
    }

    private fun sidePoint(
        extents: NailPlateCalibration.AlmondExtents,
        halfWidth: Float,
        s: Float,
        positive: Boolean,
        short: Boolean,
        cuticleWidth: Float,
        tipWidth: Float,
    ): PixelPoint {
        val cx = extents.cuticleX + (extents.tipX - extents.cuticleX) * s
        val cy = extents.cuticleY + (extents.tipY - extents.cuticleY) * s
        val body = sin(Math.PI * s).toFloat().coerceAtLeast(0f)
        val base = cuticleWidth + (tipWidth - cuticleWidth) * s
        val bulge = if (short) SHORT_BULGE else LONG_BULGE
        val width = halfWidth * (base + bulge * body).coerceIn(MIN_WIDTH, MAX_WIDTH)
        val sign = if (positive) 1f else -1f
        return PixelPoint(cx + extents.px * width * sign, cy + extents.py * width * sign)
    }

    private const val SAMPLES = 16
    private const val CAP_SAMPLES = 8
    private const val SHORT_SIDE_END = 0.84f
    private const val LONG_SIDE_END = 0.88f
    private const val SHORT_TIP_WIDTH = 0.76f
    private const val LONG_TIP_WIDTH = 0.60f
    private const val CUTICLE_WIDTH = 0.76f
    private const val SHORT_BULGE = 0.035f
    private const val LONG_BULGE = 0.055f
    private const val MIN_WIDTH = 0.68f
    private const val MAX_WIDTH = 0.84f
}
