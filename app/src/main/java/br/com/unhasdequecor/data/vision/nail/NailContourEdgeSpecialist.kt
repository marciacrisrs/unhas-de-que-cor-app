package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Image-guided contour refinement constrained by the learned nail mask. */
class NailContourEdgeSpecialist {
    fun refine(image: Bitmap, roi: NailRoi, mask: NailMask): NailMask {
        if (mask.width < MIN_MASK_SIZE || mask.height < MIN_MASK_SIZE) return mask

        val pixels = IntArray(mask.width * mask.height)
        image.getPixels(pixels, 0, mask.width, mask.originX, mask.originY, mask.width, mask.height)
        val frame = Frame.from(roi, mask)
        val observed = profile(mask, frame)
        if (observed.size < MIN_BINS) return mask

        val bins = observed.keys.toList().sorted()
        val leftBase = smooth(FloatArray(bins.size) { observed.getValue(bins[it]).first })
        val rightBase = smooth(FloatArray(bins.size) { observed.getValue(bins[it]).second })
        val left = smooth(traceBoundary(pixels, mask.width, mask.height, frame, bins, leftBase, -1))
        val right = smooth(traceBoundary(pixels, mask.width, mask.height, frame, bins, rightBase, 1))

        val alpha = ByteArray(mask.alpha.size)
        for (i in alpha.indices) {
            val x = i % mask.width
            val y = i / mask.width
            val (t, s) = frame.toTs(x + PIXEL_CENTER, y + PIXEL_CENTER)
            val pos = t - bins.first()
            val lo = interpolate(left, pos) ?: continue
            val hi = interpolate(right, pos) ?: continue
            if (s in lo..hi && withinObservedBand(t, s, observed, bins)) alpha[i] = FULL_ALPHA
        }
        return mask.copy(alpha = alpha, boundaryPolygon = polygon(bins, left, right, frame, mask))
    }

    private fun profile(mask: NailMask, frame: Frame): Map<Int, Pair<Float, Float>> {
        val out = HashMap<Int, Pair<Float, Float>>()
        for (i in mask.alpha.indices) {
            if ((mask.alpha[i].toInt() and ALPHA_MASK) < ALPHA_THRESHOLD) continue
            val x = i % mask.width
            val y = i / mask.width
            val (t, s) = frame.toTs(x + PIXEL_CENTER, y + PIXEL_CENTER)
            val bin = floor(t).toInt()
            val current = out[bin]
            out[bin] = if (current == null) Pair(s, s) else Pair(min(current.first, s), max(current.second, s))
        }
        return out
    }

    /**
     * Finds one globally coherent boundary path instead of greedily choosing
     * the strongest local edge at each cross-section. This prevents wrinkles
     * and texture from turning into the small lateral zig-zags seen in debug.
     */
    private fun traceBoundary(
        pixels: IntArray,
        width: Int,
        height: Int,
        frame: Frame,
        bins: List<Int>,
        base: FloatArray,
        side: Int,
    ): FloatArray {
        if (bins.isEmpty()) return base.copyOf()
        val stateCount = SEARCH_RADIUS * 2 + 1
        val costs = Array(bins.size) { FloatArray(stateCount) { Float.NEGATIVE_INFINITY } }
        val previous = Array(bins.size) { IntArray(stateCount) { NO_PREVIOUS } }

        for (state in 0 until stateCount) {
            val candidate = base[0] + state - SEARCH_RADIUS
            costs[0][state] = candidateScore(
                pixels, width, height, frame, bins[0] + PIXEL_CENTER, candidate, base[0], side,
            )
        }

        for (i in 1 until bins.size) {
            for (state in 0 until stateCount) {
                val candidate = base[i] + state - SEARCH_RADIUS
                val local = candidateScore(
                    pixels, width, height, frame, bins[i] + PIXEL_CENTER, candidate, base[i], side,
                )
                var bestCost = Float.NEGATIVE_INFINITY
                var bestPrevious = NO_PREVIOUS
                for (prior in 0 until stateCount) {
                    if (costs[i - 1][prior] == Float.NEGATIVE_INFINITY) continue
                    val priorCandidate = base[i - 1] + prior - SEARCH_RADIUS
                    val delta = abs(candidate - priorCandidate)
                    val transition = CONTINUITY_PENALTY * delta * delta
                    val total = costs[i - 1][prior] + local - transition
                    if (total > bestCost) {
                        bestCost = total
                        bestPrevious = prior
                    }
                }
                costs[i][state] = bestCost
                previous[i][state] = bestPrevious
            }
        }

        val out = FloatArray(bins.size)
        var state = bestState(costs.last())
        for (i in bins.lastIndex downTo 0) {
            out[i] = base[i] + state - SEARCH_RADIUS
            state = if (i > 0) previous[i][state] else NO_PREVIOUS
            if (state == NO_PREVIOUS && i > 0) state = SEARCH_RADIUS
        }
        return out
    }

