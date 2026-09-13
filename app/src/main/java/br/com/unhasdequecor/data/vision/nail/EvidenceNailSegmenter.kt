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

/**
 * Image-evidence nail segmenter.
 *
 * MediaPipe/ROI geometry is used only as a spatial prior. The final boundary is
 * recovered from image evidence by fitting both lateral borders along the nail
 * axis and a separate cuticle line. A smooth dynamic-programming path prevents
 * the boundary from jumping between unrelated edges in skin/background.
 *
 * This is deliberately model-free so the production pipeline can start using
 * real image evidence immediately. A learned instance-segmentation model can
 * replace this implementation later without changing NailSegmenter's contract.
 */
@Singleton
class EvidenceNailSegmenter @Inject constructor() : NailSegmenter {

    override fun segment(image: Bitmap, roi: NailRoi): NailMask? {
        val b = roi.bounds
        val width = b.width()
        val height = b.height()
        if (width < MIN_CROP_SIZE || height < MIN_CROP_SIZE) return null
        if (b.left < 0 || b.top < 0 || b.right > image.width || b.bottom > image.height) return null

        val pixels = IntArray(width * height)
        image.getPixels(pixels, 0, width, b.left, b.top, width, height)

        val geometric = roi.polygon.map { PixelPoint(it.x - b.left, it.y - b.top) }
        if (geometric.size < 4) return null

        val axis = deriveAxis(geometric, roi, b.left, b.top) ?: return null
        val frame = AxisFrame(axis.base, axis.tip)
        val tBounds = polygonProjectionBounds(geometric, frame)
        val halfWidth = nominalHalfWidth(roi, geometric, frame)
        if (tBounds.second - tBounds.first < MIN_AXIS_SPAN || halfWidth < MIN_HALF_WIDTH) return null

        val samples = buildLongitudinalSamples(tBounds.first, tBounds.second)
        if (samples.size < MIN_LONGITUDINAL_SAMPLES) return null

        val leftPrior = FloatArray(samples.size)
        val rightPrior = FloatArray(samples.size)
        for (i in samples.indices) {
            val interval = polygonCrossSection(geometric, frame, samples[i])
            if (interval != null) {
                leftPrior[i] = interval.first
                rightPrior[i] = interval.second
            } else {
                leftPrior[i] = -halfWidth
                rightPrior[i] = halfWidth
            }
        }

        val leftPath = optimizeBoundary(
            pixels = pixels,
            width = width,
            height = height,
            frame = frame,
            tValues = samples,
            priors = leftPrior,
            insideSign = 1f,
            halfWidth = halfWidth,
        )
        val rightPath = optimizeBoundary(
            pixels = pixels,
            width = width,
            height = height,
            frame = frame,
            tValues = samples,
            priors = rightPrior,
            insideSign = -1f,
            halfWidth = halfWidth,
        )

        if (leftPath == null || rightPath == null) return null

        val cuticleT = detectCuticle(
            pixels = pixels,
            width = width,
            height = height,
            frame = frame,
            tMin = tBounds.first,
            tMax = tBounds.second,
            halfWidth = halfWidth,
        ) ?: tBounds.first

        val points = buildContour(
            frame = frame,
            tValues = samples,
            left = leftPath,
            right = rightPath,
            cuticleT = cuticleT,
            halfWidth = halfWidth,
            geometric = geometric,
        ) ?: return null

        val bounded = clampContour(points, width, height)
        if (!isCoherent(bounded, geometric, frame, halfWidth)) return null

        val solid = ByteArray(width * height)
        rasterizePolygon(bounded, width, height, solid)
        val filled = solid.count { (it.toInt() and 0xFF) >= SOLID_ALPHA }
        if (filled < MIN_FILLED_PIXELS) return null

        val boundary = bounded.map { PixelPoint(it.x + b.left, it.y + b.top) }
        return NailMask(
            width = width,
            height = height,
            alpha = feather(solid, width, height),
            originX = b.left,
            originY = b.top,
            boundaryPolygon = boundary,
        )
    }

