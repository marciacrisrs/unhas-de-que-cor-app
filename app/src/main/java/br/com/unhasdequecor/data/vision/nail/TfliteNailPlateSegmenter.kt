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
 * Learned nail-plate segmenter.
 *
 * MediaPipe/ROI remain the spatial prior. The TFLite model is responsible for
 * deciding which pixels in that prior are actually nail plate. The mask is
 * then cleaned by a single-component constraint so another finger cannot leak
 * into the current nail.
 */
@Singleton
class TfliteNailPlateSegmenter @Inject constructor(
    @ApplicationContext private val context: Context,
) : NailSegmenter {

    private val lock = Any()
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
        val fullMask = projectMaskToRoi(
            modelMask = modelMask,
            contextBounds = contextRect,
            roiBounds = roiRect,
        )
        modelMask.recycle()
        inputBitmap.recycle()

        val midpoint = PixelPoint(
            x = (roi.axisFromDip.x + roi.axisToTip.x) * 0.5f,
            y = (roi.axisFromDip.y + roi.axisToTip.y) * 0.5f,
        )
        val cleaned = keepNailComponent(
            alpha = fullMask,
            width = roiRect.width(),
            height = roiRect.height(),
            seed = midpoint,
            originX = roiRect.left,
            originY = roiRect.top,
        ) ?: return null

        return NailMask(
            width = roiRect.width(),
            height = roiRect.height(),
            alpha = cleaned,
            originX = roiRect.left,
            originY = roiRect.top,
            boundaryPolygon = buildBoundaryPolygon(
                alpha = cleaned,
                width = roiRect.width(),
                height = roiRect.height(),
                originX = roiRect.left,
                originY = roiRect.top,
            ),
        )
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
        val positiveChannel = if (channels == 1) 0 else channels - 1
        for (index in values.indices) {
            var selected = 0f
            for (channel in 0 until channels) {
                val value = readValue(buffer, dataType, quantization)
                if (channel == positiveChannel) selected = value
            }
            values[index] = normalizeProbability(selected)
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
            DataType.UINT8, DataType.INT8 -> raw * quantization.scale + quantization.zeroPoint
            else -> raw
        }
    }

    private fun normalizeProbability(value: Float): Float = when {
        value in 0f..1f -> value
        value > 0f -> 1f / (1f + exp(-value))
        else -> 0f
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

    private fun projectMaskToRoi(modelMask: Bitmap, contextBounds: Rect, roiBounds: Rect): ByteArray {
        val width = roiBounds.width()
        val height = roiBounds.height()
        val source = IntArray(modelMask.width * modelMask.height)
        modelMask.getPixels(source, 0, modelMask.width, 0, 0, modelMask.width, modelMask.height)
        val alpha = ByteArray(width * height)
        for (y in 0 until height) {
            val imageY = roiBounds.top + y
            val sy = ((imageY - contextBounds.top).toFloat() / contextBounds.height() * modelMask.height)
                .roundToInt().coerceIn(0, modelMask.height - 1)
            for (x in 0 until width) {
                val imageX = roiBounds.left + x
                val sx = ((imageX - contextBounds.left).toFloat() / contextBounds.width() * modelMask.width)
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
        var best: IntArray? = null
        var bestContainsSeed = false

        for (start in binary.indices) {
            if (!binary[start] || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            val points = ArrayList<Int>()
            var containsSeed = false
            while (head < tail) {
                val current = queue[head++]
                points += current
                containsSeed = containsSeed || current == seedY * width + seedX
                val x = current % width
                val y = current / width
                for (neighbor in neighbors(x, y, width, height)) {
                    if (binary[neighbor] && !visited[neighbor]) {
                        visited[neighbor] = true
                        queue[tail++] = neighbor
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

    private fun buildBoundaryPolygon(
        alpha: ByteArray,
        width: Int,
        height: Int,
        originX: Int,
        originY: Int,
    ): List<PixelPoint>? {
        val rows = ArrayList<Triple<Int, Int, Int>>()
        for (y in 0 until height) {
            var minX = width
            var maxX = -1
            for (x in 0 until width) {
                if ((alpha[y * width + x].toInt() and 0xFF) >= MODEL_THRESHOLD) {
                    minX = min(minX, x)
                    maxX = max(maxX, x)
                }
            }
            if (maxX >= minX) rows += Triple(y, minX, maxX)
        }
        if (rows.size < MIN_BOUNDARY_ROWS) return null

        val polygon = ArrayList<PixelPoint>(rows.size * 2)
        rows.forEachIndexed { index, row ->
            if (index % BOUNDARY_STEP == 0) {
                polygon += PixelPoint(row.second + originX, row.first + originY)
            }
        }
        rows.indices.reversed().forEach { index ->
            if (index % BOUNDARY_STEP == 0) {
                val row = rows[index]
                polygon += PixelPoint(row.third + originX, row.first + originY)
            }
        }
        return polygon.takeIf { it.size >= 3 }
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
        const val CONTEXT_FACTOR = 1.35f
        const val INFERENCE_THREADS = 2
        const val CHANNELS = 3
        const val MIN_SIZE = 12
        const val MODEL_THRESHOLD = 128
        const val MIN_COMPONENT_PIXELS = 12
        const val MIN_BOUNDARY_ROWS = 4
        const val BOUNDARY_STEP = 2
        const val MAX_SEGMENT_CHANNELS = 4
    }
}
