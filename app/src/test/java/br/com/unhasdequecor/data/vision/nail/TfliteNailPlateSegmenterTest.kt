package br.com.unhasdequecor.data.vision.nail

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Tensor
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TfliteNailPlateSegmenterTest {

    @Test
    fun `NHWC float output decodes without touching interpreter tensors`() {
        val buffer = ByteBuffer.allocateDirect(4 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        buffer.putFloat(0.2f)
        buffer.putFloat(0.8f)
        buffer.putFloat(0.0f)
        buffer.putFloat(1.0f)
        buffer.rewind()

        val decoded = decodeTfliteSegmentationOutput(
            buffer = buffer,
            shape = intArrayOf(1, 2, 2, 1),
            dataType = DataType.FLOAT32,
            quantization = Tensor.QuantizationParams(1f, 0),
        )

        assertThat(decoded).isNotNull()
        assertThat(decoded!!.toList()).containsExactly(0.2f, 0.8f, 0.0f, 1.0f).inOrder()
    }

    @Test
    fun `two-channel NHWC uses the last channel as the positive class`() {
        val buffer = ByteBuffer.allocateDirect(4 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        buffer.putFloat(5f)
        buffer.putFloat(0f)
        buffer.putFloat(0f)
        buffer.putFloat(5f)
        buffer.rewind()

        val decoded = decodeTfliteSegmentationOutput(
            buffer = buffer,
            shape = intArrayOf(1, 1, 2, 2),
            dataType = DataType.FLOAT32,
            quantization = Tensor.QuantizationParams(1f, 0),
        )

        assertThat(decoded).isNotNull()
        assertThat(decoded!!).hasLength(2)
        assertThat(decoded[0]).isLessThan(0.5f)
        assertThat(decoded[1]).isGreaterThan(0.5f)
    }

    @Test
    fun `unsupported output rank is rejected`() {
        val buffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        val decoded = decodeTfliteSegmentationOutput(
            buffer = buffer,
            shape = intArrayOf(1, 2, 2),
            dataType = DataType.FLOAT32,
            quantization = Tensor.QuantizationParams(1f, 0),
        )
        assertThat(decoded).isNull()
    }

    @Test
    fun `logits outside 0-1 are mapped through a sigmoid`() {
        assertThat(normalizeTfliteProbability(0.4f)).isEqualTo(0.4f)
        assertThat(normalizeTfliteProbability(2f)).isGreaterThan(0.8f)
        assertThat(normalizeTfliteProbability(-2f)).isLessThan(0.2f)
    }

    @Test
    fun `softmax uses the last channel as the nail-positive class`() {
        val probability = softmaxPositiveChannel(floatArrayOf(0f, 5f))
        assertThat(probability).isGreaterThan(0.9f)
    }
}
