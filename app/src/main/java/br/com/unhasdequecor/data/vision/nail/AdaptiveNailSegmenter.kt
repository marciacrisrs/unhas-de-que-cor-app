package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hybrid nail segmenter that combines four experts:
 *
 * 1. MediaPipe/ROI geometry: where the nail is expected to be.
 * 2. EvidenceNailSegmenter: strong boundary observations when visible.
 * 3. Nail-surface appearance: local foreground/background color model.
 * 4. Connectivity/shape: the final mask must be one coherent nail plate.
 *
 * The important design choice is that the geometric prior is a search space,
 * not the final paint boundary. This lets the surface expert grow a small
 * initial detection toward the real plate when the ROI geometry is undersized.
 */
@Singleton
class AdaptiveNailSegmenter @Inject constructor(
    private val evidence: EvidenceNailSegmenter,
) : NailSegmenter {

    override fun segment(image: Bitmap, roi: NailRoi): NailMask? {
        val bounds = roi.bounds
        val width = bounds.width()
        val height = bounds.height()
        if (width < MIN_SIZE || height < MIN_SIZE) return null
        if (bounds.left < 0 || bounds.top < 0 ||
            bounds.right > image.width || bounds.bottom > image.height
        ) return null

        val pixels = IntArray(width * height)
        image.getPixels(pixels, 0, width, bounds.left, bounds.top, width, height)

        val axis = Axis(
            base = PixelPoint(roi.axisFromDip.x - bounds.left, roi.axisFromDip.y - bounds.top),
            tip = PixelPoint(roi.axisToTip.x - bounds.left, roi.axisToTip.y - bounds.top),
        )
        if (axis.length < MIN_AXIS_LENGTH) return null

        val frame = AxisFrame(axis.base, axis.tip)
        val halfWidth = (roi.widthPx * 0.5f)
            .coerceIn(MIN_HALF_WIDTH, MAX_HALF_WIDTH)
        val domain = domain(frame, width, height, halfWidth)

        val evidenceMask = evidence.segment(image, roi)
        val seed = buildSeed(
            pixels = pixels,
            width = width,
            height = height,
            frame = frame,
            halfWidth = halfWidth,
            domain = domain,
            evidenceMask = evidenceMask,
            roi = roi,
            originX = bounds.left,
            originY = bounds.top,
        ) ?: return null

        val models = buildModels(
            pixels = pixels,
            width = width,
            height = height,
            seed = seed,
            frame = frame,
            halfWidth = halfWidth,
            domain = domain,
        ) ?: return null

        val labels = growSurface(
            pixels = pixels,
            width = width,
            height = height,
            seed = seed,
            models = models,
            frame = frame,
            halfWidth = halfWidth,
            domain = domain,
        ) ?: return null

        val cleaned = cleanup(labels, width, height)
        val component = keepSeedComponent(cleaned, seed, width, height)
        val filled = component.count { it }
        if (filled < MIN_FILLED_PIXELS) return null

        val polygon = extractBoundary(component, width, height, frame, domain) ?: return null
        if (!passesShapeChecks(polygon, roi, frame, halfWidth, domain)) return null

        val alpha = feather(component, width, height)
        return NailMask(
            width = width,
            height = height,
            alpha = alpha,
            originX = bounds.left,
            originY = bounds.top,
            boundaryPolygon = polygon.map {
                PixelPoint(it.x + bounds.left, it.y + bounds.top)
            },
        )
    }

    private fun buildSeed(
        pixels: IntArray,
        width: Int,
        height: Int,
        frame: AxisFrame,
        halfWidth: Float,
        domain: Domain,
        evidenceMask: NailMask?,
        roi: NailRoi,
        originX: Int,
        originY: Int,
    ): BooleanArray? {
        val seed = BooleanArray(width * height)
        if (evidenceMask != null) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val alpha = evidenceMask.coverageAt(x, y)
                    if (alpha >= SOLID_ALPHA) seed[y * width + x] = true
                }
            }
        }

        // Always add a conservative central plate seed. It prevents a weak
        // boundary detector from becoming the only source of truth.
        val geometric = roi.polygon.map {
            PixelPoint(it.x - originX, it.y - originY)
        }
        for (y in domain.minY..domain.maxY) {
            for (x in domain.minX..domain.maxX) {
                val p = PixelPoint(x + 0.5f, y + 0.5f)
                val projected = frame.project(p)
                val normalizedT = normalized(projected.first, domain.minT, domain.maxT)
                val normalizedS = abs(projected.second) / halfWidth
                val central = normalizedT in CENTRAL_T_RANGE && normalizedS <= CENTRAL_WIDTH
                val inGeometry = geometric.size >= 3 && pointInPolygon(p.x, p.y, geometric)
                if (central && inGeometry) seed[y * width + x] = true
            }
        }

        if (seed.count { it } < MIN_SEED_PIXELS) return null
        return seed
    }

    private fun buildModels(
        pixels: IntArray,
        width: Int,
        height: Int,
        seed: BooleanArray,
        frame: AxisFrame,
        halfWidth: Float,
        domain: Domain,
    ): Models? {
        val foreground = ArrayList<Feature>()
        val background = ArrayList<Feature>()

        for (y in domain.minY..domain.maxY) {
            for (x in domain.minX..domain.maxX) {
                val feature = featureAt(pixels[y * width + x])
                if (seed[y * width + x]) foreground += feature
                else if (isBackgroundSample(x, y, frame, halfWidth, domain)) background += feature
            }
        }

        if (foreground.size < MIN_MODEL_SAMPLES || background.size < MIN_MODEL_SAMPLES) return null
        return Models(
            foreground = modelOf(foreground),
            background = modelOf(background),
        )
    }

    private fun growSurface(
        pixels: IntArray,
        width: Int,
        height: Int,
        seed: BooleanArray,
        models: Models,
        frame: AxisFrame,
        halfWidth: Float,
        domain: Domain,
    ): BooleanArray? {
        val score = FloatArray(width * height) { Float.NEGATIVE_INFINITY }
        var seedScoreSum = 0f
        var seedCount = 0

        for (y in domain.minY..domain.maxY) {
            for (x in domain.minX..domain.maxX) {
                val idx = y * width + x
                val p = frame.project(PixelPoint(x + 0.5f, y + 0.5f))
                val normalizedT = normalized(p.first, domain.minT, domain.maxT)
                val lateral = abs(p.second) / halfWidth
                if (normalizedT !in SEARCH_T_RANGE || lateral > SEARCH_WIDTH) continue
                val s = appearanceScore(featureAt(pixels[idx]), models)
                score[idx] = s
                if (seed[idx]) {
                    seedScoreSum += s
                    seedCount++
                }
            }
        }
        if (seedCount == 0) return null

        val meanSeed = seedScoreSum / seedCount
        val threshold = max(MIN_SCORE, meanSeed - SCORE_TOLERANCE)

        val result = BooleanArray(width * height)
        val queue = ArrayDeque<Int>()
        for (i in seed.indices) {
            if (seed[i] && score[i] >= threshold) {
                result[i] = true
                queue.add(i)
            }
        }
        if (queue.isEmpty()) return null

        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            val x = index % width
            val y = index / width
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in domain.minX..domain.maxX || ny !in domain.minY..domain.maxY) continue
                    val ni = ny * width + nx
                    if (result[ni]) continue
                    val s = score[ni]
                    if (s < threshold) continue
                    val edge = localEdge(
                        pixels = pixels,
                        width = width,
                        height = height,
                        x = nx,
                        y = ny,
                        dx = dx,
                        dy = dy,
                    )
                    val relaxed = s >= threshold + STRONG_EDGE_BONUS || edge <= MAX_GROW_EDGE
                    if (!relaxed) continue
                    result[ni] = true
                    queue.add(ni)
                }
            }
        }
        return result
    }

    private fun cleanup(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        var current = mask
        repeat(CLEANUP_PASSES) {
            val next = current.copyOf()
            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val index = y * width + x
                    var neighbors = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            if (current[(y + dy) * width + (x + dx)]) neighbors++
                        }
                    }
                    if (current[index] && neighbors <= REMOVE_IF_FEW_NEIGHBORS) next[index] = false
                    if (!current[index] && neighbors >= ADD_IF_MANY_NEIGHBORS) next[index] = true
                }
            }
            current = next
        }
        return current
    }

    private fun keepSeedComponent(mask: BooleanArray, seed: BooleanArray, width: Int, height: Int): BooleanArray {
        val result = BooleanArray(mask.size)
        val queue = ArrayDeque<Int>()
        val start = seed.indices.firstOrNull { seed[it] && mask[it] } ?: return result
        result[start] = true
        queue.add(start)
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            val x = index % width
            val y = index / width
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val ni = ny * width + nx
                    if (mask[ni] && !result[ni]) {
                        result[ni] = true
                        queue.add(ni)
                    }
                }
            }
        }
        return result
    }

    private fun extractBoundary(
        mask: BooleanArray,
        width: Int,
        height: Int,
        frame: AxisFrame,
        domain: Domain,
    ): List<PixelPoint>? {
        val left = ArrayList<PixelPoint>()
        val right = ArrayList<PixelPoint>()
        for (i in 0 until BOUNDARY_SAMPLES) {
            val t = domain.minT + (domain.maxT - domain.minT) * i / (BOUNDARY_SAMPLES - 1f)
            var minS = Float.POSITIVE_INFINITY
            var maxS = Float.NEGATIVE_INFINITY
            val halfBand = BOUNDARY_T_BAND
            for (y in domain.minY..domain.maxY) {
                for (x in domain.minX..domain.maxX) {
                    if (!mask[y * width + x]) continue
                    val projected = frame.project(PixelPoint(x + 0.5f, y + 0.5f))
                    if (abs(projected.first - t) > halfBand) continue
                    minS = min(minS, projected.second)
                    maxS = max(maxS, projected.second)
                }
            }
            if (minS.isFinite() && maxS.isFinite()) {
                left += frame.point(t, minS)
                right += frame.point(t, maxS)
            }
        }
        if (left.size < MIN_BOUNDARY_POINTS || right.size < MIN_BOUNDARY_POINTS) return null
        val polygon = ArrayList<PixelPoint>(left.size + right.size)
        polygon += left
        polygon += right.asReversed()
        return polygon
    }

    private fun passesShapeChecks(
        polygon: List<PixelPoint>,
        roi: NailRoi,
        frame: AxisFrame,
        halfWidth: Float,
        domain: Domain,
    ): Boolean {
        if (polygon.size < MIN_BOUNDARY_POINTS * 2) return false
        val area = abs(polygonArea(polygon))
        val roiArea = (roi.lengthPx * roi.widthPx).coerceAtLeast(1f)
        if (area < roiArea * MIN_AREA_RATIO || area > roiArea * MAX_AREA_RATIO) return false
        val projected = polygon.map(frame::project)
        val minT = projected.minOf { it.first }
        val maxT = projected.maxOf { it.first }
        if (minT < domain.minT - MAX_T_DRIFT || maxT > domain.maxT + MAX_T_DRIFT) return false
        val maxLateral = projected.maxOf { abs(it.second) }
        return maxLateral <= halfWidth * MAX_LATERAL_FACTOR
    }

    private fun isBackgroundSample(
        x: Int,
        y: Int,
        frame: AxisFrame,
        halfWidth: Float,
        domain: Domain,
    ): Boolean {
        val p = frame.project(PixelPoint(x + 0.5f, y + 0.5f))
        val lateral = abs(p.second) / halfWidth
        val t = normalized(p.first, domain.minT, domain.maxT)
        return lateral >= BACKGROUND_LATERAL_MIN || t <= BACKGROUND_T_MIN || t >= BACKGROUND_T_MAX
    }

    private fun appearanceScore(feature: Feature, models: Models): Float {
        val fg = distance(feature, models.foreground)
        val bg = distance(feature, models.background)
        return (bg - fg).coerceIn(-2f, 2f)
    }

    private fun modelOf(features: List<Feature>): Model {
        fun mean(selector: (Feature) -> Float): Float =
            features.sumOf { selector(it).toDouble() }.toFloat() / features.size
        fun variance(selector: (Feature) -> Float, m: Float): Float =
            (features.sumOf { val d = selector(it) - m; (d * d).toDouble() }.toFloat() / features.size)
                .coerceAtLeast(MIN_VARIANCE)
        val l = mean { it.luma }
        val s = mean { it.saturation }
        val cr = mean { it.chromaR }
        val cg = mean { it.chromaG }
        val cb = mean { it.chromaB }
        return Model(
            luma = l,
            saturation = s,
            chromaR = cr,
            chromaG = cg,
            chromaB = cb,
            varLuma = variance({ it.luma }, l),
            varSaturation = variance({ it.saturation }, s),
            varChromaR = variance({ it.chromaR }, cr),
            varChromaG = variance({ it.chromaG }, cg),
            varChromaB = variance({ it.chromaB }, cb),
        )
    }

    private fun distance(feature: Feature, model: Model): Float {
        val l = sq((feature.luma - model.luma) / sqrt(model.varLuma))
        val s = sq((feature.saturation - model.saturation) / sqrt(model.varSaturation))
        val r = sq((feature.chromaR - model.chromaR) / sqrt(model.varChromaR))
        val g = sq((feature.chromaG - model.chromaG) / sqrt(model.varChromaG))
        val b = sq((feature.chromaB - model.chromaB) / sqrt(model.varChromaB))
        return (l * LUMA_WEIGHT + s * SATURATION_WEIGHT + (r + g + b) / 3f * CHROMA_WEIGHT)
            .coerceAtMost(MAX_DISTANCE)
    }

    private fun localEdge(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        dx: Int,
        dy: Int,
    ): Float {
        val ax = (x - dx).coerceIn(0, width - 1)
        val ay = (y - dy).coerceIn(0, height - 1)
        val a = featureAt(pixels[ay * width + ax])
        val b = featureAt(pixels[y * width + x])
        return abs(a.luma - b.luma) * 0.55f +
            abs(a.saturation - b.saturation) * 0.15f +
            (abs(a.chromaR - b.chromaR) + abs(a.chromaG - b.chromaG) + abs(a.chromaB - b.chromaB)) / 3f * 0.30f
    }

    private fun featureAt(color: Int): Feature {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val sum = (r + g + b).coerceAtLeast(1)
        val maxChannel = max(r, max(g, b)).toFloat()
        val minChannel = min(r, min(g, b)).toFloat()
        return Feature(
            luma = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f,
            saturation = (maxChannel - minChannel) / maxChannel.coerceAtLeast(1f),
            chromaR = r.toFloat() / sum,
            chromaG = g.toFloat() / sum,
            chromaB = b.toFloat() / sum,
        )
    }

    private fun domain(frame: AxisFrame, width: Int, height: Int, halfWidth: Float): Domain {
        val corners = listOf(
            PixelPoint(0f, 0f),
            PixelPoint(width.toFloat(), 0f),
            PixelPoint(0f, height.toFloat()),
            PixelPoint(width.toFloat(), height.toFloat()),
        ).map(frame::project)
        val minT = corners.minOf { it.first } - halfWidth * DOMAIN_T_PADDING
        val maxT = corners.maxOf { it.first } + halfWidth * DOMAIN_T_PADDING
        val minY = 0
        val maxY = height - 1
        val minX = 0
        val maxX = width - 1
        return Domain(
            minT = minT,
            maxT = maxT,
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
        )
    }

    private fun feather(mask: BooleanArray, width: Int, height: Int): ByteArray {
        val out = ByteArray(mask.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (mask[index]) {
                    var edge = false
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val nx = x + dx
                            val ny = y + dy
                            if (nx !in 0 until width || ny !in 0 until height || !mask[ny * width + nx]) edge = true
                        }
                    }
                    out[index] = if (edge) EDGE_ALPHA.toByte() else SOLID_ALPHA.toByte()
                }
            }
        }
        return out
    }

    private fun pointInPolygon(x: Float, y: Float, polygon: List<PixelPoint>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            if (((a.y > y) != (b.y > y)) &&
                x < (b.x - a.x) * (y - a.y) / ((b.y - a.y).takeIf { abs(it) > EPSILON } ?: EPSILON) + a.x
            ) inside = !inside
            j = i
        }
        return inside
    }

    private fun polygonArea(points: List<PixelPoint>): Float {
        var area = 0f
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            area += a.x * b.y - b.x * a.y
        }
        return area * 0.5f
    }

    private fun normalized(value: Float, minValue: Float, maxValue: Float): Float =
        ((value - minValue) / (maxValue - minValue).coerceAtLeast(EPSILON))

    private fun sq(value: Float): Float = value * value

    private data class Axis(val base: PixelPoint, val tip: PixelPoint) {
        val length: Float
            get() = sqrt((tip.x - base.x).let { it * it } + (tip.y - base.y).let { it * it })
    }

    private data class Domain(
        val minT: Float,
        val maxT: Float,
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int,
    )

    private data class Feature(
        val luma: Float,
        val saturation: Float,
        val chromaR: Float,
        val chromaG: Float,
        val chromaB: Float,
    )

    private data class Model(
        val luma: Float,
        val saturation: Float,
        val chromaR: Float,
        val chromaG: Float,
        val chromaB: Float,
        val varLuma: Float,
        val varSaturation: Float,
        val varChromaR: Float,
        val varChromaG: Float,
        val varChromaB: Float,
    )

    private data class Models(val foreground: Model, val background: Model)

    private class AxisFrame(base: PixelPoint, tip: PixelPoint) {
        private val dx = tip.x - base.x
        private val dy = tip.y - base.y
        private val length = sqrt(dx * dx + dy * dy).coerceAtLeast(EPSILON)
        private val ux = dx / length
        private val uy = dy / length
        private val vx = -uy
        private val vy = ux
        private val origin = base

        fun project(point: PixelPoint): Pair<Float, Float> =
            ((point.x - origin.x) * ux + (point.y - origin.y) * uy) to
                ((point.x - origin.x) * vx + (point.y - origin.y) * vy)

        fun point(t: Float, s: Float): PixelPoint =
            PixelPoint(origin.x + ux * t + vx * s, origin.y + uy * t + vy * s)
    }

    private companion object {
        const val MIN_SIZE = 8
        const val MIN_AXIS_LENGTH = 6f
        const val MIN_HALF_WIDTH = 4f
        const val MAX_HALF_WIDTH = 55f
        const val CENTRAL_WIDTH = 0.55f
        const val CENTRAL_T_RANGE_START = 0.18f
        const val CENTRAL_T_RANGE_END = 0.78f
        const val SEARCH_WIDTH = 1.15f
        const val SEARCH_T_START = -0.08f
        const val SEARCH_T_END = 1.10f
        const val BACKGROUND_LATERAL_MIN = 0.86f
        const val BACKGROUND_T_MIN = 0.02f
        const val BACKGROUND_T_MAX = 0.98f
        const val MIN_SEED_PIXELS = 6
        const val MIN_MODEL_SAMPLES = 8
        const val MIN_VARIANCE = 0.00002f
        const val MIN_SCORE = -0.05f
        const val SCORE_TOLERANCE = 0.55f
        const val MAX_GROW_EDGE = 0.30f
        const val STRONG_EDGE_BONUS = 0.28f
        const val CLEANUP_PASSES = 2
        const val REMOVE_IF_FEW_NEIGHBORS = 2
        const val ADD_IF_MANY_NEIGHBORS = 6
        const val BOUNDARY_SAMPLES = 28
        const val BOUNDARY_T_BAND = 2.2f
        const val MIN_BOUNDARY_POINTS = 8
        const val MIN_FILLED_PIXELS = 12
        const val MIN_AREA_RATIO = 0.18f
        const val MAX_AREA_RATIO = 3.0f
        const val MAX_T_DRIFT = 12f
        const val MAX_LATERAL_FACTOR = 1.25f
        const val DOMAIN_T_PADDING = 0.12f
        const val LUMA_WEIGHT = 0.42f
        const val SATURATION_WEIGHT = 0.18f
        const val CHROMA_WEIGHT = 0.40f
        const val MAX_DISTANCE = 40f
        const val SOLID_ALPHA = 255
        const val EDGE_ALPHA = 170
        const val EPSILON = 1e-5f
        val CENTRAL_T_RANGE = CENTRAL_T_RANGE_START..CENTRAL_T_RANGE_END
        val SEARCH_T_RANGE = SEARCH_T_START..SEARCH_T_END
    }
}