    private fun deriveAxis(
        polygon: List<PixelPoint>,
        roi: NailRoi,
        originX: Int,
        originY: Int,
    ): Axis? {
        val unique = polygon.distinct()
        if (unique.size < 4) return null
        var bestA = unique.first()
        var bestB = unique.last()
        var bestDistance = 0f
        for (i in unique.indices) {
            for (j in i + 1 until unique.size) {
                val dx = unique[j].x - unique[i].x
                val dy = unique[j].y - unique[i].y
                val d = sqrt(dx * dx + dy * dy)
                if (d > bestDistance) {
                    bestDistance = d
                    bestA = unique[i]
                    bestB = unique[j]
                }
            }
        }
        if (bestDistance < MIN_AXIS_SPAN) return null

        val tip = PixelPoint(roi.axisToTip.x - originX, roi.axisToTip.y - originY)
        val base = PixelPoint(roi.axisFromDip.x - originX, roi.axisFromDip.y - originY)
        val distanceATip = distanceSquared(bestA, tip)
        val distanceBTip = distanceSquared(bestB, tip)
        val orientedTip = if (distanceATip <= distanceBTip) bestA else bestB
        val orientedBase = if (orientedTip == bestA) bestB else bestA

        // If the landmark base/tip orientation is unreliable, the longest
        // geometric chord still gives us a stable local frame.
        val landmarkLength = sqrt(distanceSquared(base, tip))
        return if (landmarkLength >= MIN_AXIS_SPAN) {
            Axis(base = base, tip = tip)
        } else {
            Axis(base = orientedBase, tip = orientedTip)
        }
    }

    private fun polygonProjectionBounds(
        polygon: List<PixelPoint>,
        frame: AxisFrame,
    ): Pair<Float, Float> {
        var minT = Float.POSITIVE_INFINITY
        var maxT = Float.NEGATIVE_INFINITY
        for (p in polygon) {
            val t = frame.t(p)
            minT = min(minT, t)
            maxT = max(maxT, t)
        }
        return minT to maxT
    }

    private fun nominalHalfWidth(
        roi: NailRoi,
        polygon: List<PixelPoint>,
        frame: AxisFrame,
    ): Float {
        val fromRoi = roi.widthPx * 0.5f
        var span = 0f
        val t = (polygonProjectionBounds(polygon, frame).first + polygonProjectionBounds(polygon, frame).second) * 0.5f
        val section = polygonCrossSection(polygon, frame, t)
        if (section != null) span = (section.second - section.first) * 0.5f
        return max(MIN_HALF_WIDTH, max(fromRoi, span)).coerceAtMost(MAX_HALF_WIDTH)
    }

    private fun buildLongitudinalSamples(minT: Float, maxT: Float): FloatArray {
        val start = minT + (maxT - minT) * SAMPLE_START
        val end = maxT - (maxT - minT) * SAMPLE_END
        if (end <= start) return FloatArray(0)
        return FloatArray(LONGITUDINAL_SAMPLES) { index ->
            start + (end - start) * index / (LONGITUDINAL_SAMPLES - 1f)
        }
    }

    private fun polygonCrossSection(
        polygon: List<PixelPoint>,
        frame: AxisFrame,
        t: Float,
    ): Pair<Float, Float>? {
        val values = ArrayList<Float>(polygon.size)
        val projected = polygon.map { frame.project(it) }
        for (i in projected.indices) {
            val a = projected[i]
            val b = projected[(i + 1) % projected.size]
            val da = a.first - t
            val db = b.first - t
            if (abs(da) < 0.001f) values += a.second
            if ((da < 0f && db > 0f) || (da > 0f && db < 0f)) {
                val ratio = da / (da - db)
                values += a.second + (b.second - a.second) * ratio
            }
        }
        if (values.size < 2) return null
        return values.minOrNull()!! to values.maxOrNull()!!
    }