    private fun candidateScore(
        pixels: IntArray,
        width: Int,
        height: Int,
        frame: Frame,
        t: Float,
        s: Float,
        target: Float,
        side: Int,
    ): Float {
        val x = frame.xAt(t, s)
        val y = frame.yAt(t, s)
        if (x < BORDER_MARGIN || y < BORDER_MARGIN || x >= width - BORDER_MARGIN || y >= height - BORDER_MARGIN) {
            return OUT_OF_BOUNDS_SCORE
        }
        return boundaryScore(pixels, width, height, frame, t, s, side) -
            abs(s - target) * DISTANCE_PENALTY
    }

    private fun boundaryScore(
        pixels: IntArray,
        width: Int,
        height: Int,
        frame: Frame,
        t: Float,
        s: Float,
        side: Int,
    ): Float {
        val inside = sampleLuma(
            pixels, width, height,
            frame.xAt(t, s - side * SAMPLE_OFFSET),
            frame.yAt(t, s - side * SAMPLE_OFFSET),
        )
        val outside = sampleLuma(
            pixels, width, height,
            frame.xAt(t, s + side * SAMPLE_OFFSET),
            frame.yAt(t, s + side * SAMPLE_OFFSET),
        )
        val fineInside = sampleLuma(
            pixels, width, height,
            frame.xAt(t, s - side * FINE_SAMPLE_OFFSET),
            frame.yAt(t, s - side * FINE_SAMPLE_OFFSET),
        )
        val fineOutside = sampleLuma(
            pixels, width, height,
            frame.xAt(t, s + side * FINE_SAMPLE_OFFSET),
            frame.yAt(t, s + side * FINE_SAMPLE_OFFSET),
        )
        return abs(inside - outside) * COARSE_EDGE_WEIGHT + abs(fineInside - fineOutside) * FINE_EDGE_WEIGHT
    }

    private fun sampleLuma(pixels: IntArray, width: Int, height: Int, x: Float, y: Float): Float {
        val ix = x.toInt().coerceIn(0, width - 1)
        val iy = y.toInt().coerceIn(0, height - 1)
        val px = pixels[iy * width + ix]
        val r = (px shr RED_SHIFT and CHANNEL_MASK) / CHANNEL_MAX
        val g = (px shr GREEN_SHIFT and CHANNEL_MASK) / CHANNEL_MAX
        val b = (px and CHANNEL_MASK) / CHANNEL_MAX
        return LUMA_RED * r + LUMA_GREEN * g + LUMA_BLUE * b
    }

    private fun smooth(values: FloatArray): FloatArray {
        if (values.size < 3) return values.copyOf()
        val out = FloatArray(values.size)
        for (i in values.indices) {
            var sum = 0f
            var weightSum = 0f
            for (j in max(0, i - SMOOTH_RADIUS)..min(values.lastIndex, i + SMOOTH_RADIUS)) {
                val distance = (j - i).toFloat()
                val weight = exp(-(distance * distance) / (TWO * SIGMA * SIGMA))
                sum += values[j] * weight
                weightSum += weight
            }
            out[i] = sum / weightSum
        }
        return out
    }

    private fun interpolate(values: FloatArray, position: Float): Float? {
        if (position < 0f || position > values.lastIndex) return null
        val lo = floor(position).toInt().coerceIn(0, values.lastIndex)
        val hi = min(lo + 1, values.lastIndex)
        val fraction = position - lo
        return values[lo] + (values[hi] - values[lo]) * fraction
    }

