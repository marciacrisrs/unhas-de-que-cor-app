package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import java.util.ArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Paint-safe nail segmenter.
 *
 * The ROI is only a search prior. The final mask is built by tracing the four
 * observable plate boundaries: proximal/cuticle, two lateral walls and tip.
 */
@Singleton
class PaintAwareNailSegmenter @Inject constructor() : NailSegmenter {
    override fun segment(image: Bitmap, roi: NailRoi): NailMask? {
        val bounds = roi.bounds
        val fullWidth = bounds.width()
        val fullHeight = bounds.height()
        if (fullWidth < MIN_SIZE || fullHeight < MIN_SIZE) return null
        if (bounds.left < 0 || bounds.top < 0 || bounds.right > image.width || bounds.bottom > image.height) return null
        val source = IntArray(fullWidth * fullHeight)
        image.getPixels(source, 0, fullWidth, bounds.left, bounds.top, fullWidth, fullHeight)
        val scale = min(1f, ANALYSIS_LONG_EDGE / max(fullWidth, fullHeight).toFloat())
        val width = max(MIN_ANALYSIS_SIZE, (fullWidth * scale).roundToInt())
        val height = max(MIN_ANALYSIS_SIZE, (fullHeight * scale).roundToInt())
        val pixels = downsample(source, fullWidth, fullHeight, width, height)
        val geometric = roi.polygon.map { PixelPoint((it.x - bounds.left) * scale, (it.y - bounds.top) * scale) }
        if (geometric.size < 4) return null
        val axisBase = PixelPoint((roi.axisFromDip.x - bounds.left) * scale, (roi.axisFromDip.y - bounds.top) * scale)
        val axisTip = PixelPoint((roi.axisToTip.x - bounds.left) * scale, (roi.axisToTip.y - bounds.top) * scale)
        val frame = Frame(axisBase, axisTip)
        if (frame.length < MIN_AXIS_LENGTH) return null
        val projections = geometric.map(frame::project)
        val geometricMinT = projections.minOf { it.t }
        val geometricMaxT = projections.maxOf { it.t }
        val nominalHalfWidth = (roi.widthPx * 0.5f * scale).coerceIn(MIN_HALF_WIDTH, MAX_HALF_WIDTH)
        val roiHalfWidth = (fullWidth * 0.5f * scale).coerceIn(MIN_HALF_WIDTH, MAX_HALF_WIDTH)
        val skinModel = buildSkinModel(pixels, width, height, frame, geometricMinT, geometricMaxT, roiHalfWidth)
        val cuticle = findEndBoundary(pixels, width, height, frame, geometricMinT, geometricMaxT, nominalHalfWidth, roiHalfWidth, skinModel, proximal = true)
        val tip = findEndBoundary(pixels, width, height, frame, geometricMinT, geometricMaxT, nominalHalfWidth, roiHalfWidth, skinModel, proximal = false)
        val traced = traceSides(pixels, width, height, frame, cuticle.t, tip.t, nominalHalfWidth, roiHalfWidth, geometric, skinModel)
        val polygonAnalysis = buildPolygon(frame, cuticle, tip, traced)
        val polygonFull = polygonAnalysis.map { PixelPoint(it.x / scale, it.y / scale) }
        val safe = insetForPainting(polygonFull, fullWidth, fullHeight)
        if (!isSafeShape(safe, roi, frame.scaleToFull(scale), fullWidth)) return null
        return NailMask(
            fullWidth,
            fullHeight,
            rasterize(safe, fullWidth, fullHeight),
            bounds.left,
            bounds.top,
            safe.map { PixelPoint(it.x + bounds.left, it.y + bounds.top) },
        )
    }

    private data class Frame(val base: PixelPoint, val tip: PixelPoint) {
        val dx = tip.x - base.x
        val dy = tip.y - base.y
        val length = sqrt(dx * dx + dy * dy)
        val ux = dx / length.coerceAtLeast(EPSILON)
        val uy = dy / length.coerceAtLeast(EPSILON)
        val vx = -uy
        val vy = ux
        fun project(point: PixelPoint) = Projection(
            (point.x - base.x) * ux + (point.y - base.y) * uy,
            (point.x - base.x) * vx + (point.y - base.y) * vy,
        )
        fun point(t: Float, s: Float) = PixelPoint(base.x + ux * t + vx * s, base.y + uy * t + vy * s)
        fun scaleToFull(scale: Float) = Frame(PixelPoint(base.x / scale, base.y / scale), PixelPoint(tip.x / scale, tip.y / scale))
    }

