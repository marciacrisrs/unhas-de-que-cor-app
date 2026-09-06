package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the existing conservative segmenter, but enforces a rounded plate contour
 * before recolor. The contour can only remove pixels; it can never expand into skin.
 */
@Singleton
class CurvedNailSegmenter @Inject constructor(
    private val delegate: GeometricNailSegmenter,
) : NailSegmenter {
    override fun segment(image: Bitmap, roi: NailRoi): NailMask? {
        val raw = delegate.segment(image, roi) ?: return null
        val contour = NailPlateContour.densify(roi.polygon)
        if (contour.size == roi.polygon.size) return raw

        val local = contour.map { point ->
            ImageCoordinates.PixelPoint(
                x = point.x - raw.originX,
                y = point.y - raw.originY,
            )
        }
        val alpha = raw.alpha.copyOf()
        for (y in 0 until raw.height) {
            for (x in 0 until raw.width) {
                val index = y * raw.width + x
                if ((alpha[index].toInt() and ALPHA_MASK) == 0) continue
                if (!contains(x + PIXEL_CENTER, y + PIXEL_CENTER, local)) {
                    alpha[index] = 0
                }
            }
        }
        return raw.copy(alpha = alpha)
    }

    private fun contains(
        x: Float,
        y: Float,
        polygon: List<ImageCoordinates.PixelPoint>,
    ): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            val denominator = (b.y - a.y).takeIf { kotlin.math.abs(it) > EPSILON } ?: EPSILON
            val crosses = ((a.y > y) != (b.y > y)) &&
                (x < (b.x - a.x) * (y - a.y) / denominator + a.x)
            if (crosses) inside = !inside
            j = i
        }
        return inside
    }

    private companion object {
        const val ALPHA_MASK = 0xFF
        const val EPSILON = 1e-5f
        const val PIXEL_CENTER = 0.5f
    }
}
