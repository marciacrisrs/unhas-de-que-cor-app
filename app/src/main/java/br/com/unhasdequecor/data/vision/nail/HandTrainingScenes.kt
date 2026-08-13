package br.com.unhasdequecor.data.vision.nail

/**
 * Cenas sintéticas JVM para treinar/regredir caminhos de pele retinta
 * (segmentação, luminância, enhance) sem fotos grandes no git.
 *
 * Não são fotos reais — valores RGB típicos de pele profunda + placa/esmalte.
 */
object HandTrainingScenes {

    /** Pele retinta média (L* baixo). */
    val RETINTA_SKIN = argb(72, 48, 38)

    /** Unha natural clara sobre pele retinta (bom contraste). */
    val RETINTA_NATURAL_PLATE = argb(168, 140, 128)

    /** Esmalte vinho escuro (pior contraste — precisa de crominância). */
    val RETINTA_WINE_POLISH = argb(58, 22, 32)

    /** Pele morena (variedade de treino). */
    val MORENA_SKIN = argb(140, 98, 78)

    val MORENA_NUDE_POLISH = argb(190, 155, 135)

    /** Pele clara (controle). */
    val CLARA_SKIN = argb(210, 170, 150)

    val CLARA_RED_POLISH = argb(180, 40, 55)

    data class Scene(
        val id: String,
        val skinToneLabel: String,
        val skin: Int,
        val plate: Int,
        /** Luminância média alvo aproximada da cena (0–255). */
        val targetMeanLumaBand: ClosedFloatingPointRange<Float>,
    )

    /** Variedades priorizando retinta (mãe / pele profunda). */
    val varieties: List<Scene> = listOf(
        Scene(
            id = "retinta_natural_plate",
            skinToneLabel = "Pele retinta",
            skin = RETINTA_SKIN,
            plate = RETINTA_NATURAL_PLATE,
            targetMeanLumaBand = 45f..90f,
        ),
        Scene(
            id = "retinta_wine_polish",
            skinToneLabel = "Pele retinta",
            skin = RETINTA_SKIN,
            plate = RETINTA_WINE_POLISH,
            targetMeanLumaBand = 40f..85f,
        ),
        Scene(
            id = "retinta_underexposed",
            skinToneLabel = "Pele retinta (subexposta)",
            skin = darken(RETINTA_SKIN, 0.72f),
            plate = darken(RETINTA_WINE_POLISH, 0.80f),
            targetMeanLumaBand = 28f..70f,
        ),
        Scene(
            id = "morena_nude",
            skinToneLabel = "Pele morena",
            skin = MORENA_SKIN,
            plate = MORENA_NUDE_POLISH,
            targetMeanLumaBand = 90f..140f,
        ),
        Scene(
            id = "clara_vermelho",
            skinToneLabel = "Pele clara",
            skin = CLARA_SKIN,
            plate = CLARA_RED_POLISH,
            targetMeanLumaBand = 140f..200f,
        ),
    )

    fun deepSkinVarieties(): List<Scene> =
        varieties.filter { it.id.startsWith("retinta") }

    /**
     * Preenche um crop [rw]x[rh] com pele e um retângulo central de placa/esmalte.
     */
    fun fillNailCrop(
        rw: Int,
        rh: Int,
        skin: Int,
        plate: Int,
        plateLeft: Int = rw / PLATE_LEFT_DIV,
        plateTop: Int = rh / PLATE_TOP_DIV,
        plateRight: Int = (rw * PLATE_RIGHT_NUM) / PLATE_RIGHT_DEN,
        plateBottom: Int = (rh * PLATE_BOTTOM_NUM) / PLATE_BOTTOM_DEN,
    ): IntArray {
        val pixels = IntArray(rw * rh) { skin }
        for (y in plateTop until plateBottom) {
            for (x in plateLeft until plateRight) {
                pixels[y * rw + x] = plate
            }
        }
        return pixels
    }

    fun argb(r: Int, g: Int, b: Int): Int =
        (ALPHA_OPAQUE shl ALPHA_SHIFT) or
            (r.coerceIn(0, CHANNEL_MAX) shl RED_SHIFT) or
            (g.coerceIn(0, CHANNEL_MAX) shl GREEN_SHIFT) or
            b.coerceIn(0, CHANNEL_MAX)

    private fun darken(argb: Int, factor: Float): Int {
        val r = ((argb shr RED_SHIFT) and CHANNEL_MAX) * factor
        val g = ((argb shr GREEN_SHIFT) and CHANNEL_MAX) * factor
        val b = (argb and CHANNEL_MAX) * factor
        return argb(r.toInt(), g.toInt(), b.toInt())
    }

    private const val ALPHA_OPAQUE = 0xFF
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val CHANNEL_MAX = 255
    private const val PLATE_LEFT_DIV = 4
    private const val PLATE_TOP_DIV = 5
    private const val PLATE_RIGHT_NUM = 3
    private const val PLATE_RIGHT_DEN = 4
    private const val PLATE_BOTTOM_NUM = 3
    private const val PLATE_BOTTOM_DEN = 4
}
