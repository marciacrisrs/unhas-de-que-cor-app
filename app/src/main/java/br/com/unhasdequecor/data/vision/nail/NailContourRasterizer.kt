package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * Converts the continuous contour produced by the contour specialist into a
 * sub-pixel antialiased alpha mask without allowing it to escape the learned
 * segmentation support.
 *
 * The learned mask remains the safety envelope. The contour only determines
 * the continuous boundary inside a one-pixel support dilation. This removes
 * the visible pixel stair-stepping while avoiding a synthetic nail expansion.
 */
class NailContourRasterizer {
    fun rasterize(mask: NailMask): NailMask {
        val polygon = mask.boundaryPolygon
        if (polygon == null || polygon.size < MIN_POLYGON_POINTS) return mask

        val contour = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(contour)
        val path = Path()
        polygon.forEachIndexed { index, point ->
            val x = point.x - mask.originX
            val y = point.y - mask.originY
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            isAntiAlias = true
            color = Color.WHITE
        }
        canvas.drawPath(path, paint)

        val rendered = IntArray(mask.width * mask.height)
        contour.getPixels(rendered, 0, mask.width, 0, 0, mask.width, mask.height)
        contour.recycle()

        val output = mergeRasterWithSupport(
            original = mask.alpha,
            rasterArgb = rendered,
            width = mask.width,
            height = mask.height,
        )
        if (output.none { (it.toInt() and ALPHA_MASK) != 0 }) {
            // Drawing or pixel unpack failed: keep the learned mask rather than
            // dropping every nail in the try-on pipeline.
            return mask
        }
        return mask.copy(alpha = output)
    }

    private companion object {
        const val MIN_POLYGON_POINTS = 6
        const val ALPHA_MASK = 255
    }
}

/**
 * [Bitmap.getPixels] returns packed ARGB. Alpha lives in the high byte; the
 * low byte is blue. Reading `pixel and 0xFF` on an opaque black/white fill
 * yields 0 and wipes the mask.
 */
internal fun packedArgbAlpha(pixel: Int): Int = (pixel ushr ALPHA_SHIFT) and ALPHA_BYTE_MASK

internal fun mergeRasterWithSupport(
    original: ByteArray,
    rasterArgb: IntArray,
    width: Int,
    height: Int,
): ByteArray {
    val output = ByteArray(original.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val index = y * width + x
            val rasterAlpha = packedArgbAlpha(rasterArgb[index])
            if (rasterAlpha != 0) {
                val support = localSupport(original, x, y, width, height)
                if (support != 0) {
                    output[index] = minOf(rasterAlpha, support).toByte()
                }
            }
        }
    }
    return output
}

private fun localSupport(alpha: ByteArray, x: Int, y: Int, width: Int, height: Int): Int {
    var strongest = 0
    for (dy in -SUPPORT_RADIUS..SUPPORT_RADIUS) {
        val ny = y + dy
        if (ny !in 0 until height) continue
        for (dx in -SUPPORT_RADIUS..SUPPORT_RADIUS) {
            val nx = x + dx
            if (nx !in 0 until width) continue
            strongest = maxOf(strongest, alpha[ny * width + nx].toInt() and ALPHA_BYTE_MASK)
        }
    }
    return strongest
}

private const val SUPPORT_RADIUS = 1
private const val ALPHA_SHIFT = 24
private const val ALPHA_BYTE_MASK = 255
