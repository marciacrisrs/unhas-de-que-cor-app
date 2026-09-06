package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import kotlin.math.cos
import kotlin.math.sin

/** Builds a dense, rounded nail-plate contour from the landmark-derived plate axis. */
object NailPlateContour {
    fun build(plate: NailPlateCalibration.PlateGeometry): List<PixelPoint> {
        val extents = NailPlateCalibration.almondExtents(plate)
        val length = plate.lengthPx * NailPlateCalibration.CONTOUR_LENGTH_FACTOR
        val halfWidth = plate.widthPx * 0.5f
        val aspect = plate.lengthPx / plate.widthPx.coerceAtLeast(1f)
        val short = aspect < NailPlateCalibration.SHORT_PLATE_ASPECT
        val tipWidth = if (short) 0.82f else 0.66f
        val cuticleWidth = 0.78f
        val points = ArrayList<PixelPoint>(CONTOUR_SAMPLES * 2)

        // Parameter s: 0 = cuticle, 1 = tip. Width is deliberately conservative.
        for (i in 0..CONTOUR_SAMPLES) {
            val s = i.toFloat() / CONTOUR_SAMPLES
            val center = extents.cuticleX + (extents.tipX - extents.cuticleX) * s
            val centerY = extents.cuticleY + (extents.tipY - extents.cuticleY) * s
            val widthFactor = smoothWidth(s, cuticleWidth, tipWidth, short)
            val half = halfWidth * widthFactor
            points += PixelPoint(
                center + extents.px * half,
                centerY + extents.py * half,
            )
        }
        for (i in CONTOUR_SAMPLES downTo 0) {
            val s = i.toFloat() / CONTOUR_SAMPLES
            val center = extents.cuticleX + (extents.tipX - extents.cuticleX) * s
            val centerY = extents.cuticleY + (extents.tipY - extents.cuticleY) * s
            val widthFactor = smoothWidth(s, cuticleWidth, tipWidth, short)
            val half = halfWidth * widthFactor
            points += PixelPoint(
                center - extents.px * half,
                centerY - extents.py * half,
            )
        }
        return points
    }

    private fun smoothWidth(s: Float, cuticleWidth: Float, tipWidth: Float, short: Boolean): Float {
        val body = sin(Math.PI * s).toFloat().coerceAtLeast(0f)
        val base = cuticleWidth + (tipWidth - cuticleWidth) * s
        val bulge = if (short) 0.045f else 0.075f
        return (base + bulge * body).coerceIn(0.70f, 0.90f)
    }

    private const val CONTOUR_SAMPLES = 12
}