    private fun optimizeBoundary(
        pixels: IntArray,
        width: Int,
        height: Int,
        frame: AxisFrame,
        tValues: FloatArray,
        priors: FloatArray,
        insideSign: Float,
        halfWidth: Float,
    ): FloatArray? {
        val radius = min(MAX_BOUNDARY_SHIFT, max(MIN_BOUNDARY_SHIFT, halfWidth * BOUNDARY_SHIFT_FRACTION))
        val candidateCount = (radius * 2f / CANDIDATE_STEP).roundToInt() + 1
        val candidates = FloatArray(candidateCount) { index ->
            -radius + index * CANDIDATE_STEP
        }

        val costs = Array(tValues.size) { FloatArray(candidateCount) { Float.POSITIVE_INFINITY } }
        val back = Array(tValues.size) { IntArray(candidateCount) { -1 } }

        for (i in tValues.indices) {
            for (j in candidates.indices) {
                val s = priors[i] + candidates[j]
                val evidence = boundaryEvidence(
                    pixels = pixels,
                    width = width,
                    height = height,
                    frame = frame,
                    t = tValues[i],
                    s = s,
                    insideSign = insideSign,
                )
                val priorPenalty = abs(candidates[j]) / radius
                costs[i][j] = -evidence * EVIDENCE_WEIGHT + priorPenalty * PRIOR_WEIGHT
                if (i == 0) continue
                var best = Float.POSITIVE_INFINITY
                var bestIndex = -1
                for (k in candidates.indices) {
                    val transition = abs((priors[i] + candidates[j]) - (priors[i - 1] + candidates[k]))
                    if (transition > MAX_STEP_CHANGE) continue
                    val candidateCost = costs[i - 1][k] + transition * SMOOTHNESS_WEIGHT
                    if (candidateCost < best) {
                        best = candidateCost
                        bestIndex = k
                    }
                }
                if (bestIndex >= 0) {
                    costs[i][j] += best
                    back[i][j] = bestIndex
                } else {
                    costs[i][j] = Float.POSITIVE_INFINITY
                }
            }
        }

        var bestIndex = costs.last().indices.minByOrNull { costs.last()[it] } ?: return null
        if (!costs.last()[bestIndex].isFinite()) return null
        val result = FloatArray(tValues.size)
        for (i in tValues.lastIndex downTo 0) {
            result[i] = priors[i] + candidates[bestIndex]
            bestIndex = if (i > 0) back[i][bestIndex] else bestIndex
            if (i > 0 && bestIndex < 0) return null
        }

        val evidence = result.indices.map { i ->
            boundaryEvidence(pixels, width, height, frame, tValues[i], result[i], insideSign)
        }.average().toFloat()
        return if (evidence >= MIN_PATH_EVIDENCE) result else null
    }

    private fun boundaryEvidence(
        pixels: IntArray,
        width: Int,
        height: Int,
        frame: AxisFrame,
        t: Float,
        s: Float,
        insideSign: Float,
    ): Float {
        val inside = sampleFeature(pixels, width, height, frame.point(t, s + insideSign * NEAR_SAMPLE))
            ?: return 0f
        val farInside = sampleFeature(pixels, width, height, frame.point(t, s + insideSign * FAR_SAMPLE))
            ?: return 0f
        val outside = sampleFeature(pixels, width, height, frame.point(t, s - insideSign * NEAR_SAMPLE))
            ?: return 0f
        val farOutside = sampleFeature(pixels, width, height, frame.point(t, s - insideSign * FAR_SAMPLE))
            ?: return 0f

        val transition = colorDistance(inside, outside) / COLOR_SCALE
        val regional = 0.5f * colorDistance(farInside, farOutside) / COLOR_SCALE +
            0.5f * colorDistance(inside, farInside) / COLOR_SCALE
        val edge = transition * EDGE_WEIGHT + regional * REGIONAL_WEIGHT
        return edge.coerceIn(0f, 2f)
    }

    private fun detectCuticle(
        pixels: IntArray,
        width: Int,
        height: Int,
        frame: AxisFrame,
        tMin: Float,
        tMax: Float,
        halfWidth: Float,
    ): Float? {
        val scanStart = tMin + (tMax - tMin) * CUTICLE_MIN_T
        val scanEnd = tMin + (tMax - tMin) * CUTICLE_MAX_T
        var bestT = Float.NaN
        var bestScore = 0f
        val lateral = FloatArray(CUTICLE_LATERAL_SAMPLES) { index ->
            -halfWidth * CUTICLE_LATERAL_SPAN +
                2f * halfWidth * CUTICLE_LATERAL_SPAN * index / (CUTICLE_LATERAL_SAMPLES - 1f)
        }
        var t = scanStart
        while (t <= scanEnd) {
            var sum = 0f
            var valid = 0
            for (s in lateral) {
                val near = sampleFeature(pixels, width, height, frame.point(t - CUTICLE_NEAR, s))
                val far = sampleFeature(pixels, width, height, frame.point(t + CUTICLE_NEAR, s))
                if (near != null && far != null) {
                    sum += colorDistance(near, far) / COLOR_SCALE
                    valid++
                }
            }
            if (valid >= CUTICLE_MIN_VALID_SAMPLES) {
                val score = sum / valid
                if (score > bestScore) {
                    bestScore = score
                    bestT = t
                }
            }
            t += CUTICLE_STEP
        }
        return if (bestT.isFinite() && bestScore >= MIN_CUTICLE_EVIDENCE) bestT else null
    }

