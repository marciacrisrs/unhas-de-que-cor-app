package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Segmentação guiada por geometria + evidência da própria imagem.
 *
 * MediaPipe continua fornecendo o prior anatômico, mas o contorno final não é
 * mais aceito cegamente como uma unha sintética. Cada ponto do contorno é
 * refinado procurando a transição real placa/pele dentro de uma faixa segura.
 * Se a imagem não tiver evidência suficiente, mantém o contorno geométrico.
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
        ) return null

        val pixels = IntArray(rw * rh)
        image.getPixels(pixels, 0, rw, bounds.left, bounds.top, rw, rh)

        val geometric = roi.polygon.map { p ->
            PixelPoint(p.x - bounds.left, p.y - bounds.top)
        }
        val refined = refineContour(pixels, rw, rh, geometric)
        val contour = refined ?: geometric

        val solid = ByteArray(rw * rh)
        rasterizePolygon(contour, rw, rh, solid)
        val kept = solid.count { (it.toInt() and 0xFF) >= MASK_SOLID }
        if (kept < MIN_ABSOLUTE_KEEP) return null

        val boundaryInImage = contour.map { p ->
            PixelPoint(p.x + bounds.left, p.y + bounds.top)
        }
        return NailMask(
            width = rw,
            height = rh,
            alpha = feather(solid, rw, rh, FEATHER_RADIUS),
            originX = bounds.left,
            originY = bounds.top,
            boundaryPolygon = boundaryInImage,
        )
    }

    /** Refina cada ponto da borda na normal aproximada da geometria. */
    private fun refineContour(
        pixels: IntArray,
        width: Int,
        height: Int,
        polygon: List<PixelPoint>,
    ): List<PixelPoint>? {
        if (polygon.size < 6) return null

        val centroid = polygon.fold(PixelPoint(0f, 0f)) { acc, p ->
            PixelPoint(acc.x + p.x, acc.y + p.y)
        }.let { PixelPoint(it.x / polygon.size, it.y / polygon.size) }

        val candidates = ArrayList<PixelPoint>(polygon.size)
        var evidenceSum = 0f
        var movedCount = 0

        for (point in polygon) {
            val vx = point.x - centroid.x
            val vy = point.y - centroid.y
            val length = hypot(vx.toDouble(), vy.toDouble()).toFloat()
            if (length < 2f) {
                candidates += point
                continue
            }

            val nx = vx / length
            val ny = vy / length
            var bestScore = Float.NEGATIVE_INFINITY
            var bestOffset = 0f

            for (offset in -SEARCH_INWARD..SEARCH_OUTWARD) {
                val cx = point.x + nx * offset
                val cy = point.y + ny * offset
                val score = boundaryScore(pixels, width, height, cx, cy, nx, ny)
                if (score > bestScore) {
                    bestScore = score
                    bestOffset = offset.toFloat()
                }
            }

            if (bestScore < MIN_EDGE_SCORE) {
                candidates += point
            } else {
                val shift = bestOffset.coerceIn(-MAX_INWARD_SHIFT, MAX_OUTWARD_SHIFT)
                candidates += PixelPoint(point.x + nx * shift, point.y + ny * shift)
                evidenceSum += bestScore
                if (abs(shift) >= 1f) movedCount++
            }
        }

        val averageEvidence = evidenceSum / polygon.size.coerceAtLeast(1)
        if (movedCount < polygon.size * MIN_MOVED_FRACTION ||
            averageEvidence < MIN_AVERAGE_EVIDENCE
        ) return null

        return smoothClosedContour(candidates)
    }

    /** Contraste entre amostras dentro/fora da borda candidata. */
    private fun boundaryScore(
        pixels: IntArray,
        width: Int,
        height: Int,
        cx: Float,
        cy: Float,
        nx: Float,
        ny: Float,
    ): Float {
        val inner = sampleColor(
            pixels,
            width,
            height,
            cx - nx * SAMPLE_DISTANCE,
            cy - ny * SAMPLE_DISTANCE,
        ) ?: return Float.NEGATIVE_INFINITY
        val outer = sampleColor(
            pixels,
            width,
            height,
            cx + nx * SAMPLE_DISTANCE,
            cy + ny * SAMPLE_DISTANCE,
        ) ?: return Float.NEGATIVE_INFINITY
        val center = sampleColor(pixels, width, height, cx, cy)
            ?: return Float.NEGATIVE_INFINITY

        val contrast = colorDistance(inner, outer) / COLOR_DISTANCE_SCALE
        val innerDelta = colorDistance(center, inner) / COLOR_DISTANCE_SCALE
        val outerDelta = colorDistance(center, outer) / COLOR_DISTANCE_SCALE
        val transition = min(innerDelta, outerDelta)

        return (contrast * CONTRAST_WEIGHT + transition * TRANSITION_WEIGHT)
            .coerceIn(0f, 4f)
    }

    private fun sampleColor(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Float,
        y: Float,
    ): Int? {
        val ix = x.roundToInt()
        val iy = y.roundToInt()
        if (ix !in 0 until width || iy !in 0 until height) return null
        return pixels[iy * width + ix]
    }

    private fun colorDistance(a: Int, b: Int): Float {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return sqrt((dr * dr + dg * dg + db * db).toFloat())
    }

    private fun smoothClosedContour(points: List<PixelPoint>): List<PixelPoint> {
        if (points.size < 3) return points
        return points.indices.map { i ->
            val prev = points[(i - 1 + points.size) % points.size]
            val current = points[i]
            val next = points[(i + 1) % points.size]
            PixelPoint(
                current.x * CURRENT_WEIGHT + (prev.x + next.x) * NEIGHBOR_WEIGHT,
                current.y * CURRENT_WEIGHT + (prev.y + next.y) * NEIGHBOR_WEIGHT,
            )
        }
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

    private fun feather(src: ByteArray, width: Int, height: Int, radius: Int): ByteArray {
        if (radius <= 0) return src
        val out = ByteArray(src.size)
        val r2 = radius * radius
        for (y in 0 until height) {
            for (x in 0 until width) {
                out[y * width + x] = featherAlpha(src, width, height, x, y, radius, r2)
            }
        }
        return out
    }

    private fun featherAlpha(
        src: ByteArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        radius: Int,
        r2: Int,
    ): Byte {
        val i = y * width + x
        if ((src[i].toInt() and 0xFF) >= MASK_SOLID) return 255.toByte()
        val best = nearestSolidDistanceSq(src, width, height, x, y, radius)
        if (best == Int.MAX_VALUE || best > r2) return 0
        val t = 1f - sqrt(best.toFloat()) / (radius + 0.01f)
        return (t.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
    }

    private fun nearestSolidDistanceSq(
        src: ByteArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        radius: Int,
    ): Int {
        var best = Int.MAX_VALUE
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until width || ny !in 0 until height) continue
                if ((src[ny * width + nx].toInt() and 0xFF) < MASK_SOLID) continue
                val d2 = dx * dx + dy * dy
                if (d2 < best) best = d2
            }
        }
        return best
    }

    private companion object {
        const val MASK_SOLID = 128
        const val MIN_ABSOLUTE_KEEP = 8
        const val FEATHER_RADIUS = 2
        const val SEARCH_INWARD = 8
        const val SEARCH_OUTWARD = 8
        const val MAX_INWARD_SHIFT = 6f
        const val MAX_OUTWARD_SHIFT = 8f
        const val SAMPLE_DISTANCE = 2.5f
        const val COLOR_DISTANCE_SCALE = 80f
        const val CONTRAST_WEIGHT = 0.70f
        const val TRANSITION_WEIGHT = 0.30f
        const val MIN_EDGE_SCORE = 0.16f
        const val MIN_MOVED_FRACTION = 0.18f
        const val MIN_AVERAGE_EVIDENCE = 0.10f
        const val CURRENT_WEIGHT = 0.72f
        const val NEIGHBOR_WEIGHT = 0.14f
    }
}
