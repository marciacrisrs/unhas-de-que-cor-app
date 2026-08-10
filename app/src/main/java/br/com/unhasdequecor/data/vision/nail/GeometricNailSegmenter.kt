package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import android.graphics.Color
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Segmentação geométrica conservadora (sem OpenCV):
 * 1) rasteriza almond suave (distance field);
 * 2) só remove pele óbvia na **borda** da placa (miolo sempre preservado);
 * 3) se o refinamento apagar demais, volta ao almond suave.
 *
 * Unhas naturais ≈ pele: trim agressivo no interior pintava buracos / nada.
 */
@Singleton
class GeometricNailSegmenter @Inject constructor() : NailSegmenter {

    override fun segment(image: Bitmap, roi: NailRoi): NailMask? {
        val bounds = roi.bounds
        val rw = bounds.width()
        val rh = bounds.height()
        if (rw < 4 || rh < 4) return null
        if (bounds.left < 0 || bounds.top < 0 ||
            bounds.right > image.width || bounds.bottom > image.height
        ) {
            return null
        }

        val pixels = IntArray(rw * rh)
        image.getPixels(pixels, 0, rw, bounds.left, bounds.top, rw, rh)

        val polyLocal = roi.polygon.map { p ->
            PixelPoint(p.x - bounds.left, p.y - bounds.top)
        }
        val softGeo = softRasterize(polyLocal, rw, rh)
        val solidGeo = ByteArray(rw * rh)
        for (i in softGeo.indices) {
            solidGeo[i] = if ((softGeo[i].toInt() and 0xFF) >= MASK_SOLID) 255.toByte() else 0
        }

        val skin = estimateSkinColor(pixels, solidGeo, rw, rh)
        val trimmed = trimBorderSkin(pixels, softGeo, solidGeo, skin, rw, rh)

        val kept = trimmed.count { (it.toInt() and 0xFF) >= MASK_SOLID }
        val geoCount = softGeo.count { (it.toInt() and 0xFF) >= MASK_SOLID }.coerceAtLeast(1)
        val alpha = if (kept.toFloat() / geoCount < MIN_KEEP_RATIO) {
            softGeo
        } else {
            feather(binarize(trimmed), rw, rh, radius = FEATHER_RADIUS)
        }

        return NailMask(
            width = rw,
            height = rh,
            alpha = alpha,
            originX = bounds.left,
            originY = bounds.top,
        )
    }

    /**
     * Mantém o miolo (~70% interno) intacto; só na coroa externa rejeita pele tipicamente igual.
     */
    private fun trimBorderSkin(
        pixels: IntArray,
        softGeo: ByteArray,
        solidGeo: ByteArray,
        skin: SkinStats,
        w: Int,
        h: Int,
    ): ByteArray {
        val out = softGeo.copyOf()
        val dist = interiorDistance(solidGeo, w, h)
        var maxDist = 1f
        for (d in dist) maxDist = max(maxDist, d)
        val coreRadius = maxDist * CORE_FRACTION

        for (i in pixels.indices) {
            val onBorder = (softGeo[i].toInt() and 0xFF) > 0 && dist[i] < coreRadius
            if (onBorder && isObviousSkin(pixels[i], skin)) {
                out[i] = 0
            }
        }
        return out
    }

