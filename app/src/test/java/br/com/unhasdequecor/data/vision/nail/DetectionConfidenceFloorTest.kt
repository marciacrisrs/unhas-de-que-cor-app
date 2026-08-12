package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DetectionConfidenceFloorTest {

    @Test
    fun handPresence_boundaries() {
        assertThat(DetectionConfidenceFloor.acceptsHandPresence(0.11f)).isFalse()
        assertThat(DetectionConfidenceFloor.acceptsHandPresence(0.12f)).isTrue()
        assertThat(DetectionConfidenceFloor.isStrongHandPresence(0.54f)).isFalse()
        assertThat(DetectionConfidenceFloor.isStrongHandPresence(0.55f)).isTrue()
    }

    @Test
    fun roiAndNail_floors() {
        assertThat(DetectionConfidenceFloor.acceptsRoi(0.23f)).isFalse()
        assertThat(DetectionConfidenceFloor.acceptsRoi(0.24f)).isTrue()
        assertThat(DetectionConfidenceFloor.acceptsNail(0.31f)).isFalse()
        assertThat(DetectionConfidenceFloor.acceptsNail(0.32f)).isTrue()
        assertThat(DetectionConfidenceFloor.isFullQualityNail(0.44f)).isFalse()
        assertThat(DetectionConfidenceFloor.isFullQualityNail(0.45f)).isTrue()
    }

    @Test
    fun countPaintableAndFullQuality_splitByFloors() {
        val nails = listOf(
            nail(0.33f), // paintable only
            nail(0.46f), // full
            nail(0.50f), // full
            nail(0.20f), // below paint floor
        )
        assertThat(DetectionConfidenceFloor.countPaintable(nails)).isEqualTo(3)
        assertThat(DetectionConfidenceFloor.countFullQuality(nails)).isEqualTo(2)
        assertThat(DetectionConfidenceFloor.meetsFullNailFloor(nails)).isFalse()
        assertThat(
            DetectionConfidenceFloor.meetsFullNailFloor(
                listOf(nail(0.45f), nail(0.50f), nail(0.60f)),
            ),
        ).isTrue()
    }

    @Test
    fun mediapipeMin_isBelowHandAccept() {
        assertThat(DetectionConfidenceFloor.MEDIAPIPE_MIN)
            .isLessThan(DetectionConfidenceFloor.HAND_PRESENCE_ACCEPT)
        assertThat(DetectionConfidenceFloor.NAIL_COMBINED_MIN)
            .isLessThan(DetectionConfidenceFloor.NAIL_FULL_MIN)
    }

    private fun nail(confidence: Float): DetectedNail {
        val roi = NailRoi(
            finger = Finger.INDEX,
            bounds = PixelRect(0, 0, 10, 10),
            polygon = listOf(
                PixelPoint(0f, 0f),
                PixelPoint(10f, 0f),
                PixelPoint(10f, 10f),
                PixelPoint(0f, 10f),
            ),
            axisFromDip = PixelPoint(5f, 10f),
            axisToTip = PixelPoint(5f, 0f),
            lengthPx = 10f,
            widthPx = 6f,
            rotationDegrees = 0f,
            geometricConfidence = 0.9f,
        )
        val mask = NailMask(
            width = 4,
            height = 4,
            alpha = ByteArray(16) { 255.toByte() },
            originX = 0,
            originY = 0,
        )
        return DetectedNail(finger = Finger.INDEX, roi = roi, mask = mask, confidence = confidence)
    }
}
