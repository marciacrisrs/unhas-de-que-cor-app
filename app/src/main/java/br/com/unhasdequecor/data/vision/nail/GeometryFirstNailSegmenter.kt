package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Produces the plate mask from the calibrated contour without color-gating the nail interior.
 * Natural nails can be nearly the same RGB as skin; using color as a hard gate creates the
 * broken white patches seen in the live preview. Geometry is the primary boundary and the
 * boundary guard remains the final skin-safety net.
 */
@Singleton
class GeometryFirstNailSegmenter @Inject constructor() : NailSegmenter {
    override fun segment(image: Bitmap, roi: NailRoi): NailMask? {
        val b = roi.bounds
        val width = b.width()
        val height = b.height()
        if (width < 4 || height < 4) return null
        val polygon = roi.polygon.map { p ->
            ImageCoordinates.PixelPoint(p.x - b.left, p.y - b.top)
        }
        if (polygon.size < 3) return null

        val alpha = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pointInPolygon(x + 0.5f, y + 0.5f, polygon)) {
                    alpha[y * width + x] = 255.toByte()
                }
            }
        }

        // A tiny feather gives a painted, photographic edge without changing the geometry.
        val feathered = feather(alpha, width, height)
        val filled = feathered.count { (it.toInt() and 0xFF) >= 160 }
        if (filled < MIN_FILLED_PIXELS) return null

        return NailMask(
            width = width,
            height = height,
            alpha = feathered,
            originX = b.left,
            originY = b.top,
        )
    }

    private fun feather(src: ByteArray, width: Int, height: Int): ByteArray {
        val out = src.copyOf()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                if (src[i].toInt() and 0xFF != 0) continue
                var best = 2f
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx !in 0 until width || ny !in 0 until height) continue
                        if ((src[ny * width + nx].toInt() and 0xFF) != 0) {
                            best = minOf(best, hypot(dx.toDouble(), dy.toDouble()).toFloat())
                        }
                    }
                }
                if (best <= 1.01f) out[i] = 72.toByte()
            }
        }
        return out
    }

    private fun pointInPolygon(x: Float, y: Float, polygon: List<ImageCoordinates.PixelPoint>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            val denominator = (b.y - a.y).takeIf { abs(it) > EPSILON } ?: EPSILON
            if (((a.y > y) != (b.y > y)) && x < (b.x - a.x) * (y - a.y) / denominator + a.x) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private companion object {
        const val EPSILON = 1e-5f
        const val MIN_FILLED_PIXELS = 12
    }
}
