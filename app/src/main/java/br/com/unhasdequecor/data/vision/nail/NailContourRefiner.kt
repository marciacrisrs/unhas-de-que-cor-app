package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Refina a placa estimada usando evidência visual, mantendo a anatomia do dedo
 * como prior. A ordem é deliberada: cutícula -> laterais -> tip -> coerência.
 */
class NailContourRefiner {

    data class Result(
        val polygon: List<PixelPoint>,
        val confidence: Float,
        val cuticleConfidence: Float,
        val sideConfidence: Float,
        val tipConfidence: Float,
    )

    fun refine(
        pixels: IntArray,
        width: Int,
        height: Int,
        geometric: List<PixelPoint>,
        axisFromBase: PixelPoint,
        axisToTip: PixelPoint,
        nominalWidth: Float,
    ): Result? {
        if (geometric.size < 4 || width < 8 || height < 8) return null

        val dx = axisToTip.x - axisFromBase.x
        val dy = axisToTip.y - axisFromBase.y
        val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (length < MIN_AXIS_LENGTH) return null

        val ux = dx / length
        val uy = dy / length
        val px = -uy
        val py = ux
        val halfWidth = (nominalWidth * 0.5f).coerceIn(MIN_HALF_WIDTH, MAX_HALF_WIDTH)

        val cuticle = detectCuticle(
            pixels, width, height, axisFromBase, ux, uy, px, py, halfWidth,
        ) ?: return null

        val tip = detectTip(
            pixels, width, height, axisToTip, ux, uy, px, py, halfWidth,
        ) ?: return null

        val samples = SIDE_T_SAMPLES.map { t ->
            val center = lerpPoint(cuticle.center, tip.center, t)
            val expectedHalf = lerpFloat(cuticle.halfWidth, tip.halfWidth, t)
            SideSample(
                center = center,
                left = detectSide(pixels, width, height, center, px, py, expectedHalf, -1),
                right = detectSide(pixels, width, height, center, px, py, expectedHalf, 1),
            )
        }

        val valid = samples.filter { it.left != null && it.right != null }
        if (valid.size < MIN_SIDE_POINTS) return null

        val sideConfidence = valid.map {
            (it.left!!.confidence + it.right!!.confidence) * 0.5f
        }.average().toFloat()

        if (cuticle.confidence < MIN_CUTICLE_CONFIDENCE ||
            tip.confidence < MIN_TIP_CONFIDENCE ||
            sideConfidence < MIN_SIDE_CONFIDENCE
        ) return null

        val left = samples.mapNotNull { it.left?.point }
        val right = samples.mapNotNull { it.right?.point }
        if (left.size < MIN_SIDE_POINTS || right.size < MIN_SIDE_POINTS) return null

        val contour = buildContour(cuticle, tip, left, right, ux, uy, px, py)
        if (!isCoherent(contour, geometric, cuticle, tip, halfWidth)) return null

        val confidence = (
            cuticle.confidence * CUTICLE_WEIGHT +
                sideConfidence * SIDE_WEIGHT +
                tip.confidence * TIP_WEIGHT
            ).coerceIn(0f, 1f)

        return Result(
            polygon = smoothClosedContour(contour),
            confidence = confidence,
            cuticleConfidence = cuticle.confidence,
            sideConfidence = sideConfidence,
            tipConfidence = tip.confidence,
        )
    }

    private data class Boundary(
        val center: PixelPoint,
        val left: PixelPoint,
        val right: PixelPoint,
        val halfWidth: Float,
        val confidence: Float,
    )

    private data class SidePoint(
        val point: PixelPoint,
        val confidence: Float,
    )

    private data class SideSample(
        val center: PixelPoint,
        val left: SidePoint?,
        val right: SidePoint?,
    )

