package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

/** Model-free image-evidence segmenter using geometry only as a search prior. */
@Singleton
class EvidenceNailSegmenter @Inject constructor() : NailSegmenter {
    override fun segment(image: Bitmap, roi: NailRoi): NailMask? {
        val bounds = roi.bounds
        val width = bounds.width()
        val height = bounds.height()
        if (width < MIN_SIZE || height < MIN_SIZE) return null
        if (bounds.left < 0 || bounds.top < 0 || bounds.right > image.width || bounds.bottom > image.height) return null
        val pixels = IntArray(width * height)
        image.getPixels(pixels, 0, width, bounds.left, bounds.top, width, height)
        val polygon = roi.polygon.map { PixelPoint(it.x - bounds.left, it.y - bounds.top) }
        if (polygon.size < MIN_POLYGON_POINTS) return null
        val frame = Frame(
            PixelPoint(roi.axisFromDip.x - bounds.left, roi.axisFromDip.y - bounds.top),
            PixelPoint(roi.axisToTip.x - bounds.left, roi.axisToTip.y - bounds.top),
        )
        if (frame.length < MIN_AXIS_LENGTH) return null
        val projected = polygon.map(frame::project)
        val minT = projected.minOf { it.t }
        val maxT = projected.maxOf { it.t }
        val geometricHalfWidth = max(roi.widthPx * HALF, projected.maxOf { abs(it.s) })
            .coerceIn(MIN_HALF_WIDTH, MAX_HALF_WIDTH)
        val searchHalfWidth = max(geometricHalfWidth * SEARCH_WIDTH_FACTOR, geometricHalfWidth + SEARCH_WIDTH_MARGIN)
            .coerceAtMost(MAX_SEARCH_HALF_WIDTH)
        val skin = estimateSkin(pixels, width, height, frame, minT, maxT, searchHalfWidth)
        val candidates = classify(pixels, width, height, frame, minT, maxT, searchHalfWidth, skin)
        val component = seededComponent(candidates, width, height, frame, polygon) ?: return null
        if (component.count { it } < MIN_COMPONENT_PIXELS) return null
        val samples = boundarySamples(component, width, height, frame, minT, maxT)
        if (samples.size < MIN_SAMPLES) return null
        val left = samples.map { frame.point(it.t, it.minS + INSET) }
        val right = samples.asReversed().map { frame.point(it.t, it.maxS - INSET) }
        val contour = (left + right).map { PixelPoint(it.x, it.y) }
        if (!coherent(contour, polygon, frame, searchHalfWidth)) return null
        val alpha = rasterize(contour, width, height)
        if (alpha.count { (it.toInt() and ALPHA_MASK) >= SOLID_ALPHA } < MIN_COMPONENT_PIXELS) return null
        return NailMask(
            width = width,
            height = height,
            alpha = alpha,
            originX = bounds.left,
            originY = bounds.top,
            boundaryPolygon = contour.map { PixelPoint(it.x + bounds.left, it.y + bounds.top) },
        )
    }

    private data class Frame(val base: PixelPoint, val tip: PixelPoint) {
        val dx = tip.x - base.x
        val dy = tip.y - base.y
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(EPSILON)
        val ux = dx / length
        val uy = dy / length
        val vx = -uy
        val vy = ux
        fun project(point: PixelPoint) = Projection(
            (point.x - base.x) * ux + (point.y - base.y) * uy,
            (point.x - base.x) * vx + (point.y - base.y) * vy,
        )
        fun point(t: Float, s: Float) = PixelPoint(base.x + ux * t + vx * s, base.y + uy * t + vy * s)
    }

    private data class Projection(val t: Float, val s: Float)
    private data class Feature(val r: Float, val g: Float, val b: Float)
    private data class SkinModel(val mean: Feature, val spread: Float)
    private data class BoundarySample(val t: Float, val minS: Float, val maxS: Float)

