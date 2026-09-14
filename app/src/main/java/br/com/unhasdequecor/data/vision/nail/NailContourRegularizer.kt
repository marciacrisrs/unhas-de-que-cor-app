package br.com.unhasdequecor.data.vision.nail

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Anatomical contour regularization for the visible nail plate. */
class NailContourRegularizer {
    fun regularize(roi: NailRoi, mask: NailMask): NailMask {
        if (mask.width < 12 || mask.height < 12) return mask
        val dx = roi.axisToTip.x - roi.axisFromDip.x
        val dy = roi.axisToTip.y - roi.axisFromDip.y
        val length = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
        val ux = dx / length
        val uy = dy / length
        val vx = -uy
        val vy = ux
        val baseX = roi.axisFromDip.x - mask.originX
        val baseY = roi.axisFromDip.y - mask.originY
        fun frame(x: Float, y: Float): Pair<Float, Float> {
            val px = x - baseX
            val py = y - baseY
            return Pair(px * ux + py * uy, px * vx + py * vy)
        }
        val rawMin = HashMap<Int, Float>()
        val rawMax = HashMap<Int, Float>()
        for (i in mask.alpha.indices) {
            if ((mask.alpha[i].toInt() and 255) < ALPHA_THRESHOLD) continue
            val x = i % mask.width
            val y = i / mask.width
            val (t, s) = frame(x.toFloat() + 0.5f, y.toFloat() + 0.5f)
            val bin = floor(t).toInt()
            rawMin[bin] = min(rawMin[bin] ?: Float.POSITIVE_INFINITY, s)
            rawMax[bin] = max(rawMax[bin] ?: Float.NEGATIVE_INFINITY, s)
        }
        if (rawMin.size < 5) return mask
        val firstBin = min(rawMin.keys.minOrNull() ?: 0, rawMax.keys.minOrNull() ?: 0)
        val lastBin = max(rawMin.keys.maxOrNull() ?: 0, rawMax.keys.maxOrNull() ?: 0)
        val bins = (firstBin..lastBin).toList()
        val rawLeft = interpolateMissing(bins, rawMin)
        val rawRight = interpolateMissing(bins, rawMax)
        val smoothLeft = clampToObserved(gaussianSmooth(rawLeft), rawLeft, isLeft = true)
        val smoothRight = clampToObserved(gaussianSmooth(rawRight), rawRight, isLeft = false)
        val out = mask.alpha.copyOf()
        for (i in out.indices) {
            val x = i % mask.width
            val y = i / mask.width
            val (t, s) = frame(x.toFloat() + 0.5f, y.toFloat() + 0.5f)
            val pos = t - firstBin
            val lo = interpolateAt(smoothLeft, pos) ?: continue
            val hi = interpolateAt(smoothRight, pos) ?: continue
            val value = out[i].toInt() and 255
            if (value >= ALPHA_THRESHOLD) {
                if (isIsolatedSpike(out, x, y, mask.width, mask.height)) {
                    out[i] = 0
                    continue
                }
                val leftExcess = lo - s
                val rightExcess = s - hi
                if (leftExcess > MAX_BOUNDARY_CORRECTION || rightExcess > MAX_BOUNDARY_CORRECTION) out[i] = 0
            } else if (isBoundaryNotch(out, x, y, mask.width, mask.height) ||
                (s in lo..hi && nearForeground(out, x, y, mask.width, mask.height))
            ) {
                out[i] = 255.toByte()
            }
        }
        val polygon = polygonFromEnvelope(bins, smoothLeft, smoothRight, baseX, baseY, ux, uy, vx, vy, mask.originX, mask.originY)
        return mask.copy(alpha = out, boundaryPolygon = polygon)
    }

    private fun interpolateMissing(bins: List<Int>, values: Map<Int, Float>): FloatArray {
        val result = FloatArray(bins.size)
        var previous = -1
        for (i in bins.indices) {
            if (values.containsKey(bins[i])) {
                result[i] = values.getValue(bins[i]); previous = i
            } else if (previous >= 0) {
                var next = i + 1
                while (next < bins.size && !values.containsKey(bins[next])) next++
                result[i] = if (next < bins.size) {
                    val f = (i - previous).toFloat() / (next - previous).toFloat()
                    result[previous] + (values.getValue(bins[next]) - result[previous]) * f
                } else result[previous]
            } else {
                var next = i + 1
                while (next < bins.size && !values.containsKey(bins[next])) next++
                result[i] = if (next < bins.size) values.getValue(bins[next]) else 0f
            }
        }
        return result
    }

