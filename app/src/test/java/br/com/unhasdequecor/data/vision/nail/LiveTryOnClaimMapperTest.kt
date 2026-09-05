package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.NormPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveTryOnClaimMapperTest {

    @Test
    fun decide_whenRejected_hidesOverlayAndDoesNotClaimHand() {
        val decision = LiveTryOnClaimMapper.decide(
            reliability = TryOnReliability.REJECTED,
            paintableNailCount = 5,
            fullQualityNailCount = 5,
            hasMappableAnchors = true,
            failureReason = DetectionFailureReason.Generic,
        )

        assertThat(decision.showOverlay).isFalse()
        assertThat(decision.claim).isEqualTo(TryOnPreviewClaim.NOT_DETECTED)
        assertThat(TryOnPreviewLabels.contentDescription("Vermelho", decision.claim))
            .doesNotContain("sua mão")
    }

    @Test
    fun decide_whenStrongWithFullQuality_claimsUserHand() {
        val decision = LiveTryOnClaimMapper.decide(
            reliability = TryOnReliability.STRONG,
            paintableNailCount = 5,
            fullQualityNailCount = DetectionConfidenceFloor.MIN_MASKS_FOR_FULL,
            hasMappableAnchors = true,
            failureReason = null,
        )

        assertThat(decision.showOverlay).isTrue()
        assertThat(decision.claim).isEqualTo(TryOnPreviewClaim.FULL_USER)
        assertThat(decision.reason).isNull()
    }

    @Test
    fun decide_whenStrongWithoutMasks_hidesOverlayAndDoesNotClaimHand() {
        val decision = LiveTryOnClaimMapper.decide(
            reliability = TryOnReliability.STRONG,
            paintableNailCount = 0,
            fullQualityNailCount = 0,
            hasMappableAnchors = true,
            failureReason = DetectionFailureReason.Generic,
        )

        assertThat(decision.showOverlay).isFalse()
        assertThat(decision.claim).isEqualTo(TryOnPreviewClaim.NOT_DETECTED)
        assertThat(TryOnPreviewLabels.contentDescription("Nude", decision.claim))
            .doesNotContain("sua mão")
    }

    @Test
    fun decide_whenStrongButOnlyPaintable_isApproximate() {
        val decision = LiveTryOnClaimMapper.decide(
            reliability = TryOnReliability.STRONG,
            paintableNailCount = 5,
            fullQualityNailCount = 2,
            hasMappableAnchors = true,
            failureReason = DetectionFailureReason.Generic,
        )

        assertThat(decision.claim).isEqualTo(TryOnPreviewClaim.APPROXIMATE)
        assertThat(decision.showOverlay).isTrue()
    }

    @Test
    fun decide_whenWeakWithMasks_isApproximate() {
        val decision = LiveTryOnClaimMapper.decide(
            reliability = TryOnReliability.WEAK,
            paintableNailCount = 3,
            fullQualityNailCount = 0,
            hasMappableAnchors = false,
            failureReason = DetectionFailureReason.Generic,
        )

        assertThat(decision.claim).isEqualTo(TryOnPreviewClaim.APPROXIMATE)
        assertThat(decision.showOverlay).isTrue()
    }

    @Test
    fun decide_whenWeakWithoutMasksOrAnchors_hidesOverlay() {
        val decision = LiveTryOnClaimMapper.decide(
            reliability = TryOnReliability.WEAK,
            paintableNailCount = 0,
            fullQualityNailCount = 0,
            hasMappableAnchors = false,
            failureReason = DetectionFailureReason.Generic,
        )

        assertThat(decision.showOverlay).isFalse()
        assertThat(decision.claim).isEqualTo(TryOnPreviewClaim.NOT_DETECTED)
    }

    @Test
    fun decide_whenReliabilityMissing_hidesOverlay() {
        val decision = LiveTryOnClaimMapper.decide(
            reliability = null,
            paintableNailCount = 5,
            fullQualityNailCount = 5,
            hasMappableAnchors = true,
            failureReason = DetectionFailureReason.Generic,
        )

        assertThat(decision.showOverlay).isFalse()
        assertThat(decision.claim).isEqualTo(TryOnPreviewClaim.NOT_DETECTED)
    }

    @Test
    fun hasMappableAnchors_whenLandmarksMissingOrCollapsed_isFalse() {
        assertThat(LiveTryOnClaimMapper.hasMappableAnchors(null)).isFalse()
        val collapsed = HandLandmarks(
            points = List(21) { NormPoint(0.5f, 0.5f) },
            imageWidth = 800,
            imageHeight = 1200,
        )
        assertThat(LiveTryOnClaimMapper.hasMappableAnchors(collapsed)).isFalse()
    }
}
