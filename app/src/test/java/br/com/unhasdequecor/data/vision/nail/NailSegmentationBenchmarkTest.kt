package br.com.unhasdequecor.data.vision.nail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NailSegmentationBenchmarkTest {

    @Test
    fun identicalMasksPassAccuracyGate() {
        val reference = mask(20, 20, 5..14, 6..13)

        val score = NailSegmentationBenchmark.score(
            prediction = reference,
            reference = reference,
            width = 20,
            height = 20,
        )

        assertEquals(1f, score.intersectionOverUnion)
        assertEquals(1f, score.precision)
        assertEquals(1f, score.recall)
        assertEquals(0f, score.boundaryErrorPx)
        assertEquals(0f, score.falsePositiveRatio)
        assertTrue(score.passesAccuracyGate)
    }

    @Test
    fun displacedMaskFailsBoundaryAndIouGate() {
        val reference = mask(20, 20, 5..14, 6..13)
        val prediction = mask(20, 20, 7..16, 6..13)

        val score = NailSegmentationBenchmark.score(
            prediction = prediction,
            reference = reference,
            width = 20,
            height = 20,
        )

        assertTrue(score.intersectionOverUnion < 0.85f)
        assertTrue(score.boundaryErrorPx > 0f)
        assertFalse(score.passesAccuracyGate)
    }

    private fun mask(
        width: Int,
        height: Int,
        xRange: IntRange,
        yRange: IntRange,
    ): ByteArray {
        val output = ByteArray(width * height)
        for (y in yRange) {
            for (x in xRange) {
                output[y * width + x] = 255.toByte()
            }
        }
        return output
    }
}
