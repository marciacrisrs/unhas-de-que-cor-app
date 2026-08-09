package br.com.unhasdequecor.ui.components

/**
 * Âncoras normalizadas (0–1) no espaço da imagem (ContentScale.FillBounds + aspect da bitmap).
 * Pose das amostras: punho semi-fechado com unhas de frente para a câmera.
 *
 * Preferência: máscara em `hand_nail_masks/` (recoloração). As âncoras são fallback.
 */
data class NailOverlayAnchor(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float = 0f,
)

object NailOverlayAnchors {
    /**
     * Fallback para foto própria (mão aberta / unhas no topo).
     * MediaPipe tem prioridade quando detectar a mão do usuário.
     */
    val DEFAULT: List<NailOverlayAnchor> = listOf(
        NailOverlayAnchor(0.28f, 0.34f, 0.075f, 0.055f, -28f),
        NailOverlayAnchor(0.42f, 0.28f, 0.070f, 0.060f, -8f),
        NailOverlayAnchor(0.54f, 0.26f, 0.070f, 0.062f, 2f),
        NailOverlayAnchor(0.66f, 0.30f, 0.065f, 0.058f, 14f),
        NailOverlayAnchor(0.76f, 0.38f, 0.060f, 0.052f, 28f),
    )

    private val SAMPLE_LAYOUTS: Map<String, List<NailOverlayAnchor>> = mapOf(
        "clara_vermelho" to listOf(
            NailOverlayAnchor(0.251f, 0.182f, 0.075f, 0.095f, -20f),
            NailOverlayAnchor(0.387f, 0.404f, 0.085f, 0.095f, -5f),
            NailOverlayAnchor(0.495f, 0.493f, 0.090f, 0.100f, 5f),
            NailOverlayAnchor(0.586f, 0.580f, 0.085f, 0.095f, 15f),
            NailOverlayAnchor(0.677f, 0.637f, 0.075f, 0.085f, 28f),
        ),
        "media_rosa" to listOf(
            NailOverlayAnchor(0.444f, 0.158f, 0.080f, 0.105f, -16f),
            NailOverlayAnchor(0.482f, 0.290f, 0.075f, 0.110f, -3f),
            NailOverlayAnchor(0.532f, 0.386f, 0.080f, 0.115f, 4f),
            NailOverlayAnchor(0.566f, 0.482f, 0.075f, 0.110f, 12f),
            NailOverlayAnchor(0.552f, 0.568f, 0.070f, 0.100f, 20f),
        ),
        "morena_nude" to listOf(
            NailOverlayAnchor(0.400f, 0.195f, 0.075f, 0.085f, -22f),
            NailOverlayAnchor(0.435f, 0.395f, 0.080f, 0.095f, -4f),
            NailOverlayAnchor(0.510f, 0.495f, 0.085f, 0.100f, 4f),
            NailOverlayAnchor(0.580f, 0.575f, 0.080f, 0.095f, 14f),
            NailOverlayAnchor(0.650f, 0.635f, 0.070f, 0.085f, 25f),
        ),
        "retinta_vinho" to listOf(
            NailOverlayAnchor(0.355f, 0.195f, 0.080f, 0.090f, -18f),
            NailOverlayAnchor(0.430f, 0.375f, 0.085f, 0.100f, -4f),
            NailOverlayAnchor(0.510f, 0.475f, 0.090f, 0.105f, 3f),
            NailOverlayAnchor(0.590f, 0.555f, 0.085f, 0.100f, 13f),
            NailOverlayAnchor(0.665f, 0.620f, 0.075f, 0.090f, 23f),
        ),
        "morena_clara_coral" to listOf(
            NailOverlayAnchor(0.400f, 0.175f, 0.070f, 0.075f, -16f),
            NailOverlayAnchor(0.480f, 0.345f, 0.075f, 0.080f, -2f),
            NailOverlayAnchor(0.545f, 0.435f, 0.080f, 0.085f, 5f),
            NailOverlayAnchor(0.610f, 0.515f, 0.075f, 0.080f, 14f),
            NailOverlayAnchor(0.675f, 0.580f, 0.070f, 0.070f, 24f),
        ),
    )

    fun forSample(sampleId: String?): List<NailOverlayAnchor> =
        sampleId?.let { SAMPLE_LAYOUTS[it] } ?: DEFAULT

    fun hasMaskAsset(sampleId: String?): Boolean =
        sampleId != null && sampleId in MASK_SAMPLES

    private val MASK_SAMPLES = setOf(
        "clara_vermelho",
        "media_rosa",
    )
}