    private fun detectCuticle(
        pixels: IntArray,
        width: Int,
        height: Int,
        base: PixelPoint,
        ux: Float,
        uy: Float,
        px: Float,
        py: Float,
        halfWidth: Float,
    ): Boundary? {
        var best: Boundary? = null
        var bestScore = Float.NEGATIVE_INFINITY

        for (offset in CUTICLE_SCAN_MIN..CUTICLE_SCAN_MAX) {
            val center = PixelPoint(base.x + ux * offset, base.y + uy * offset)
            var total = 0f
            var positive = 0
            var samples = 0

            for (i in 0 until CUTICLE_LATERAL_SAMPLES) {
                val lateral = ((i.toFloat() / (CUTICLE_LATERAL_SAMPLES - 1)) * 2f - 1f) * halfWidth * 0.82f
                val x = center.x + px * lateral
                val y = center.y + py * lateral
                val proximal = sampleColor(
                    pixels, width, height,
                    x - ux * SAMPLE_DISTANCE,
                    y - uy * SAMPLE_DISTANCE,
                ) ?: continue
                val distal = sampleColor(
                    pixels, width, height,
                    x + ux * SAMPLE_DISTANCE,
                    y + uy * SAMPLE_DISTANCE,
                ) ?: continue
                val contrast = normalizedDistance(proximal, distal)
                total += contrast
                if (contrast >= MIN_LINE_CONTRAST) positive++
                samples++
            }

            if (samples < CUTICLE_LATERAL_SAMPLES * 0.7f) continue
            val mean = total / samples
            val continuity = positive.toFloat() / samples
            val score = mean * CONTRAST_WEIGHT + continuity * CONTINUITY_WEIGHT

            if (continuity >= MIN_CUTICLE_CONTINUITY && score > bestScore) {
                bestScore = score
                val left = PixelPoint(center.x - px * halfWidth, center.y - py * halfWidth)
                val right = PixelPoint(center.x + px * halfWidth, center.y + py * halfWidth)
                best = Boundary(center, left, right, halfWidth, score.coerceIn(0f, 1f))
            }
        }
        return best
    }

    private fun detectTip(
        pixels: IntArray,
        width: Int,
        height: Int,
        tip: PixelPoint,
        ux: Float,
        uy: Float,
        px: Float,
        py: Float,
        halfWidth: Float,
    ): Boundary? {
        var best: Boundary? = null
        var bestScore = Float.NEGATIVE_INFINITY

        for (offset in TIP_SCAN_MIN..TIP_SCAN_MAX) {
            val center = PixelPoint(tip.x + ux * offset, tip.y + uy * offset)
            val evidence = lineContrast(
                pixels, width, height, center, ux, uy, halfWidth,
            )
            if (evidence.score > bestScore && evidence.continuity >= MIN_TIP_CONTINUITY) {
                bestScore = evidence.score
                val tipHalf = estimateTipHalfWidth(pixels, width, height, center, px, py, halfWidth)
                best = Boundary(
                    center,
                    PixelPoint(center.x - px * tipHalf, center.y - py * tipHalf),
                    PixelPoint(center.x + px * tipHalf, center.y + py * tipHalf),
                    tipHalf,
                    evidence.score.coerceIn(0f, 1f),
                )
            }
        }
        return best
    }

    private fun detectSide(
        pixels: IntArray,
        width: Int,
        height: Int,
        center: PixelPoint,
        px: Float,
        py: Float,
        expectedHalf: Float,
        side: Int,
    ): SidePoint? {
        var bestScore = Float.NEGATIVE_INFINITY
        var bestPoint: PixelPoint? = null

        for (delta in SIDE_SEARCH_MIN..SIDE_SEARCH_MAX) {
            val lateral = side * (expectedHalf + delta)
            val boundaryX = center.x + px * lateral
            val boundaryY = center.y + py * lateral

            val inside = sampleColor(
                pixels, width, height,
                center.x + px * (lateral - side * SAMPLE_DISTANCE),
                center.y + py * (lateral - side * SAMPLE_DISTANCE),
            ) ?: continue
            val outside = sampleColor(
                pixels, width, height,
                boundaryX + px * side * SAMPLE_DISTANCE,
                boundaryY + py * side * SAMPLE_DISTANCE,
            ) ?: continue

            val score = normalizedDistance(inside, outside)
            if (score > bestScore) {
                bestScore = score
                bestPoint = PixelPoint(boundaryX, boundaryY)
            }
        }

        return bestPoint?.let {
            SidePoint(it, bestScore.coerceIn(0f, 1f))
        }?.takeIf { it.confidence >= MIN_SIDE_POINT_CONFIDENCE }
    }

