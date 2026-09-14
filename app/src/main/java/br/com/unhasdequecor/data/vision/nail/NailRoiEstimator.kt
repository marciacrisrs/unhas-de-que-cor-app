package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.HandLandmarks
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import kotlin.math.max
import kotlin.math.min
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estima ROI + contorno da placa da unha a partir de MCP/PIP/DIP/TIP.
 *
 * Geometria da placa alinhada ao [NailLandmarkMapper] via [NailPlateCalibration].
 * Polegar usa eixo MCP→TIP (sem DIP distinto no MediaPipe).
 */
@Singleton
class NailRoiEstimator @Inject constructor() {

    data class Estimation(val rois: List<NailRoi>, val rejected: List<Rejection>)
    data class Rejection(val finger: Finger, val reason: String)

    fun estimateAll(hand: HandLandmarks): List<NailRoi> =
        Finger.ALL.mapNotNull { finger -> estimate(hand, finger) }

    fun estimate(hand: HandLandmarks, finger: Finger): NailRoi? {
        val w = hand.imageWidth
        val h = hand.imageHeight
        val mcp = ImageCoordinates.toPixel(hand.point(finger.mcpIndex), w, h)
        val pip = ImageCoordinates.toPixel(hand.point(finger.pipIndex), w, h)
        val dip = ImageCoordinates.toPixel(hand.point(finger.dipIndex), w, h)
        val tip = ImageCoordinates.toPixel(hand.point(finger.tipIndex), w, h)
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
        if (!NailPlateCalibration.isUsablePlate(plate)) return null
        val tipDip = ImageCoordinates.distancePx(tip, dip)
        val tipPip = ImageCoordinates.distancePx(tip, pip)
        val tipMcp = ImageCoordinates.distancePx(tip, mcp)
        val polygon = NailPlateContour.buildSixPoint(plate)
        val nailLen = plate.lengthPx
        val nailWidth = plate.widthPx
        val pad = max(nailWidth, nailLen) * PAD_SCALE + PAD_EXTRA
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in polygon) {
            minX = min(minX, p.x)
            minY = min(minY, p.y)
            maxX = max(maxX, p.x)
            maxY = max(maxY, p.y)
        }
        val bounds = PixelRect(
            left = (minX - pad).toInt().coerceIn(0, w - 1),
            top = (minY - pad).toInt().coerceIn(0, h - 1),
            right = (maxX + pad).toInt().coerceIn(1, w),
            bottom = (maxY + pad).toInt().coerceIn(1, h),
        )
        if (bounds.width() < MIN_ROI_SIZE || bounds.height() < MIN_ROI_SIZE) return null
        val geometricConfidence = geometricConfidence(
            tipDip = tipDip,
            tipPip = tipPip,
            tipMcp = tipMcp,
            nailLen = nailLen,
            rawLengthPx = plate.rawLengthPx,
            nailWidth = nailWidth,
            thumbMode = plate.thumbMode,
            facing = plate.facing,
            presence = hand.presenceScore,
        )
        return NailRoi(
            finger = finger,
            bounds = bounds,
            polygon = polygon,
            axisFromDip = PixelPoint(plate.axisStartX, plate.axisStartY),
            axisToTip = PixelPoint(tip.x, tip.y),
            lengthPx = nailLen,
            widthPx = nailWidth,
            rotationDegrees = plate.rotationDegrees,
            geometricConfidence = geometricConfidence,
        )
    }

    private fun geometricConfidence(
        tipDip: Float, tipPip: Float, tipMcp: Float, nailLen: Float, rawLengthPx: Float,
        nailWidth: Float, thumbMode: Boolean, facing: Boolean, presence: Float,
    ): Float {
        val axisOk = when {
            thumbMode -> tipMcp > NailPlateCalibration.MIN_AXIS_THUMB_PX
            facing -> tipPip > NailPlateCalibration.MIN_AXIS_FACING_PX
            else -> tipDip > NailPlateCalibration.MIN_AXIS_OPEN_PX
        }
        if (!axisOk) return LOW_GEOMETRY_SCORE
        val aspect = nailLen / nailWidth.coerceAtLeast(1f)
        val aspectScore = when {
            aspect in IDEAL_ASPECT -> FULL_SCORE
            aspect in ACCEPTABLE_ASPECT -> MID_SCORE
            else -> LOW_SCORE
        }
        val sizeScore = when {
            rawLengthPx in IDEAL_LENGTH -> FULL_SCORE
            rawLengthPx in ACCEPTABLE_LENGTH -> MID_SCORE
            else -> LOW_SCORE
        }
        return (
            PRESENCE_WEIGHT * presence.coerceIn(0f, 1f) +
                ASPECT_WEIGHT * aspectScore + SIZE_WEIGHT * sizeScore
        ).coerceIn(0f, 1f)
    }

    private companion object {
        const val MIN_ROI_SIZE = 4
        const val PAD_SCALE = 0.22f
        const val PAD_EXTRA = 2f
        const val PRESENCE_WEIGHT = 0.30f
        const val ASPECT_WEIGHT = 0.40f
        const val SIZE_WEIGHT = 0.30f
        const val LOW_GEOMETRY_SCORE = 0.15f
        const val FULL_SCORE = 1f
        const val MID_SCORE = 0.75f
        const val LOW_SCORE = 0.4f
        val IDEAL_ASPECT = 1.15f..2.2f
        val ACCEPTABLE_ASPECT = 0.9f..2.8f
        val IDEAL_LENGTH = 16f..140f
        val ACCEPTABLE_LENGTH = 10f..180f
    }
}

fun NailRoiEstimator.estimateAllWithDiagnostics(hand: HandLandmarks): NailRoiEstimator.Estimation {
    val rois = estimateAll(hand)
    val accepted = rois.mapTo(mutableSetOf()) { it.finger }
    val rejected = Finger.ALL.filterNot { it in accepted }
        .map { NailRoiEstimator.Rejection(it, "plate_geometry_unusable") }
    return NailRoiEstimator.Estimation(rois = rois, rejected = rejected)
}
