package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Image-guided contour refinement constrained by the learned nail mask. */
class NailContourEdgeSpecialist {
    fun refine(image: Bitmap, roi: NailRoi, mask: NailMask): NailMask {
        if (mask.width < 16 || mask.height < 16) return mask

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
            val (t, s) = frame.toTs(x + 0.5f, y + 0.5f)
            val pos = t - bins.first()
            val lo = interpolate(left, pos) ?: continue
            val hi = interpolate(right, pos) ?: continue
            if (s in lo..hi && withinObservedBand(t, s, observed, bins)) alpha[i] = 255.toByte()
        }
        return mask.copy(alpha = alpha, boundaryPolygon = polygon(bins, left, right, frame, mask))
    }

    private fun profile(mask: NailMask, frame: Frame): Map<Int, Pair<Float, Float>> {
        val out = HashMap<Int, Pair<Float, Float>>()
        for (i in mask.alpha.indices) {
            if ((mask.alpha[i].toInt() and 255) < ALPHA_THRESHOLD) continue
            val x = i % mask.width
            val y = i / mask.width
            val (t, s) = frame.toTs(x + 0.5f, y + 0.5f)
            val bin = floor(t).toInt()
            val current = out[bin]
            out[bin] = if (current == null) Pair(s, s) else Pair(min(current.first, s), max(current.second, s))
        }
        return out
    }

    private fun traceBoundary(
        pixels: IntArray,
        width: Int,
        height: Int,
        frame: Frame,
        bins: List<Int>,
        base: FloatArray,
        side: Int,
    ): FloatArray {
        val out = base.copyOf()
        var previous = base.firstOrNull() ?: 0f
        for (i in bins.indices) {
            val target = base[i]
            var best = target
            var bestScore = Float.NEGATIVE_INFINITY
            for (step in -SEARCH_RADIUS..SEARCH_RADIUS) {
                val s = target + step * SEARCH_STEP
                val x = frame.xAt(bins[i] + 0.5f, s)
                val y = frame.yAt(bins[i] + 0.5f, s)
                if (x < 1f || y < 1f || x >= width - 1f || y >= height - 1f) continue
                val score = boundaryScore(pixels, width, height, frame, bins[i] + 0.5f, s, side) -
                    abs(s - target) * DISTANCE_PENALTY - abs(s - previous) * CONTINUITY_PENALTY
                if (score > bestScore) {
                    bestScore = score
                    best = s
                }
            }
            out[i] = best
            previous = best
        }
        return out
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
        val inside = sampleLuma(pixels, width, height, frame.xAt(t, s - side * SAMPLE_OFFSET), frame.yAt(t, s - side * SAMPLE_OFFSET))
        val outside = sampleLuma(pixels, width, height, frame.xAt(t, s + side * SAMPLE_OFFSET), frame.yAt(t, s + side * SAMPLE_OFFSET))
        val fineInside = sampleLuma(pixels, width, height, frame.xAt(t, s - side * 0.5f), frame.yAt(t, s - side * 0.5f))
        val fineOutside = sampleLuma(pixels, width, height, frame.xAt(t, s + side * 0.5f), frame.yAt(t, s + side * 0.5f))
        return abs(inside - outside) * 0.75f + abs(fineInside - fineOutside) * 0.25f
    }

    private fun sampleLuma(pixels: IntArray, width: Int, height: Int, x: Float, y: Float): Float {
        val ix = x.toInt().coerceIn(0, width - 1)
        val iy = y.toInt().coerceIn(0, height - 1)
        val px = pixels[iy * width + ix]
        val r = (px shr 16 and 255) / 255f
        val g = (px shr 8 and 255) / 255f
        val b = (px and 255) / 255f
        return .299f * r + .587f * g + .114f * b
    }

    private fun smooth(values: FloatArray): FloatArray {
        if (values.size < 3) return values.copyOf()
        val out = FloatArray(values.size)
        for (i in values.indices) {
            var sum = 0f
            var weightSum = 0f
            for (j in max(0, i - SMOOTH_RADIUS)..min(values.lastIndex, i + SMOOTH_RADIUS)) {
                val d = (j - i).toFloat()
                val weight = exp(-(d * d) / (2f * SIGMA * SIGMA))
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
        val f = position - lo
        return values[lo] + (values[hi] - values[lo]) * f
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
        if (bins.size < 4) return null
        val stride = max(1, bins.size / MAX_POLYGON_POINTS)
        val out = ArrayList<ImageCoordinates.PixelPoint>(MAX_POLYGON_POINTS * 2)
        for (i in bins.indices step stride) out += frame.point(bins[i] + 0.5f, left[i], mask)
        var i = bins.lastIndex
        while (i >= 0) {
            out += frame.point(bins[i] + 0.5f, right[i], mask)
            i -= stride
        }
        return out.takeIf { it.size >= 6 }
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
                val length = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
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
        const val ALPHA_THRESHOLD = 128
        const val SEARCH_RADIUS = 3
        const val SEARCH_STEP = 1f
        const val SAMPLE_OFFSET = 1.5f
        const val DISTANCE_PENALTY = 0.08f
        const val CONTINUITY_PENALTY = 0.18f
        const val ALLOWED_OUTWARD = 1.0f
        const val SMOOTH_RADIUS = 3
        const val SIGMA = 1.4f
        const val MIN_BINS = 6
        const val MAX_POLYGON_POINTS = 64
    }
}
