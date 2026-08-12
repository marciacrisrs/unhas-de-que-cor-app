package br.com.unhasdequecor.data.vision

import br.com.unhasdequecor.data.vision.nail.DetectionConfidenceFloor
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HandPresenceScoringTest {

    @Test
    fun tipGlare_keepsAcceptButNotStrong() {
        val score = HandPresenceScoring.score(handednessScore = 0.72f, tipPresence = 0.05f)
        assertThat(score).isAtLeast(DetectionConfidenceFloor.HAND_PRESENCE_ACCEPT)
        assertThat(score).isLessThan(DetectionConfidenceFloor.HAND_PRESENCE_STRONG)
    }

    @Test
    fun missingTipPresence_fallsBackToHandedness() {
        val score = HandPresenceScoring.score(handednessScore = 0.55f, tipPresence = 0f)
        assertThat(score).isWithin(0.001f).of(0.55f)
    }

    @Test
    fun strongTips_raiseWeakHandednessViaBlend() {
        val score = HandPresenceScoring.score(handednessScore = 0.20f, tipPresence = 0.90f)
        // 0.20*0.35 + 0.90*0.65 = 0.655; max com tip → 0.90
        assertThat(score).isWithin(0.001f).of(0.90f)
    }
}
