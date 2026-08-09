package br.com.unhasdequecor.ui.components

/**
 * Âncoras normalizadas (0–1) no espaço da imagem (ContentScale.FillBounds + aspect da bitmap).
 * Pose das amostras: punho semi-fechado com unhas de frente para a câmera.
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
        NailOverlayAnchor(0.28f, 0.36f, 0.09f, 0.07f, -28f),
        NailOverlayAnchor(0.42f, 0.30f, 0.085f, 0.075f, -8f),
        NailOverlayAnchor(0.54f, 0.28f, 0.085f, 0.08f, 2f),
        NailOverlayAnchor(0.66f, 0.32f, 0.08f, 0.075f, 14f),
        NailOverlayAnchor(0.76f, 0.40f, 0.075f, 0.07f, 28f),
    )

    private val SAMPLE_LAYOUTS: Map<String, List<NailOverlayAnchor>> = mapOf(
        // Centros calibrados sobre a unha (validado visualmente).
        "clara_vermelho" to listOf(
            NailOverlayAnchor(0.256f, 0.178f, 0.08f, 0.10f, -20f),
            NailOverlayAnchor(0.387f, 0.404f, 0.09f, 0.10f, -5f),
            NailOverlayAnchor(0.495f, 0.492f, 0.095f, 0.105f, 5f),
            NailOverlayAnchor(0.587f, 0.579f, 0.09f, 0.10f, 15f),
            NailOverlayAnchor(0.676f, 0.637f, 0.08f, 0.09f, 28f),
        ),
        "media_rosa" to listOf(
            NailOverlayAnchor(0.448f, 0.156f, 0.08f, 0.11f, -20f),
            NailOverlayAnchor(0.520f, 0.280f, 0.085f, 0.12f, -2f),
            NailOverlayAnchor(0.560f, 0.385f, 0.09f, 0.125f, 6f),
            NailOverlayAnchor(0.575f, 0.490f, 0.085f, 0.12f, 14f),
            NailOverlayAnchor(0.575f, 0.585f, 0.08f, 0.11f, 24f),
        ),
        // Mesma pose das demais amostras (punho fechado).
        "morena_nude" to listOf(
            NailOverlayAnchor(0.40f, 0.20f, 0.08f, 0.09f, -24f),
            NailOverlayAnchor(0.42f, 0.38f, 0.085f, 0.10f, -4f),
            NailOverlayAnchor(0.49f, 0.48f, 0.09f, 0.105f, 4f),
            NailOverlayAnchor(0.56f, 0.56f, 0.085f, 0.10f, 14f),
            NailOverlayAnchor(0.63f, 0.62f, 0.08f, 0.09f, 26f),
        ),
        "retinta_vinho" to listOf(
            NailOverlayAnchor(0.36f, 0.20f, 0.085f, 0.10f, -22f),
            NailOverlayAnchor(0.42f, 0.36f, 0.09f, 0.11f, -4f),
            NailOverlayAnchor(0.50f, 0.46f, 0.095f, 0.115f, 4f),
            NailOverlayAnchor(0.58f, 0.54f, 0.09f, 0.11f, 14f),
            NailOverlayAnchor(0.66f, 0.61f, 0.085f, 0.10f, 26f),
        ),
        "morena_clara_coral" to listOf(
            NailOverlayAnchor(0.40f, 0.17f, 0.075f, 0.065f, -18f),
            NailOverlayAnchor(0.48f, 0.33f, 0.08f, 0.07f, -2f),
            NailOverlayAnchor(0.54f, 0.42f, 0.085f, 0.075f, 6f),
            NailOverlayAnchor(0.61f, 0.50f, 0.08f, 0.07f, 16f),
            NailOverlayAnchor(0.68f, 0.57f, 0.075f, 0.06f, 28f),
        ),
    )

    fun forSample(sampleId: String?): List<NailOverlayAnchor> =
        sampleId?.let { SAMPLE_LAYOUTS[it] } ?: DEFAULT

    fun hasMaskAsset(sampleId: String?): Boolean =
        sampleId != null && sampleId in MASK_SAMPLES

    private val MASK_SAMPLES = setOf("clara_vermelho", "media_rosa")
}