    private fun buildContour(
        frame: AxisFrame,
        tValues: FloatArray,
        left: FloatArray,
        right: FloatArray,
        cuticleT: Float,
        halfWidth: Float,
        geometric: List<PixelPoint>,
    ): List<PixelPoint>? {
        if (tValues.isEmpty()) return null
        val cuticleIndex = tValues.indexOfFirst { it >= cuticleT }.let { if (it < 0) 0 else it }
        val points = ArrayList<PixelPoint>(tValues.size * 2 + 4)

        val leftBase = left[cuticleIndex]
        val rightBase = right[cuticleIndex]
        val cuticleLeft = frame.point(cuticleT, leftBase)
        val cuticleRight = frame.point(cuticleT, rightBase)
        points += cuticleLeft
        for (i in cuticleIndex until tValues.size) {
            points += frame.point(tValues[i], left[i])
        }
        for (i in tValues.lastIndex downTo cuticleIndex) {
            points += frame.point(tValues[i], right[i])
        }
        points += cuticleRight

        val distinct = points.distinct()
        if (distinct.size < MIN_CONTOUR_POINTS) return null
        val area = polygonArea(distinct)
        val geometricArea = abs(polygonArea(geometric))
        if (area < MIN_AREA || area < geometricArea * MIN_AREA_RATIO) return null
        if (abs(leftBase) > halfWidth * MAX_BASE_WIDTH_FACTOR ||
            abs(rightBase) > halfWidth * MAX_BASE_WIDTH_FACTOR
        ) return null
        return smooth(distinct)
    }

    private fun isCoherent(
        candidate: List<PixelPoint>,
        geometric: List<PixelPoint>,
        frame: AxisFrame,
        halfWidth: Float,
    ): Boolean {
        if (candidate.size < MIN_CONTOUR_POINTS) return false
        val candidateArea = abs(polygonArea(candidate))
        val geometricArea = abs(polygonArea(geometric)).coerceAtLeast(1f)
        if (candidateArea !in geometricArea * MIN_AREA_RATIO..geometricArea * MAX_AREA_RATIO) return false

        val candidateBounds = candidate.map { frame.project(it) }
        val geometricBounds = geometric.map { frame.project(it) }
        val candidateMinT = candidateBounds.minOf { it.first }
        val candidateMaxT = candidateBounds.maxOf { it.first }
        val geometricMinT = geometricBounds.minOf { it.first }
        val geometricMaxT = geometricBounds.maxOf { it.first }
        if (candidateMinT < geometricMinT - halfWidth * MAX_AXIS_DRIFT_FACTOR) return false
        if (candidateMaxT > geometricMaxT + halfWidth * MAX_AXIS_DRIFT_FACTOR) return false
        return true
    }

    private fun clampContour(points: List<PixelPoint>, width: Int, height: Int): List<PixelPoint> =
        points.map {
            PixelPoint(
                x = it.x.coerceIn(0.5f, width - 0.5f),
                y = it.y.coerceIn(0.5f, height - 0.5f),
            )
        }

    private fun smooth(points: List<PixelPoint>): List<PixelPoint> {
        if (points.size < 5) return points
        return points.indices.map { i ->
            val prev = points[(i - 1 + points.size) % points.size]
            val current = points[i]
            val next = points[(i + 1) % points.size]
            PixelPoint(
                current.x * SMOOTH_CURRENT + (prev.x + next.x) * SMOOTH_NEIGHBOR,
                current.y * SMOOTH_CURRENT + (prev.y + next.y) * SMOOTH_NEIGHBOR,
            )
        }
    }

    private fun sampleFeature(
        pixels: IntArray,
        width: Int,
        height: Int,
        point: PixelPoint,
    ): Feature? {
        val x = point.x.roundToInt()
        val y = point.y.roundToInt()
        if (x !in 0 until width || y !in 0 until height) return null
        val color = pixels[y * width + x]
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val max = max(r, max(g, b)).toFloat()
        val min = min(r, min(g, b)).toFloat()
        val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
        val saturation = if (max <= 0f) 0f else (max - min) / max
        return Feature(luma, saturation, r, g, b)
    }

    private fun colorDistance(a: Feature, b: Feature): Float {
        val dl = abs(a.luma - b.luma)
        val ds = abs(a.saturation - b.saturation)
        val dr = abs(a.r - b.r) / 255f
        val dg = abs(a.g - b.g) / 255f
        val db = abs(a.b - b.b) / 255f
        return dl * LUMA_WEIGHT + ds * SATURATION_WEIGHT + (dr + dg + db) / 3f * RGB_WEIGHT
    }

