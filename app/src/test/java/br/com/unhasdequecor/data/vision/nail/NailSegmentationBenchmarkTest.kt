package br.com.unhasdequecor.data.vision.nail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

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

        assertThat(score.intersectionOverUnion).isEqualTo(1f)
        assertThat(score.precision).isEqualTo(1f)
        assertThat(score.recall).isEqualTo(1f)
        assertThat(score.boundaryErrorPx).isEqualTo(0f)
        assertThat(score.falsePositiveRatio).isEqualTo(0f)
        assertThat(score.passesAccuracyGate).isTrue()
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

        assertThat(score.intersectionOverUnion).isLessThan(0.85f)
        assertThat(score.boundaryErrorPx).isGreaterThan(0f)
        assertThat(score.passesAccuracyGate).isFalse()
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
