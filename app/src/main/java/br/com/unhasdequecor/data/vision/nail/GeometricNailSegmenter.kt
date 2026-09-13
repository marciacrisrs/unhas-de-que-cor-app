package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Segmentação geométrica da placa ungueal (sem OpenCV):
 * 1) rasteriza o contorno anatômico calibrado;
 * 2) preserva a placa inteira para o try-on;
 * 3) aplica apenas feather na borda.
 *
 * A cor/iluminação da foto não deve decidir se uma região da placa é pintada.
 * Unha natural pode ter cor muito próxima da pele, e isso criava buracos na máscara.
 */
@Singleton
class GeometricNailSegmenter @Inject constructor() : NailSegmenter {

    override fun segment(image: Bitmap, roi: NailRoi): NailMask? {
        val bounds = roi.bounds
        val rw = bounds.width()
        val rh = bounds.height()
        if (rw < 4 || rh < 4) return null
        if (bounds.left < 0 || bounds.top < 0 ||
            bounds.right > image.width || bounds.bottom > image.height
        ) {
            return null
        }

        val polyLocal = roi.polygon.map { p ->
            PixelPoint(p.x - bounds.left, p.y - bounds.top)
        }
        val solid = ByteArray(rw * rh)
        rasterizePolygon(polyLocal, rw, rh, solid)

        val kept = solid.count { (it.toInt() and 0xFF) >= MASK_SOLID }
        if (kept < MIN_ABSOLUTE_KEEP) return null

        val alpha = feather(solid, rw, rh, radius = FEATHER_RADIUS)
        return NailMask(
            width = rw,
            height = rh,
            alpha = alpha,
            originX = bounds.left,
            originY = bounds.top,
        )
    }

    private fun rasterizePolygon(
        poly: List<PixelPoint>,
        width: Int,
        height: Int,
        out: ByteArray,
    ) {
        if (poly.size < 3) return
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pointInPolygon(x + 0.5f, y + 0.5f, poly)) {
                    out[y * width + x] = 255.toByte()
                }
            }
        }
    }

    private fun pointInPolygon(x: Float, y: Float, poly: List<PixelPoint>): Boolean {
        var inside = false
        var j = poly.lastIndex
        for (i in poly.indices) {
            val xi = poly[i].x
            val yi = poly[i].y
            val xj = poly[j].x
            val yj = poly[j].y
            val intersect = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { abs(it) > 1e-5f } ?: 1e-5f) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    private fun feather(src: ByteArray, w: Int, h: Int, radius: Int): ByteArray {
        if (radius <= 0) return src
        val out = ByteArray(src.size)
        val r2 = radius * radius
        for (y in 0 until h) {
            for (x in 0 until w) {
                out[y * w + x] = featherAlpha(src, w, h, x, y, radius, r2)
            }
        }
        return out
    }

    private fun featherAlpha(
        src: ByteArray,
        w: Int,
        h: Int,
        x: Int,
        y: Int,
        radius: Int,
        r2: Int,
    ): Byte {
        val i = y * w + x
        if ((src[i].toInt() and 0xFF) >= MASK_SOLID) {
            return 255.toByte()
        }
        val best = nearestSolidDistanceSq(src, w, h, x, y, radius)
        if (best == Int.MAX_VALUE || best > r2) {
            return 0
        }
        val t = 1f - sqrt(best.toFloat()) / (radius + 0.01f)
        return (t.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
    }

    private fun nearestSolidDistanceSq(
        src: ByteArray,
        w: Int,
        h: Int,
        x: Int,
        y: Int,
        radius: Int,
    ): Int {
        var best = Int.MAX_VALUE
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val nx = x + dx
                val ny = y + dy
                val inside = nx in 0 until w && ny in 0 until h
                val solid = inside && (src[ny * w + nx].toInt() and MASK_SOLID) >= MASK_SOLID
                if (solid) {
                    val d2 = dx * dx + dy * dy
                    if (d2 < best) best = d2
                }
            }
        }
        return best
    }

    private companion object {
        const val MASK_SOLID = 128
        const val MIN_ABSOLUTE_KEEP = 8
        const val FEATHER_RADIUS = 2
    }
}
