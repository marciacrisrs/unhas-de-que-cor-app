package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Segmentador pensado do ponto de vista de quem vai pintar a unha:
 *
 * 1. começa no miolo da placa observada;
 * 2. aprende a aparência local de placa vs pele dentro da ROI;
 * 3. cresce somente enquanto a evidência continua compatível;
 * 4. usa borda/continuidade para impedir spill para cutícula e pele;
 * 5. extrai o contorno observado, sem impor almond/oval;
 * 6. aplica uma pequena margem interna de pintura para errar para dentro.
 *
 * MediaPipe/ROI é prior de busca, nunca a máscara final.
 * A análise acontece em resolução reduzida e o contorno é levado de volta para
 * a resolução original, evitando o custo do segmentador full-resolution anterior.
 */
@Singleton
class PaintAwareNailSegmenter @Inject constructor() : NailSegmenter {
    override fun segment(image: Bitmap, roi: NailRoi): NailMask? {
        val b = roi.bounds
        val width = b.width()
        val height = b.height()
        if (width < MIN_SIZE || height < MIN_SIZE) return null
        if (b.left < 0 || b.top < 0 || b.right > image.width || b.bottom > image.height) return null

        val full = IntArray(width * height)
        image.getPixels(full, 0, width, b.left, b.top, width, height)

        val scale = min(1f, ANALYSIS_LONG_EDGE / max(width, height).toFloat())
        val aw = max(MIN_ANALYSIS_SIZE, (width * scale).roundToInt())
        val ah = max(MIN_ANALYSIS_SIZE, (height * scale).roundToInt())
        val analysis = downsample(full, width, height, aw, ah)

        val geometric = roi.polygon.map { PixelPoint((it.x - b.left) * scale, (it.y - b.top) * scale) }
        if (geometric.size < 4) return null

        val base = PixelPoint((roi.axisFromDip.x - b.left) * scale, (roi.axisFromDip.y - b.top) * scale)
        val tip = PixelPoint((roi.axisToTip.x - b.left) * scale, (roi.axisToTip.y - b.top) * scale)
        val frame = Frame(base, tip)
        if (frame.length < MIN_AXIS_LENGTH) return null

        val projected = geometric.map(frame::project)
        val minT = projected.minOf { it.t } - abs(frame.length) * DOMAIN_MARGIN
        val maxT = projected.maxOf { it.t } + abs(frame.length) * DOMAIN_MARGIN
        val halfWidth = (roi.widthPx * 0.5f * scale).coerceIn(MIN_HALF_WIDTH, MAX_HALF_WIDTH)
        val seed = buildSeed(analysis, aw, ah, geometric, frame, minT, maxT, halfWidth)
        if (seed.count { it } < MIN_SEED_PIXELS) return null

        val models = buildModels(analysis, aw, ah, seed, geometric, frame)
        val grown = grow(analysis, aw, ah, seed, models, frame, minT, maxT, halfWidth) ?: return null
        val cleaned = cleanup(grown, aw, ah)
        val component = keepSeedComponent(cleaned, seed, aw, ah)
        if (component.count { it } < MIN_FILLED_PIXELS) return null

        val polygonAnalysis = boundary(component, aw, ah, frame, minT, maxT) ?: return null
        val polygonFull = polygonAnalysis.map { PixelPoint(it.x / scale, it.y / scale) }
        val safePolygon = insetForPainting(polygonFull, width, height)
        val fullFrame = Frame(
            PixelPoint(roi.axisFromDip.x - b.left, roi.axisFromDip.y - b.top),
            PixelPoint(roi.axisToTip.x - b.left, roi.axisToTip.y - b.top),
        )
        if (!shapeIsSafe(safePolygon, roi, fullFrame, minT / scale, maxT / scale)) return null

        val alpha = rasterizeAndFeather(safePolygon, width, height)
        return NailMask(
            width = width,
            height = height,
            alpha = alpha,
            originX = b.left,
            originY = b.top,
            boundaryPolygon = safePolygon.map { PixelPoint(it.x + b.left, it.y + b.top) },
        )
    }

