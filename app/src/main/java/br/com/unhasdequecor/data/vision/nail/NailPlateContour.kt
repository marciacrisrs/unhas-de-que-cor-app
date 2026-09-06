package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import kotlin.math.sin

/** Builds a dense, conservative contour that follows the plate rather than a sticker-like oval. */
object NailPlateContour {
    fun build(plate: NailPlateCalibration.PlateGeometry): List<PixelPoint> {
        val extents = NailPlateCalibration.almondExtents(plate)
        val halfWidth = plate.widthPx * 0.5f
        val aspect = plate.lengthPx / plate.widthPx.coerceAtLeast(1f)
        val short = aspect < NailPlateCalibration.SHORT_PLATE_ASPECT
        val tipWidth = if (short) 0.78f else 0.62f
        val cuticleWidth = 0.76f
        val points = ArrayList<PixelPoint>(SAMPLES * 2 + 2)
        for (i in 0..SAMPLES) {
            points += sidePoint(extents, halfWidth, i.toFloat() / SAMPLES, true, short, cuticleWidth, tipWidth)
        }
        for (i in SAMPLES downTo 0) {
            points += sidePoint(extents, halfWidth, i.toFloat() / SAMPLES, false, short, cuticleWidth, tipWidth)
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
        val width = halfWidth * (base + bulge * body).coerceIn(0.68f, 0.84f)
        val sign = if (positive) 1f else -1f
        return PixelPoint(cx + extents.px * width * sign, cy + extents.py * width * sign)
    }

    private const val SAMPLES = 16
    private const val SHORT_BULGE = 0.035f
    private const val LONG_BULGE = 0.055f
}