    private fun gaussianSmooth(values: FloatArray): FloatArray {
        if (values.size < 3) return values.copyOf()
        val out = FloatArray(values.size)
        val radius = SMOOTH_RADIUS
        val sigma = 1.35f
        for (i in values.indices) {
            var weighted = 0f
            var weightSum = 0f
            for (j in max(0, i - radius)..min(values.lastIndex, i + radius)) {
                val d = (j - i).toFloat()
                val weight = kotlin.math.exp(-(d * d) / (2f * sigma * sigma))
                weighted += values[j] * weight
                weightSum += weight
            }
            out[i] = weighted / weightSum
        }
        return out
    }

    private fun clampToObserved(smoothed: FloatArray, raw: FloatArray, isLeft: Boolean): FloatArray {
        val out = smoothed.copyOf()
        for (i in out.indices) out[i] = if (isLeft) max(out[i], raw[i]) else min(out[i], raw[i])
        return out
    }

    private fun interpolateAt(values: FloatArray, position: Float): Float? {
        if (position < 0f || position > values.lastIndex.toFloat()) return null
        val lo = floor(position).toInt().coerceIn(0, values.lastIndex)
        val hi = min(lo + 1, values.lastIndex)
        val f = position - lo
        return values[lo] + (values[hi] - values[lo]) * f
    }

    private fun nearForeground(alpha: ByteArray, x: Int, y: Int, w: Int, h: Int): Boolean =
        foregroundNeighborCount(alpha, x, y, w, h) >= MIN_FOREGROUND_NEIGHBORS

    private fun isBoundaryNotch(alpha: ByteArray, x: Int, y: Int, w: Int, h: Int): Boolean =
        (alpha[y * w + x].toInt() and 255) < ALPHA_THRESHOLD &&
            foregroundNeighborCount(alpha, x, y, w, h) >= MIN_NOTCH_NEIGHBORS

    private fun isIsolatedSpike(alpha: ByteArray, x: Int, y: Int, w: Int, h: Int): Boolean =
        foregroundNeighborCount(alpha, x, y, w, h) <= MAX_SPIKE_NEIGHBORS

    private fun foregroundNeighborCount(alpha: ByteArray, x: Int, y: Int, w: Int, h: Int): Int {
        var count = 0
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until w && ny in 0 until h && (alpha[ny * w + nx].toInt() and 255) >= ALPHA_THRESHOLD) count++
        }
        return count
    }

    private fun polygonFromEnvelope(
        bins: List<Int>, left: FloatArray, right: FloatArray,
        baseX: Float, baseY: Float, ux: Float, uy: Float, vx: Float, vy: Float,
        ox: Int, oy: Int,
    ): List<ImageCoordinates.PixelPoint>? {
        if (bins.size < 4) return null
        val stride = max(1, bins.size / MAX_POLYGON_POINTS)
        val out = ArrayList<ImageCoordinates.PixelPoint>(MAX_POLYGON_POINTS * 2)
        for (i in bins.indices step stride) {
            out += point(bins[i].toFloat() + 0.5f, left[i], baseX, baseY, ux, uy, vx, vy, ox, oy)
        }
        var i = bins.lastIndex
        while (i >= 0) {
            out += point(bins[i].toFloat() + 0.5f, right[i], baseX, baseY, ux, uy, vx, vy, ox, oy)
            i -= stride
        }
        return out.takeIf { it.size >= 6 }
    }

    private fun point(
        t: Float, s: Float, baseX: Float, baseY: Float,
        ux: Float, uy: Float, vx: Float, vy: Float, ox: Int, oy: Int,
    ): ImageCoordinates.PixelPoint = ImageCoordinates.PixelPoint(
        baseX + t * ux + s * vx + ox,
        baseY + t * uy + s * vy + oy,
    )

    private companion object {
        const val ALPHA_THRESHOLD = 128
        const val SMOOTH_RADIUS = 3
        const val MAX_BOUNDARY_CORRECTION = 1.25f
        const val MIN_FOREGROUND_NEIGHBORS = 3
        const val MIN_NOTCH_NEIGHBORS = 3
        const val MAX_SPIKE_NEIGHBORS = 2
        const val MAX_POLYGON_POINTS = 64
    }
}
