package br.com.unhasdequecor.data.vision.nail

import kotlin.math.hypot

/**
 * Conversões centralizadas: landmarks normalizados ↔ pixels da imagem.
 * Preview Compose com FillBounds + aspect da bitmap: image == preview.
 */
object ImageCoordinates {
    data class NormPoint(val x: Float, val y: Float)
    data class PixelPoint(val x: Float, val y: Float)

    /** Retângulo em pixels da imagem (left/top inclusivos, right/bottom exclusivos). */
    data class PixelRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        fun width(): Int = (right - left).coerceAtLeast(0)
        fun height(): Int = (bottom - top).coerceAtLeast(0)
    }

    fun toPixel(norm: NormPoint, imageWidth: Int, imageHeight: Int): PixelPoint =
        PixelPoint(x = norm.x * imageWidth, y = norm.y * imageHeight)

    fun toNorm(pixel: PixelPoint, imageWidth: Int, imageHeight: Int): NormPoint =
        NormPoint(
            x = (pixel.x / imageWidth.toFloat()).coerceIn(0f, 1f),
            y = (pixel.y / imageHeight.toFloat()).coerceIn(0f, 1f),
        )

    fun distancePx(a: PixelPoint, b: PixelPoint): Float =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    /** Preview FillBounds: fração da imagem == fração da view. */
    fun imageNormToPreviewNorm(norm: NormPoint): NormPoint = norm
}