    private fun estimateSkin(
        pixels: IntArray,
        width: Int,
        height: Int,
        frame: Frame,
        minT: Float,
        maxT: Float,
        searchHalfWidth: Float,
    ): SkinModel {
        val samples = ArrayList<Feature>()
        val ring = searchHalfWidth * SKIN_RING_FACTOR
        for (y in 1 until height - 1 step SAMPLE_STRIDE) {
            for (x in 1 until width - 1 step SAMPLE_STRIDE) {
                val p = frame.project(PixelPoint(x + HALF_PIXEL, y + HALF_PIXEL))
                if (p.t !in minT..maxT || abs(p.s) < ring) continue
                samples += featureAt(pixels, width, height, x, y)
            }
        }
        if (samples.isEmpty()) return SkinModel(DEFAULT_SKIN, DEFAULT_SPREAD)
        val mean = Feature(
            samples.sumOf { it.r.toDouble() }.toFloat() / samples.size,
            samples.sumOf { it.g.toDouble() }.toFloat() / samples.size,
            samples.sumOf { it.b.toDouble() }.toFloat() / samples.size,
        )
        val spread = samples.map { distance(it, mean) }.average().toFloat().coerceAtLeast(MIN_SPREAD)
        return SkinModel(mean, spread)
    }

    private fun classify(
        pixels: IntArray,
        width: Int,
        height: Int,
        frame: Frame,
        minT: Float,
        maxT: Float,
        searchHalfWidth: Float,
        skin: SkinModel,
    ): BooleanArray {
        val result = BooleanArray(width * height)
        val threshold = max(MIN_COLOR_DISTANCE, skin.spread * SPREAD_FACTOR)
        for (y in 0 until height) for (x in 0 until width) {
            val p = frame.project(PixelPoint(x + HALF_PIXEL, y + HALF_PIXEL))
            if (p.t !in minT..maxT || abs(p.s) > searchHalfWidth) continue
            result[y * width + x] = distance(featureAt(pixels, width, height, x, y), skin.mean) >= threshold
        }
        return result
    }

