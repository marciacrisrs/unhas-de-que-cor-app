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
        // Fotos reais com tip presence ~0.20 devem pintar (APPROXIMATE), não sumir.
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
    fun planRender_whenWeakWithMasks_isApproximateNeverFull() {
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
    fun planRender_whenWeakWithoutMasksOrAnchors_isNone() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.WEAK,
                nailCount = 0,
                hasMappableAnchors = false,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.NONE)
        assertThat(plan.useCanvasAnchors).isFalse()
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
    fun planRender_whenStrongWithoutMasksOrAnchors_isNone() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.STRONG,
                nailCount = 0,
                hasMappableAnchors = false,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.NONE)
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

    @Test
    fun planRender_neverUsesDefaultAnchorsFlagWhenNone() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = null,
                nailCount = 0,
                hasMappableAnchors = false,
            )
        // Contrato: sem detecção → zero paint / zero Canvas (UI não deve usar DEFAULT).
        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.NONE)
        assertThat(plan.useEllipsePaint).isFalse()
        assertThat(plan.useCanvasAnchors).isFalse()
        assertThat(plan.useNailMasks).isFalse()
    }
}
