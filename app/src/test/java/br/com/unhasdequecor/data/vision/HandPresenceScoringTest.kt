package br.com.unhasdequecor.data.vision

import br.com.unhasdequecor.data.vision.nail.DetectionConfidenceFloor
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HandPresenceScoringTest {

    @Test
    fun tipGlare_doesNotDropClearHandednessBelowAccept() {
        // Flash estoura tips → tip presence baixa, mas handedness clara.
        val score = HandPresenceScoring.score(handednessScore = 0.72f, tipPresence = 0.05f)
        assertThat(score).isAtLeast(DetectionConfidenceFloor.HAND_PRESENCE_ACCEPT)
        assertThat(score).isAtLeast(0.72f)
    }

    @Test
    fun missingTipPresence_fallsBackToHandedness() {
        val score = HandPresenceScoring.score(handednessScore = 0.55f, tipPresence = 0f)
        assertThat(score).isWithin(0.001f).of(0.55f)
    }

    @Test
    fun strongTips_raiseWeakHandedness() {
        val score = HandPresenceScoring.score(handednessScore = 0.20f, tipPresence = 0.90f)
        assertThat(score).isAtLeast(0.90f)
    }
}
