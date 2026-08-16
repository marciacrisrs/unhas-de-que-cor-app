package br.com.unhasdequecor.data.vision.nail

/**
 * Métricas de uma execução do Live Try-On.
 *
 * Os tempos são monotônicos e representam o trabalho do pipeline, não o tempo
 * de renderização da UI. A estrutura é deliberadamente imutável para poder ser
 * exposta por um pipeline singleton sem compartilhar estado mutável com a UI.
 */
data class NailTryOnPerformanceMetrics(
    val totalMs: Long = 0L,
    val landmarkMs: Long = 0L,
    val roiMs: Long = 0L,
    val segmentationMs: Long = 0L,
    val trackingMs: Long = 0L,
    val recolorMs: Long = 0L,
    val frameIntervalMs: Long = 0L,
    val inferenceCount: Int = 0,
    val framesDropped: Int = 0,
    val overlayAgeMs: Long = 0L,
) {
    val effectiveFps: Float
        get() = if (frameIntervalMs <= 0L) 0f else 1000f / frameIntervalMs.toFloat()

    val pipelineFps: Float
        get() = if (totalMs <= 0L) 0f else 1000f / totalMs.toFloat()
}

/** Acumulador mínimo para uma janela de métricas sem alocar listas por frame. */
class NailTryOnPerformanceWindow(
    private val maxSamples: Int = DEFAULT_MAX_SAMPLES,
) {
    private val samples = ArrayDeque<NailTryOnPerformanceMetrics>()

    init {
        require(maxSamples > 0)
    }

    fun add(sample: NailTryOnPerformanceMetrics) {
        if (samples.size == maxSamples) samples.removeFirst()
        samples.addLast(sample)
    }

    fun latest(): NailTryOnPerformanceMetrics? = samples.lastOrNull()

    fun size(): Int = samples.size

    fun medianTotalMs(): Long = median { it.totalMs }

    fun p95TotalMs(): Long = percentile(0.95f) { it.totalMs }

    fun medianFrameIntervalMs(): Long = median { it.frameIntervalMs }

    fun medianMediaPipeMs(): Long = median { it.landmarkMs }

    fun totalDroppedFrames(): Int = samples.sumOf { it.framesDropped }

    private fun median(selector: (NailTryOnPerformanceMetrics) -> Long): Long =
        percentile(0.50f, selector)

    private fun percentile(
        percentile: Float,
        selector: (NailTryOnPerformanceMetrics) -> Long,
    ): Long {
        if (samples.isEmpty()) return 0L
        val sorted = samples.map(selector).sorted()
        val index = ((sorted.lastIndex) * percentile).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    companion object {
        const val DEFAULT_MAX_SAMPLES = 60
    }
}
