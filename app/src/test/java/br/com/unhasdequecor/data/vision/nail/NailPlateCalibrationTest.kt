package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.Handedness
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class NailPlateCalibrationTest {

    @Test
    fun plateFromPixels_openIndex_centerBetweenDipAndTip() {
        val tipX = 0.38f * 800f
        val tipY = 0.26f * 1200f
        val dipX = 0.40f * 800f
        val dipY = 0.36f * 1200f
        val pipX = 0.41f * 800f
        val pipY = 0.40f * 1200f
        val mcpX = 0.41f * 800f
        val mcpY = 0.50f * 1200f

        val plate = NailPlateCalibration.plateFromPixels(
            finger = Finger.INDEX,
            tipX = tipX,
            tipY = tipY,
            dipX = dipX,
            dipY = dipY,
            pipX = pipX,
            pipY = pipY,
            mcpX = mcpX,
            mcpY = mcpY,
        )

        assertThat(plate.facing).isFalse()
        assertThat(plate.thumbMode).isFalse()
        assertThat(plate.centerY).isLessThan(dipY)
        assertThat(plate.centerY).isGreaterThan(tipY)
        val tipDist = hypot(
            (plate.centerX - tipX).toDouble(),
            (plate.centerY - tipY).toDouble(),
        )
        val dipDist = hypot(
            (plate.centerX - dipX).toDouble(),
            (plate.centerY - dipY).toDouble(),
        )
        assertThat(tipDist).isLessThan(dipDist)
    }

    @Test
    fun isFacing_relativeTipDip_scaleInvariantForOpenProportions() {
        // tipDip/tipPip = 0.4 (open). Em px absolutos tipDip pode ser < SHORT_TIP_DIP_PX.
        assertThat(
            NailPlateCalibration.isFacing(thumbMode = false, tipDipPx = 12f, tipPipPx = 30f),
        ).isFalse()
        assertThat(
            NailPlateCalibration.isFacing(thumbMode = false, tipDipPx = 6f, tipPipPx = 120f),
        ).isTrue()
    }

    @Test
    fun isUsablePlate_rejectsCollapsedAxis() {
        val plate = NailPlateCalibration.plateFromPixels(
            finger = Finger.RING,
            tipX = 100f,
            tipY = 100f,
            dipX = 100f,
            dipY = 102f,
            pipX = 100f,
            pipY = 104f,
            mcpX = 100f,
            mcpY = 110f,
        )
        assertThat(NailPlateCalibration.isUsablePlate(plate)).isFalse()
    }

    @Test
    fun plateFromPixels_facing_widthFromTipPipNotShortLength() {
        val tipPip = 120f
        val plate = NailPlateCalibration.plateFromPixels(
            finger = Finger.MIDDLE,
            tipX = 400f,
            tipY = 354f,
            dipX = 400f,
            dipY = 360f,
            pipX = 400f,
            pipY = 354f + tipPip,
            mcpX = 400f,
            mcpY = 600f,
        )
        assertThat(plate.facing).isTrue()
        assertThat(plate.widthPx).isWithin(0.5f)
            .of(tipPip * NailPlateCalibration.FACING_WIDTH_SCALE)
        // Largura maior que length * widthScale típico do comprimento encurtado.
        val foreshortenedWidth = plate.lengthPx * NailPlateCalibration.scalesFor(Finger.MIDDLE).widthScale
        assertThat(plate.widthPx).isGreaterThan(foreshortenedWidth)
    }

    @Test
    fun almondExtents_facing_tipPastLandmarkByOvershootOnly() {
        val tipY = 354f
        val plate = NailPlateCalibration.plateFromPixels(
            finger = Finger.MIDDLE,
            tipX = 400f,
            tipY = tipY,
            dipX = 400f,
            dipY = 360f,
            pipX = 400f,
            pipY = 480f,
            mcpX = 400f,
            mcpY = 600f,
        )
        val almond = NailPlateCalibration.almondExtents(plate)
        val pastTip = tipY - almond.tipY // tip aponta para Y menor
        assertThat(pastTip).isGreaterThan(0f)
        assertThat(pastTip).isAtMost(plate.lengthPx * 0.06f)
        assertThat(pastTip).isWithin(0.5f).of(plate.overshootPx)
    }

    @Test
    fun almondExtents_thumb_tipAtOrBeyondLandmarkTip() {
        val hand = openHand(800, 1200)
        val tip = ImageCoordinates.toPixel(hand.point(Finger.THUMB.tipIndex), 800, 1200)
        val dip = ImageCoordinates.toPixel(hand.point(Finger.THUMB.dipIndex), 800, 1200)
        val pip = ImageCoordinates.toPixel(hand.point(Finger.THUMB.pipIndex), 800, 1200)
        val mcp = ImageCoordinates.toPixel(hand.point(Finger.THUMB.mcpIndex), 800, 1200)
        val plate = NailPlateCalibration.plateFromPixels(
            finger = Finger.THUMB,
            tipX = tip.x,
            tipY = tip.y,
            dipX = dip.x,
            dipY = dip.y,
            pipX = pip.x,
            pipY = pip.y,
            mcpX = mcp.x,
            mcpY = mcp.y,
        )
        val almond = NailPlateCalibration.almondExtents(plate)
        val along = (almond.tipX - tip.x) * plate.ux + (almond.tipY - tip.y) * plate.uy
        assertThat(along).isAtLeast(0f)
        assertThat(along).isWithin(0.5f).of(plate.overshootPx)
    }

    @Test
    fun mapperAndRoi_shareCentersOnOpenHand() {
        val w = 800
        val h = 1200
        val hand = openHand(w, h)
        val estimator = NailRoiEstimator()
        val rois = estimator.estimateAll(hand)
        val anchors = NailLandmarkMapper.fromNormalizedLandmarks(
            landmarks = hand.points.map { NailLandmarkMapper.NormalizedPoint(it.x, it.y) },
            imageWidth = w,
            imageHeight = h,
        )
        assertThat(anchors).isNotNull()
        assertThat(rois).hasSize(5)
        assertThat(anchors).hasSize(5)

        Finger.ALL.forEachIndexed { index, finger ->
            val tip = ImageCoordinates.toPixel(hand.point(finger.tipIndex), w, h)
            val dip = ImageCoordinates.toPixel(hand.point(finger.dipIndex), w, h)
            val pip = ImageCoordinates.toPixel(hand.point(finger.pipIndex), w, h)
            val mcp = ImageCoordinates.toPixel(hand.point(finger.mcpIndex), w, h)
            val plate = NailPlateCalibration.plateFromPixels(
                finger = finger,
                tipX = tip.x,
                tipY = tip.y,
                dipX = dip.x,
                dipY = dip.y,
                pipX = pip.x,
                pipY = pip.y,
                mcpX = mcp.x,
                mcpY = mcp.y,
            )
            val roi = rois.first { it.finger == finger }
            val anchor = anchors!![index]
            val ax = anchor.centerX * w
            val ay = anchor.centerY * h
            assertThat(abs(ax - plate.centerX)).isLessThan(0.5f)
            assertThat(abs(ay - plate.centerY)).isLessThan(0.5f)
            assertThat(abs(roi.lengthPx - plate.lengthPx)).isLessThan(0.5f)
            assertThat(abs(roi.widthPx - plate.widthPx)).isLessThan(0.5f)
            assertThat(abs(roi.axisFromDip.x - plate.axisStartX)).isLessThan(0.5f)
            assertThat(abs(roi.axisFromDip.y - plate.axisStartY)).isLessThan(0.5f)
        }
    }

    @Test
    fun facingMapperCenter_matchesPipPlusFacingCenter() {
        val landmarks = MutableList(21) { NailLandmarkMapper.NormalizedPoint(0.5f, 0.5f) }
        fun finger(tip: Int, dip: Int, pip: Int, x: Float) {
            landmarks[pip] = NailLandmarkMapper.NormalizedPoint(x, 0.40f)
            landmarks[dip] = NailLandmarkMapper.NormalizedPoint(x, 0.30f)
            landmarks[tip] = NailLandmarkMapper.NormalizedPoint(x, 0.295f)
        }
        finger(4, 3, 2, 0.30f)
        finger(8, 7, 6, 0.40f)
        finger(12, 11, 10, 0.50f)
        finger(16, 15, 14, 0.60f)
        finger(20, 19, 18, 0.70f)
        // MCP indices for non-thumb
        landmarks[5] = NailLandmarkMapper.NormalizedPoint(0.40f, 0.50f)
        landmarks[9] = NailLandmarkMapper.NormalizedPoint(0.50f, 0.50f)
        landmarks[13] = NailLandmarkMapper.NormalizedPoint(0.60f, 0.50f)
        landmarks[17] = NailLandmarkMapper.NormalizedPoint(0.70f, 0.52f)

        val anchors = NailLandmarkMapper.fromNormalizedLandmarks(
            landmarks = landmarks,
            imageWidth = 800,
            imageHeight = 1200,
        )
        requireNotNull(anchors)
        val index = anchors[1]
        val tipY = 0.295f
        val pipY = 0.40f
        val tipPipNorm = pipY - tipY
        val expectedY = pipY + (tipY - pipY) * NailPlateCalibration.FACING_CENTER -
            tipPipNorm * NailPlateCalibration.TIP_OVERSHOOT
        assertThat(index.centerY).isWithin(0.01f).of(expectedY)
    }

    @Test
    fun ellipseRadii_matchCalibrationFactors() {
        val rx = NailPlateCalibration.ellipseRadiusX(0.10f, 1000)
        val ry = NailPlateCalibration.ellipseRadiusY(0.12f, 1000)
        assertThat(rx).isWithin(0.01f).of(1000f * 0.10f * NailPlateCalibration.ELLIPSE_RX_FACTOR)
        assertThat(ry).isWithin(0.01f).of(1000f * 0.12f * NailPlateCalibration.ELLIPSE_RY_FACTOR)
        assertThat(NailPlateCalibration.canvasNailWidthNorm(0.10f))
            .isWithin(0.001f).of(0.10f * 2f * NailPlateCalibration.ELLIPSE_RX_FACTOR)
    }

    @Test
    fun plateTipOfAlmond_isSlightlyPastLandmarkTip() {
        val hand = openHand(800, 1200)
        val roi = NailRoiEstimator().estimate(hand, Finger.INDEX)!!
        val tipLandmark = ImageCoordinates.toPixel(hand.point(Finger.INDEX.tipIndex), 800, 1200)
        val almondTip = mid(roi.polygon[0], roi.polygon[5])
        val toAlmond = (almondTip.x - tipLandmark.x) * roi.uxApprox() +
            (almondTip.y - tipLandmark.y) * roi.uyApprox()
        assertThat(toAlmond).isGreaterThan(0f)
        assertThat(toAlmond).isAtMost(roi.lengthPx * 0.06f)
    }

    private fun mid(
        a: ImageCoordinates.PixelPoint,
        b: ImageCoordinates.PixelPoint,
    ): ImageCoordinates.PixelPoint =
        ImageCoordinates.PixelPoint((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)

    private fun NailRoi.uxApprox(): Float {
        val tip = mid(polygon[0], polygon[5])
        val cut = mid(polygon[2], polygon[3])
        val dx = tip.x - cut.x
        val dy = tip.y - cut.y
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
        return dx / len
    }

    private fun NailRoi.uyApprox(): Float {
        val tip = mid(polygon[0], polygon[5])
        val cut = mid(polygon[2], polygon[3])
        val dx = tip.x - cut.x
        val dy = tip.y - cut.y
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
        return dy / len
    }

    private fun openHand(width: Int, height: Int): HandLandmarks {
        val pts = MutableList(21) { ImageCoordinates.NormPoint(0.5f, 0.5f) }
        pts[0] = ImageCoordinates.NormPoint(0.50f, 0.78f)
        pts[2] = ImageCoordinates.NormPoint(0.30f, 0.52f)
        pts[3] = ImageCoordinates.NormPoint(0.28f, 0.48f)
        pts[4] = ImageCoordinates.NormPoint(0.22f, 0.40f)
        pts[5] = ImageCoordinates.NormPoint(0.41f, 0.50f)
        pts[6] = ImageCoordinates.NormPoint(0.41f, 0.40f)
        pts[7] = ImageCoordinates.NormPoint(0.40f, 0.36f)
        pts[8] = ImageCoordinates.NormPoint(0.38f, 0.26f)
        pts[9] = ImageCoordinates.NormPoint(0.50f, 0.50f)
        pts[10] = ImageCoordinates.NormPoint(0.50f, 0.38f)
        pts[11] = ImageCoordinates.NormPoint(0.50f, 0.34f)
        pts[12] = ImageCoordinates.NormPoint(0.50f, 0.22f)
        pts[13] = ImageCoordinates.NormPoint(0.59f, 0.50f)
        pts[14] = ImageCoordinates.NormPoint(0.59f, 0.40f)
        pts[15] = ImageCoordinates.NormPoint(0.60f, 0.36f)
        pts[16] = ImageCoordinates.NormPoint(0.62f, 0.26f)
        pts[17] = ImageCoordinates.NormPoint(0.68f, 0.52f)
        pts[18] = ImageCoordinates.NormPoint(0.68f, 0.44f)
        pts[19] = ImageCoordinates.NormPoint(0.70f, 0.40f)
        pts[20] = ImageCoordinates.NormPoint(0.74f, 0.32f)
        return HandLandmarks(
            points = pts,
            imageWidth = width,
            imageHeight = height,
            handedness = Handedness.RIGHT,
            presenceScore = 0.95f,
        )
    }
}
