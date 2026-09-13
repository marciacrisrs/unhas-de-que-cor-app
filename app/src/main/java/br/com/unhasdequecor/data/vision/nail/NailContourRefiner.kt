package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Refina a placa estimada usando evidência visual, mantendo a anatomia do dedo
 * como prior. A ordem é deliberada: cutícula -> laterais -> tip -> coerência.
 *
 * O algoritmo não tenta "pintar um dedo". Ele procura uma região de placa
 * ungueal coerente dentro do ROI e usa a geometria apenas para restringir a
 * busca e impedir saltos para pele/fundo.
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
        if (geometric.size < 6 || width < 8 || height < 8) return null

        val axisDx = axisToTip.x - axisFromBase.x
        val axisDy = axisToTip.y - axisFromBase.y
        val axisLength = hypot(axisDx.toDouble(), axisDy.toDouble()).toFloat()
        if (axisLength < MIN_AXIS_LENGTH) return null
        val ux = axisDx / axisLength
        val uy = axisDy / axisLength
        val px = -uy
        val py = ux
        val halfWidth = (nominalWidth * 0.5f).coerceIn(MIN_HALF_WIDTH, MAX_HALF_WIDTH)

        val cuticle = detectCuticle(
            pixels, width, height, axisFromBase, ux, uy, px, py, halfWidth,
        ) ?: return null

        val tip = detectTip(
            pixels, width, height, axisToTip, ux, uy, px, py, halfWidth,
        ) ?: return null

        val sideSamples = SIDE_T_SAMPLES.map { t ->
            val center = interpolate(cuticle.center, tip.center, t)
            val expectedHalf = interpolate(cuticle.halfWidth, tip.halfWidth, t)
            val left = detectSide(
                pixels, width, height, center, px, py, expectedHalf, side = -1,
            )
            val right = detectSide(
                pixels, width, height, center, px, py, expectedHalf, side = 1,
            )
            SideSample(t, center, left, right)
        }

        val validSides = sideSamples.filter { it.left != null && it.right != null }
        val sideConfidence = if (validSides.isEmpty()) 0f else {
            validSides.map { (it.left!!.confidence + it.right!!.confidence) * 0.5f }.average().toFloat()
        }

        // We require evidence from the proximal boundary plus at least some
        // lateral evidence. This prevents a single strong background edge from
        // replacing the entire geometric prior.
        if (cuticle.confidence < MIN_CUTICLE_CONFIDENCE ||
            tip.confidence < MIN_TIP_CONFIDENCE ||
            sideConfidence < MIN_SIDE_CONFIDENCE
        ) return null

        val leftCurve = sideSamples.mapNotNull { it.left?.point }
        val rightCurve = sideSamples.mapNotNull { it.right?.point }
        if (leftCurve.size < MIN_SIDE_POINTS || rightCurve.size < MIN_SIDE_POINTS) return null

        val contour = buildContour(
            cuticle = cuticle,
            tip = tip,
            left = leftCurve,
            right = rightCurve,
            ux = ux,
            uy = uy,
            px = px,
            py = py,
        )
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
        val t: Float,
        val center: PixelPoint,
        val left: SidePoint?,
        val right: SidePoint?,
    )

    /** Finds the proximal plate/skin transition as a coherent line. */
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
            val edgePositions = ArrayList<Float>(CUTICLE_LATERAL_SAMPLES)

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
                edgePositions += lateral
                samples++
            }

            if (samples < CUTICLE_LATERAL_SAMPLES * 0.7f) continue
            val mean = total / samples
            val continuity = positive.toFloat() / samples
            val score = mean * CONTRAST_WEIGHT + continuity * CONTINUITY_WEIGHT
            if (score > bestScore && continuity >= MIN_CUTICLE_CONTINUITY) {
                val refinedHalf = estimateBoundaryHalfWidth(edgePositions, halfWidth)
                val left = PixelPoint(center.x - px * refinedHalf, center.y - py * refinedHalf)
                val right = PixelPoint(center.x + px * refinedHalf, center.y + py * refinedHalf)
                bestScore = score
                best = Boundary(
                    center = center,
                    left = left,
                    right = right,
                    halfWidth = refinedHalf,
                    confidence = score.coerceIn(0f, 1f),
                )
            }
        }
        return best
    }

    /** Finds the distal plate edge, constrained around the MediaPipe tip prior. */
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
                pixels, width, height, center, ux, uy, px, py, halfWidth,
            )
            if (evidence.score > bestScore && evidence.continuity >= MIN_TIP_CONTINUITY) {
                bestScore = evidence.score
                val tipHalf = estimateTipHalfWidth(pixels, width, height, center, px, py, halfWidth)
                best = Boundary(
                    center = center,
                    left = PixelPoint(center.x - px * tipHalf, center.y - py * tipHalf),
                    right = PixelPoint(center.x + px * tipHalf, center.y + py * tipHalf),
                    halfWidth = tipHalf,
                    confidence = evidence.score.coerceIn(0f, 1f),
                )
            }
        }
        return best
    }

    /** Searches each lateral border around the expected width. */
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
            val cx = center.x + px * lateral
            val cy = center.y + py * lateral
            val inside = sampleColor(
                pixels, width, height,
                center.x + px * (lateral - side * SAMPLE_DISTANCE),
                center.y + py * (lateral - side * SAMPLE_DISTANCE),
            ) ?: continue
            val outside = sampleColor(
                pixels, width, height,
                cx + px * side * SAMPLE_DISTANCE,
                cy + py * side * SAMPLE_DISTANCE,
            ) ?: continue
            val contrast = normalizedDistance(inside, outside)
            val score = contrast
            if (score > bestScore) {
                bestScore = score
                bestPoint = PixelPoint(cx, cy)
            }
        }
        return bestPoint?.let { SidePoint(it, bestScore.coerceIn(0f, 1f)) }
            ?.takeIf { it.confidence >= MIN_SIDE_POINT_CONFIDENCE }
    }

    private data class LineEvidence(val score: Float, val continuity: Float)

    private fun lineContrast(
        pixels: IntArray,
        width: Int,
        height: Int,
        center: PixelPoint,
        ux: Float,
        uy: Float,
        px: Float,
        py: Float,
        halfWidth: Float,
    ): LineEvidence {
        var total = 0f
        var positive = 0
        var samples = 0
        for (i in 0 until TIP_LATERAL_SAMPLES) {
            val lateral = ((i.toFloat() / (TIP_LATERAL_SAMPLES - 1)) * 2f - 1f) * halfWidth * 0.82f
            val x = center.x + px * lateral
            val y = center.y + py * lateral
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
            score = mean * CONTRAST_WEIGHT + continuity * CONTINUITY_WEIGHT,
            continuity = continuity,
        )
    }

    private fun estimateBoundaryHalfWidth(edgePositions: List<Float>, nominal: Float): Float {
        if (edgePositions.isEmpty()) return nominal
        val sorted = edgePositions.sorted()
        val q1 = sorted[(sorted.lastIndex * 0.15f).roundToInt().coerceIn(0, sorted.lastIndex)]
        val q3 = sorted[(sorted.lastIndex * 0.85f).roundToInt().coerceIn(0, sorted.lastIndex)]
        return max(abs(q1), abs(q3)).coerceIn(nominal * 0.65f, nominal * 1.20f)
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
            val left = PixelPoint(tip.x - px * candidate, tip.y - py * candidate)
            val right = PixelPoint(tip.x + px * candidate, tip.y + py * candidate)
            val center = sampleColor(pixels, width, height, tip.x, tip.y) ?: continue
            val l = sampleColor(pixels, width, height, left.x, left.y) ?: continue
            val r = sampleColor(pixels, width, height, right.x, right.y) ?: continue
            val score = ((normalizedDistance(center, l) + normalizedDistance(center, r)) * 0.5f)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best.coerceIn(nominal * 0.65f, nominal * 1.15f)
    }

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
        contour += cuticle.left
        contour += left.reversed()
        contour += tip.left

        // Rounded distal cap. The midpoint is intentionally used instead of a
        // sharp landmark tip so short/rounded nails don't become triangles.
        val tipCenter = tip.center
        val capHalf = tip.halfWidth
        val capSamples = 8
        for (i in 1 until capSamples) {
            val t = i.toFloat() / capSamples
            val angle = Math.PI * t
            val x = tipCenter.x + ux * (kotlin.math.cos(angle).toFloat() * capHalf * 0.45f) + px * (kotlin.math.sin(angle).toFloat() * capHalf)
            val y = tipCenter.y + uy * (kotlin.math.cos(angle).toFloat() * capHalf * 0.45f) + py * (kotlin.math.sin(angle).toFloat() * capHalf)
            contour += PixelPoint(x, y)
        }
        contour += tip.right
        contour += right
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
        val geometricCenter = geometric.fold(PixelPoint(0f, 0f)) { acc, p ->
            PixelPoint(acc.x + p.x, acc.y + p.y)
        }.let { PixelPoint(it.x / geometric.size, it.y / geometric.size) }
        val contourCenter = contour.fold(PixelPoint(0f, 0f)) { acc, p ->
            PixelPoint(acc.x + p.x, acc.y + p.y)
        }.let { PixelPoint(it.x / contour.size, it.y / contour.size) }
        val centerDrift = hypot(
            (contourCenter.x - geometricCenter.x).toDouble(),
            (contourCenter.y - geometricCenter.y).toDouble(),
        ).toFloat()
        val maxDrift = max(nominalHalf * 1.8f, 18f)
        val span = hypot(
            (tip.center.x - cuticle.center.x).toDouble(),
            (tip.center.y - cuticle.center.y).toDouble(),
        ).toFloat()
        return centerDrift <= maxDrift && span >= MIN_PLATE_SPAN
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

    private fun interpolate(a: PixelPoint, b: PixelPoint, t: Float): PixelPoint =
        PixelPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

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
        const val COLOR_DISTANCE_SCALE = 80f
        const val CONTRAST_WEIGHT = 0.70f
        const val CONTINUITY_WEIGHT = 0.30f
        const val CUTICLE_WEIGHT = 0.45f
        const val SIDE_WEIGHT = 0.35f
        const val TIP_WEIGHT = 0.20f
        val SIDE_T_SAMPLES = listOf(0.08f, 0.22f, 0.38f, 0.54f, 0.70f, 0.84f, 0.95f)
    }
}
