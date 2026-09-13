package br.com.unhasdequecor.data.vision.nail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates.PixelPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Learned nail-plate segmenter followed by a strict pixel-level boundary
 * refiner. MediaPipe/ROI provide localization only; the learned model supplies
 * a high-precision seed and the refiner recovers the full plate conservatively.
 */
@Singleton
class TfliteNailPlateSegmenter @Inject constructor(
    @ApplicationContext private val context: Context,
) : NailSegmenter {

    private val lock = Any()
    private val boundaryRefiner = NailPlateBoundaryRefiner()
    private var interpreter: Interpreter? = null

    override fun segment(image: Bitmap, roi: NailRoi): NailMask? {
        val bounds = roi.bounds
        if (bounds.width() < MIN_SIZE || bounds.height() < MIN_SIZE) return null
        if (!validBounds(bounds, image)) return null

        val model = getInterpreter() ?: return null
        val input = model.getInputTensor(0)
        val output = model.getOutputTensor(0)
        val inputShape = input.shape()
        if (inputShape.size != 4) return null

        val inputHeight = inputShape[1]
        val inputWidth = inputShape[2]
        val roiRect = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        val contextRect = expandedBounds(roiRect, image)
        val inputBitmap = Bitmap.createBitmap(
            image,
            contextRect.left,
            contextRect.top,
            contextRect.width(),
            contextRect.height(),
        )
        val inputBuffer = encodeInput(
            source = inputBitmap,
            width = inputWidth,
            height = inputHeight,
            dataType = input.dataType(),
            quantization = input.quantizationParams(),
        )
        val outputBuffer = ByteBuffer.allocateDirect(output.numBytes()).order(ByteOrder.nativeOrder())

        synchronized(lock) {
            inputBuffer.rewind()
            outputBuffer.rewind()
            model.run(inputBuffer, outputBuffer)
        }

        outputBuffer.rewind()
        val probability = decodeOutput(outputBuffer, output) ?: run {
            inputBitmap.recycle()
            return null
        }
        val modelMask = probabilityToBitmap(probability, inputWidth, inputHeight)
        val fullMask = projectMaskToContext(
            modelMask = modelMask,
            contextBounds = contextRect,
        )
        modelMask.recycle()
        inputBitmap.recycle()

        val midpoint = PixelPoint(
            x = (roi.axisFromDip.x + roi.axisToTip.x) * 0.5f,
            y = (roi.axisFromDip.y + roi.axisToTip.y) * 0.5f,
        )
        val cleaned = keepNailComponent(
            alpha = fullMask,
            width = contextRect.width(),
            height = contextRect.height(),
            seed = midpoint,
            originX = contextRect.left,
            originY = contextRect.top,
        ) ?: return null

        val seedMask = NailMask(
            width = contextRect.width(),
            height = contextRect.height(),
            alpha = cleaned,
            originX = contextRect.left,
            originY = contextRect.top,
        )
        return boundaryRefiner.refine(image, roi, seedMask)
    }

    private fun getInterpreter(): Interpreter? {
        synchronized(lock) {
            interpreter?.let { return it }
            return runCatching {
                Interpreter(
                    loadModel(),
                    Interpreter.Options().apply { setNumThreads(INFERENCE_THREADS) },
                ).also { interpreter = it }
            }.getOrNull()
        }
    }

    private fun loadModel(): ByteBuffer {
        val descriptor = context.assets.openFd(MODEL_ASSET)
        FileInputStream(descriptor.fileDescriptor).use { stream ->
            return stream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                descriptor.startOffset,
                descriptor.declaredLength,
            ).load()
        }
    }

    private fun encodeInput(
        source: Bitmap,
        width: Int,
        height: Int,
        dataType: DataType,
        quantization: org.tensorflow.lite.Tensor.QuantizationParams,
    ): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        scaled.recycle()
        val bytesPerElement = if (dataType == DataType.FLOAT32) 4 else 1
        val buffer = ByteBuffer.allocateDirect(width * height * CHANNELS * bytesPerElement)
            .order(ByteOrder.nativeOrder())
        for (pixel in pixels) {
            val channels = intArrayOf(pixel shr 16 and 0xFF, pixel shr 8 and 0xFF, pixel and 0xFF)
            for (channel in channels) {
                when (dataType) {
                    DataType.FLOAT32 -> buffer.putFloat(channel / 255f)
                    DataType.UINT8 -> buffer.put(channel.toByte())
                    DataType.INT8 -> {
                        val scale = quantization.scale.takeIf { it > 0f } ?: 1f
                        buffer.put((channel / 255f / scale + quantization.zeroPoint)
                            .roundToInt().coerceIn(-128, 127).toByte())
                    }
                    else -> error("Unsupported TFLite input type: $dataType")
                }
            }
        }
        return buffer
    }

    private fun decodeOutput(
        buffer: ByteBuffer,
        tensor: org.tensorflow.lite.Tensor,
    ): FloatArray? {
        val shape = tensor.shape()
        if (shape.size != 4) return null
        val channels: Int
        val height: Int
        val width: Int
        if (shape[3] <= MAX_SEGMENT_CHANNELS) {
            height = shape[1]
            width = shape[2]
            channels = shape[3]
        } else if (shape[1] <= MAX_SEGMENT_CHANNELS) {
            channels = shape[1]
            height = shape[2]
            width = shape[3]
        } else {
            return null
        }
        if (channels < 1) return null

        val values = FloatArray(height * width)
        val dataType = tensor.dataType()
        val quantization = tensor.quantizationParams()
        for (index in values.indices) {
            val channelValues = FloatArray(channels)
            for (channel in 0 until channels) {
                channelValues[channel] = readValue(buffer, dataType, quantization)
            }
            values[index] = if (channels == 1) {
                normalizeProbability(channelValues[0])
            } else {
                softmaxPositive(channelValues)
            }
        }
        return values
    }

    private fun readValue(
        buffer: ByteBuffer,
        dataType: DataType,
        quantization: org.tensorflow.lite.Tensor.QuantizationParams,
    ): Float {
        val raw = when (dataType) {
            DataType.FLOAT32 -> buffer.float
            DataType.UINT8 -> (buffer.get().toInt() and 0xFF).toFloat()
            DataType.INT8 -> buffer.get().toFloat()
            else -> return 0f
        }
        return when (dataType) {
            DataType.FLOAT32 -> raw
            DataType.UINT8, DataType.INT8 -> (raw - quantization.zeroPoint) * quantization.scale
            else -> raw
        }
    }

    private fun normalizeProbability(value: Float): Float = when {
        value in 0f..1f -> value
        else -> 1f / (1f + exp(-value))
    }

    private fun softmaxPositive(values: FloatArray): Float {
        val maxValue = values.maxOrNull() ?: return 0f
        var sum = 0f
        for (value in values) sum += exp(value - maxValue)
        val positive = exp(values.last() - maxValue)
        return (positive / sum).coerceIn(0f, 1f)
    }

    private fun probabilityToBitmap(probability: FloatArray, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val alpha = (probability[i] * 255f).roundToInt().coerceIn(0, 255)
            pixels[i] = alpha shl 24 or 0x00FFFFFF
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun expandedBounds(bounds: Rect, image: Bitmap): Rect {
        val width = max(bounds.width(), (bounds.width() * CONTEXT_FACTOR).roundToInt())
        val height = max(bounds.height(), (bounds.height() * CONTEXT_FACTOR).roundToInt())
        val left = (bounds.centerX() - width / 2).coerceIn(0, max(0, image.width - width))
        val top = (bounds.centerY() - height / 2).coerceIn(0, max(0, image.height - height))
        return Rect(
            left,
            top,
            min(image.width, left + width),
            min(image.height, top + height),
        )
    }

    private fun projectMaskToContext(modelMask: Bitmap, contextBounds: Rect): ByteArray {
        val width = contextBounds.width()
        val height = contextBounds.height()
        val source = IntArray(modelMask.width * modelMask.height)
        modelMask.getPixels(source, 0, modelMask.width, 0, 0, modelMask.width, modelMask.height)
        val alpha = ByteArray(width * height)
        for (y in 0 until height) {
            val sy = (y.toFloat() / height * modelMask.height)
                .roundToInt().coerceIn(0, modelMask.height - 1)
            for (x in 0 until width) {
                val sx = (x.toFloat() / width * modelMask.width)
                    .roundToInt().coerceIn(0, modelMask.width - 1)
                alpha[y * width + x] = (source[sy * modelMask.width + sx] ushr 24).toByte()
            }
        }
        return alpha
    }

    private fun keepNailComponent(
        alpha: ByteArray,
        width: Int,
        height: Int,
        seed: PixelPoint,
        originX: Int,
        originY: Int,
    ): ByteArray? {
        val binary = BooleanArray(alpha.size) {
            (alpha[it].toInt() and 0xFF) >= MODEL_THRESHOLD
        }
        val seedX = (seed.x - originX).roundToInt().coerceIn(0, width - 1)
        val seedY = (seed.y - originY).roundToInt().coerceIn(0, height - 1)
        val visited = BooleanArray(binary.size)
        val queue = IntArray(binary.size)
        var head = 0
        var tail = 0
        var best: IntArray? = null
        var bestContainsSeed = false

        for (start in binary.indices) {
            if (!binary[start] || visited[start]) continue
            var componentHead = 0
            var componentTail = 0
            queue[componentTail++] = start
            visited[start] = true
            val points = ArrayList<Int>()
            var containsSeed = false
            while (componentHead < componentTail) {
                val current = queue[componentHead++]
                points += current
                containsSeed = containsSeed || current == seedY * width + seedX
                val x = current % width
                val y = current / width
                for (neighbor in neighbors(x, y, width, height)) {
                    if (binary[neighbor] && !visited[neighbor]) {
                        visited[neighbor] = true
                        queue[componentTail++] = neighbor
                    }
                }
            }
            if (containsSeed || best == null || (!bestContainsSeed && points.size > best!!.size)) {
                best = points.toIntArray()
                bestContainsSeed = containsSeed
            }
        }

        val selected = best ?: return null
        if (selected.size < MIN_COMPONENT_PIXELS) return null
        val result = ByteArray(alpha.size)
        for (index in selected) result[index] = alpha[index]
        return result
    }

    private fun neighbors(x: Int, y: Int, width: Int, height: Int): IntArray {
        val result = IntArray(4)
        var count = 0
        if (x > 0) result[count++] = y * width + x - 1
        if (x + 1 < width) result[count++] = y * width + x + 1
        if (y > 0) result[count++] = (y - 1) * width + x
        if (y + 1 < height) result[count++] = (y + 1) * width + x
        return result.copyOf(count)
    }

    private fun validBounds(bounds: ImageCoordinates.PixelRect, image: Bitmap): Boolean =
        bounds.left >= 0 && bounds.top >= 0 &&
            bounds.right <= image.width && bounds.bottom <= image.height

    fun close() {
        synchronized(lock) {
            interpreter?.close()
            interpreter = null
        }
    }

    private companion object {
        const val MODEL_ASSET = "nail_segmentation_mobilenet_v2.tflite"
        const val CONTEXT_FACTOR = 1.75f
        const val INFERENCE_THREADS = 2
        const val CHANNELS = 3
        const val MIN_SIZE = 12
        const val MODEL_THRESHOLD = 128
        const val MIN_COMPONENT_PIXELS = 12
        const val MAX_SEGMENT_CHANNELS = 4
    }
}
