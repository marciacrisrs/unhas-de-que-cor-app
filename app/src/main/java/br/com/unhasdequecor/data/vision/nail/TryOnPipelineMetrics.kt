package br.com.unhasdequecor.data.vision.nail

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * Rolling, in-memory metrics for the try-on pipeline.
 *
 * The collector deliberately measures processing time, not camera arrival time.
 * Arrival gaps/drop rates require instrumentation at the camera/frame producer
 * boundary and must not be inferred from processing duration.
 */
@Singleton
class TryOnPipelineMetrics @Inject constructor() {
    private val lock = Any()
    private val samples = ArrayDeque<TryOnPipelineMetricsSample>(WINDOW_SIZE)

    @Volatile
    private var latestSnapshot = TryOnPipelineMetricsSnapshot.EMPTY

    fun record(sample: TryOnPipelineMetricsSample) {
        synchronized(lock) {
            if (samples.size == WINDOW_SIZE) samples.removeFirst()
            samples.addLast(sample)
            latestSnapshot = buildSnapshot(samples)
            latestDebugSnapshot = latestSnapshot
        }
    }

    fun snapshot(): TryOnPipelineMetricsSnapshot = latestSnapshot

    fun reset() {
        synchronized(lock) {
            samples.clear()
            latestSnapshot = TryOnPipelineMetricsSnapshot.EMPTY
            latestDebugSnapshot = TryOnPipelineMetricsSnapshot.EMPTY
        }
    }

    private fun buildSnapshot(values: Collection<TryOnPipelineMetricsSample>): TryOnPipelineMetricsSnapshot {
        if (values.isEmpty()) return TryOnPipelineMetricsSnapshot.EMPTY

        val total = values.map { it.totalMs }
        val sorted = total.sorted()
        val totalSeconds = total.sum() / MILLIS_PER_SECOND
        val effectiveFps = if (totalSeconds > 0f) values.size / totalSeconds else 0f
        val predictionFrames = values.count { it.predictionApplied }
        val recoveryFrames = values.count { it.predictionReason == NailPredictionReason.RECOVERY }
        val rejectedFrames = values.count { it.failureReason != null }

        return TryOnPipelineMetricsSnapshot(
            sampleCount = values.size,
            effectiveFps = effectiveFps,
            p50Ms = percentile(sorted, 0.50f),
            p90Ms = percentile(sorted, 0.90f),
            p95Ms = percentile(sorted, 0.95f),
            maxMs = sorted.last(),
            mediaPipeMs = values.map { it.mediaPipeMs }.average().toFloat(),
            segmentationMs = values.map { it.segmentationMs }.average().toFloat(),
            trackingMs = values.map { it.trackingMs }.average().toFloat(),
            predictionFrames = predictionFrames,
            recoveryFrames = recoveryFrames,
            rejectedFrames = rejectedFrames,
            liveFrames = values.count { it.stabilized },
            stillFrames = values.count { !it.stabilized },
        )
    }

    private fun percentile(sorted: List<Float>, percentile: Float): Float {
        if (sorted.isEmpty()) return 0f
        val index = ceil((sorted.size - 1) * percentile).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    companion object {
        const val WINDOW_SIZE = 60
        private const val MILLIS_PER_SECOND = 1000f

        @Volatile
        var latestDebugSnapshot: TryOnPipelineMetricsSnapshot = TryOnPipelineMetricsSnapshot.EMPTY
            private set
    }
}

data class TryOnPipelineMetricsSample(
    val totalMs: Float,
    val mediaPipeMs: Float,
    val segmentationMs: Float,
    val trackingMs: Float,
    val stabilized: Boolean,
    val nailsDetected: Int,
    val predictionApplied: Boolean,
    val predictionReason: NailPredictionReason,
    val failureReason: DetectionFailureReason?,
)

data class TryOnPipelineMetricsSnapshot(
    val sampleCount: Int,
    val effectiveFps: Float,
    val p50Ms: Float,
    val p90Ms: Float,
    val p95Ms: Float,
    val maxMs: Float,
    val mediaPipeMs: Float,
    val segmentationMs: Float,
    val trackingMs: Float,
    val predictionFrames: Int,
    val recoveryFrames: Int,
    val rejectedFrames: Int,
    val liveFrames: Int,
    val stillFrames: Int,
) {
    companion object {
        val EMPTY = TryOnPipelineMetricsSnapshot(
            sampleCount = 0,
            effectiveFps = 0f,
            p50Ms = 0f,
            p90Ms = 0f,
            p95Ms = 0f,
            maxMs = 0f,
            mediaPipeMs = 0f,
            segmentationMs = 0f,
            trackingMs = 0f,
            predictionFrames = 0,
            recoveryFrames = 0,
            rejectedFrames = 0,
            liveFrames = 0,
            stillFrames = 0,
        )
    }
}
