package br.com.unhasdequecor.data.vision.nail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HandTrainingScenesTest {

    @Test
    fun `varieties prioritize deep skin scenes`() {
        assertThat(HandTrainingScenes.deepSkinVarieties()).hasSize(3)
        assertThat(HandTrainingScenes.varieties.map { it.id }.first())
            .startsWith("retinta")
        assertThat(HandTrainingScenes.varieties).hasSize(5)
    }

    @Test
    fun `fillNailCrop paints plate over retinta skin`() {
        val scene = HandTrainingScenes.varieties.first { it.id == "retinta_natural_plate" }
        val pixels = HandTrainingScenes.fillNailCrop(40, 50, scene.skin, scene.plate)
        assertThat(pixels.count { it == scene.plate }).isGreaterThan(50)
        assertThat(pixels.count { it == scene.skin }).isGreaterThan(50)
        assertThat(pixels.first()).isEqualTo(scene.skin)
    }
}

class HandCaptureGuidanceTest {

    @Test
    fun `checklist mentions frontal light and retinta`() {
        assertThat(HandCaptureGuidance.checklist).isNotEmpty()
        val joined = HandCaptureGuidance.asPlainText()
        assertThat(joined).contains("Luz")
        assertThat(joined).ignoringCase().contains("retinta")
        assertThat(HandCaptureGuidance.SAMPLE_PICKER_HINT).ignoringCase().contains("retinta")
    }
}
