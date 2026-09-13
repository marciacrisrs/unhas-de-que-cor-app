package br.com.unhasdequecor.data.vision.nail

import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelRect
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NailContourRegularizerTest {
    private val regularizer = NailContourRegularizer()

    @Test
    fun `removes isolated lateral spike without shrinking main plate`() {
        val width = 40
        val height = 70
        val alpha = ByteArray(width * height)
        for (y in 10 until 60) for (x in 13..26) alpha[y * width + x] = 255.toByte()
        alpha[34 * width + 29] = 255.toByte()
        alpha[35 * width + 28] = 255.toByte()

        val result = regularizer.regularize(roi(), NailMask(width, height, alpha))

        assertThat(result.alpha[34 * width + 29].toInt() and 255).isEqualTo(0)
        assertThat(result.alpha[35 * width + 28].toInt() and 255).isEqualTo(0)
        assertThat(result.filledRatio()).isGreaterThan(0.20f)
        assertThat(result.boundaryPolygon).isNotNull()
    }

    @Test
    fun `fills a one pixel boundary notch when surrounded by plate`() {
        val width = 40
        val height = 70
        val alpha = ByteArray(width * height)
        for (y in 10 until 60) for (x in 13..26) alpha[y * width + x] = 255.toByte()
        alpha[35 * width + 13] = 0

        val result = regularizer.regularize(roi(), NailMask(width, height, alpha))

        assertThat(result.alpha[35 * width + 13].toInt() and 255).isEqualTo(255)
    }

    @Test
    fun `boundary polygon stays aligned with mask origin and plate extent`() {
        val width = 40
        val height = 70
        val alpha = ByteArray(width * height)
        for (y in 10 until 60) for (x in 13..26) alpha[y * width + x] = 255.toByte()

        val mask = NailMask(
            width = width,
            height = height,
            alpha = alpha,
            originX = 100,
            originY = 200,
        )
        val result = regularizer.regularize(roi(), mask)
        val polygon = result.boundaryPolygon!!

        assertThat(polygon.minOf { it.x }).isGreaterThan(108f)
        assertThat(polygon.maxOf { it.x }).isLessThan(130f)
        assertThat(polygon.minOf { it.y }).isGreaterThan(208f)
        assertThat(polygon.maxOf { it.y }).isLessThan(260f)
    }

    private fun roi() = NailRoi(
        finger = Finger.MIDDLE,
        bounds = PixelRect(5, 5, 35, 65),
        polygon = listOf(
            PixelPoint(13f, 10f), PixelPoint(26f, 10f),
            PixelPoint(26f, 60f), PixelPoint(13f, 60f),
        ),
        axisFromDip = PixelPoint(19.5f, 60f),
        axisToTip = PixelPoint(19.5f, 10f),
        lengthPx = 50f,
        widthPx = 13f,
        rotationDegrees = 0f,
        geometricConfidence = 0.95f,
    )
}
