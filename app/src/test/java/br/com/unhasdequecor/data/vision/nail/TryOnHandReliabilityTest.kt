package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
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
        assertThat(
            TryOnHandReliability.classify(DetectionConfidenceFloor.HAND_PRESENCE_ACCEPT),
        ).isEqualTo(TryOnReliability.WEAK)
    }

    @Test
    fun classify_whenPresenceAcceptableButWeak_isWeak() {
        assertThat(TryOnHandReliability.classify(0.40f))
            .isEqualTo(TryOnReliability.WEAK)
    }

    @Test
    fun classify_atStrongBoundary_isStrong() {
        assertThat(
            TryOnHandReliability.classify(DetectionConfidenceFloor.HAND_PRESENCE_STRONG),
        ).isEqualTo(TryOnReliability.STRONG)
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
                fullQualityNailCount = 5,
                hasMappableAnchors = true,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.NONE)
        assertThat(plan.useNailMasks).isFalse()
    }

    @Test
    fun planRender_whenStrongWithEnoughFullQuality_isFull() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.STRONG,
                paintableNailCount = 5,
                fullQualityNailCount = DetectionConfidenceFloor.MIN_MASKS_FOR_FULL,
                hasMappableAnchors = true,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.FULL)
    }

    @Test
    fun planRender_whenStrongButOnlyWeakMasks_isApproximateNotFull() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.STRONG,
                paintableNailCount = 5,
                fullQualityNailCount = 1,
                hasMappableAnchors = true,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.APPROXIMATE)
        assertThat(plan.useNailMasks).isTrue()
    }

    @Test
    fun planRender_nails_strongWithThreePaintableBelowFullFloor_isApproximate() {
        val nails = List(3) { nail(DetectionConfidenceFloor.NAIL_COMBINED_MIN + 0.01f) }
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.STRONG,
                nails = nails,
                hasMappableAnchors = true,
            )
        assertThat(DetectionConfidenceFloor.meetsFullNailFloor(nails)).isFalse()
        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.APPROXIMATE)
    }

    @Test
    fun planRender_nails_strongWithThreeFullQuality_isFull() {
        val nails = List(3) { nail(DetectionConfidenceFloor.NAIL_FULL_MIN) }
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.STRONG,
                nails = nails,
                hasMappableAnchors = true,
            )
        assertThat(DetectionConfidenceFloor.meetsFullNailFloor(nails)).isTrue()
        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.FULL)
    }

    @Test
    fun planRender_nails_mixedPaintableAndFull_needsThreeFull() {
        val nails = listOf(
            nail(0.33f),
            nail(0.46f),
            nail(0.50f),
        )
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.STRONG,
                nails = nails,
                hasMappableAnchors = false,
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
                fullQualityNailCount = 0,
                hasMappableAnchors = true,
            )

        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.APPROXIMATE)
        assertThat(plan.useEllipsePaint).isTrue()
        assertThat(plan.useCanvasAnchors).isTrue()
    }

    @Test
    fun planRender_whenWeakWithoutMasksOrAnchors_isNone() {
        val plan =
            TryOnHandReliability.planRender(
                reliability = TryOnReliability.WEAK,
                paintableNailCount = 0,
                fullQualityNailCount = 0,
                hasMappableAnchors = false,
            )
        assertThat(plan.mode).isEqualTo(UserTryOnRenderMode.NONE)
    }

    private fun nail(confidence: Float): DetectedNail {
        val roi = NailRoi(
            finger = Finger.INDEX,
            bounds = PixelRect(0, 0, 10, 10),
            polygon = listOf(
                PixelPoint(0f, 0f),
                PixelPoint(10f, 0f),
                PixelPoint(10f, 10f),
                PixelPoint(0f, 10f),
            ),
            axisFromDip = PixelPoint(5f, 10f),
            axisToTip = PixelPoint(5f, 0f),
            lengthPx = 10f,
            widthPx = 6f,
            rotationDegrees = 0f,
            geometricConfidence = 0.9f,
        )
        val mask = NailMask(
            width = 4,
            height = 4,
            alpha = ByteArray(16) { 255.toByte() },
            originX = 0,
            originY = 0,
        )
        return DetectedNail(Finger.INDEX, roi, mask, confidence)
    }
}