    private data class Projection(val t: Float, val s: Float)
    private data class Feature(val luma: Float, val saturation: Float, val r: Float, val g: Float, val b: Float)
    private data class Model(val feature: Feature, val variance: Float)
    private data class EndBoundary(val t: Float, val halfWidth: Float, val confidence: Float)
    private data class SideBoundary(val t: Float, val s: Float, val confidence: Float)
    private data class SideTrace(val left: List<SideBoundary>, val right: List<SideBoundary>)
    private data class LineEvidence(val score: Float, val halfWidth: Float)

    private fun findEndBoundary(
        pixels: IntArray, width: Int, height: Int, frame: Frame, minT: Float, maxT: Float,
        nominalHalfWidth: Float, searchHalfWidth: Float, skinModel: Model, proximal: Boolean,
    ): EndBoundary {
        val span = (maxT - minT).coerceAtLeast(MIN_AXIS_LENGTH)
        val anchor = if (proximal) minT else maxT
        val direction = if (proximal) 1f else -1f
        var best = EndBoundary(anchor, nominalHalfWidth, 0f)
        var bestScore = Float.NEGATIVE_INFINITY
        for (step in 0..END_SCAN_STEPS) {
            val t = anchor + step.toFloat() * scaleLength(span) * direction
            if (t !in minT - span * .45f..maxT + span * .45f) continue
            val line = lineEvidence(pixels, width, height, frame, t, nominalHalfWidth, searchHalfWidth, direction, skinModel)
            if (line.score > bestScore) {
                bestScore = line.score
                best = EndBoundary(t, line.halfWidth, line.score)
            }
        }
        return if (best.confidence < MIN_END_CONFIDENCE) EndBoundary(anchor, nominalHalfWidth, 0f) else best
    }

    private fun lineEvidence(
        pixels: IntArray, width: Int, height: Int, frame: Frame, t: Float,
        nominalHalfWidth: Float, searchHalfWidth: Float, direction: Float, skinModel: Model,
    ): LineEvidence {
        var total = 0f
        var positive = 0
        var count = 0
        val scanHalf = min(searchHalfWidth * .88f, max(nominalHalfWidth * 1.45f, nominalHalfWidth + 3f))
        for (i in 0 until END_LATERAL_SAMPLES) {
            val lateral = lerp(-scanHalf, scanHalf, i.toFloat() / (END_LATERAL_SAMPLES - 1))
            val center = frame.point(t, lateral)
            val outside = sampleFeature(pixels, width, height, center.x - frame.ux * direction * END_DISTANCE, center.y - frame.uy * direction * END_DISTANCE) ?: continue
            val inside = sampleFeature(pixels, width, height, center.x + frame.ux * direction * END_DISTANCE, center.y + frame.uy * direction * END_DISTANCE) ?: continue
            val contrast = featureDistance(outside, inside)
            val insideNail = 1f - normalizedModelDistance(inside, skinModel)
            total += contrast * .72f + insideNail * .28f
            if (contrast >= MIN_EDGE_CONTRAST) positive++
            count++
        }
        if (count == 0) return LineEvidence(0f, nominalHalfWidth)
        val continuity = positive.toFloat() / count
        val mean = total / count
        val widthEstimate = estimateEndWidth(pixels, width, height, frame, t, nominalHalfWidth, searchHalfWidth, skinModel)
        return LineEvidence((mean * .68f + continuity * .32f).coerceIn(0f, 1f), widthEstimate)
    }

