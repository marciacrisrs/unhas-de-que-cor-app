package br.com.unhasdequecor.data.vision.nail

/**
 * Região de interesse da unha em pixels da imagem.
 * [polygon] descreve a placa estimada (almond), não um retângulo fixo.
 */
data class NailRoi(
    val finger: Finger,
    val bounds: ImageCoordinates.PixelRect,
    val polygon: List<ImageCoordinates.PixelPoint>,
    val axisFromDip: ImageCoordinates.PixelPoint,
    val axisToTip: ImageCoordinates.PixelPoint,
    val lengthPx: Float,
    val widthPx: Float,
    val rotationDegrees: Float,
    val geometricConfidence: Float,
)
