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
        plateLeft: Int = rw / 4,
        plateTop: Int = rh / 5,
        plateRight: Int = (rw * 3) / 4,
        plateBottom: Int = (rh * 3) / 4,
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
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or
            (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    private fun darken(argb: Int, factor: Float): Int {
        val r = ((argb shr 16) and 0xFF) * factor
        val g = ((argb shr 8) and 0xFF) * factor
        val b = (argb and 0xFF) * factor
        return argb(r.toInt(), g.toInt(), b.toInt())
    }
}
