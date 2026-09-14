package br.com.unhasdequecor.data.vision.nail

import kotlin.math.abs
import kotlin.math.max

/**
 * Deterministic discrete variational solver for one nail boundary profile.
 *
 * The unknown is the transverse boundary coordinate s_i at regularly sampled
 * longitudinal positions. The objective is a discrete analogue of
 * data fidelity + first derivative regularization + curvature regularization.
 * Endpoints are hard boundary conditions and bounds are hard anatomical walls.
 *
 * This is a research-stage solver: it is intentionally not wired into the
 * production pipeline until benchmark fixtures prove that it beats baseline.
 */
class NailBoundaryOptimizer(
    private val config: Config = Config(),
) {
    fun solve(problem: Problem): Result? {
        if (!problem.isValid()) return null

        val values = problem.initial.copyOf()
        clampInPlace(values, problem.lowerBounds, problem.upperBounds)
        val free = if (values.size > 2) values.copyOfRange(1, values.lastIndex) else FloatArray(0)
        var previousEnergy = energy(values, problem)
        var iterations = 0

        while (iterations < config.maxIterations) {
            val gradient = gradient(values, problem)
            var maxStep = 0f
            for (i in 1 until values.lastIndex) {
                val step = (config.learningRate * gradient[i]).coerceIn(-config.maxStep, config.maxStep)
                values[i] = (values[i] - step).coerceIn(problem.lowerBounds[i], problem.upperBounds[i])
                maxStep = max(maxStep, abs(step))
            }

            val currentEnergy = energy(values, problem)
            if (currentEnergy > previousEnergy) {
                config.learningRate *= config.backtrackFactor
                if (config.learningRate < config.minLearningRate) {
                    return Result(values, previousEnergy, iterations, converged = false)
                }
            } else {
                previousEnergy = currentEnergy
            }

            iterations++
            if (maxStep <= config.positionTolerance || abs(previousEnergy - currentEnergy) <= config.energyTolerance) {
                return Result(values, currentEnergy, iterations, converged = true)
            }
        }

        return Result(values, previousEnergy, iterations, converged = false)
    }

    fun energy(values: FloatArray, problem: Problem): Float {
        require(values.size == problem.size)
        return dataEnergy(values, problem) +
            firstDerivativeEnergy(values) * config.firstDerivativeWeight +
            curvatureEnergy(values) * config.curvatureWeight
    }

    private fun gradient(values: FloatArray, problem: Problem): FloatArray {
        val result = FloatArray(values.size)
        for (i in 1 until values.lastIndex) {
            result[i] += 2f * problem.dataWeights[i] * (values[i] - problem.dataTargets[i])
            result[i] += firstGradient(values, i) * config.firstDerivativeWeight
        }
        for (i in 1 until values.lastIndex) {
            val curvature = values[i + 1] - 2f * values[i] + values[i - 1]
            result[i - 1] += 2f * curvature * config.curvatureWeight
            result[i] -= 4f * curvature * config.curvatureWeight
            result[i + 1] += 2f * curvature * config.curvatureWeight
        }
        return result
    }

    private fun dataEnergy(values: FloatArray, problem: Problem): Float {
        var total = 0f
        for (i in values.indices) {
            val error = values[i] - problem.dataTargets[i]
            total += problem.dataWeights[i] * error * error
        }
        return total
    }

    private fun firstDerivativeEnergy(values: FloatArray): Float {
        var total = 0f
        for (i in 1 until values.size) {
            val delta = values[i] - values[i - 1]
            total += delta * delta
        }
        return total
    }

    private fun curvatureEnergy(values: FloatArray): Float {
        var total = 0f
        for (i in 1 until values.lastIndex) {
            val curvature = values[i + 1] - 2f * values[i] + values[i - 1]
            total += curvature * curvature
        }
        return total
    }

    private fun firstGradient(values: FloatArray, index: Int): Float =
        2f * (2f * values[index] - values[index - 1] - values[index + 1])

    private fun clampInPlace(values: FloatArray, lower: FloatArray, upper: FloatArray) {
        for (i in values.indices) values[i] = values[i].coerceIn(lower[i], upper[i])
    }

    data class Problem(
        val initial: FloatArray,
        val dataTargets: FloatArray,
        val dataWeights: FloatArray,
        val lowerBounds: FloatArray,
        val upperBounds: FloatArray,
    ) {
        val size: Int get() = initial.size

        fun isValid(): Boolean =
            size >= 3 &&
                dataTargets.size == size &&
                dataWeights.size == size &&
                lowerBounds.size == size &&
                upperBounds.size == size &&
                dataWeights.all { it >= 0f } &&
                lowerBounds.indices.all { lowerBounds[it] <= upperBounds[it] }
    }

    data class Result(
        val positions: FloatArray,
        val energy: Float,
        val iterations: Int,
        val converged: Boolean,
    )

    data class Config(
        val firstDerivativeWeight: Float = 0.20f,
        val curvatureWeight: Float = 0.80f,
        var learningRate: Float = 0.08f,
        val maxStep: Float = 0.50f,
        val maxIterations: Int = 250,
        val positionTolerance: Float = 0.001f,
        val energyTolerance: Float = 0.00001f,
        val minLearningRate: Float = 0.0001f,
        val backtrackFactor: Float = 0.5f,
    )
}
