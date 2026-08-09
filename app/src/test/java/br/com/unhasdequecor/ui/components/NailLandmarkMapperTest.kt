package br.com.unhasdequecor.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NailLandmarkMapperTest {

    @Test
    fun `maps open hand landmarks into five nail anchors`() {
        val landmarks = syntheticOpenHand()
        val anchors = NailLandmarkMapper.fromNormalizedLandmarks(
            landmarks = landmarks,
            imageWidth = 800,
            imageHeight = 1200,
            viewAspect = NailLandmarkMapper.PREVIEW_ASPECT,
        )

        assertThat(anchors).isNotNull()
        assertThat(anchors).hasSize(5)
        anchors!!.forEach { nail ->
            assertThat(nail.centerX).isAtLeast(0f)
            assertThat(nail.centerX).isAtMost(1f)
            assertThat(nail.centerY).isAtLeast(0f)
            assertThat(nail.centerY).isAtMost(1f)
            assertThat(nail.width).isGreaterThan(0f)
            assertThat(nail.height).isGreaterThan(0f)
        }
    }

    @Test
    fun `rejects incomplete landmark sets`() {
        assertThat(
            NailLandmarkMapper.fromNormalizedLandmarks(
                landmarks = emptyList(),
                imageWidth = 100,
                imageHeight = 100,
            ),
        ).isNull()
    }

    @Test
    fun `crop mapping keeps image center at view center`() {
        val mapped = NailLandmarkMapper.mapImageNormToViewNorm(
            x = 0.5f,
            y = 0.5f,
            imageWidth = 1000,
            imageHeight = 1000,
            viewAspect = 0.75f,
        )
        assertThat(mapped.x).isWithin(0.001f).of(0.5f)
        assertThat(mapped.y).isWithin(0.001f).of(0.5f)
    }

    private fun syntheticOpenHand(): List<NailLandmarkMapper.NormalizedPoint> {
        val points = MutableList(21) { NailLandmarkMapper.NormalizedPoint(0.5f, 0.5f) }
        // wrist
        points[0] = NailLandmarkMapper.NormalizedPoint(0.50f, 0.78f)
        // thumb tip/dip
        points[3] = NailLandmarkMapper.NormalizedPoint(0.28f, 0.48f)
        points[4] = NailLandmarkMapper.NormalizedPoint(0.22f, 0.40f)
        // index
        points[7] = NailLandmarkMapper.NormalizedPoint(0.40f, 0.36f)
        points[8] = NailLandmarkMapper.NormalizedPoint(0.38f, 0.26f)
        // middle
        points[11] = NailLandmarkMapper.NormalizedPoint(0.50f, 0.34f)
        points[12] = NailLandmarkMapper.NormalizedPoint(0.50f, 0.22f)
        // ring
        points[15] = NailLandmarkMapper.NormalizedPoint(0.60f, 0.36f)
        points[16] = NailLandmarkMapper.NormalizedPoint(0.62f, 0.26f)
        // pinky
        points[19] = NailLandmarkMapper.NormalizedPoint(0.70f, 0.40f)
        points[20] = NailLandmarkMapper.NormalizedPoint(0.74f, 0.32f)
        return points
    }
}
