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
        // Centro entre dip e tip (não além da ponta).
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
    fun plateFromPixels_facing_usesTipPipOvershootBase() {
        // tip≈dip → facing; overshoot deve usar tip–pip (não tip–dip ~0).
        val tipX = 400f
        val tipY = 354f
        val dipX = 400f
        val dipY = 360f // 6px → facing
        val pipX = 400f
        val pipY = 480f
        val mcpX = 400f
        val mcpY = 600f

        val plate = NailPlateCalibration.plateFromPixels(
            finger = Finger.MIDDLE,
            tipX = tipX,
            tipY = tipY,
            dipX = dipX,
            dipY = dipY,
            pipX = pipX,
            pipY = pipY,
            mcpX = mcpX,
            mcpY = mcpY,
        )
        assertThat(plate.facing).isTrue()

        val withoutOvershootY = pipY + (tipY - pipY) * NailPlateCalibration.FACING_CENTER
        // Overshoot tipPip * 0.02 em direção à tip (Y menor).
        assertThat(plate.centerY).isLessThan(withoutOvershootY)
        assertThat(plate.centerY).isGreaterThan(tipY)
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
    fun plateTipOfAlmond_isSlightlyPastLandmarkTip() {
        val hand = openHand(800, 1200)
        val roi = NailRoiEstimator().estimate(hand, Finger.INDEX)!!
        val tipLandmark = ImageCoordinates.toPixel(hand.point(Finger.INDEX.tipIndex), 800, 1200)
        val almondTip = mid(roi.polygon[0], roi.polygon[5])
        // Ponta do almond além da tip landmark ao longo do eixo (borda livre).
        val toAlmond = (almondTip.x - tipLandmark.x) * roi.uxApprox() +
            (almondTip.y - tipLandmark.y) * roi.uyApprox()
        assertThat(toAlmond).isGreaterThan(0f)
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
