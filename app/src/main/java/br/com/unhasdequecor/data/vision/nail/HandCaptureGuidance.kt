package br.com.unhasdequecor.data.vision.nail

/**
 * Checklist de captura alinhado aos especialistas (vision + UI + product visual).
 * Prioriza luz frontal e unhas à mostra — crítico para pele retinta.
 */
object HandCaptureGuidance {
    const val TITLE = "Para o try-on funcionar melhor"

    val checklist: List<String> = listOf(
        "Luz na frente da mão (janela ou lâmpada) — evite contraluz e flash direto",
        "Unhas de frente para a câmera, dedos um pouco abertos",
        "Mão preenchendo boa parte da foto (não longe demais)",
        "Em pele retinta, prefira luz natural ou uma lâmpada clara na frente",
    )

    const val CONFIRM_LEAD =
        "Confira a foto com a lista abaixo — isso ajuda muito em peles retintas."

    const val REPLACE_SHEET_HINT =
        "Dica: luz frontal e unhas à mostra. Em pele retinta, evite sombra no rosto da mão."

    const val SAMPLE_PICKER_HINT =
        "Comece por pele retinta se for o seu tom — as fotos de exemplo também treinam o try-on."

    fun asPlainText(): String = checklist.joinToString(separator = "\n") { "• $it" }
}