    private data class LineEvidence(val score: Float, val continuity: Float)

    private fun lineContrast(
        pixels: IntArray,
        width: Int,
        height: Int,
        center: PixelPoint,
        ux: Float,
        uy: Float,
        halfWidth: Float,
    ): LineEvidence {
        var total = 0f
        var positive = 0
        var samples = 0

        for (i in 0 until TIP_LATERAL_SAMPLES) {
            val lateral = ((i.toFloat() / (TIP_LATERAL_SAMPLES - 1)) * 2f - 1f) * halfWidth * 0.82f
            val x = center.x + (-uy) * lateral
            val y = center.y + ux * lateral
            val inside = sampleColor(pixels, width, height, x - ux * SAMPLE_DISTANCE, y - uy * SAMPLE_DISTANCE) ?: continue
            val outside = sampleColor(pixels, width, height, x + ux * SAMPLE_DISTANCE, y + uy * SAMPLE_DISTANCE) ?: continue
            val contrast = normalizedDistance(inside, outside)
            total += contrast
            if (contrast >= MIN_LINE_CONTRAST) positive++
            samples++
        }

        if (samples == 0) return LineEvidence(0f, 0f)
        val mean = total / samples
        val continuity = positive.toFloat() / samples
        return LineEvidence(
            mean * CONTRAST_WEIGHT + continuity * CONTINUITY_WEIGHT,
            continuity,
        )
    }