    private fun estimateEndWidth(
        pixels: IntArray, width: Int, height: Int, frame: Frame, t: Float,
        nominalHalfWidth: Float, searchHalfWidth: Float, skinModel: Model,
    ): Float {
        var best = nominalHalfWidth
        var bestScore = Float.NEGATIVE_INFINITY
        val maxCandidate = min(searchHalfWidth * .90f, max(nominalHalfWidth * 1.65f, nominalHalfWidth + 4f))
        for (candidate in END_WIDTH_FACTORS) {
            val half = (nominalHalfWidth * candidate).coerceAtMost(maxCandidate)
            val center = sampleFeature(pixels, width, height, frame.point(t, 0f).x, frame.point(t, 0f).y) ?: continue
            val left = sampleFeature(pixels, width, height, frame.point(t, -half).x, frame.point(t, -half).y) ?: continue
            val right = sampleFeature(pixels, width, height, frame.point(t, half).x, frame.point(t, half).y) ?: continue
            val centerNail = 1f - normalizedModelDistance(center, skinModel)
            val lateralContrast = (featureDistance(center, left) + featureDistance(center, right)) * .5f
            val score = centerNail * .55f + lateralContrast * .45f
            if (score > bestScore) {
                bestScore = score
                best = half
            }
        }
        return best.coerceIn(nominalHalfWidth * .72f, maxCandidate)
    }

    private fun traceSides(
        pixels: IntArray, width: Int, height: Int, frame: Frame, minT: Float, maxT: Float,
        nominalHalfWidth: Float, searchHalfWidth: Float, geometric: List<PixelPoint>, skinModel: Model,
    ): SideTrace {
        val left = ArrayList<SideBoundary>(SIDE_SAMPLES)
        val right = ArrayList<SideBoundary>(SIDE_SAMPLES)
        var previousLeft = -nominalHalfWidth
        var previousRight = nominalHalfWidth
        for (index in 0 until SIDE_SAMPLES) {
            val t = lerp(minT, maxT, SIDE_T[index])
            val expected = geometricHalfWidth(geometric, frame, t, nominalHalfWidth)
            val l = findSideBoundary(pixels, width, height, frame, t, expected, searchHalfWidth, -1, previousLeft, skinModel)
            val r = findSideBoundary(pixels, width, height, frame, t, expected, searchHalfWidth, 1, previousRight, skinModel)
            left += SideBoundary(t, l.s, l.confidence)
            right += SideBoundary(t, r.s, r.confidence)
            previousLeft = l.s
            previousRight = r.s
        }
        return SideTrace(left, right)
    }

    private fun findSideBoundary(
        pixels: IntArray, width: Int, height: Int, frame: Frame, t: Float, expectedHalf: Float,
        searchHalfWidth: Float, side: Int, previous: Float, skinModel: Model,
    ): SideBoundary {
        var bestS = side * expectedHalf
        var bestScore = Float.NEGATIVE_INFINITY
        val minHalf = expectedHalf * SIDE_MIN_FACTOR
        // The geometric contour is a prior, not a hard width ceiling. Search the
        // complete ROI width and let image evidence choose the lateral edge.
        val maxHalf = searchHalfWidth * SIDE_MAX_ROI_FACTOR
        for (step in 0..SIDE_SEARCH_STEPS) {
            val magnitude = lerp(minHalf, maxHalf, step.toFloat() / SIDE_SEARCH_STEPS)
            val s = side * magnitude
            val inside = sampleFeature(pixels, width, height, frame.point(t, s - side * SIDE_INSET).x, frame.point(t, s - side * SIDE_INSET).y) ?: continue
            val outside = sampleFeature(pixels, width, height, frame.point(t, s + side * SIDE_OUTSET).x, frame.point(t, s + side * SIDE_OUTSET).y) ?: continue
            val edge = featureDistance(inside, outside)
            val insideNail = 1f - normalizedModelDistance(inside, skinModel)
            val outsideSkin = 1f - normalizedModelDistance(outside, skinModel)
            val smoothness = (1f - abs(abs(s) - abs(previous)) / (expectedHalf * .8f).coerceAtLeast(1f)).coerceIn(0f, 1f)
            val score = edge * .42f + insideNail * .28f + outsideSkin * .20f + smoothness * .10f
            if (score > bestScore) {
                bestScore = score
                bestS = s
            }
        }
        return SideBoundary(t, bestS, bestScore.coerceIn(0f, 1f))
    }

    private fun geometricHalfWidth(polygon: List<PixelPoint>, frame: Frame, t: Float, fallback: Float): Float {
        var minS = Float.POSITIVE_INFINITY
        var maxS = Float.NEGATIVE_INFINITY
        for (point in polygon) {
            val projection = frame.project(point)
            if (abs(projection.t - t) <= fallback * .45f) {
                minS = min(minS, projection.s)
                maxS = max(maxS, projection.s)
            }
        }
        return if (minS.isFinite() && maxS.isFinite()) ((maxS - minS) * .5f).coerceIn(fallback * .55f, fallback * 1.20f) else fallback
    }

