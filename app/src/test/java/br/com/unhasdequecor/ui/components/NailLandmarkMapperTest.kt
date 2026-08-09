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

    private fun syntheticOpenHand(): List<NailLandmarkMapper.NormalizedPoint> {
        val points = MutableList(21) { NailLandmarkMapper.NormalizedPoint(0.5f, 0.5f) }
        points[0] = NailLandmarkMapper.NormalizedPoint(0.50f, 0.78f)
        points[2] = NailLandmarkMapper.NormalizedPoint(0.30f, 0.52f)
        points[3] = NailLandmarkMapper.NormalizedPoint(0.28f, 0.48f)
        points[4] = NailLandmarkMapper.NormalizedPoint(0.22f, 0.40f)
        points[6] = NailLandmarkMapper.NormalizedPoint(0.41f, 0.40f)
        points[7] = NailLandmarkMapper.NormalizedPoint(0.40f, 0.36f)
        points[8] = NailLandmarkMapper.NormalizedPoint(0.38f, 0.26f)
        points[10] = NailLandmarkMapper.NormalizedPoint(0.50f, 0.38f)
        points[11] = NailLandmarkMapper.NormalizedPoint(0.50f, 0.34f)
        points[12] = NailLandmarkMapper.NormalizedPoint(0.50f, 0.22f)
        points[14] = NailLandmarkMapper.NormalizedPoint(0.59f, 0.40f)
        points[15] = NailLandmarkMapper.NormalizedPoint(0.60f, 0.36f)
        points[16] = NailLandmarkMapper.NormalizedPoint(0.62f, 0.26f)
        points[18] = NailLandmarkMapper.NormalizedPoint(0.68f, 0.44f)
        points[19] = NailLandmarkMapper.NormalizedPoint(0.70f, 0.40f)
        points[20] = NailLandmarkMapper.NormalizedPoint(0.74f, 0.32f)
        return points
    }
}
