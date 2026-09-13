package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Conservative second-stage refiner: ML seed -> connected plate expansion -> skin rejection. */
class StrictNailPlateBoundaryRefiner {
    fun refine(image: Bitmap, roi: NailRoi, seedMask: NailMask): NailMask? {
        val w = seedMask.width
        val h = seedMask.height
        if (w < 12 || h < 12) return null
        val pixels = IntArray(w * h)
        image.getPixels(pixels, 0, w, seedMask.originX, seedMask.originY, w, h)
        val seed = BooleanArray(pixels.size) { (seedMask.alpha[it].toInt() and 255) >= 185 }
        if (seed.count { it } < 10) return null

        val frame = frame(roi, seedMask)
        val envelope = envelope(roi, frame, seedMask)
        val nail = model(pixels, seed, 240) ?: return null
        val skin = skinModel(pixels, seed, envelope, w, h)
        val grown = grow(pixels, seed, nail, skin, envelope, w, h)
        val safe = prune(pixels, grown, seed, nail, skin, envelope, w, h)
        if (safe.count { (it.toInt() and 255) > 0 } < 20) return null
        val polygon = polygon(safe, w, h, seedMask.originX, seedMask.originY) ?: return null
        return seedMask.copy(alpha = safe, boundaryPolygon = polygon)
    }

    private data class F(val r: Float, val g: Float, val b: Float, val y: Float, val s: Float)
    private data class M(val f: F)
    private data class Frame(val bx: Float, val by: Float, val ux: Float, val uy: Float, val vx: Float, val vy: Float) {
        fun p(x: Float, y: Float): Pair<Float, Float> = Pair((x - bx) * ux + (y - by) * uy, (x - bx) * vx + (y - by) * vy)
    }
    private data class Env(val minT: Float, val maxT: Float, val half: Float, val frame: Frame) {
        fun contains(x: Float, y: Float): Boolean {
            val (t, s) = frame.p(x, y)
            val taper = when { t < minT -> .55f; t > maxT -> .60f; else -> 1f }
            return t >= minT - EXTRA_BASE && t <= maxT + EXTRA_TIP && abs(s) <= half * taper
        }
        fun outsideDistance(x: Float, y: Float): Float {
            val (t, s) = frame.p(x, y)
            val dt = max(minT - t, t - maxT).coerceAtLeast(0f)
            val ds = abs(s) - half
            return max(dt, ds).coerceAtLeast(0f)
        }
    }

    private fun frame(roi: NailRoi, mask: NailMask): Frame {
        val dx = roi.axisToTip.x - roi.axisFromDip.x
        val dy = roi.axisToTip.y - roi.axisFromDip.y
        val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
        return Frame(roi.axisFromDip.x - mask.originX, roi.axisFromDip.y - mask.originY, dx / len, dy / len, -dy / len, dx / len)
    }

    private fun envelope(roi: NailRoi, frame: Frame, mask: NailMask): Env {
        val points = roi.polygon.map { frame.p(it.x - mask.originX, it.y - mask.originY) }
        val minT = points.minOfOrNull { it.first } ?: 0f
        val maxT = points.maxOfOrNull { it.first } ?: 1f
        val half = (points.maxOfOrNull { abs(it.second) } ?: roi.widthPx * .5f) * 1.48f
        return Env(minT, maxT, min(half, max(mask.width, mask.height) * .46f), frame)
    }

    private fun grow(p: IntArray, seed: BooleanArray, nail: M, skin: M?, env: Env, w: Int, h: Int): ByteArray {
        val out = ByteArray(p.size)
        val seen = BooleanArray(p.size)
        val q = IntArray(p.size)
        var head = 0; var tail = 0
        for (i in seed.indices) if (seed[i]) { out[i] = 255.toByte(); seen[i] = true; q[tail++] = i }
        while (head < tail) {
            val cur = q[head++]
            val x = cur % w; val y = cur / w
            for (n in neighbors(x, y, w, h)) {
                if (seen[n]) continue
                seen[n] = true
                val nx = n % w; val ny = n / w
                if (!env.contains(nx.toFloat(), ny.toFloat())) continue
                val nf = feature(p[n]); val ns = skin?.let { similarity(nf, it) } ?: 0f
                val nm = similarity(nf, nail); val cm = similarity(feature(p[cur]), nail)
                val score = nm * .70f + cm * .30f - ns * .50f
                if (score >= .31f && nm >= .47f && (skin == null || ns <= .90f || nm - ns >= .04f)) {
                    out[n] = 255.toByte(); q[tail++] = n
                }
            }
        }
        return out
    }

