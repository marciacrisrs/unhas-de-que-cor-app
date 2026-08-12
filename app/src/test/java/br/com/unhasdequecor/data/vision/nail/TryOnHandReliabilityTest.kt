package br.com.unhasdequecor.data.vision.nail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TryOnHandReliabilityTest {

    @Test
    fun classify_whenPresenceBelowAcceptFloor_isRejected() {
        assertThat(TryOnHandReliability.classify(0.10f))
            .isEqualTo(TryOnReliability.REJECTED)
    }

    @Test
    fun classify_whenPresenceWasPreviouslyRejectedFloor_isNowWeak() {
        assertThat(TryOnHandReliability.classify(0.20f))
            .isEqualTo(TryOnReliability.WEAK)
    }

    @Test
    fun classify_atAcceptBoundary_isWeak() {
        assertThat(TryOnHandReliability.classify(TryOnHandReliability.MIN_PRESENCE_ACCEPT))
            .isEqualTo(TryOnReliability.WEAK)
    }

    @Test
    fun classify_whenPresenceAcceptableButWeak_isWeak() {
        assertThat(TryOnHandReliability.classify(0.40f))
            .isEqualTo(TryOnReliability.WEAK)
    }

    @Test
    fun classify_atStrongBoundary_isStrong() {
        assertThat(TryOnHandReliability.classify(TryOnHandReliability.MIN_PRESENCE_STRONG))
            .isEqualTo(TryOnReliability.STRONG)
    }

    @Test
    fun classify_whenStrongPresence_isStrong() {
        assertThat(TryOnHandReliability.classify(0.80f))
            .isEqualTo(TryOnReliability.STRONG)
    }

    @Test
    fun planRender_whenRejected_isNoneWithoutPaint() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.REJECTED,
                paintableNailCount = 5,
                hasMappableAnchors = true,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.NONE)
        assertThat(plan.useNailMasks).isFalse()
        assertThat(plan.useEllipsePaint).isFalse()
        assertThat(plan.useCanvasAnchors).isFalse()
    }

    @Test
    fun planRender_whenNullReliability_isNone() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = null,
                paintableNailCount = 0,
                hasMappableAnchors = false,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.NONE)
    }

    @Test
    fun planRender_whenWeakWithMasks_isApproximateNeverFull() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.WEAK,
                paintableNailCount = 5,
                hasMappableAnchors = true,
                fullQualityNailCount = 5,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.APPROXIMATE)
        assertThat(plan.useNailMasks).isTrue()
    }

    @Test
    fun planRender_whenWeakWithoutMasksOrAnchors_isNone() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.WEAK,
                paintableNailCount = 0,
                hasMappableAnchors = false,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.NONE)
        assertThat(plan.useCanvasAnchors).isFalse()
    }

    @Test
    fun planRender_whenStrongWithEnoughFullQualityMasks_isFull() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.STRONG,
                paintableNailCount = TryOnHandReliability.MIN_MASKS_FOR_FULL,
                hasMappableAnchors = true,
                fullQualityNailCount = TryOnHandReliability.MIN_MASKS_FOR_FULL,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.FULL)
        assertThat(plan.useNailMasks).isTrue()
        assertThat(plan.useCanvasAnchors).isFalse()
    }

    @Test
    fun planRender_whenStrongButOnlyWeakMasks_isApproximateNotFull() {
        // 5 paintable below FULL floor → não claim “Prévia na sua mão”.
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.STRONG,
                paintableNailCount = 5,
                hasMappableAnchors = true,
                fullQualityNailCount = 1,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.APPROXIMATE)
        assertThat(plan.useNailMasks).isTrue()
    }

    @Test
    fun planRender_whenStrongWithoutMasks_ellipseIsApproximateNotFull() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.STRONG,
                paintableNailCount = 0,
                hasMappableAnchors = true,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.APPROXIMATE)
        assertThat(plan.useEllipsePaint).isTrue()
        assertThat(plan.useCanvasAnchors).isTrue()
        assertThat(plan.useNailMasks).isFalse()
    }

    @Test
    fun planRender_whenStrongWithoutMasksOrAnchors_isNone() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.STRONG,
                paintableNailCount = 0,
                hasMappableAnchors = false,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.NONE)
    }

    @Test
    fun planRender_whenStrongWithPartialMasks_isApproximate() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.STRONG,
                paintableNailCount = 2,
                hasMappableAnchors = false,
                fullQualityNailCount = 2,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.APPROXIMATE)
        assertThat(plan.useNailMasks).isTrue()
    }

    @Test
    fun planRender_neverUsesDefaultAnchorsFlagWhenNone() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = null,
                paintableNailCount = 0,
                hasMappableAnchors = false,
            )
        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.NONE)
        assertThat(plan.useEllipsePaint).isFalse()
        assertThat(plan.useCanvasAnchors).isFalse()
        assertThat(plan.useNailMasks).isFalse()
    }

    @Test
    fun presenceAliases_matchDetectionConfidenceFloor() {
        assertThat(TryOnHandReliability.MIN_PRESENCE_ACCEPT)
            .isEqualTo(DetectionConfidenceFloor.HAND_PRESENCE_ACCEPT)
        assertThat(TryOnHandReliability.MIN_PRESENCE_STRONG)
            .isEqualTo(DetectionConfidenceFloor.HAND_PRESENCE_STRONG)
        assertThat(NailColorApplier.MIN_CONFIDENCE)
            .isEqualTo(DetectionConfidenceFloor.NAIL_COMBINED_MIN)
    }
}
