package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Paint-aware plate segmentation.
 *
 * Landmark geometry supplies the search region and seed. Pixel appearance is
 * used to recover the visible plate, including polished nails whose color may
 * be darker than the surrounding skin. The result is kept inside the ROI and
 * receives a one-pixel inset before becoming paintable.
 */
@Singleton
class PaintAwareNailSegmenter @Inject constructor() : NailSegmenter {
    override fun segment(image: Bitmap, roi: NailRoi): NailMask? {
        val bounds = roi.bounds
        val width = bounds.width()
        val height = bounds.height()
        if (!validBounds(image, bounds) || width < MIN_SIZE || height < MIN_SIZE) return null

        val pixels = IntArray(width * height)
        image.getPixels(pixels, 0, width, bounds.left, bounds.top, width, height)
        val polygon = roi.polygon.map { PixelPoint(it.x - bounds.left, it.y - bounds.top) }
        if (polygon.size < MIN_POLYGON_POINTS) return null

        val base = PixelPoint(roi.axisFromDip.x - bounds.left, roi.axisFromDip.y - bounds.top)
        val tip = PixelPoint(roi.axisToTip.x - bounds.left, roi.axisToTip.y - bounds.top)
        val frame = Frame(base, tip)
        if (frame.length < MIN_AXIS_LENGTH) return null

        val projections = polygon.map(frame::project)
        val minT = projections.minOf { it.t }
        val maxT = projections.maxOf { it.t }
        val nominalHalfWidth = (roi.widthPx * HALF_WIDTH_SCALE).coerceIn(MIN_HALF_WIDTH, MAX_HALF_WIDTH)
        val roiHalfWidth = (width * ROI_HALF_WIDTH_SCALE).coerceIn(MIN_HALF_WIDTH, MAX_HALF_WIDTH)
        val skin = estimateSkin(pixels, width, height, frame, minT, maxT, roiHalfWidth)
        val mask = classifyPlate(pixels, width, height, frame, minT, maxT, roiHalfWidth, skin)
        val component = largestSeededComponent(mask, width, height, frame, polygon)
            ?: return null
        val componentArea = component.count { it }
        if (componentArea < MIN_COMPONENT_PIXELS) return null

        val boundary = traceBoundary(component, width, height, frame, minT, maxT)
        if (boundary.size < MIN_POLYGON_POINTS) return null
        val inset = inset(boundary, width, height)
        if (!shapeIsValid(inset, polygon, frame, width, height, nominalHalfWidth)) return null

        val alpha = rasterize(inset, width, height)
        val filled = alpha.count { (it.toInt() and ALPHA_MASK) >= SOLID_ALPHA }
        if (filled < MIN_COMPONENT_PIXELS) return null

        return NailMask(
            width = width,
            height = height,
            alpha = alpha,
            originX = bounds.left,
            originY = bounds.top,
            boundaryPolygon = inset.map { PixelPoint(it.x + bounds.left, it.y + bounds.top) },
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
        fun point(t: Float, s: Float) = PixelPoint(
            base.x + ux * t + vx * s,
            base.y + uy * t + vy * s,
        )
    }

    private data class Projection(val t: Float, val s: Float)
    private data class Feature(val r: Float, val g: Float, val b: Float, val luma: Float)
    private data class SkinModel(val mean: Feature, val spread: Float)

    private fun validBounds(image: Bitmap, bounds: ImageCoordinates.PixelRect): Boolean =
        bounds.left >= 0 && bounds.top >= 0 && bounds.right <= image.width && bounds.bottom <= image.height

    private fun estimateSkin(
        pixels: IntArray,
        width: Int,
        height: Int,
        frame: Frame,
        minT: Float,
        maxT: Float,
        halfWidth: Float,
    ): SkinModel {
        val samples = ArrayList<Feature>()
        val ring = halfWidth * SKIN_RING_FACTOR
        for (y in 1 until height - 1 step SAMPLE_STRIDE) {
            for (x in 1 until width - 1 step SAMPLE_STRIDE) {
                val feature = featureAt(pixels, width, height, x, y) ?: continue
                val projection = frame.project(PixelPoint(x + HALF_PIXEL, y + HALF_PIXEL))
                if (projection.t in minT..maxT && abs(projection.s) >= ring) samples += feature
            }
        }
        if (samples.isEmpty()) return SkinModel(DEFAULT_SKIN, DEFAULT_SPREAD)
        val mean = meanFeature(samples)
        val spread = samples.map { distance(it, mean) }.average().toFloat().coerceAtLeast(MIN_SPREAD)
        return SkinModel(mean, spread)
    }

    private fun classifyPlate(
        pixels: IntArray,
        width: Int,
        height: Int,
        frame: Frame,
        minT: Float,
        maxT: Float,
        halfWidth: Float,
        skin: SkinModel,
    ): BooleanArray {
        val result = BooleanArray(width * height)
        val threshold = max(MIN_COLOR_DISTANCE, skin.spread * SPREAD_THRESHOLD)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val projection = frame.project(PixelPoint(x + HALF_PIXEL, y + HALF_PIXEL))
                if (projection.t !in minT..maxT || abs(projection.s) > halfWidth) continue
                val feature = featureAt(pixels, width, height, x, y) ?: continue
                result[y * width + x] = distance(feature, skin.mean) >= threshold
            }
        }
        return result
    }

    private fun largestSeededComponent(
        candidate: BooleanArray,
        width: Int,
        height: Int,
        frame: Frame,
        polygon: List<PixelPoint>,
    ): BooleanArray? {
        val center = polygon.map { frame.project(it) }
            .let { Projection(it.map(Projection::t).average().toFloat(), it.map(Projection::s).average().toFloat()) }
        val seed = frame.point(center.t, center.s)
        val seedX = seed.x.roundToInt().coerceIn(0, width - 1)
        val seedY = seed.y.roundToInt().coerceIn(0, height - 1)
        val start = nearestCandidate(candidate, width, height, seedX, seedY) ?: return null

        val visited = BooleanArray(candidate.size)
        val queue = ArrayDeque<Int>()
        val component = BooleanArray(candidate.size)
        queue += start
        visited[start] = true
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            if (!candidate[index]) continue
            component[index] = true
            val x = index % width
            val y = index / width
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val next = ny * width + nx
                    if (!visited[next] && candidate[next]) {
                        visited[next] = true
                        queue += next
                    }
                }
            }
        }
        return component.takeIf { it.any { value -> value } }
    }

    private fun nearestCandidate(mask: BooleanArray, width: Int, height: Int, x: Int, y: Int): Int? {
        if (mask[y * width + x]) return y * width + x
        for (radius in 1..SEED_SEARCH_RADIUS) {
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until width && ny in 0 until height && mask[ny * width + nx]) {
                        return ny * width + nx
                    }
                }
            }
        }
        return null
    }

    private fun traceBoundary(
        component: BooleanArray,
        width: Int,
        height: Int,
        frame: Frame,
        minT: Float,
        maxT: Float,
    ): List<PixelPoint> {
        val samples = ArrayList<Projection>()
        val span = (maxT - minT).coerceAtLeast(1f)
        var t = minT
        while (t <= maxT) {
            val row = boundaryAt(component, width, height, frame, t)
            if (row != null) samples += row
            t += max(SIDE_SAMPLE_STEP, span / MAX_BOUNDARY_SAMPLES)
        }
        if (samples.size < 2) return emptyList()
        val left = samples.map { frame.point(it.t, it.s - BOUNDARY_INSET) }
        val right = samples.asReversed().map { frame.point(it.t, it.s + BOUNDARY_INSET) }
        return (left + right).map { PixelPoint(it.x, it.y) }
    }

    private fun boundaryAt(
        component: BooleanArray,
        width: Int,
        height: Int,
        frame: Frame,
        t: Float,
    ): Projection? {
        var minS = Float.POSITIVE_INFINITY
        var maxS = Float.NEGATIVE_INFINITY
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!component[y * width + x]) continue
                val projection = frame.project(PixelPoint(x + HALF_PIXEL, y + HALF_PIXEL))
                if (abs(projection.t - t) <= SAMPLE_TOLERANCE) {
                    minS = min(minS, projection.s)
                    maxS = max(maxS, projection.s)
                }
            }
        }
        if (!minS.isFinite() || !maxS.isFinite()) return null
        return Projection(t, (minS + maxS) * HALF)
    }

    private fun inset(points: List<PixelPoint>, width: Int, height: Int): List<PixelPoint> {
        val cx = points.map { it.x }.average().toFloat()
        val cy = points.map { it.y }.average().toFloat()
        return points.map { point ->
            val dx = cx - point.x
            val dy = cy - point.y
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(EPSILON)
            PixelPoint(
                (point.x + dx / distance * PAINT_INSET_PX).coerceIn(0.5f, width - 0.5f),
                (point.y + dy / distance * PAINT_INSET_PX).coerceIn(0.5f, height - 0.5f),
            )
        }
    }

    private fun shapeIsValid(
        polygon: List<PixelPoint>,
        roiPolygon: List<PixelPoint>,
        frame: Frame,
        width: Int,
        height: Int,
        nominalHalfWidth: Float,
    ): Boolean {
        if (polygon.size < MIN_POLYGON_POINTS) return false
        val area = abs(polygonArea(polygon))
        val roiArea = abs(polygonArea(roiPolygon)).coerceAtLeast(1f)
        if (area < roiArea * MIN_AREA_RATIO || area > roiArea * MAX_AREA_RATIO) return false
        val projected = polygon.map(frame::project)
        val geometric = roiPolygon.map(frame::project)
        val minT = geometric.minOf { it.t }
        val maxT = geometric.maxOf { it.t }
        val candidateMinT = projected.minOf { it.t }
        val candidateMaxT = projected.maxOf { it.t }
        if (candidateMinT < minT - width * MAX_AXIS_DRIFT_RATIO) return false
        if (candidateMaxT > maxT + width * MAX_AXIS_DRIFT_RATIO) return false
        val maxS = projected.maxOf { abs(it.s) }
        return maxS <= nominalHalfWidth * MAX_SHAPE_WIDTH_FACTOR + height * SHAPE_WIDTH_MARGIN
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
                if ((a.y <= y + HALF_PIXEL && b.y > y + HALF_PIXEL) ||
                    (b.y <= y + HALF_PIXEL && a.y > y + HALF_PIXEL)
                ) {
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

    private fun featureAt(pixels: IntArray, width: Int, height: Int, x: Int, y: Int): Feature? {
        if (x !in 0 until width || y !in 0 until height) return null
        val color = pixels[y * width + x]
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        return Feature(r, g, b, LUMA_R * r + LUMA_G * g + LUMA_B * b)
    }

    private fun meanFeature(features: List<Feature>): Feature {
        val n = features.size.toFloat()
        return Feature(
            features.sumOf { it.r.toDouble() }.toFloat() / n,
            features.sumOf { it.g.toDouble() }.toFloat() / n,
            features.sumOf { it.b.toDouble() }.toFloat() / n,
            features.sumOf { it.luma.toDouble() }.toFloat() / n,
        )
    }

    private fun distance(a: Feature, b: Feature): Float =
        (abs(a.r - b.r) * RGB_WEIGHT +
            abs(a.g - b.g) * RGB_WEIGHT +
            abs(a.b - b.b) * RGB_WEIGHT +
            abs(a.luma - b.luma) * LUMA_WEIGHT).coerceIn(0f, 1f)

    private fun polygonArea(points: List<PixelPoint>): Float =
        points.indices.fold(0f) { sum, index ->
            val a = points[index]
            val b = points[(index + 1) % points.size]
            sum + (a.x * b.y - b.x * a.y)
        } * HALF

    private companion object {
        const val MIN_SIZE = 8
        const val MIN_POLYGON_POINTS = 4
        const val MIN_AXIS_LENGTH = 6f
        const val MIN_HALF_WIDTH = 4f
        const val MAX_HALF_WIDTH = 55f
        const val HALF_WIDTH_SCALE = 0.5f
        const val ROI_HALF_WIDTH_SCALE = 0.5f
        const val SKIN_RING_FACTOR = 0.78f
        const val SAMPLE_STRIDE = 3
        const val MIN_SPREAD = 0.008f
        const val SPREAD_THRESHOLD = 2.4f
        const val MIN_COLOR_DISTANCE = 0.035f
        const val SEED_SEARCH_RADIUS = 12
        const val MIN_COMPONENT_PIXELS = 12
        const val SIDE_SAMPLE_STEP = 1f
        const val MAX_BOUNDARY_SAMPLES = 48f
        const val SAMPLE_TOLERANCE = 0.8f
        const val BOUNDARY_INSET = 0.5f
        const val PAINT_INSET_PX = 0.8f
        const val MIN_AREA_RATIO = 0.08f
        const val MAX_AREA_RATIO = 7f
        const val MAX_AXIS_DRIFT_RATIO = 0.08f
        const val MAX_SHAPE_WIDTH_FACTOR = 2.0f
        const val SHAPE_WIDTH_MARGIN = 2f
        const val SOLID_ALPHA = 255
        const val ALPHA_MASK = 255
        const val LUMA_R = 0.2126f
        const val LUMA_G = 0.7152f
        const val LUMA_B = 0.0722f
        const val RGB_WEIGHT = 0.25f
        const val LUMA_WEIGHT = 0.25f
        const val HALF = 0.5f
        const val HALF_PIXEL = 0.5f
        const val EPSILON = 1e-4f
        val DEFAULT_SKIN = Feature(0.33f, 0.33f, 0.34f, 0.33f)
        const val DEFAULT_SPREAD = 0.15f
    }
}
