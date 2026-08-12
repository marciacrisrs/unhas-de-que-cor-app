package br.com.unhasdequecor.data.vision

import br.com.unhasdequecor.data.vision.nail.DetectionConfidenceFloor
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.NormPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HandLandmarkQualityTest {

    @Test
    fun tipSpan_openHand_greaterThanCollapsed() {
        val open = openHandPoints()
        val collapsed = List(21) { NormPoint(0.5f, 0.55f) }
        assertThat(HandLandmarkQuality.tipSpanNorm(open))
            .isGreaterThan(HandLandmarkQuality.tipSpanNorm(collapsed))
    }

    @Test
    fun ranking_prefersOpenHandNearStrongBoundary() {
        val collapsed =
            HandLandmarks(
                points = List(21) { NormPoint(0.5f, 0.55f) },
                imageWidth = 800,
                imageHeight = 1200,
                presenceScore = 0.60f,
            )
        val open =
            HandLandmarks(
                points = openHandPoints(),
                imageWidth = 800,
                imageHeight = 1200,
                presenceScore = 0.58f,
            )
        assertThat(HandLandmarkQuality.rankingScore(open))
            .isGreaterThan(HandLandmarkQuality.rankingScore(collapsed))
    }

    @Test
    fun shouldStopSearching_onlyAboveEarlyStop() {
        assertThat(
            HandLandmarkQuality.shouldStopSearching(
                DetectionConfidenceFloor.HAND_PRESENCE_STRONG,
            ),
        ).isFalse()
        assertThat(
            HandLandmarkQuality.shouldStopSearching(
                DetectionConfidenceFloor.HAND_PRESENCE_EARLY_STOP,
            ),
        ).isTrue()
    }

    private fun openHandPoints(): List<NormPoint> {
        val pts = MutableList(21) { NormPoint(0.5f, 0.5f) }
        pts[4] = NormPoint(0.22f, 0.40f)
        pts[8] = NormPoint(0.38f, 0.26f)
        pts[12] = NormPoint(0.50f, 0.22f)
        pts[16] = NormPoint(0.62f, 0.26f)
        pts[20] = NormPoint(0.74f, 0.32f)
        return pts
    }
}
