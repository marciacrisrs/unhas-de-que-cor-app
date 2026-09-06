package br.com.unhasdequecor.data.vision.nail

/**
 * Final spatial safety barrier between segmentation and recolor.
 *
 * The geometric segmenter intentionally feathers the ROI edge. Feathering is
 * useful visually, but it can create non-zero alpha just outside the polygon.
 * Recolor must never turn that anti-aliasing fringe into paint on skin, so the
 * final mask is clipped back to the anatomical nail-plate polygon.
 */
object NailPlateMaskBoundaryGuard {

    fun clamp(mask: NailMask, roi: NailRoi): NailMask {
        if (mask.alpha.none { (it.toInt() and ALPHA_MASK) != 0 }) return mask

        val polygon = roi.polygon.map { point ->
            ImageCoordinates.PixelPoint(
                x = point.x - mask.originX,
                y = point.y - mask.originY,
            )
        }
        if (polygon.size < MIN_POLYGON_POINTS) return mask

        val alpha = mask.alpha.copyOf()
        for (y in 0 until mask.height) {
            for (x in 0 until mask.width) {
                val index = y * mask.width + x
                if ((alpha[index].toInt() and ALPHA_MASK) == 0) continue
                if (!pointInPolygon(x + PIXEL_CENTER, y + PIXEL_CENTER, polygon)) {
                    alpha[index] = 0
                }
            }
        }
        return if (alpha.contentEquals(mask.alpha)) {
            mask
        } else {
            mask.copy(alpha = alpha)
        }
    }

    private fun pointInPolygon(
        x: Float,
        y: Float,
        polygon: List<ImageCoordinates.PixelPoint>,
    ): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y
            val denominator = (yj - yi).takeIf { kotlin.math.abs(it) > EPSILON } ?: EPSILON
            val intersects = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / denominator + xi)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    private const val ALPHA_MASK = 0xFF
    private const val EPSILON = 1e-5f
    private const val MIN_POLYGON_POINTS = 3
    private const val PIXEL_CENTER = 0.5f
}