    private fun estimateTipHalfWidth(
        pixels: IntArray,
        width: Int,
        height: Int,
        tip: PixelPoint,
        px: Float,
        py: Float,
        nominal: Float,
    ): Float {
        var best = nominal
        var bestScore = Float.NEGATIVE_INFINITY
        for (delta in TIP_WIDTH_MIN..TIP_WIDTH_MAX) {
            val candidate = (nominal + delta).coerceAtLeast(MIN_HALF_WIDTH)
            val center = sampleColor(pixels, width, height, tip.x, tip.y) ?: continue
            val left = sampleColor(pixels, width, height, tip.x - px * candidate, tip.y - py * candidate) ?: continue
            val right = sampleColor(pixels, width, height, tip.x + px * candidate, tip.y + py * candidate) ?: continue
            val score = (normalizedDistance(center, left) + normalizedDistance(center, right)) * 0.5f
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best.coerceIn(nominal * 0.65f, nominal * 1.15f)
    }

    /** Builds one simple, non-self-intersecting contour around the plate. */
    private fun buildContour(
        cuticle: Boundary,
        tip: Boundary,
        left: List<PixelPoint>,
        right: List<PixelPoint>,
        ux: Float,
        uy: Float,
        px: Float,
        py: Float,
    ): List<PixelPoint> {
        val contour = ArrayList<PixelPoint>(left.size + right.size + 10)

        // Samples are ordered proximal -> distal. Walk the left side toward the tip,
        // then the rounded distal cap, then return along the right side.
        contour += cuticle.left
        contour += left
        contour += tip.left

        val capHalf = tip.halfWidth
        for (i in 1 until TIP_CAP_SAMPLES) {
            val t = i.toFloat() / TIP_CAP_SAMPLES
            val angle = Math.PI * t
            contour += PixelPoint(
                tip.center.x + ux * (cos(angle).toFloat() * capHalf * 0.45f) + px * (sin(angle).toFloat() * capHalf),
                tip.center.y + uy * (cos(angle).toFloat() * capHalf * 0.45f) + py * (sin(angle).toFloat() * capHalf),
            )
        }

        contour += tip.right
        contour += right.reversed()
        contour += cuticle.right
        return contour
    }

    private fun isCoherent(
        contour: List<PixelPoint>,
        geometric: List<PixelPoint>,
        cuticle: Boundary,
        tip: Boundary,
        nominalHalf: Float,
    ): Boolean {
        if (contour.size < 8) return false

        val geometricCenter = averagePoint(geometric)
        val contourCenter = averagePoint(contour)
        val drift = hypot(
            (contourCenter.x - geometricCenter.x).toDouble(),
            (contourCenter.y - geometricCenter.y).toDouble(),
        ).toFloat()
        val maxDrift = max(nominalHalf * 1.8f, 18f)
        val span = hypot(
            (tip.center.x - cuticle.center.x).toDouble(),
            (tip.center.y - cuticle.center.y).toDouble(),
        ).toFloat()
        return drift <= maxDrift && span >= MIN_PLATE_SPAN
    }

    private fun averagePoint(points: List<PixelPoint>): PixelPoint {
        var x = 0f
        var y = 0f
        points.forEach {
            x += it.x
            y += it.y
        }
        return PixelPoint(x / points.size, y / points.size)
    }

    private fun smoothClosedContour(points: List<PixelPoint>): List<PixelPoint> {
        if (points.size < 3) return points
        return points.indices.map { i ->
            val prev = points[(i - 1 + points.size) % points.size]
            val current = points[i]
            val next = points[(i + 1) % points.size]
            PixelPoint(
                current.x * 0.72f + (prev.x + next.x) * 0.14f,
                current.y * 0.72f + (prev.y + next.y) * 0.14f,
            )
        }
    }

    private fun lerpPoint(a: PixelPoint, b: PixelPoint, t: Float): PixelPoint =
        PixelPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    private fun lerpFloat(a: Float, b: Float, t: Float): Float =
        a + (b - a) * t

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

    private fun normalizedDistance(a: Int, b: Int): Float {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return (sqrt((dr * dr + dg * dg + db * db).toFloat()) / COLOR_DISTANCE_SCALE)
            .coerceIn(0f, 1f)
    }

    private companion object {
        const val MIN_AXIS_LENGTH = 6f
        const val MIN_HALF_WIDTH = 5f
        const val MAX_HALF_WIDTH = 55f
        const val CUTICLE_SCAN_MIN = -18
        const val CUTICLE_SCAN_MAX = 22
        const val CUTICLE_LATERAL_SAMPLES = 11
        const val MIN_LINE_CONTRAST = 0.10f
        const val MIN_CUTICLE_CONTINUITY = 0.55f
        const val MIN_CUTICLE_CONFIDENCE = 0.16f
        const val TIP_SCAN_MIN = -8
        const val TIP_SCAN_MAX = 16
        const val TIP_LATERAL_SAMPLES = 9
        const val MIN_TIP_CONTINUITY = 0.45f
        const val MIN_TIP_CONFIDENCE = 0.10f
        const val TIP_WIDTH_MIN = -10
        const val TIP_WIDTH_MAX = 10
        const val SIDE_SEARCH_MIN = -12
        const val SIDE_SEARCH_MAX = 12
        const val MIN_SIDE_POINT_CONFIDENCE = 0.08f
        const val MIN_SIDE_CONFIDENCE = 0.10f
        const val MIN_SIDE_POINTS = 3
        const val MIN_PLATE_SPAN = 10f
        const val TIP_CAP_SAMPLES = 8
        const val SAMPLE_DISTANCE = 3f
        const val COLOR_DISTANCE_SCALE = 80f
        const val CONTRAST_WEIGHT = 0.70f
        const val CONTINUITY_WEIGHT = 0.30f
        const val CUTICLE_WEIGHT = 0.45f
        const val SIDE_WEIGHT = 0.35f
        const val TIP_WEIGHT = 0.20f
        val SIDE_T_SAMPLES = listOf(0.08f, 0.22f, 0.38f, 0.54f, 0.70f, 0.84f, 0.95f)
    }
}
