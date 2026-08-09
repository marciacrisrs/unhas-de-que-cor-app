package br.com.unhasdequecor.data.vision.nail

/**
 * Dedos MediaPipe Hands (índices MCP / PIP / DIP / TIP).
 * Polegar: MCP=2, IP≈PIP=3, tip=4 (sem DIP distinto — IP faz o papel de DIP).
 */
enum class Finger(
    val mcpIndex: Int,
    val pipIndex: Int,
    val dipIndex: Int,
    val tipIndex: Int,
) {
    THUMB(mcpIndex = 2, pipIndex = 3, dipIndex = 3, tipIndex = 4),
    INDEX(mcpIndex = 5, pipIndex = 6, dipIndex = 7, tipIndex = 8),
    MIDDLE(mcpIndex = 9, pipIndex = 10, dipIndex = 11, tipIndex = 12),
    RING(mcpIndex = 13, pipIndex = 14, dipIndex = 15, tipIndex = 16),
    PINKY(mcpIndex = 17, pipIndex = 18, dipIndex = 19, tipIndex = 20),
    ;

    companion object {
        val ALL: List<Finger> = entries
    }
}
