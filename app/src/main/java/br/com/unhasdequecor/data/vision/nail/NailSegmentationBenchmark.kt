package br.com.unhasdequecor.data.vision.nail

import kotlin.math.sqrt

/** Objective metrics used to decide whether a nail segmentation candidate is good enough. */
data class NailSegmentationScore(
    val intersectionOverUnion: Float,
    val precision: Float,
    val recall: Float,
    val boundaryErrorPx: Float,
    val falsePositiveRatio: Float,
) {
    val passesAccuracyGate: Boolean
        get() = intersectionOverUnion >= MIN_IOU &&
            boundaryErrorPx <= MAX_BOUNDARY_ERROR_PX &&
            falsePositiveRatio <= MAX_FALSE_POSITIVE_RATIO

    companion object {
        const val MIN_IOU = 0.85f
        const val MAX_BOUNDARY_ERROR_PX = 3f
        const val MAX_FALSE_POSITIVE_RATIO = 0.02f
    }
}

object NailSegmentationBenchmark {
    /**
     * [prediction] and [reference] are row-major binary masks. Non-zero is
     * foreground. The two arrays must have the same dimensions.
     */
    fun score(
        prediction: ByteArray,
        reference: ByteArray,
        width: Int,
        height: Int,
    ): NailSegmentationScore {
        require(width > 0 && height > 0)
        require(prediction.size == width * height)
        require(reference.size == width * height)

        var intersection = 0
        var predicted = 0
        var actual = 0
        var falsePositive = 0
        for (i in prediction.indices) {
            val p = (prediction[i].toInt() and 0xFF) > 127
            val r = (reference[i].toInt() and 0xFF) > 127
            if (p) predicted++
            if (r) actual++
            if (p && r) intersection++
            if (p && !r) falsePositive++
        }

        val union = predicted + actual - intersection
        val iou = if (union == 0) 1f else intersection.toFloat() / union
        val precision = if (predicted == 0) 0f else intersection.toFloat() / predicted
        val recall = if (actual == 0) 0f else intersection.toFloat() / actual
        val falsePositiveRatio = if (predicted == 0) 0f else falsePositive.toFloat() / predicted
        val boundaryError = symmetricBoundaryError(prediction, reference, width, height)

        return NailSegmentationScore(
            intersectionOverUnion = iou,
            precision = precision,
            recall = recall,
            boundaryErrorPx = boundaryError,
            falsePositiveRatio = falsePositiveRatio,
        )
    }

    private fun symmetricBoundaryError(
        prediction: ByteArray,
        reference: ByteArray,
        width: Int,
        height: Int,
    ): Float {
        val predictedBoundary = boundaryPixels(prediction, width, height)
        val referenceBoundary = boundaryPixels(reference, width, height)
        if (predictedBoundary.isEmpty() && referenceBoundary.isEmpty()) return 0f
        if (predictedBoundary.isEmpty() || referenceBoundary.isEmpty()) return Float.POSITIVE_INFINITY

        val forward = meanNearestDistance(predictedBoundary, referenceBoundary)
        val backward = meanNearestDistance(referenceBoundary, predictedBoundary)
        return (forward + backward) * 0.5f
    }

    private fun boundaryPixels(mask: ByteArray, width: Int, height: Int): List<Point> {
        val points = ArrayList<Point>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if ((mask[index].toInt() and 0xFF) <= 127) continue
                var boundary = false
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx !in 0 until width || ny !in 0 until height) {
                            boundary = true
                        } else if ((mask[ny * width + nx].toInt() and 0xFF) <= 127) {
                            boundary = true
                        }
                    }
                }
                if (boundary) points += Point(x.toFloat(), y.toFloat())
            }
        }
        return points
    }

    private fun meanNearestDistance(from: List<Point>, to: List<Point>): Float {
        var sum = 0f
        for (point in from) {
            var best = Float.POSITIVE_INFINITY
            for (candidate in to) {
                val dx = point.x - candidate.x
                val dy = point.y - candidate.y
                val distance = sqrt(dx * dx + dy * dy)
                if (distance < best) best = distance
                if (best <= 0f) break
            }
            sum += best
        }
        return sum / from.size
    }

    private data class Point(val x: Float, val y: Float)
}
