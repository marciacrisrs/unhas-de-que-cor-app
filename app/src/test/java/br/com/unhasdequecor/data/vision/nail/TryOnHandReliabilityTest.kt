package br.com.unhasdequecor.data.vision.nail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TryOnHandReliabilityTest {

    @Test
    fun classify_whenPresenceBelowAcceptFloor_isRejected() {
        assertThat(TryOnHandReliability.classify(0.20f, reliableNailCount = 5))
            .isEqualTo(TryOnReliability.REJECTED)
    }

    @Test
    fun classify_whenPresenceAcceptableButWeak_isWeak() {
        assertThat(TryOnHandReliability.classify(0.40f, reliableNailCount = 1))
            .isEqualTo(TryOnReliability.WEAK)
    }

    @Test
    fun classify_whenStrongPresence_isStrongEvenWithFewMasks() {
        assertThat(TryOnHandReliability.classify(0.80f, reliableNailCount = 1))
            .isEqualTo(TryOnReliability.STRONG)
    }

    @Test
    fun classify_whenModeratePresenceButEnoughMasks_isStrong() {
        assertThat(
            TryOnHandReliability.classify(
                presenceScore = 0.40f,
                reliableNailCount = TryOnHandReliability.MIN_MASKS_FOR_FULL,
            ),
        ).isEqualTo(TryOnReliability.STRONG)
    }

    @Test
    fun planRender_whenRejected_isNoneWithoutPaint() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.REJECTED,
                nailCount = 5,
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
                nailCount = 0,
                hasMappableAnchors = false,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.NONE)
    }

    @Test
    fun planRender_whenWeakWithAnchors_isApproximateNeverFull() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.WEAK,
                nailCount = 5,
                hasMappableAnchors = true,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.APPROXIMATE)
        assertThat(plan.useNailMasks).isTrue()
    }

    @Test
    fun planRender_whenStrongWithEnoughMasks_isFull() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.STRONG,
                nailCount = TryOnHandReliability.MIN_MASKS_FOR_FULL,
                hasMappableAnchors = true,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.FULL)
        assertThat(plan.useNailMasks).isTrue()
        assertThat(plan.useCanvasAnchors).isFalse()
    }

    @Test
    fun planRender_whenStrongWithoutMasks_ellipseIsApproximateNotFull() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.STRONG,
                nailCount = 0,
                hasMappableAnchors = true,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.APPROXIMATE)
        assertThat(plan.useEllipsePaint).isTrue()
        assertThat(plan.useCanvasAnchors).isTrue()
        assertThat(plan.useNailMasks).isFalse()
    }

    @Test
    fun planRender_whenStrongWithPartialMasks_isApproximate() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.STRONG,
                nailCount = 2,
                hasMappableAnchors = false,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.APPROXIMATE)
        assertThat(plan.useNailMasks).isTrue()
    }
}