    private fun downsample(full: IntArray, width: Int, height: Int, outWidth: Int, outHeight: Int): IntArray {
        val out = IntArray(outWidth * outHeight)
        for (y in 0 until outHeight) {
            val sy = min(height - 1, (y * height) / outHeight)
            for (x in 0 until outWidth) {
                val sx = min(width - 1, (x * width) / outWidth)
                out[y * outWidth + x] = full[sy * width + sx]
            }
        }
        return out
    }

    private fun buildSeed(
        pixels: IntArray,
        width: Int,
        height: Int,
        polygon: List<PixelPoint>,
        frame: Frame,
        minT: Float,
        maxT: Float,
        halfWidth: Float,
    ): BooleanArray {
        val seed = BooleanArray(pixels.size)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val p = PixelPoint(x + 0.5f, y + 0.5f)
                if (!pointInPolygon(p.x, p.y, polygon)) continue
                val q = frame.project(p)
                val t = normalize(q.t, minT, maxT)
                val s = abs(q.s) / halfWidth.coerceAtLeast(1f)
                if (t in SEED_T && s <= SEED_WIDTH) seed[y * width + x] = true
            }
        }
        return seed
    }

    private fun buildModels(
        pixels: IntArray,
        width: Int,
        height: Int,
        seed: BooleanArray,
        polygon: List<PixelPoint>,
        frame: Frame,
    ): Models {
        val fg = ArrayList<Feature>()
        val bg = ArrayList<Feature>()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val feature = featureOf(pixels[i])
                if (seed[i]) {
                    fg += feature
                } else {
                    val p = PixelPoint(x + 0.5f, y + 0.5f)
                    val q = frame.project(p)
                    val outside = !pointInPolygon(p.x, p.y, polygon)
                    if (outside || abs(q.s) > abs(frame.length) * BACKGROUND_LATERAL || normalize(q.t, 0f, frame.length) !in BACKGROUND_T) {
                        bg += feature
                    }
                }
            }
        }
        return Models(modelOf(fg), modelOf(bg))
    }

    private fun grow(
        pixels: IntArray,
        width: Int,
        height: Int,
        seed: BooleanArray,
        models: Models,
        frame: Frame,
        minT: Float,
        maxT: Float,
        halfWidth: Float,
    ): BooleanArray? {
        val scores = FloatArray(pixels.size) { Float.NEGATIVE_INFINITY }
        var seedTotal = 0f
        var seedCount = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val p = PixelPoint(x + 0.5f, y + 0.5f)
                val q = frame.project(p)
                val t = normalize(q.t, minT, maxT)
                val lateral = abs(q.s) / halfWidth.coerceAtLeast(1f)
                if (t !in SEARCH_T || lateral > SEARCH_WIDTH) continue
                val score = appearanceScore(featureOf(pixels[i]), models)
                scores[i] = score
                if (seed[i]) {
                    seedTotal += score
                    seedCount++
                }
            }
        }
        if (seedCount == 0) return null
        val threshold = max(MIN_SCORE, seedTotal / seedCount - SCORE_TOLERANCE)

        val result = BooleanArray(pixels.size)
        val queue = ArrayDeque<Int>()
        for (i in seed.indices) {
            if (seed[i] && scores[i] >= threshold) {
                result[i] = true
                queue.add(i)
            }
        }
        if (queue.isEmpty()) return null

        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val x = i % width
            val y = i / width
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx !in 1 until width - 1 || ny !in 1 until height - 1) continue
                val ni = ny * width + nx
                if (result[ni] || scores[ni] < threshold) continue
                val edge = edgeScore(pixels, width, x, y, nx, ny)
                if (edge > MAX_CROSS_EDGE && scores[ni] < threshold + STRONG_EDGE_BONUS) continue
                result[ni] = true
                queue.add(ni)
            }
        }
        return result
    }

    private fun cleanup(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        var current = mask
        repeat(CLEANUP_PASSES) {
            val next = current.copyOf()
            for (y in 1 until height - 1) for (x in 1 until width - 1) {
                var neighbors = 0
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx != 0 || dy != 0) if (current[(y + dy) * width + x + dx]) neighbors++
                }
                val i = y * width + x
                if (current[i] && neighbors < REMOVE_NEIGHBORS) next[i] = false
                if (!current[i] && neighbors >= ADD_NEIGHBORS) next[i] = true
            }
            current = next
        }
        return current
    }

    private fun keepSeedComponent(mask: BooleanArray, seed: BooleanArray, width: Int, height: Int): BooleanArray {
        val result = BooleanArray(mask.size)
        val queue = ArrayDeque<Int>()
        seed.indices.filter { seed[it] && mask[it] }.forEach {
            result[it] = true
            queue.add(it)
        }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val x = i % width
            val y = i / width
            for (dy in -1..1) for (dx in -1..1) {
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
        return result
    }

    private fun boundary(mask: BooleanArray, width: Int, height: Int, frame: Frame, minT: Float, maxT: Float): List<PixelPoint>? {
        val minS = FloatArray(BOUNDARY_BINS) { Float.POSITIVE_INFINITY }
        val maxS = FloatArray(BOUNDARY_BINS) { Float.NEGATIVE_INFINITY }
        val seen = BooleanArray(BOUNDARY_BINS)
        val span = (maxT - minT).coerceAtLeast(1f)
        for (y in 0 until height) for (x in 0 until width) {
            if (!mask[y * width + x]) continue
            val q = frame.project(PixelPoint(x + 0.5f, y + 0.5f))
            val t = normalize(q.t, minT, maxT)
            if (t !in 0f..1f) continue
            val bin = min(BOUNDARY_BINS - 1, (t * BOUNDARY_BINS).toInt())
            seen[bin] = true
            minS[bin] = min(minS[bin], q.s)
            maxS[bin] = max(maxS[bin], q.s)
        }
        if (seen.count { it } < MIN_BOUNDARY_BINS) return null
        val leftS = smoothBins(minS, seen)
        val rightS = smoothBins(maxS, seen)
        val left = ArrayList<PixelPoint>(BOUNDARY_BINS)
        val right = ArrayList<PixelPoint>(BOUNDARY_BINS)
        for (i in 0 until BOUNDARY_BINS) {
            if (!seen[i]) continue
            val t = minT + span * (i + 0.5f) / BOUNDARY_BINS
            left += frame.point(t, leftS[i])
            right += frame.point(t, rightS[i])
        }
        if (left.size < MIN_BOUNDARY_BINS || right.size < MIN_BOUNDARY_BINS) return null
        return left + right.asReversed()
    }

    private fun smoothBins(values: FloatArray, seen: BooleanArray): FloatArray {
        val out = values.copyOf()
        for (i in values.indices) {
            if (!seen[i]) continue
            var sum = 0f
            var count = 0
            for (j in max(0, i - SMOOTH_RADIUS)..min(values.lastIndex, i + SMOOTH_RADIUS)) {
                if (seen[j] && values[j].isFinite()) {
                    sum += values[j]
                    count++
                }
            }
            if (count > 0) out[i] = sum / count
        }
        return out
    }

    private fun insetForPainting(polygon: List<PixelPoint>, width: Int, height: Int): List<PixelPoint> {
        if (polygon.size < 3) return polygon
        val cx = polygon.map { it.x }.average().toFloat()
        val cy = polygon.map { it.y }.average().toFloat()
        return polygon.map { p ->
            val dx = cx - p.x
            val dy = cy - p.y
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val inset = PAINT_MARGIN_PX.coerceAtMost(d * 0.25f)
            PixelPoint(
                (p.x + dx / d * inset).coerceIn(0f, width - 1f),
                (p.y + dy / d * inset).coerceIn(0f, height - 1f),
            )
        }
    }

    private fun shapeIsSafe(
        polygon: List<PixelPoint>,
        roi: NailRoi,
        frame: Frame,
        minT: Float,
        maxT: Float,
    ): Boolean {
        if (polygon.size < MIN_BOUNDARY_BINS * 2) return false
        val roiArea = (roi.widthPx * roi.lengthPx).coerceAtLeast(1f)
        val area = abs(area(polygon))
        if (area < roiArea * MIN_AREA_RATIO || area > roiArea * MAX_AREA_RATIO) return false
        val maxLateral = polygon.maxOf { abs(frame.project(it).s) }
        if (maxLateral > roi.widthPx * MAX_LATERAL_FACTOR) return false
        val projected = polygon.map(frame::project)
        return projected.minOf { it.t } >= minT - MAX_T_DRIFT && projected.maxOf { it.t } <= maxT + MAX_T_DRIFT
    }

    private fun rasterizeAndFeather(polygon: List<PixelPoint>, width: Int, height: Int): ByteArray {
        val mask = BooleanArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            if (pointInPolygon(x + 0.5f, y + 0.5f, polygon)) mask[y * width + x] = true
        }
        val out = ByteArray(mask.size)
        for (y in 0 until height) for (x in 0 until width) {
            val i = y * width + x
            if (!mask[i]) continue
            var edge = false
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until width || ny !in 0 until height || !mask[ny * width + nx]) edge = true
            }
            out[i] = if (edge) EDGE_ALPHA.toByte() else SOLID_ALPHA.toByte()
        }
        return out
    }

    private fun featureOf(color: Int): Feature {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val sum = (r + g + b).coerceAtLeast(1)
        val maxC = max(r, max(g, b)).toFloat()
        val minC = min(r, min(g, b)).toFloat()
        return Feature(
            luma = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f,
            saturation = (maxC - minC) / maxC.coerceAtLeast(1f),
            chromaR = r.toFloat() / sum,
            chromaG = g.toFloat() / sum,
            chromaB = b.toFloat() / sum,
        )
    }

    private fun modelOf(features: List<Feature>): Model {
        val safe = if (features.isEmpty()) listOf(Feature(0.5f, 0.2f, 0.33f, 0.33f, 0.34f)) else features
        fun mean(selector: (Feature) -> Float) = safe.sumOf { selector(it).toDouble() }.toFloat() / safe.size
        fun variance(selector: (Feature) -> Float, mean: Float): Float =
            max(MIN_VARIANCE, safe.sumOf { val d = selector(it) - mean; (d * d).toDouble() }.toFloat() / safe.size)
        val l = mean { it.luma }
        val s = mean { it.saturation }
        val r = mean { it.chromaR }
        val g = mean { it.chromaG }
        val b = mean { it.chromaB }
        return Model(l, s, r, g, b, variance({ it.luma }, l), variance({ it.saturation }, s), variance({ it.chromaR }, r), variance({ it.chromaG }, g), variance({ it.chromaB }, b))
    }

    private fun appearanceScore(feature: Feature, models: Models): Float {
        val fg = distance(feature, models.foreground)
        val bg = distance(feature, models.background)
        return (bg - fg).coerceIn(-2f, 2f)
    }

    private fun distance(f: Feature, m: Model): Float {
        val l = sq((f.luma - m.luma) / sqrt(m.varLuma))
        val s = sq((f.saturation - m.saturation) / sqrt(m.varSaturation))
        val r = sq((f.chromaR - m.chromaR) / sqrt(m.varChromaR))
        val g = sq((f.chromaG - m.chromaG) / sqrt(m.varChromaG))
        val b = sq((f.chromaB - m.chromaB) / sqrt(m.varChromaB))
        return (l * LUMA_WEIGHT + s * SATURATION_WEIGHT + (r + g + b) / 3f * CHROMA_WEIGHT).coerceAtMost(MAX_DISTANCE)
    }

    private fun edgeScore(pixels: IntArray, width: Int, x1: Int, y1: Int, x2: Int, y2: Int): Float {
        val a = featureOf(pixels[y1 * width + x1])
        val b = featureOf(pixels[y2 * width + x2])
        return abs(a.luma - b.luma) * 0.55f + abs(a.saturation - b.saturation) * 0.15f +
            (abs(a.chromaR - b.chromaR) + abs(a.chromaG - b.chromaG) + abs(a.chromaB - b.chromaB)) / 3f * 0.30f
    }

    private fun pointInPolygon(x: Float, y: Float, polygon: List<PixelPoint>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            if (((a.y > y) != (b.y > y)) && x < (b.x - a.x) * (y - a.y) / ((b.y - a.y).takeIf { abs(it) > EPSILON } ?: EPSILON) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    private fun area(points: List<PixelPoint>): Float =
        points.indices.sumOf { i ->
            val a = points[i]
            val b = points[(i + 1) % points.size]
            (a.x * b.y - b.x * a.y).toDouble()
        }.toFloat() * 0.5f

    private fun normalize(value: Float, minValue: Float, maxValue: Float): Float =
        (value - minValue) / (maxValue - minValue).coerceAtLeast(EPSILON)

    private fun sq(value: Float): Float = value * value

    private data class Frame(val base: PixelPoint, val tip: PixelPoint) {
        val dx = tip.x - base.x
        val dy = tip.y - base.y
        val length = sqrt(dx * dx + dy * dy)
        val ux = dx / length.coerceAtLeast(EPSILON)
        val uy = dy / length.coerceAtLeast(EPSILON)
        val vx = -uy
        val vy = ux
        fun project(p: PixelPoint): Projection = Projection(
            t = (p.x - base.x) * ux + (p.y - base.y) * uy,
            s = (p.x - base.x) * vx + (p.y - base.y) * vy,
        )
        fun point(t: Float, s: Float): PixelPoint = PixelPoint(base.x + ux * t + vx * s, base.y + uy * t + vy * s)
    }

    private data class Projection(val t: Float, val s: Float)
    private data class Feature(val luma: Float, val saturation: Float, val chromaR: Float, val chromaG: Float, val chromaB: Float)
    private data class Model(val luma: Float, val saturation: Float, val chromaR: Float, val chromaG: Float, val chromaB: Float, val varLuma: Float, val varSaturation: Float, val varChromaR: Float, val varChromaG: Float, val varChromaB: Float)
    private data class Models(val foreground: Model, val background: Model)

    private companion object {
        const val MIN_SIZE = 8
        const val MIN_ANALYSIS_SIZE = 12
        const val ANALYSIS_LONG_EDGE = 96
        const val MIN_AXIS_LENGTH = 6f
        const val MIN_HALF_WIDTH = 4f
        const val MAX_HALF_WIDTH = 55f
        const val DOMAIN_MARGIN = 0.10f
        const val SEED_WIDTH = 0.52f
        const val SEARCH_WIDTH = 1.30f
        const val BACKGROUND_LATERAL = 0.90f
        const val MIN_SEED_PIXELS = 6
        const val MIN_BOUNDARY_BINS = 8
        const val BOUNDARY_BINS = 20
        const val SMOOTH_RADIUS = 1
        const val CLEANUP_PASSES = 1
        const val REMOVE_NEIGHBORS = 2
        const val ADD_NEIGHBORS = 6
        const val SCORE_TOLERANCE = 0.48f
        const val MIN_SCORE = -0.05f
        const val MAX_CROSS_EDGE = 0.34f
        const val STRONG_EDGE_BONUS = 0.24f
        const val MIN_FILLED_PIXELS = 12
        const val MIN_AREA_RATIO = 0.12f
        const val MAX_AREA_RATIO = 5.5f
        const val MAX_LATERAL_FACTOR = 1.45f
        const val MAX_T_DRIFT = 16f
        const val PAINT_MARGIN_PX = 0.9f
        const val EDGE_ALPHA = 175
        const val SOLID_ALPHA = 255
        const val MIN_VARIANCE = 0.00002f
        const val LUMA_WEIGHT = 0.42f
        const val SATURATION_WEIGHT = 0.18f
        const val CHROMA_WEIGHT = 0.40f
        const val MAX_DISTANCE = 40f
        const val EPSILON = 1e-5f
        val SEED_T = 0.18f..0.78f
        val SEARCH_T = -0.12f..1.18f
        val BACKGROUND_T = 0.04f..0.96f
    }
}
