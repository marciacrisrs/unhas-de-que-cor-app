package br.com.unhasdequecor.data.vision.nail

/**
 * Máscara 8-bit da unha no espaço da ROI (ou imagem completa).
 * 0 = fora; 255 = dentro; valores intermediários = feathering.
 */
data class NailMask(
    val width: Int,
    val height: Int,
    val alpha: ByteArray,
    /** Origem da máscara no espaço da imagem completa (px). */
    val originX: Int = 0,
    val originY: Int = 0,
) {
    init {
        require(width > 0 && height > 0)
        require(alpha.size == width * height) {
            "alpha size ${alpha.size} != ${width * height}"
        }
    }

    fun coverageAt(localX: Int, localY: Int): Int {
        if (localX !in 0 until width || localY !in 0 until height) return 0
        return alpha[localY * width + localX].toInt() and 0xFF
    }

    fun filledRatio(): Float {
        var filled = 0
        for (v in alpha) {
            if ((v.toInt() and 0xFF) >= SOLID_THRESHOLD) filled++
        }
        return filled.toFloat() / alpha.size.toFloat()
    }

    private companion object {
        const val SOLID_THRESHOLD = 128
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NailMask) return false
        return width == other.width &&
            height == other.height &&
            originX == other.originX &&
            originY == other.originY &&
            alpha.contentEquals(other.alpha)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + originX
        result = 31 * result + originY
        result = 31 * result + alpha.contentHashCode()
        return result
    }
}
