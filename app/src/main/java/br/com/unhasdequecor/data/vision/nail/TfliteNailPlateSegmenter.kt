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
 * the nail-plate evidence and the refiner preserves that evidence conservatively.
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

        val roiRect = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        val contextRect = expandedBounds(roiRect, image)
        val inputBitmap = Bitmap.createBitmap(
            image,
            contextRect.left,
            contextRect.top,
            contextRect.width(),
            contextRect.height(),
        )
        val inference = try {
            inferProbability(inputBitmap)
        } finally {
            if (!inputBitmap.isRecycled) inputBitmap.recycle()
        } ?: return null
        val probability = inference.probability
        val inputWidth = inference.inputWidth
        val inputHeight = inference.inputHeight

        // Keep the model probability field until projection. Nearest-neighbour
        // expansion was turning the low-resolution model boundary into visible
        // stair steps; bilinear sampling gives the refiner a continuous edge
        // signal without inventing coverage outside the learned probability.
        val fullMask = projectProbabilityToContext(
            probability = probability,
            modelWidth = inputWidth,
            modelHeight = inputHeight,
            contextBounds = contextRect,
        )

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

    /**
     * Result still-try-on runs on `Dispatchers.Default` and Live frames run on
     * the CameraX analyzer thread. Both share this singleton. TFLite's
     * [Interpreter] is not thread-safe, so tensor metadata and [Interpreter.run]
     * stay on the same lock. Reading `getOutputTensor` while another thread is
     * inside `run()` is a native crash.
     */
    private fun inferProbability(inputBitmap: Bitmap): InferenceResult? {
        val spec: ModelIoSpec
        synchronized(lock) {
            spec = snapshotModelIoLocked() ?: return null
        }
        val inputBuffer = encodeInput(
            source = inputBitmap,
            width = spec.inputWidth,
            height = spec.inputHeight,
            dataType = spec.inputDataType,
            quantization = spec.inputQuantization,
        )
        val outputBuffer = ByteBuffer.allocateDirect(spec.outputNumBytes).order(ByteOrder.nativeOrder())
        synchronized(lock) {
            val model = interpreterOrNullLocked() ?: return null
            inputBuffer.rewind()
            outputBuffer.rewind()
            model.run(inputBuffer, outputBuffer)
        }
        outputBuffer.rewind()
        val probability = decodeTfliteSegmentationOutput(
            buffer = outputBuffer,
            shape = spec.outputShape,
            dataType = spec.outputDataType,
            quantization = spec.outputQuantization,
        ) ?: return null
        return InferenceResult(probability, spec.inputWidth, spec.inputHeight)
    }

    private fun snapshotModelIoLocked(): ModelIoSpec? {
        val model = interpreterOrNullLocked() ?: return null
        val input = model.getInputTensor(0)
        val output = model.getOutputTensor(0)
        val inputShape = input.shape()
        if (inputShape.size != 4) return null
        return ModelIoSpec(
            inputWidth = inputShape[2],
            inputHeight = inputShape[1],
            inputDataType = input.dataType(),
            inputQuantization = input.quantizationParams(),
            outputNumBytes = output.numBytes(),
            outputShape = output.shape().copyOf(),
            outputDataType = output.dataType(),
            outputQuantization = output.quantizationParams(),
        )
    }

    private fun interpreterOrNullLocked(): Interpreter? {
        interpreter?.let { return it }
        return runCatching {
            Interpreter(
                loadModel(),
                Interpreter.Options().apply { setNumThreads(INFERENCE_THREADS) },
            ).also { interpreter = it }
        }.getOrNull()
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

    private data class ModelIoSpec(
        val inputWidth: Int,
        val inputHeight: Int,
        val inputDataType: DataType,
        val inputQuantization: org.tensorflow.lite.Tensor.QuantizationParams,
        val outputNumBytes: Int,
        val outputShape: IntArray,
        val outputDataType: DataType,
        val outputQuantization: org.tensorflow.lite.Tensor.QuantizationParams,
    )

    private data class InferenceResult(
        val probability: FloatArray,
        val inputWidth: Int,
        val inputHeight: Int,
    )

    /**
     * Projects the learned probability field directly to the image context.
     * The returned alpha remains a soft confidence field, so later contour
     * stages can use the model's sub-pixel transition instead of a staircase
     * produced by binary nearest-neighbour upscaling.
     */
    private fun projectProbabilityToContext(
        probability: FloatArray,
        modelWidth: Int,
        modelHeight: Int,
        contextBounds: Rect,
    ): ByteArray {
        val width = contextBounds.width()
        val height = contextBounds.height()
        val alpha = ByteArray(width * height)
        val maxX = modelWidth - 1
        val maxY = modelHeight - 1
        for (y in 0 until height) {
            val modelY = if (height <= 1) 0f else y.toFloat() * maxY / (height - 1).toFloat()
            val y0 = modelY.toInt().coerceIn(0, maxY)
            val y1 = (y0 + 1).coerceAtMost(maxY)
            val fy = modelY - y0
            for (x in 0 until width) {
                val modelX = if (width <= 1) 0f else x.toFloat() * maxX / (width - 1).toFloat()
                val x0 = modelX.toInt().coerceIn(0, maxX)
                val x1 = (x0 + 1).coerceAtMost(maxX)
                val fx = modelX - x0
                val top = probability[y0 * modelWidth + x0] * (1f - fx) +
                    probability[y0 * modelWidth + x1] * fx
                val bottom = probability[y1 * modelWidth + x0] * (1f - fx) +
                    probability[y1 * modelWidth + x1] * fx
                val value = top * (1f - fy) + bottom * fy
                alpha[y * width + x] = (value * 255f).roundToInt().coerceIn(0, 255).toByte()
            }
        }
        return alpha
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

    private fun keepNailComponent(
        alpha: ByteArray,
        width: Int,
        height: Int,
        seed: PixelPoint,
        originX: Int,
        originY: Int,
    ): ByteArray? {
        // Natural nails can produce a weaker model response than painted nails.
        // Start conservatively, then relax only when the strict mask contains no
        // component. This keeps painted-nail precision while recovering low-
        // contrast natural plates instead of reporting a false "no nail".
        for (threshold in MODEL_THRESHOLDS) {
            val selected = selectComponent(
                alpha = alpha,
                width = width,
                height = height,
                seed = seed,
                originX = originX,
                originY = originY,
                threshold = threshold,
            ) ?: continue
            if (selected.size >= MIN_COMPONENT_PIXELS) {
                val result = ByteArray(alpha.size)
                for (index in selected) result[index] = alpha[index]
                return result
            }
        }
        return null
    }

    private fun selectComponent(
        alpha: ByteArray,
        width: Int,
        height: Int,
        seed: PixelPoint,
        originX: Int,
        originY: Int,
        threshold: Int,
    ): IntArray? {
        val binary = BooleanArray(alpha.size) {
            (alpha[it].toInt() and 0xFF) >= threshold
        }
        val seedX = (seed.x - originX).roundToInt().coerceIn(0, width - 1)
        val seedY = (seed.y - originY).roundToInt().coerceIn(0, height - 1)
        val seedIndex = seedY * width + seedX
        val visited = BooleanArray(binary.size)
        val queue = IntArray(binary.size)
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
                containsSeed = containsSeed || current == seedIndex
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
        return best
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
        val MODEL_THRESHOLDS = intArrayOf(128, 96, 64)
        const val MIN_COMPONENT_PIXELS = 12
    }
}

/**
 * Decodes a copied TFLite output buffer. Callers must snapshot tensor metadata
 * under the interpreter lock; this must not touch [Interpreter] itself.
 */
internal fun decodeTfliteSegmentationOutput(
    buffer: ByteBuffer,
    shape: IntArray,
    dataType: DataType,
    quantization: org.tensorflow.lite.Tensor.QuantizationParams,
): FloatArray? {
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
    for (index in values.indices) {
        val channelValues = FloatArray(channels)
        for (channel in 0 until channels) {
            channelValues[channel] = readTfliteValue(buffer, dataType, quantization)
        }
        values[index] = if (channels == 1) {
            normalizeTfliteProbability(channelValues[0])
        } else {
            softmaxPositiveChannel(channelValues)
        }
    }
    return values
}

private fun readTfliteValue(
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

internal fun normalizeTfliteProbability(value: Float): Float = when {
    value in 0f..1f -> value
    else -> 1f / (1f + exp(-value))
}

internal fun softmaxPositiveChannel(values: FloatArray): Float {
    val maxValue = values.maxOrNull() ?: return 0f
    var sum = 0f
    for (value in values) sum += exp(value - maxValue)
    val positive = exp(values.last() - maxValue)
    return (positive / sum).coerceIn(0f, 1f)
}

private const val MAX_SEGMENT_CHANNELS = 4