    private fun isObviousSkin(pixel: Int, skin: SkinStats): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        val skinDist = colorDistance(r, g, b, skin)
        val sat = saturation(r, g, b)
        val lum = luminance(r, g, b)
        val looksLikePlate = skinDist > SKIN_SOFT_DIST ||
            sat > 0.14f ||
            lum > skin.lum + 22f
        return !looksLikePlate && skinDist < SKIN_REJECT_DIST
    }

    private fun softRasterize(poly: List<PixelPoint>, width: Int, height: Int): ByteArray {
        val solid = ByteArray(width * height)
        rasterizePolygon(poly, width, height, solid)
        return feather(solid, width, height, radius = FEATHER_RADIUS)
    }

    private fun binarize(src: ByteArray): ByteArray {
        val out = ByteArray(src.size)
        for (i in src.indices) {
            out[i] = if ((src[i].toInt() and 0xFF) >= MASK_SOLID) 255.toByte() else 0
        }
        return out
    }

    private fun rasterizePolygon(
        poly: List<PixelPoint>,
        width: Int,
        height: Int,
        out: ByteArray,
    ) {
        if (poly.size < 3) return
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pointInPolygon(x + 0.5f, y + 0.5f, poly)) {
                    out[y * width + x] = 255.toByte()
                }
            }
        }
    }

    private fun pointInPolygon(x: Float, y: Float, poly: List<PixelPoint>): Boolean {
        var inside = false
        var j = poly.lastIndex
        for (i in poly.indices) {
            val xi = poly[i].x
            val yi = poly[i].y
            val xj = poly[j].x
            val yj = poly[j].y
            val intersect = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { abs(it) > 1e-5f } ?: 1e-5f) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    private data class SkinStats(val r: Float, val g: Float, val b: Float, val lum: Float)

    private fun estimateSkinColor(pixels: IntArray, geo: ByteArray, w: Int, h: Int): SkinStats {
        var sr = 0.0
        var sg = 0.0
        var sb = 0.0
        var n = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val outsideGeo = geo[i] == 0.toByte()
                if (outsideGeo && nearMask(geo, w, h, x, y, RING_RADIUS)) {
                    val c = pixels[i]
                    sr += Color.red(c)
                    sg += Color.green(c)
                    sb += Color.blue(c)
                    n++
                }
            }
        }
        if (n < 8) {
            for (x in 0 until w) {
                val c1 = pixels[x]
                val c2 = pixels[(h - 1) * w + x]
                sr += Color.red(c1) + Color.red(c2)
                sg += Color.green(c1) + Color.green(c2)
                sb += Color.blue(c1) + Color.blue(c2)
                n += 2
            }
        }
        val inv = 1.0 / n.coerceAtLeast(1)
        val r = (sr * inv).toFloat()
        val g = (sg * inv).toFloat()
        val b = (sb * inv).toFloat()
        return SkinStats(r, g, b, luminance(r.roundToInt(), g.roundToInt(), b.roundToInt()))
    }

    private fun nearMask(mask: ByteArray, w: Int, h: Int, x: Int, y: Int, radius: Int): Boolean {
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until w || ny !in 0 until h) continue
                if (mask[ny * w + nx] != 0.toByte()) return true
            }
        }
        return false
    }

    /** Distância aproximada ao exterior (maior = mais interno). */
    private fun interiorDistance(solid: ByteArray, w: Int, h: Int): FloatArray {
        val dist = FloatArray(solid.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                dist[i] = if (solid[i] == 0.toByte()) {
                    0f
                } else {
                    nearestOutsideDistance(solid, w, h, x, y, search = EDGE_SEARCH)
                }
            }
        }
        return dist
    }

    private fun nearestOutsideDistance(
        solid: ByteArray,
        w: Int,
        h: Int,
        x: Int,
        y: Int,
        search: Int,
    ): Float {
        var best = search.toFloat()
        for (dy in -search..search) {
            for (dx in -search..search) {
                val nx = x + dx
                val ny = y + dy
                val outside = nx !in 0 until w ||
                    ny !in 0 until h ||
                    solid[ny * w + nx] == 0.toByte()
                if (outside) {
                    best = min(best, hypot(dx.toDouble(), dy.toDouble()).toFloat())
                }
            }
        }
        return best
    }

    private fun feather(src: ByteArray, w: Int, h: Int, radius: Int): ByteArray {
        if (radius <= 0) return src
        val out = ByteArray(src.size)
        val r2 = radius * radius
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                out[i] = featherAlpha(src, w, h, x, y, radius, r2)
            }
        }
        return out
    }

    private fun featherAlpha(
        src: ByteArray,
        w: Int,
        h: Int,
        x: Int,
        y: Int,
        radius: Int,
        r2: Int,
    ): Byte {
        val i = y * w + x
        if ((src[i].toInt() and 0xFF) >= MASK_SOLID) {
            return 255.toByte()
        }
        val best = nearestSolidDistanceSq(src, w, h, x, y, radius)
        if (best == Int.MAX_VALUE || best > r2) {
            return 0
        }
        val t = 1f - sqrt(best.toFloat()) / (radius + 0.01f)
        return (t.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
    }

    private fun nearestSolidDistanceSq(
        src: ByteArray,
        w: Int,
        h: Int,
        x: Int,
        y: Int,
        radius: Int,
    ): Int {
        var best = Int.MAX_VALUE
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val nx = x + dx
                val ny = y + dy
                val inside = nx in 0 until w && ny in 0 until h
                val solid = inside && (src[ny * w + nx].toInt() and 0xFF) >= MASK_SOLID
                if (solid) {
                    val d2 = dx * dx + dy * dy
                    if (d2 < best) best = d2
                }
            }
        }
        return best
    }

    private fun colorDistance(r: Int, g: Int, b: Int, skin: SkinStats): Float {
        val dr = r - skin.r
        val dg = g - skin.g
        val db = b - skin.b
        return sqrt(dr * dr + dg * dg + db * db)
    }

    private fun saturation(r: Int, g: Int, b: Int): Float {
        val maxC = max(r, max(g, b)).toFloat()
        val minC = min(r, min(g, b)).toFloat()
        return if (maxC <= 1f) 0f else (maxC - minC) / maxC
    }

    private fun luminance(r: Int, g: Int, b: Int): Float =
        0.299f * r + 0.587f * g + 0.114f * b

    private companion object {
        const val SKIN_REJECT_DIST = 36f
        const val SKIN_SOFT_DIST = 24f
        const val RING_RADIUS = 3
        const val MASK_SOLID = 128
        const val MIN_KEEP_RATIO = 0.40f
        const val FEATHER_RADIUS = 2
        const val CORE_FRACTION = 0.55f
        const val EDGE_SEARCH = 6
    }
}
