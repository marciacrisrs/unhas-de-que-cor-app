package br.com.unhasdequecor.data.vision

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates

enum class Handedness {
    LEFT,
    RIGHT,
    UNKNOWN,
}

/**
 * Resultado bruto do Hand Landmarker (21 pontos normalizados + metadados).
 */
data class HandLandmarks(
    val points: List<ImageCoordinates.NormPoint>,
    val imageWidth: Int,
    val imageHeight: Int,
    val handedness: Handedness = Handedness.UNKNOWN,
    val presenceScore: Float = 1f,
) {
    init {
        require(points.size >= MIN_POINTS)
        require(imageWidth > 0 && imageHeight > 0)
    }

    fun point(index: Int): ImageCoordinates.NormPoint = points[index]

    companion object {
        const val MIN_POINTS = 21
        /** Tips MediaPipe: polegar, indicador, médio, anelar, mindinho. */
        val TIP_INDICES = intArrayOf(4, 8, 12, 16, 20)
    }
}