    private fun withinObservedBand(t: Float, s: Float, observed: Map<Int, Pair<Float, Float>>, bins: List<Int>): Boolean {
        val index = floor(t - bins.first()).toInt().coerceIn(0, bins.lastIndex)
        val pair = observed[bins[index]] ?: return false
        return s >= pair.first - ALLOWED_OUTWARD && s <= pair.second + ALLOWED_OUTWARD
    }

    private fun polygon(
        bins: List<Int>,
        left: FloatArray,
        right: FloatArray,
        frame: Frame,
        mask: NailMask,
    ): List<ImageCoordinates.PixelPoint>? {
        if (bins.size < MIN_POLYGON_BINS) return null
        val stride = max(1, bins.size / MAX_POLYGON_POINTS)
        val out = ArrayList<ImageCoordinates.PixelPoint>(MAX_POLYGON_POINTS * 2)
        for (i in bins.indices step stride) out += frame.point(bins[i] + PIXEL_CENTER, left[i], mask)
        var i = bins.lastIndex
        while (i >= 0) {
            out += frame.point(bins[i] + PIXEL_CENTER, right[i], mask)
            i -= stride
        }
        return out.takeIf { it.size >= MIN_POLYGON_POINTS }
    }

    private fun bestState(values: FloatArray): Int {
        var best = 0
        for (i in 1 until values.size) if (values[i] > values[best]) best = i
        return best
    }

    private data class Frame(
        val bx: Float,
        val by: Float,
        val ux: Float,
        val uy: Float,
        val vx: Float,
        val vy: Float,
    ) {
        fun toTs(x: Float, y: Float): Pair<Float, Float> {
            val px = x - bx
            val py = y - by
            return Pair(px * ux + py * uy, px * vx + py * vy)
        }

        fun xAt(t: Float, s: Float) = bx + t * ux + s * vx
        fun yAt(t: Float, s: Float) = by + t * uy + s * vy
        fun point(t: Float, s: Float, mask: NailMask) = ImageCoordinates.PixelPoint(
            bx + t * ux + s * vx + mask.originX,
            by + t * uy + s * vy + mask.originY,
        )

        companion object {
            fun from(roi: NailRoi, mask: NailMask): Frame {
                val dx = roi.axisToTip.x - roi.axisFromDip.x
                val dy = roi.axisToTip.y - roi.axisFromDip.y
                val length = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
                return Frame(
                    roi.axisFromDip.x - mask.originX,
                    roi.axisFromDip.y - mask.originY,
                    dx / length,
                    dy / length,
                    -dy / length,
                    dx / length,
                )
            }
        }
    }

    private companion object {
        const val ALPHA_MASK = 255
        const val ALPHA_THRESHOLD = 128
        const val MIN_MASK_SIZE = 16
        const val SEARCH_RADIUS = 4
        const val SAMPLE_OFFSET = 1.5f
        const val FINE_SAMPLE_OFFSET = 0.5f
        const val DISTANCE_PENALTY = 0.10f
        const val CONTINUITY_PENALTY = 0.42f
        const val ALLOWED_OUTWARD = 1.0f
        const val SMOOTH_RADIUS = 2
        const val SIGMA = 1.15f
        const val MIN_BINS = 6
        const val MIN_POLYGON_BINS = 4
        const val MIN_POLYGON_POINTS = 6
        const val MAX_POLYGON_POINTS = 64
        const val BORDER_MARGIN = 1f
        const val OUT_OF_BOUNDS_SCORE = -1f
        const val COARSE_EDGE_WEIGHT = 0.75f
        const val FINE_EDGE_WEIGHT = 0.25f
        const val TWO = 2f
        const val PIXEL_CENTER = 0.5f
        const val FULL_ALPHA: Byte = -1
        const val NO_PREVIOUS = -1
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val CHANNEL_MASK = 255
        const val CHANNEL_MAX = 255f
        const val LUMA_RED = 0.299f
        const val LUMA_GREEN = 0.587f
        const val LUMA_BLUE = 0.114f
    }
}
