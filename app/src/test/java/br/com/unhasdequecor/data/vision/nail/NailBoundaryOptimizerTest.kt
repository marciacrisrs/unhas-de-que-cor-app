package br.com.unhasdequecor.data.vision.nail

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

class NailBoundaryOptimizerTest {
    private val optimizer = NailBoundaryOptimizer()

    @Test
    fun `reduces discrete variational energy while preserving hard endpoints`() {
        val initial = floatArrayOf(0f, 4f, -3f, 5f, -2f, 4f, 0f)
        val targets = floatArrayOf(0f, 1f, 2f, 3f, 2f, 1f, 0f)
        val weights = FloatArray(initial.size) { 1f }
        val bounds = FloatArray(initial.size) { -6f }
        val upper = FloatArray(initial.size) { 6f }
        val problem = NailBoundaryOptimizer.Problem(initial, targets, weights, bounds, upper)

        val before = optimizer.energy(initial, problem)
        val result = optimizer.solve(problem)!!

        assertThat(result.energy).isLessThan(before)
        assertThat(result.positions[0]).isEqualTo(initial[0])
        assertThat(result.positions.last()).isEqualTo(initial.last())
        assertThat(result.iterations).isGreaterThan(0)
    }

    @Test
    fun `curvature regularization suppresses oscillation without changing endpoints`() {
        val initial = floatArrayOf(0f, 3f, -3f, 3f, -3f, 3f, 0f)
        val targets = floatArrayOf(0f, 1f, 1.5f, 2f, 1.5f, 1f, 0f)
        val weights = FloatArray(initial.size) { 0.5f }
        val bounds = FloatArray(initial.size) { -5f }
        val upper = FloatArray(initial.size) { 5f }
        val problem = NailBoundaryOptimizer.Problem(initial, targets, weights, bounds, upper)

        val result = optimizer.solve(problem)!!
        val beforeCurvature = curvatureEnergy(initial)
        val afterCurvature = curvatureEnergy(result.positions)

        assertThat(afterCurvature).isLessThan(beforeCurvature)
        assertThat(abs(result.positions[0] - initial[0])).isEqualTo(0f)
        assertThat(abs(result.positions.last() - initial.last())).isEqualTo(0f)
    }

    @Test
    fun `hard anatomical bounds are never crossed`() {
        val initial = floatArrayOf(0f, 8f, -8f, 8f, 0f)
        val targets = floatArrayOf(0f, 8f, -8f, 8f, 0f)
        val weights = FloatArray(initial.size) { 1f }
        val lower = FloatArray(initial.size) { -2f }
        val upper = FloatArray(initial.size) { 2f }
        val problem = NailBoundaryOptimizer.Problem(initial, targets, weights, lower, upper)

        val result = optimizer.solve(problem)!!

        assertThat(result.positions.all { it in -2f..2f }).isTrue()
    }

    private fun curvatureEnergy(values: FloatArray): Float {
        var total = 0f
        for (i in 1 until values.lastIndex) {
            val curvature = values[i + 1] - 2f * values[i] + values[i - 1]
            total += curvature * curvature
        }
        return total
    }
}