    private fun seededComponent(
        candidates: BooleanArray,
        width: Int,
        height: Int,
        frame: Frame,
        polygon: List<PixelPoint>,
    ): BooleanArray? {
        val center = polygon.map(frame::project)
        val t = center.map { it.t }.average().toFloat()
        val s = center.map { it.s }.average().toFloat()
        val seedPoint = frame.point(t, s)
        val start = nearest(candidates, width, height, seedPoint.x.roundToInt(), seedPoint.y.roundToInt()) ?: return null
        val visited = BooleanArray(candidates.size)
        val component = BooleanArray(candidates.size)
        val queue = java.util.ArrayDeque<Int>()
        queue += start
        visited[start] = true
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            if (!candidates[index]) continue
            component[index] = true
            val x = index % width
            val y = index / width
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until width || ny !in 0 until height) continue
                val next = ny * width + nx
                if (!visited[next] && candidates[next]) {
                    visited[next] = true
                    queue += next
                }
            }
        }
        return component.takeIf { it.any { value -> value } }
    }

    private fun nearest(mask: BooleanArray, width: Int, height: Int, x: Int, y: Int): Int? {
        val cx = x.coerceIn(0, width - 1)
        val cy = y.coerceIn(0, height - 1)
        if (mask[cy * width + cx]) return cy * width + cx
        for (radius in 1..SEED_RADIUS) {
            for (dy in -radius..radius) for (dx in -radius..radius) {
                val nx = cx + dx
                val ny = cy + dy
                if (nx in 0 until width && ny in 0 until height && mask[ny * width + nx]) return ny * width + nx
            }
        }
        return null
    }

    private fun boundarySamples(
        component: BooleanArray,
        width: Int,
        height: Int,
        frame: Frame,
        minT: Float,
        maxT: Float,
    ): List<BoundarySample> {
        val result = ArrayList<BoundarySample>()
        val step = max(1f, (maxT - minT) / MAX_SAMPLES)
        var t = minT
        while (t <= maxT) {
            var minS = Float.POSITIVE_INFINITY
            var maxS = Float.NEGATIVE_INFINITY
            for (y in 0 until height) for (x in 0 until width) {
                if (!component[y * width + x]) continue
                val p = frame.project(PixelPoint(x + HALF_PIXEL, y + HALF_PIXEL))
                if (abs(p.t - t) <= SAMPLE_TOLERANCE) {
                    minS = min(minS, p.s)
                    maxS = max(maxS, p.s)
                }
            }
            if (minS.isFinite() && maxS.isFinite()) result += BoundarySample(t, minS, maxS)
            t += step
        }
        return result
    }

    private fun coherent(candidate: List<PixelPoint>, geometric: List<PixelPoint>, frame: Frame, halfWidth: Float): Boolean {
        if (candidate.size < MIN_POLYGON_POINTS) return false
        val area = abs(polygonArea(candidate))
        val geometricArea = abs(polygonArea(geometric)).coerceAtLeast(1f)
        if (area < geometricArea * MIN_AREA_RATIO || area > geometricArea * MAX_AREA_RATIO) return false
        val candidateProjection = candidate.map(frame::project)
        val geometricProjection = geometric.map(frame::project)
        val minT = geometricProjection.minOf { it.t }
        val maxT = geometricProjection.maxOf { it.t }
        if (candidateProjection.minOf { it.t } < minT - halfWidth * AXIS_DRIFT_FACTOR) return false
        if (candidateProjection.maxOf { it.t } > maxT + halfWidth * AXIS_DRIFT_FACTOR) return false
        return true
    }

    private fun rasterize(polygon: List<PixelPoint>, width: Int, height: Int): ByteArray {
        val output = ByteArray(width * height)
        val minY = polygon.minOf { it.y }.roundToInt().coerceIn(0, height - 1)
        val maxY = polygon.maxOf { it.y }.roundToInt().coerceIn(0, height - 1)
        for (y in minY..maxY) {
            val intersections = ArrayList<Float>()
            for (i in polygon.indices) {
                val a = polygon[i]
                val b = polygon[(i + 1) % polygon.size]
                if ((a.y <= y + HALF_PIXEL && b.y > y + HALF_PIXEL) || (b.y <= y + HALF_PIXEL && a.y > y + HALF_PIXEL)) {
                    val ratio = (y + HALF_PIXEL - a.y) / (b.y - a.y)
                    intersections += a.x + (b.x - a.x) * ratio
                }
            }
            intersections.sort()
            var i = 0
            while (i + 1 < intersections.size) {
                val left = intersections[i].roundToInt().coerceIn(0, width - 1)
                val right = intersections[i + 1].roundToInt().coerceIn(0, width - 1)
                for (x in min(left, right)..max(left, right)) output[y * width + x] = SOLID_ALPHA.toByte()
                i += 2
            }
        }
        return output
    }

    private fun featureAt(pixels: IntArray, width: Int, height: Int, x: Int, y: Int): Feature {
        val color = pixels[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]
        return Feature(((color shr 16) and 0xFF) / 255f, ((color shr 8) and 0xFF) / 255f, (color and 0xFF) / 255f)
    }

    private fun distance(a: Feature, b: Feature): Float =
        (abs(a.r - b.r) + abs(a.g - b.g) + abs(a.b - b.b)) / 3f

    private fun polygonArea(points: List<PixelPoint>): Float =
        points.indices.fold(0f) { sum, i ->
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum + a.x * b.y - b.x * a.y
        } * HALF

    private companion object {
        const val MIN_SIZE = 8
        const val MIN_POLYGON_POINTS = 4
        const val MIN_AXIS_LENGTH = 6f
        const val MIN_HALF_WIDTH = 4f
        const val MAX_HALF_WIDTH = 55f
        const val MAX_SEARCH_HALF_WIDTH = 55f
        const val SEARCH_WIDTH_FACTOR = 1.8f
        const val SEARCH_WIDTH_MARGIN = 6f
        const val SKIN_RING_FACTOR = 0.8f
        const val SAMPLE_STRIDE = 3
        const val MIN_SPREAD = 0.008f
        const val SPREAD_FACTOR = 2.2f
        const val MIN_COLOR_DISTANCE = 0.04f
        const val SEED_RADIUS = 16
        const val MIN_COMPONENT_PIXELS = 12
        const val MIN_SAMPLES = 4
        const val MAX_SAMPLES = 48f
        const val SAMPLE_TOLERANCE = 0.8f
        const val INSET = 0f
        const val MIN_AREA_RATIO = 0.08f
        const val MAX_AREA_RATIO = 7f
        const val AXIS_DRIFT_FACTOR = 0.15f
        const val SOLID_ALPHA = 255
        const val ALPHA_MASK = 255
        const val HALF = 0.5f
        const val HALF_PIXEL = 0.5f
        const val EPSILON = 1e-4f
        val DEFAULT_SKIN = Feature(0.33f, 0.33f, 0.34f)
        const val DEFAULT_SPREAD = 0.15f
    }
}
