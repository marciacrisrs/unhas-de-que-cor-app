package br.com.unhasdequecor.data.vision.nail

/**
 * Converte landmarks MediaPipe (normalizados 0–1) em âncoras de unha.
 *
 * Distâncias em **pixels** (corrige aspect da foto); depois volta a frações
 * da imagem para o preview FillBounds.
 *
 * Placa da unha ≈ do DIP até a ponta; geometria via [NailPlateCalibration]
 * (paridade com [NailRoiEstimator]). Filtra dedos com eixo colapsado (punho /
 * oclusão) para não pintar elipses flutuantes.
 */
object NailLandmarkMapper {

    fun fromNormalizedLandmarks(
        landmarks: List<NormalizedPoint>,
        imageWidth: Int = 1,
        imageHeight: Int = 1,
    ): List<NailOverlayAnchor>? {
        if (landmarks.size < MIN_LANDMARKS || imageWidth <= 0 || imageHeight <= 0) {
            return null
        }
        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()
        val anchors = Finger.ALL.mapNotNull { finger ->
            val tip = landmarks[finger.tipIndex]
            val dip = landmarks[finger.dipIndex]
            val pip = landmarks[finger.pipIndex]
            val mcp = landmarks[finger.mcpIndex]
            val plate = NailPlateCalibration.plateFromPixels(
                finger = finger,
                tipX = tip.x * w,
                tipY = tip.y * h,
                dipX = dip.x * w,
                dipY = dip.y * h,
                pipX = pip.x * w,
                pipY = pip.y * h,
                mcpX = mcp.x * w,
                mcpY = mcp.y * h,
            )
            if (!NailPlateCalibration.isUsablePlate(plate)) return@mapNotNull null
            val anchor = NailOverlayAnchor(
                centerX = (plate.centerX / w).coerceIn(0f, 1f),
                centerY = (plate.centerY / h).coerceIn(0f, 1f),
                width = (plate.widthPx / w).coerceIn(MIN_NAIL_WIDTH_NORM, MAX_NAIL_WIDTH_NORM),
                height = (plate.lengthPx / h).coerceIn(MIN_NAIL_HEIGHT_NORM, MAX_NAIL_HEIGHT_NORM),
                rotationDegrees = plate.rotationDegrees,
            )
            anchor.takeIf {
                it.centerX in PLAUSIBLE_RANGE && it.centerY in PLAUSIBLE_RANGE
            }
        }
        return anchors.takeIf { it.size >= MIN_PLAUSIBLE_NAILS }
    }

    data class NormalizedPoint(val x: Float, val y: Float)

    private val PLAUSIBLE_RANGE = 0.02f..0.98f

    const val PREVIEW_ASPECT = 3f / 4f
    /** Mínimo de unhas usáveis para aceitar âncoras / alinhar path de máscara. */
    const val MIN_PLAUSIBLE_NAILS = 2
    private const val MIN_LANDMARKS = 21
    private const val MIN_NAIL_WIDTH_NORM = 0.018f
    private const val MAX_NAIL_WIDTH_NORM = 0.14f
    private const val MIN_NAIL_HEIGHT_NORM = 0.018f
    private const val MAX_NAIL_HEIGHT_NORM = 0.14f
}
