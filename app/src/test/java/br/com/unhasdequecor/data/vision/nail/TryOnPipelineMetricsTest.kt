package br.com.unhasdequecor.data.vision.nail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TryOnPipelineMetricsTest {

    @Test
    fun `snapshot calculates percentiles and processing fps from rolling samples`() {
        val metrics = TryOnPipelineMetrics()

        listOf(10f, 20f, 30f, 40f, 50f).forEach { totalMs ->
            metrics.record(sample(totalMs))
        }

        val snapshot = metrics.snapshot()

        assertEquals(5, snapshot.sampleCount)
        assertEquals(30f, snapshot.p50Ms, 0.001f)
        assertEquals(50f, snapshot.p90Ms, 0.001f)
        assertEquals(50f, snapshot.p95Ms, 0.001f)
        assertEquals(50f, snapshot.maxMs, 0.001f)
        assertEquals(33.333f, snapshot.effectiveFps, 0.01f)
        assertEquals(5, snapshot.liveFrames)
        assertEquals(0, snapshot.stillFrames)
    }

    @Test
    fun `snapshot counts prediction recovery and rejected frames`() {
        val metrics = TryOnPipelineMetrics()

        metrics.record(
            sample(
                totalMs = 60f,
                predictionApplied = true,
                predictionReason = NailPredictionReason.APPLIED,
            ),
        )
        metrics.record(
            sample(
                totalMs = 70f,
                predictionReason = NailPredictionReason.RECOVERY,
            ),
        )
        metrics.record(
            sample(
                totalMs = 80f,
                stabilized = false,
                failureReason = DetectionFailureReason.Generic,
            ),
        )

        val snapshot = metrics.snapshot()

        assertEquals(1, snapshot.predictionFrames)
        assertEquals(1, snapshot.recoveryFrames)
        assertEquals(1, snapshot.rejectedFrames)
        assertEquals(2, snapshot.liveFrames)
        assertEquals(1, snapshot.stillFrames)
    }

    @Test
    fun `collector keeps only the latest rolling window`() {
        val metrics = TryOnPipelineMetrics()

        repeat(TryOnPipelineMetrics.WINDOW_SIZE + 5) { index ->
            metrics.record(sample((index + 1).toFloat()))
        }

        val snapshot = metrics.snapshot()

        assertEquals(TryOnPipelineMetrics.WINDOW_SIZE, snapshot.sampleCount)
        assertEquals(65f, snapshot.maxMs, 0.001f)
        assertTrue(snapshot.p50Ms >= 35f)
    }

    @Test
    fun `reset clears rolling metrics`() {
        val metrics = TryOnPipelineMetrics()
        metrics.record(sample(80f))

        metrics.reset()

        assertEquals(TryOnPipelineMetricsSnapshot.EMPTY, metrics.snapshot())
    }

    private fun sample(
        totalMs: Float,
        mediaPipeMs: Float = totalMs * 0.7f,
        segmentationMs: Float = totalMs * 0.1f,
        trackingMs: Float = totalMs * 0.02f,
        stabilized: Boolean = true,
        predictionApplied: Boolean = false,
        predictionReason: NailPredictionReason = NailPredictionReason.STABLE,
        failureReason: DetectionFailureReason? = null,
    ) = TryOnPipelineMetricsSample(
        totalMs = totalMs,
        mediaPipeMs = mediaPipeMs,
        segmentationMs = segmentationMs,
        trackingMs = trackingMs,
        stabilized = stabilized,
        nailsDetected = 5,
        predictionApplied = predictionApplied,
        predictionReason = predictionReason,
        failureReason = failureReason,
    )
}