    private fun prune(p: IntArray, mask: ByteArray, seed: BooleanArray, nail: M, skin: M?, env: Env, w: Int, h: Int): ByteArray {
        val out = mask.copyOf()
        for (i in out.indices) {
            if ((out[i].toInt() and 255) == 0 || seed[i]) continue
            val x = i % w; val y = i / w
            if (!env.contains(x.toFloat(), y.toFloat())) { out[i] = 0; continue }
            val nm = similarity(feature(p[i]), nail)
            val ns = skin?.let { similarity(feature(p[i]), it) } ?: 0f
            if (nm < .40f || (skin != null && ns > .93f && nm - ns < .02f)) out[i] = 0
        }
        return connectedToSeed(out, seed, w, h)
    }

    private fun connectedToSeed(mask: ByteArray, seed: BooleanArray, w: Int, h: Int): ByteArray {
        val out = ByteArray(mask.size); val seen = BooleanArray(mask.size); val q = IntArray(mask.size)
        var head = 0; var tail = 0
        for (i in seed.indices) if (seed[i] && (mask[i].toInt() and 255) > 0) { seen[i] = true; out[i] = 255.toByte(); q[tail++] = i }
        while (head < tail) {
            val cur = q[head++]; val x = cur % w; val y = cur / w
            for (n in neighbors(x, y, w, h)) if (!seen[n] && (mask[n].toInt() and 255) > 0) { seen[n] = true; out[n] = mask[n]; q[tail++] = n }
        }
        return out
    }

    private fun model(p: IntArray, mask: BooleanArray, limit: Int): M? {
        val fs = ArrayList<F>(limit); val step = max(1, mask.size / limit); var i = 0
        while (i < mask.size && fs.size < limit) { if (mask[i]) fs += feature(p[i]); i += step }
        return if (fs.size >= 8) M(mean(fs)) else null
    }

    private fun skinModel(p: IntArray, seed: BooleanArray, env: Env, w: Int, h: Int): M? {
        val fs = ArrayList<F>(120)
        for (y in 0 until h step 3) for (x in 0 until w step 3) {
            if (fs.size >= 120) return M(mean(fs))
            if (env.outsideDistance(x.toFloat(), y.toFloat()) < 2f) continue
            if (!seed[y * w + x]) fs += feature(p[y * w + x])
        }
        return if (fs.size >= 10) M(mean(fs)) else null
    }

    private fun mean(fs: List<F>): F = F(
        fs.map { it.r }.average().toFloat(), fs.map { it.g }.average().toFloat(), fs.map { it.b }.average().toFloat(),
        fs.map { it.y }.average().toFloat(), fs.map { it.s }.average().toFloat(),
    )

    private fun feature(px: Int): F {
        val r = (px shr 16 and 255) / 255f; val g = (px shr 8 and 255) / 255f; val b = (px and 255) / 255f
        val hi = max(r, max(g, b)); val lo = min(r, min(g, b)); val sat = if (hi == 0f) 0f else (hi - lo) / hi
        return F(r, g, b, .299f * r + .587f * g + .114f * b, sat)
    }

    private fun similarity(a: F, m: M): Float {
        val rgb = kotlin.math.sqrt(((a.r - m.f.r) * (a.r - m.f.r) + (a.g - m.f.g) * (a.g - m.f.g) + (a.b - m.f.b) * (a.b - m.f.b)).toDouble()).toFloat() / 1.732f
        return (1f - (rgb * .58f + abs(a.y - m.f.y) * .27f + abs(a.s - m.f.s) * .15f)).coerceIn(0f, 1f)
    }

    private fun polygon(mask: ByteArray, w: Int, h: Int, ox: Int, oy: Int): List<ImageCoordinates.PixelPoint>? {
        val rows = ArrayList<Triple<Int, Int, Int>>()
        for (y in 0 until h) {
            var l = w; var r = -1
            for (x in 0 until w) if ((mask[y * w + x].toInt() and 255) >= 255) { l = min(l, x); r = max(r, x) }
            if (r >= l) rows += Triple(y, l, r)
        }
        if (rows.size < 4) return null
        val out = ArrayList<ImageCoordinates.PixelPoint>(rows.size * 2)
        rows.forEachIndexed { i, row -> if (i % 2 == 0) out += ImageCoordinates.PixelPoint((row.second + ox).toFloat(), (row.first + oy).toFloat()) }
        rows.indices.reversed().forEach { i -> if (i % 2 == 0) { val row = rows[i]; out += ImageCoordinates.PixelPoint((row.third + ox).toFloat(), (row.first + oy).toFloat()) } }
        return out.takeIf { it.size >= 3 }
    }

    private fun neighbors(x: Int, y: Int, w: Int, h: Int): IntArray {
        val r = IntArray(4); var c = 0
        if (x > 0) r[c++] = y * w + x - 1
        if (x + 1 < w) r[c++] = y * w + x + 1
        if (y > 0) r[c++] = (y - 1) * w + x
        if (y + 1 < h) r[c++] = (y + 1) * w + x
        return r.copyOf(c)
    }

    companion object { const val EXTRA_BASE = .20f; const val EXTRA_TIP = .30f }
}
