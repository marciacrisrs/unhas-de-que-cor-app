package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.Handedness
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NailRoiEstimatorTest {

    private val estimator = NailRoiEstimator()

    @Test
    fun `estimates five rois for open right hand`() {
        val hand = openHand(width = 800, height = 1200, handedness = Handedness.RIGHT)
        val rois = estimator.estimateAll(hand)
        assertThat(rois).hasSize(5)
        rois.forEach { roi ->
            assertThat(roi.bounds.width()).isGreaterThan(4)
            assertThat(roi.bounds.height()).isGreaterThan(4)
            assertThat(roi.polygon).hasSize(6)
            assertThat(roi.geometricConfidence).isGreaterThan(0.3f)
            assertThat(roi.bounds.left).isAtLeast(0)
            assertThat(roi.bounds.top).isAtLeast(0)
            assertThat(roi.bounds.right).isAtMost(800)
            assertThat(roi.bounds.bottom).isAtMost(1200)
        }
    }

    @Test
    fun `rois scale with image resolution`() {
        val small = estimator.estimateAll(openHand(400, 600))
        val large = estimator.estimateAll(openHand(1600, 2400))
        assertThat(small).hasSize(5)
        assertThat(large).hasSize(5)
        val smallIndex = small.first { it.finger == Finger.INDEX }
        val largeIndex = large.first { it.finger == Finger.INDEX }
        assertThat(largeIndex.lengthPx).isGreaterThan(smallIndex.lengthPx * 1.5f)
        // Largura ~70% do comprimento (não “fio” fino).
        val aspect = largeIndex.lengthPx / largeIndex.widthPx
        assertThat(aspect).isLessThan(2.0f)
        assertThat(aspect).isGreaterThan(1.1f)
    }

    @Test
    fun `tilted finger produces rotated roi`() {
        val hand = openHand(800, 1200)
        val tilted = estimator.estimate(hand, Finger.INDEX)
        assertThat(tilted).isNotNull()
        assertThat(tilted!!.rotationDegrees).isNotEqualTo(0f)
    }

    @Test
    fun `thumb uses mcp to tip axis instead of collapsed dip`() {
        val hand = openHand(800, 1200)
        val thumb = estimator.estimate(hand, Finger.THUMB)
        assertThat(thumb).isNotNull()
        assertThat(thumb!!.geometricConfidence).isGreaterThan(0.3f)
        assertThat(thumb.lengthPx).isGreaterThan(20f)
        assertThat(thumb.widthPx).isGreaterThan(14f)
        val mcp = ImageCoordinates.toPixel(hand.point(Finger.THUMB.mcpIndex), 800, 1200)
        assertThat(thumb.axisFromDip.x).isWithin(0.5f).of(mcp.x)
        assertThat(thumb.axisFromDip.y).isWithin(0.5f).of(mcp.y)
    }

    @Test
    fun `facing camera uses tip pip width and capped almond tip`() {
        val hand = facingHand(800, 1200)
        val index = estimator.estimate(hand, Finger.INDEX)
        assertThat(index).isNotNull()
        val tip = ImageCoordinates.toPixel(hand.point(Finger.INDEX.tipIndex), 800, 1200)
        val pip = ImageCoordinates.toPixel(hand.point(Finger.INDEX.pipIndex), 800, 1200)
        val tipPip = ImageCoordinates.distancePx(tip, pip)
        assertThat(index!!.widthPx).isWithin(1f)
            .of(tipPip * NailPlateCalibration.FACING_WIDTH_SCALE)
        val almondTipX = (index.polygon[0].x + index.polygon[5].x) * 0.5f
        val almondTipY = (index.polygon[0].y + index.polygon[5].y) * 0.5f
        val past = kotlin.math.hypot(
            (almondTipX - tip.x).toDouble(),
            (almondTipY - tip.y).toDouble(),
        ).toFloat()
        assertThat(past).isLessThan(index.lengthPx * 0.06f)
        assertThat(index.geometricConfidence).isGreaterThan(0.3f)
    }

    private fun facingHand(width: Int, height: Int): HandLandmarks {
        val pts = MutableList(21) { ImageCoordinates.NormPoint(0.5f, 0.5f) }
        pts[0] = ImageCoordinates.NormPoint(0.50f, 0.78f)
        fun finger(mcp: Int, pip: Int, dip: Int, tip: Int, x: Float) {
            pts[mcp] = ImageCoordinates.NormPoint(x, 0.50f)
            pts[pip] = ImageCoordinates.NormPoint(x, 0.40f)
            pts[dip] = ImageCoordinates.NormPoint(x, 0.301f)
            pts[tip] = ImageCoordinates.NormPoint(x, 0.295f)
        }
        finger(2, 3, 3, 4, 0.30f)
        finger(5, 6, 7, 8, 0.40f)
        finger(9, 10, 11, 12, 0.50f)
        finger(13, 14, 15, 16, 0.60f)
        finger(17, 18, 19, 20, 0.70f)
        return HandLandmarks(
            points = pts,
            imageWidth = width,
            imageHeight = height,
            presenceScore = 0.95f,
        )
    }

    private fun openHand(
        width: Int,
        height: Int,
        handedness: Handedness = Handedness.UNKNOWN,
    ): HandLandmarks {
        val pts = MutableList(21) { ImageCoordinates.NormPoint(0.5f, 0.5f) }
        pts[0] = ImageCoordinates.NormPoint(0.50f, 0.78f)
        // thumb
        pts[2] = ImageCoordinates.NormPoint(0.30f, 0.52f)
        pts[3] = ImageCoordinates.NormPoint(0.28f, 0.48f)
        pts[4] = ImageCoordinates.NormPoint(0.22f, 0.40f)
        // index
        pts[5] = ImageCoordinates.NormPoint(0.41f, 0.50f)
        pts[6] = ImageCoordinates.NormPoint(0.41f, 0.40f)
        pts[7] = ImageCoordinates.NormPoint(0.40f, 0.36f)
        pts[8] = ImageCoordinates.NormPoint(0.38f, 0.26f)
        // middle
        pts[9] = ImageCoordinates.NormPoint(0.50f, 0.50f)
        pts[10] = ImageCoordinates.NormPoint(0.50f, 0.38f)
        pts[11] = ImageCoordinates.NormPoint(0.50f, 0.34f)
        pts[12] = ImageCoordinates.NormPoint(0.50f, 0.22f)
        // ring
        pts[13] = ImageCoordinates.NormPoint(0.59f, 0.50f)
        pts[14] = ImageCoordinates.NormPoint(0.59f, 0.40f)
        pts[15] = ImageCoordinates.NormPoint(0.60f, 0.36f)
        pts[16] = ImageCoordinates.NormPoint(0.62f, 0.26f)
        // pinky
        pts[17] = ImageCoordinates.NormPoint(0.68f, 0.52f)
        pts[18] = ImageCoordinates.NormPoint(0.68f, 0.44f)
        pts[19] = ImageCoordinates.NormPoint(0.70f, 0.40f)
        pts[20] = ImageCoordinates.NormPoint(0.74f, 0.32f)
        return HandLandmarks(
            points = pts,
            imageWidth = width,
            imageHeight = height,
            handedness = handedness,
            presenceScore = 0.95f,
        )
    }
}
