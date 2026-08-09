package br.com.unhasdequecor.ui.components

/**
 * Âncoras normalizadas (0–1) das unhas sobre a foto da mão (ContentScale.Crop).
 * Prévia aproximada até existir segmentação real.
 */
data class NailOverlayAnchor(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float = 0f,
)

object NailOverlayAnchors {
    /** Layout genérico para foto própria (pose tipo “unhas à mostra”). */
    val DEFAULT: List<NailOverlayAnchor> = listOf(
        NailOverlayAnchor(0.28f, 0.38f, 0.11f, 0.07f, -28f),
        NailOverlayAnchor(0.42f, 0.30f, 0.10f, 0.075f, -8f),
        NailOverlayAnchor(0.54f, 0.28f, 0.10f, 0.08f, 2f),
        NailOverlayAnchor(0.66f, 0.32f, 0.095f, 0.075f, 12f),
        NailOverlayAnchor(0.76f, 0.40f, 0.09f, 0.07f, 26f),
    )

    private val SAMPLE_LAYOUTS: Map<String, List<NailOverlayAnchor>> = mapOf(
        "retinta_vinho" to curledRightStack(),
        "morena_nude" to curledRightStack(shiftX = -0.02f),
        "clara_vermelho" to curledLeftStack(),
        "morena_clara_coral" to shortNailsLayout(),
        "media_rosa" to longNailsLayout(),
    )

    fun forSample(sampleId: String?): List<NailOverlayAnchor> =
        sampleId?.let { SAMPLE_LAYOUTS[it] } ?: DEFAULT

    private fun curledRightStack(shiftX: Float = 0f): List<NailOverlayAnchor> = listOf(
        NailOverlayAnchor(0.30f + shiftX, 0.36f, 0.10f, 0.065f, -35f),
        NailOverlayAnchor(0.48f + shiftX, 0.29f, 0.095f, 0.07f, -6f),
        NailOverlayAnchor(0.58f + shiftX, 0.27f, 0.095f, 0.075f, 4f),
        NailOverlayAnchor(0.68f + shiftX, 0.31f, 0.09f, 0.07f, 14f),
        NailOverlayAnchor(0.76f + shiftX, 0.39f, 0.085f, 0.065f, 28f),
    )

    private fun curledLeftStack(): List<NailOverlayAnchor> = listOf(
        NailOverlayAnchor(0.72f, 0.36f, 0.10f, 0.065f, 32f),
        NailOverlayAnchor(0.54f, 0.28f, 0.095f, 0.07f, 8f),
        NailOverlayAnchor(0.44f, 0.26f, 0.095f, 0.075f, -2f),
        NailOverlayAnchor(0.34f, 0.30f, 0.09f, 0.07f, -14f),
        NailOverlayAnchor(0.26f, 0.38f, 0.085f, 0.065f, -28f),
    )

    private fun shortNailsLayout(): List<NailOverlayAnchor> = listOf(
        NailOverlayAnchor(0.30f, 0.38f, 0.09f, 0.05f, -30f),
        NailOverlayAnchor(0.46f, 0.32f, 0.085f, 0.052f, -6f),
        NailOverlayAnchor(0.56f, 0.30f, 0.085f, 0.055f, 2f),
        NailOverlayAnchor(0.66f, 0.33f, 0.08f, 0.052f, 12f),
        NailOverlayAnchor(0.74f, 0.40f, 0.075f, 0.05f, 24f),
    )

    private fun longNailsLayout(): List<NailOverlayAnchor> = listOf(
        NailOverlayAnchor(0.28f, 0.34f, 0.10f, 0.09f, -32f),
        NailOverlayAnchor(0.44f, 0.26f, 0.095f, 0.095f, -8f),
        NailOverlayAnchor(0.55f, 0.24f, 0.095f, 0.10f, 2f),
        NailOverlayAnchor(0.66f, 0.28f, 0.09f, 0.095f, 14f),
        NailOverlayAnchor(0.75f, 0.36f, 0.085f, 0.09f, 28f),
    )
}