    private fun rasterizePolygon(poly: List<PixelPoint>, width: Int, height: Int, out: ByteArray) {
        if (poly.size < 3) return
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pointInPolygon(x + 0.5f, y + 0.5f, poly)) out[y * width + x] = 255.toByte()
            }
        }
    }

    private fun pointInPolygon(x: Float, y: Float, poly: List<PixelPoint>): Boolean {
        var inside = false
        var j = poly.lastIndex
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            val denominator = (b.y - a.y).takeIf { abs(it) > EPSILON } ?: EPSILON
            if (((a.y > y) != (b.y > y)) && x < (b.x - a.x) * (y - a.y) / denominator + a.x) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private fun feather(src: ByteArray, width: Int, height: Int): ByteArray {
        val out = src.copyOf()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if ((src[index].toInt() and 0xFF) != 0) continue
                var neighbor = false
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until width && ny in 0 until height &&
                            (src[ny * width + nx].toInt() and 0xFF) != 0
                        ) {
                            neighbor = true
                        }
                    }
                }
                if (neighbor) out[index] = FEATHER_ALPHA.toByte()
            }
        }
        return out
    }

    private fun polygonArea(points: List<PixelPoint>): Float {
        var sum = 0f
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        return sum * 0.5f
    }

    private fun distanceSquared(a: PixelPoint, b: PixelPoint): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy
    }

    private data class Axis(val base: PixelPoint, val tip: PixelPoint)

    private data class Feature(
        val luma: Float,
        val saturation: Float,
        val r: Int,
        val g: Int,
        val b: Int,
    )

    private class AxisFrame(base: PixelPoint, tip: PixelPoint) {
        private val dx = tip.x - base.x
        private val dy = tip.y - base.y
        private val length = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        private val ux = dx / length
        private val uy = dy / length
        private val vx = -uy
        private val vy = ux
        private val origin = base

        fun t(point: PixelPoint): Float =
            (point.x - origin.x) * ux + (point.y - origin.y) * uy

        fun project(point: PixelPoint): Pair<Float, Float> =
            t(point) to ((point.x - origin.x) * vx + (point.y - origin.y) * vy)

        fun point(t: Float, s: Float): PixelPoint =
            PixelPoint(
                x = origin.x + ux * t + vx * s,
                y = origin.y + uy * t + vy * s,
            )
    }

    private companion object {
        const val MIN_CROP_SIZE = 8
        const val MIN_AXIS_SPAN = 6f
        const val MIN_HALF_WIDTH = 4f
        const val MAX_HALF_WIDTH = 55f
        const val LONGITUDINAL_SAMPLES = 25
        const val MIN_LONGITUDINAL_SAMPLES = 8
        const val SAMPLE_START = 0.04f
        const val SAMPLE_END = 0.05f
        const val BOUNDARY_SHIFT_FRACTION = 0.55f
        const val MIN_BOUNDARY_SHIFT = 4f
        const val MAX_BOUNDARY_SHIFT = 20f
        const val CANDIDATE_STEP = 1.5f
        const val MAX_STEP_CHANGE = 5f
        const val NEAR_SAMPLE = 1.5f
        const val FAR_SAMPLE = 4f
        const val COLOR_SCALE = 1f
        const val EVIDENCE_WEIGHT = 1.0f
        const val PRIOR_WEIGHT = 0.08f
        const val SMOOTHNESS_WEIGHT = 0.16f
        const val EDGE_WEIGHT = 0.70f
        const val REGIONAL_WEIGHT = 0.30f
        const val MIN_PATH_EVIDENCE = 0.035f
        const val CUTICLE_MIN_T = 0.02f
        const val CUTICLE_MAX_T = 0.34f
        const val CUTICLE_STEP = 1.5f
        const val CUTICLE_NEAR = 2.5f
        const val CUTICLE_LATERAL_SAMPLES = 11
        const val CUTICLE_LATERAL_SPAN = 0.72f
        const val CUTICLE_MIN_VALID_SAMPLES = 7
        const val MIN_CUTICLE_EVIDENCE = 0.035f
        const val MIN_CONTOUR_POINTS = 12
        const val MIN_AREA = 12f
        const val MIN_AREA_RATIO = 0.45f
        const val MAX_AREA_RATIO = 1.55f
        const val MAX_BASE_WIDTH_FACTOR = 1.35f
        const val MAX_AXIS_DRIFT_FACTOR = 0.45f
        const val LUMA_WEIGHT = 0.45f
        const val SATURATION_WEIGHT = 0.20f
        const val RGB_WEIGHT = 0.35f
        const val SMOOTH_CURRENT = 0.72f
        const val SMOOTH_NEIGHBOR = 0.14f
        const val SOLID_ALPHA = 128
        const val FEATHER_ALPHA = 72
        const val MIN_FILLED_PIXELS = 8
        const val EPSILON = 1e-5f
    }
}