    private fun buildPolygon(frame: Frame, cuticle: EndBoundary, tip: EndBoundary, trace: SideTrace): List<PixelPoint> {
        val polygon = ArrayList<PixelPoint>(trace.left.size + trace.right.size + TIP_CAP_SAMPLES + 2)
        polygon += frame.point(cuticle.t, -cuticle.halfWidth)
        trace.left.forEach { polygon += frame.point(it.t, it.s) }
        polygon += frame.point(tip.t, -tip.halfWidth)
        for (i in 1 until TIP_CAP_SAMPLES) {
            val theta = Math.PI * i / TIP_CAP_SAMPLES
            val s = kotlin.math.sin(theta).toFloat() * tip.halfWidth
            val t = tip.t + kotlin.math.cos(theta).toFloat() * tip.halfWidth * TIP_FORWARD_FACTOR
            polygon += frame.point(t, s)
        }
        polygon += frame.point(tip.t, tip.halfWidth)
        trace.right.asReversed().forEach { polygon += frame.point(it.t, it.s) }
        polygon += frame.point(cuticle.t, cuticle.halfWidth)
        return smoothPolygon(polygon)
    }

    private fun smoothPolygon(points: List<PixelPoint>): List<PixelPoint> {
        if (points.size < 3) return points
        return points.indices.map { index ->
            val previous = points[(index - 1 + points.size) % points.size]
            val current = points[index]
            val next = points[(index + 1) % points.size]
            PixelPoint(current.x * .70f + (previous.x + next.x) * .15f, current.y * .70f + (previous.y + next.y) * .15f)
        }
    }

    private fun buildSkinModel(
        pixels: IntArray, width: Int, height: Int, frame: Frame, minT: Float, maxT: Float, searchHalfWidth: Float,
    ): Model {
        val samples = ArrayList<Feature>()
        for (y in 2 until height - 2 step 3) for (x in 2 until width - 2 step 3) {
            val point = PixelPoint(x + .5f, y + .5f)
            val projection = frame.project(point)
            if (projection.t !in minT..maxT) continue
            if (abs(projection.s) < searchHalfWidth * SKIN_RING_FACTOR) continue
            sampleFeature(pixels, width, height, point.x, point.y)?.let(samples::add)
        }
        if (samples.isEmpty()) return Model(Feature(.5f, .2f, .33f, .33f, .34f), .15f)
        val mean = meanFeature(samples)
        val variance = samples.map { featureDistance(it, mean) }.average().toFloat().coerceAtLeast(MIN_VARIANCE)
        return Model(mean, variance)
    }

    private fun normalizedModelDistance(feature: Feature, model: Model): Float =
        (featureDistance(feature, model.feature) / (model.variance * MODEL_DISTANCE_SCALE).coerceAtLeast(EPSILON)).coerceIn(0f, 1f)

    private fun meanFeature(features: List<Feature>): Feature {
        var l = 0f; var s = 0f; var r = 0f; var g = 0f; var b = 0f
        features.forEach { l += it.luma; s += it.saturation; r += it.r; g += it.g; b += it.b }
        val n = features.size.toFloat()
        return Feature(l / n, s / n, r / n, g / n, b / n)
    }

    private fun sampleFeature(pixels: IntArray, width: Int, height: Int, x: Float, y: Float): Feature? {
        val ix = x.roundToInt()
        val iy = y.roundToInt()
        if (ix !in 0 until width || iy !in 0 until height) return null
        val color = pixels[iy * width + ix]
        val r = ((color shr 16) and 0xFF).toFloat() / 255f
        val g = ((color shr 8) and 0xFF).toFloat() / 255f
        val b = (color and 0xFF).toFloat() / 255f
        val maxChannel = max(r, max(g, b))
        val minChannel = min(r, min(g, b))
        return Feature(.2126f * r + .7152f * g + .0722f * b, (maxChannel - minChannel) / maxChannel.coerceAtLeast(1f / 255f), r, g, b)
    }

