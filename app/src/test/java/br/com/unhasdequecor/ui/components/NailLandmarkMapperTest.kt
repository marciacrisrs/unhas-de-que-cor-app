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
    fun `nail center stays between dip and tip on open fingers`() {
        val landmarks = syntheticOpenHand()
        val anchors = NailLandmarkMapper.fromNormalizedLandmarks(
            landmarks = landmarks,
            imageWidth = 800,
            imageHeight = 1200,
        )
        requireNotNull(anchors)
        // Indicador: tip=8 (0.38,0.26), dip=7 (0.40,0.36) — centro mais perto da tip, sem passar.
        val index = anchors[1]
        assertThat(index.centerY).isLessThan(0.36f)
        assertThat(index.centerY).isAtLeast(0.26f)
        assertThat(index.centerX).isWithin(0.08f).of(0.39f)
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
    fun `clamps nail centers near image edges instead of rejecting hand`() {
        val landmarks = MutableList(21) { NailLandmarkMapper.NormalizedPoint(0.5f, 0.5f) }
        landmarks[2] = NailLandmarkMapper.NormalizedPoint(0.28f, 0.12f)
        landmarks[3] = NailLandmarkMapper.NormalizedPoint(0.26f, 0.08f)
        landmarks[4] = NailLandmarkMapper.NormalizedPoint(0.24f, 0.02f)
        landmarks[6] = NailLandmarkMapper.NormalizedPoint(0.40f, 0.10f)
        landmarks[7] = NailLandmarkMapper.NormalizedPoint(0.40f, 0.06f)
        landmarks[8] = NailLandmarkMapper.NormalizedPoint(0.40f, 0.01f)
        landmarks[10] = NailLandmarkMapper.NormalizedPoint(0.52f, 0.10f)
        landmarks[11] = NailLandmarkMapper.NormalizedPoint(0.52f, 0.06f)
        landmarks[12] = NailLandmarkMapper.NormalizedPoint(0.52f, 0.01f)
        landmarks[14] = NailLandmarkMapper.NormalizedPoint(0.64f, 0.11f)
        landmarks[15] = NailLandmarkMapper.NormalizedPoint(0.64f, 0.07f)
        landmarks[16] = NailLandmarkMapper.NormalizedPoint(0.64f, 0.02f)
        landmarks[18] = NailLandmarkMapper.NormalizedPoint(0.74f, 0.14f)
        landmarks[19] = NailLandmarkMapper.NormalizedPoint(0.76f, 0.10f)
        landmarks[20] = NailLandmarkMapper.NormalizedPoint(0.78f, 0.04f)

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
        }
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
