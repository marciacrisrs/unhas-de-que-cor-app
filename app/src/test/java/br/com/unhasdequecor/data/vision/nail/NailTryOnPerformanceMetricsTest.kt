package br.com.unhasdequecor.data.vision.nail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NailTryOnPerformanceMetricsTest {

    @Test
    fun `fps is derived from frame interval and pipeline time`() {
        val metrics = NailTryOnPerformanceMetrics(
            totalMs = 80L,
            frameIntervalMs = 100L,
        )

        assertThat(metrics.pipelineFps).isWithin(0.001f).of(12.5f)
        assertThat(metrics.effectiveFps).isWithin(0.001f).of(10f)
    }

    @Test
    fun `window keeps only the configured number of samples`() {
        val window = NailTryOnPerformanceWindow(maxSamples = 2)
        window.add(NailTryOnPerformanceMetrics(totalMs = 60L, landmarkMs = 50L))
        window.add(NailTryOnPerformanceMetrics(totalMs = 80L, landmarkMs = 60L))
        window.add(NailTryOnPerformanceMetrics(totalMs = 100L, landmarkMs = 70L))

        assertThat(window.size()).isEqualTo(2)
        assertThat(window.medianTotalMs()).isEqualTo(80L)
        assertThat(window.medianMediaPipeMs()).isEqualTo(60L)
    }

    @Test
    fun `percentile is deterministic for a small window`() {
        val window = NailTryOnPerformanceWindow(maxSamples = 5)
        listOf(60L, 70L, 80L, 90L, 100L).forEach { total ->
            window.add(NailTryOnPerformanceMetrics(totalMs = total))
        }

        assertThat(window.p95TotalMs()).isEqualTo(100L)
    }

    @Test
    fun `empty window returns zero metrics`() {
        val window = NailTryOnPerformanceWindow()

        assertThat(window.medianTotalMs()).isEqualTo(0L)
        assertThat(window.p95TotalMs()).isEqualTo(0L)
        assertThat(window.medianFrameIntervalMs()).isEqualTo(0L)
        assertThat(window.totalDroppedFrames()).isEqualTo(0)
    }
}