    private fun featureDistance(a: Feature, b: Feature): Float =
        (abs(a.luma - b.luma) * .45f + abs(a.saturation - b.saturation) * .15f + (abs(a.r - b.r) + abs(a.g - b.g) + abs(a.b - b.b)) / 3f * .40f).coerceIn(0f, 1f)

    private fun insetForPainting(points: List<PixelPoint>, width: Int, height: Int): List<PixelPoint> {
        if (points.size < 3) return points
        val cx = points.map { it.x }.average().toFloat()
        val cy = points.map { it.y }.average().toFloat()
        return points.map { point ->
            val dx = cx - point.x
            val dy = cy - point.y
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val inset = PAINT_INSET_PX.coerceAtMost(distance * .20f)
            PixelPoint(
                (point.x + dx / distance * inset).coerceIn(0f, width - 1f),
                (point.y + dy / distance * inset).coerceIn(0f, height - 1f),
            )
        }
    }

    private fun isSafeShape(polygon: List<PixelPoint>, roi: NailRoi, frame: Frame, width: Int): Boolean {
        if (polygon.size < MIN_POLYGON_POINTS) return false
        val area = abs(polygonArea(polygon))
        val roiArea = abs(polygonArea(roi.polygon)).coerceAtLeast(1f)
        if (area < roiArea * MIN_AREA_RATIO || area > roiArea * MAX_AREA_RATIO) return false
        val projected = polygon.map(frame::project)
        val geometric = roi.polygon.map(frame::project)
        val minT = geometric.minOf { it.t }
        val maxT = geometric.maxOf { it.t }
        val candidateMinT = projected.minOf { it.t }
        val candidateMaxT = projected.maxOf { it.t }
        if (candidateMinT < minT - width * MAX_AXIS_DRIFT_RATIO || candidateMaxT > maxT + width * MAX_AXIS_DRIFT_RATIO) return false
        return true
    }

    private fun polygonArea(points: List<PixelPoint>): Float =
        points.indices.fold(0f) { sum, index ->
            val a = points[index]
            val b = points[(index + 1) % points.size]
            sum + (a.x * b.y - b.x * a.y)
        } * .5f

    private fun downsample(source: IntArray, sourceWidth: Int, sourceHeight: Int, width: Int, height: Int): IntArray {
        if (sourceWidth == width && sourceHeight == height) return source
        val out = IntArray(width * height)
        for (y in 0 until height) {
            val sy = (y * sourceHeight / height).coerceIn(0, sourceHeight - 1)
            for (x in 0 until width) {
                val sx = (x * sourceWidth / width).coerceIn(0, sourceWidth - 1)
                out[y * width + x] = source[sy * sourceWidth + sx]
            }
        }
        return out
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    private fun scaleLength(span: Float): Float = span / END_SCAN_STEPS.toFloat()

    private companion object {
        const val MIN_SIZE = 8
        const val MIN_ANALYSIS_SIZE = 16
        const val ANALYSIS_LONG_EDGE = 128
        const val MIN_AXIS_LENGTH = 6f
        const val MIN_HALF_WIDTH = 4f
        const val MAX_HALF_WIDTH = 55f
        const val END_SCAN_STEPS = 22
        const val END_LATERAL_SAMPLES = 13
        const val END_DISTANCE = 2.5f
        const val MIN_END_CONFIDENCE = .12f
        const val MIN_EDGE_CONTRAST = .055f
        const val MODEL_DISTANCE_SCALE = 3f
        const val SIDE_SAMPLES = 9
        const val SIDE_SEARCH_STEPS = 18
        const val SIDE_MIN_FACTOR = .62f
        const val SIDE_MAX_FACTOR = 1.30f
        const val SIDE_MAX_ROI_FACTOR = .92f
        const val SIDE_INSET = 1.5f
        const val SIDE_OUTSET = 2.5f
        const val SKIN_RING_FACTOR = .78f
        const val TIP_CAP_SAMPLES = 8
        const val TIP_FORWARD_FACTOR = .18f
        const val PAINT_INSET_PX = .8f
        const val MIN_POLYGON_POINTS = 8
        const val MIN_AREA_RATIO = .10f
        const val MAX_AREA_RATIO = 7.0f
        const val MAX_AXIS_DRIFT_RATIO = .08f
        const val EPSILON = 1e-4f
    }
}
