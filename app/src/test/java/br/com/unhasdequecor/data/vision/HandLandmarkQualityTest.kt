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
    fun tipSpan_incompletePoints_isZero() {
        assertThat(HandLandmarkQuality.tipSpanNorm(emptyList())).isEqualTo(0f)
        assertThat(HandLandmarkQuality.tipSpanNorm(List(10) { NormPoint(0.1f, 0.1f) }))
            .isEqualTo(0f)
    }

    @Test
    fun ranking_collapsedHighPresence_losesToOpenMidPresence() {
        val collapsed =
            HandLandmarks(
                points = List(21) { NormPoint(0.5f, 0.55f) },
                imageWidth = 800,
                imageHeight = 1200,
                presenceScore = 0.85f,
            )
        val open =
            HandLandmarks(
                points = openHandPoints(),
                imageWidth = 800,
                imageHeight = 1200,
                presenceScore = 0.58f,
            )
        assertThat(HandLandmarkQuality.tipSpanNorm(collapsed.points)).isLessThan(0.05f)
        assertThat(HandLandmarkQuality.tipSpanNorm(open.points))
            .isAtLeast(HandLandmarkQuality.GOOD_OPEN_TIP_SPAN)
        assertThat(HandLandmarkQuality.rankingScore(open))
            .isGreaterThan(HandLandmarkQuality.rankingScore(collapsed))
    }

    @Test
    fun shouldStopSearching_highPresenceLowSpan_keepsSearching() {
        assertThat(
            HandLandmarkQuality.shouldStopSearching(
                presenceScore = DetectionConfidenceFloor.HAND_PRESENCE_EARLY_STOP,
                tipSpan = 0f,
            ),
        ).isFalse()
    }

    @Test
    fun shouldStopSearching_highPresenceWithMinSpan_stops() {
        assertThat(
            HandLandmarkQuality.shouldStopSearching(
                presenceScore = DetectionConfidenceFloor.HAND_PRESENCE_EARLY_STOP,
                tipSpan = HandLandmarkQuality.MIN_TIP_SPAN_FOR_EARLY_STOP,
            ),
        ).isTrue()
    }

    @Test
    fun shouldStopSearching_strongPresenceWithGoodOpenSpan_stops() {
        assertThat(
            HandLandmarkQuality.shouldStopSearching(
                presenceScore = DetectionConfidenceFloor.HAND_PRESENCE_STRONG,
                tipSpan = HandLandmarkQuality.GOOD_OPEN_TIP_SPAN,
            ),
        ).isTrue()
    }

    @Test
    fun consider_prefersOpenAfterCollapsedHighPresence_andDoesNotStopEarly() {
        val collapsed =
            HandLandmarks(
                points = List(21) { NormPoint(0.5f, 0.55f) },
                imageWidth = 800,
                imageHeight = 1200,
                presenceScore = 0.90f,
            )
        val open =
            HandLandmarks(
                points = openHandPoints(),
                imageWidth = 800,
                imageHeight = 1200,
                presenceScore = 0.70f,
            )
        val (afterCollapsed, stopCollapsed) =
            HandLandmarkQuality.consider(currentBest = null, candidate = collapsed)
        assertThat(afterCollapsed).isSameInstanceAs(collapsed)
        assertThat(stopCollapsed).isFalse()

        val (afterOpen, stopOpen) =
            HandLandmarkQuality.consider(currentBest = afterCollapsed, candidate = open)
        assertThat(afterOpen).isSameInstanceAs(open)
        assertThat(stopOpen).isTrue()
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
