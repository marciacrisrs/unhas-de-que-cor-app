package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.HandPresenceScoring
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.NormPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DetectionFailureDiagnosticsTest {

    @Test
    fun fromImageStats_glareWinsOverDark() {
        val reason = DetectionFailureDiagnostics.fromImageStats(
            meanLuminance = 40f,
            highlightShare = 0.20f,
        )
        assertThat(reason).isEqualTo(DetectionFailureReason.ExcessiveGlare)
    }

    @Test
    fun fromImageStats_darkWhenNoGlare() {
        val reason = DetectionFailureDiagnostics.fromImageStats(
            meanLuminance = 30f,
            highlightShare = 0.01f,
        )
        assertThat(reason).isEqualTo(DetectionFailureReason.TooDark)
    }

    @Test
    fun fromImageStats_midLumaRetintaIsNotTooDark() {
        // Pele retinta bem iluminada costuma cair ~45–90 — não deve ser TooDark.
        val reason = DetectionFailureDiagnostics.fromImageStats(
            meanLuminance = 48f,
            highlightShare = 0.02f,
        )
        assertThat(reason).isEqualTo(DetectionFailureReason.Generic)
    }

    @Test
    fun fromImageStats_genericOtherwise() {
        val reason = DetectionFailureDiagnostics.fromImageStats(
            meanLuminance = 120f,
            highlightShare = 0.02f,
        )
        assertThat(reason).isEqualTo(DetectionFailureReason.Generic)
    }

    @Test
    fun fromLandmarks_handTooFar_smallExtent() {
        val landmarks = clusteredHand(cx = 0.5f, cy = 0.5f, spread = 0.04f, presence = 0.50f)
        val reason = DetectionFailureDiagnostics.fromLandmarks(
            landmarks = landmarks,
            reliability = TryOnReliability.WEAK,
        )
        assertThat(reason).isEqualTo(DetectionFailureReason.HandTooFar)
        assertThat(DetectionFailureDiagnostics.handExtentNorm(landmarks))
            .isLessThan(DetectionFailureDiagnostics.HAND_EXTENT_TOO_SMALL)
    }

    @Test
    fun fromLandmarks_badAngle_collapsedTips() {
        // Extent ok, tips colados (punho).
        val points = MutableList(21) { NormPoint(0.40f + it * 0.01f, 0.50f) }
        // Tips (4,8,12,16,20) quase no mesmo lugar.
        for (idx in HandLandmarks.TIP_INDICES) {
            points[idx] = NormPoint(0.55f, 0.45f)
        }
        val landmarks = HandLandmarks(
            points = points,
            imageWidth = 200,
            imageHeight = 300,
            presenceScore = 0.40f,
        )
        val reason = DetectionFailureDiagnostics.fromLandmarks(
            landmarks = landmarks,
            reliability = TryOnReliability.WEAK,
            paintableNailCount = 0,
            hasMappableAnchors = false,
        )
        assertThat(reason).isEqualTo(DetectionFailureReason.BadAngle)
    }

    @Test
    fun fromLandmarks_noNailWhenOpenButEmptyMasks() {
        val landmarks = openHand(presence = 0.60f)
        val reason = DetectionFailureDiagnostics.fromLandmarks(
            landmarks = landmarks,
            reliability = TryOnReliability.STRONG,
            barrier = RejectionBarrier.NAIL_COMBINED,
            paintableNailCount = 0,
            hasMappableAnchors = true,
        )
        assertThat(reason).isEqualTo(DetectionFailureReason.NoNailVisible)
    }

    @Test
    fun fromLandmarks_tipGlareMapsToExcessiveGlare() {
        val landmarks = openHand(presence = 0.50f)
        val reason = DetectionFailureDiagnostics.fromLandmarks(
            landmarks = landmarks,
            reliability = TryOnReliability.WEAK,
            tipPresence = HandPresenceScoring.TIP_GLARE_MAX - 0.01f,
            paintableNailCount = 2,
            hasMappableAnchors = true,
        )
        assertThat(reason).isEqualTo(DetectionFailureReason.ExcessiveGlare)
    }

    @Test
    fun meanLuminanceArgb_averagesSampledPixels() {
        // ARGB: black + white → mean ~127.5 with step 1
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val mean = DetectionFailureDiagnostics.meanLuminanceArgb(
            intArrayOf(black, white),
            sampleStep = 1,
        )
        assertThat(mean).isWithin(1f).of(127.5f)
    }

    @Test
    fun userMessages_areFriendlyAndDistinct() {
        val messages = DetectionFailureReason.all.map { it.userMessage }.toSet()
        assertThat(messages).hasSize(DetectionFailureReason.all.size)
        DetectionFailureReason.all.forEach { reason ->
            assertThat(reason.userMessage).doesNotContain("threshold")
            assertThat(reason.userMessage).doesNotContain("confidence")
            assertThat(reason.logCode).isNotEmpty()
        }
    }

    @Test
    fun fromLandmarks_tooDark_whenMeanLumaBelowFloor() {
        val landmarks = openHand(presence = 0.40f)
        val reason = DetectionFailureDiagnostics.fromLandmarks(
            landmarks = landmarks,
            reliability = TryOnReliability.WEAK,
            meanLuminance = DetectionFailureDiagnostics.MEAN_LUMA_TOO_DARK - 1f,
            highlightShare = 0.01f,
            paintableNailCount = 2,
            hasMappableAnchors = true,
        )
        assertThat(reason).isEqualTo(DetectionFailureReason.TooDark)
    }

    @Test
    fun fromLandmarks_excessiveGlare_whenHighlightShareAboveFloor() {
        val landmarks = openHand(presence = 0.40f)
        val reason = DetectionFailureDiagnostics.fromLandmarks(
            landmarks = landmarks,
            reliability = TryOnReliability.WEAK,
            meanLuminance = 120f,
            highlightShare = DetectionFailureDiagnostics.HIGHLIGHT_SHARE_GLARE,
            paintableNailCount = 2,
            hasMappableAnchors = true,
        )
        assertThat(reason).isEqualTo(DetectionFailureReason.ExcessiveGlare)
    }

    @Test
    fun fromLandmarks_largeExtent_doesNotMapToHandTooFar() {
        val landmarks = openHand(presence = 0.60f)
        assertThat(DetectionFailureDiagnostics.handExtentNorm(landmarks))
            .isGreaterThan(DetectionFailureDiagnostics.HAND_EXTENT_TOO_SMALL)
        val reason = DetectionFailureDiagnostics.fromLandmarks(
            landmarks = landmarks,
            reliability = TryOnReliability.STRONG,
            paintableNailCount = 3,
            hasMappableAnchors = true,
        )
        assertThat(reason).isNotEqualTo(DetectionFailureReason.HandTooFar)
    }

    private fun clusteredHand(
        cx: Float,
        cy: Float,
        spread: Float,
        presence: Float,
    ): HandLandmarks {
        val points = List(21) { i ->
            NormPoint(cx + (i % 3) * spread * 0.2f, cy + (i % 2) * spread * 0.2f)
        }
        return HandLandmarks(
            points = points,
            imageWidth = 200,
            imageHeight = 300,
            presenceScore = presence,
        )
    }

    private fun openHand(presence: Float): HandLandmarks {
        val points = MutableList(21) { NormPoint(0.45f, 0.55f) }
        // Tips espalhados (mão aberta).
        val tips = listOf(
            NormPoint(0.20f, 0.35f),
            NormPoint(0.35f, 0.20f),
            NormPoint(0.50f, 0.18f),
            NormPoint(0.65f, 0.22f),
            NormPoint(0.80f, 0.38f),
        )
        HandLandmarks.TIP_INDICES.forEachIndexed { i, idx ->
            points[idx] = tips[i]
        }
        // Wrist / palm spread for extent.
        points[0] = NormPoint(0.50f, 0.85f)
        return HandLandmarks(
            points = points,
            imageWidth = 200,
            imageHeight = 300,
            presenceScore = presence,
        )
    }
}
