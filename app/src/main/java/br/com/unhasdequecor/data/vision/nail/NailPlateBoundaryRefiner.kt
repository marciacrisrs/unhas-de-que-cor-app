package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Refines a learned nail mask to the physical nail plate.
 *
 * The learned mask is treated as a high-precision seed, never as the final
 * paint area. The refiner expands only through pixels that remain connected to
 * that seed, agree with the nail-surface appearance, and stay outside a robust
 * local skin model. MediaPipe geometry is a search envelope, not a paint mask.
 */
class NailPlateBoundaryRefiner {

    fun refine(image: Bitmap, roi: NailRoi, seedMask: NailMask): NailMask? {
        val width = seedMask.width
        val height = seedMask.height
        if (width < MIN_SIZE || height < MIN_SIZE) return null

        val pixels = IntArray(width * height)
        image.getPixels(pixels, 0, width, seedMask.originX, seedMask.originY, width, height)

        val seed = BooleanArray(pixels.size) { index ->
            (seedMask.alpha[index].toInt() and 0xFF) >= SEED_THRESHOLD
        }
        val seedCount = seed.count { it }
        if (seedCount < MIN_SEED_PIXELS) return null

        val frame = Frame.fromRoi(roi, seedMask)
        val envelope = SearchEnvelope.from(roi, frame, width, height)
        val nailModel = buildModel(pixels, seed, MAX_MODEL_SAMPLES) ?: return null
        val skinModel = buildSkinModel(pixels, seed, envelope, width, height)

        val refined = growConnectedPlate(
            pixels = pixels,
            seed = seed,
            nailModel = nailModel,
            skinModel = skinModel,
            envelope = envelope,
            width = width,
            height = height,
        )

        val cleaned = removeUnsafePixels(
            pixels = pixels,
            mask = refined,
            seed = seed,
            nailModel = nailModel,
            skinModel = skinModel,
            envelope = envelope,
            width = width,
            height = height,
        )
        val finalMask = fillSmallGaps(cleaned, width, height)
        if (finalMask.count { it } < MIN_FINAL_PIXELS) return null

        val polygon = boundaryPolygon(finalMask, width, height, seedMask.originX, seedMask.originY)
            ?: return null

        return seedMask.copy(alpha = finalMask, boundaryPolygon = polygon)
    }

    private fun growConnectedPlate(
        pixels: IntArray,
        seed: BooleanArray,
        nailModel: Model,
        skinModel: Model?,
        envelope: SearchEnvelope,
        width: Int,
        height: Int,
    ): ByteArray {
        val accepted = ByteArray(seed.size)
        val visited = BooleanArray(seed.size)
        val queue = IntArray(seed.size)
        var head = 0
        var tail = 0

        for (index in seed.indices) {
            if (seed[index]) {
                accepted[index] = SOLID_ALPHA
                visited[index] = true
                queue[tail++] = index
            }
        }

        while (head < tail) {
            val current = queue[head++]
            val x = current % width
            val y = current / width
            for (neighbor in neighbors(x, y, width, height)) {
                if (visited[neighbor]) continue
                visited[neighbor] = true
                val nx = neighbor % width
                val ny = neighbor / width
                if (!envelope.contains(nx.toFloat(), ny.toFloat())) continue

                val feature = feature(pixels[neighbor])
                val nailSimilarity = similarity(feature, nailModel)
                val skinSimilarity = skinModel?.let { similarity(feature, it) } ?: 0f
                val localContinuity = similarity(
                    feature(pixels[current]),
                    nailModel,
                )
                val skinPenalty = if (skinModel == null) 0f else skinSimilarity * SKIN_PENALTY
                val score = nailSimilarity * NAIL_WEIGHT +
                    localContinuity * CONTINUITY_WEIGHT - skinPenalty

                if (score >= GROW_THRESHOLD &&
                    nailSimilarity >= MIN_NAIL_SIMILARITY &&
                    (skinModel == null || skinSimilarity <= MAX_SKIN_SIMILARITY ||
                        nailSimilarity - skinSimilarity >= MIN_NAIL_SKIN_MARGIN)
                ) {
                    accepted[neighbor] = SOLID_ALPHA
                    queue[tail++] = neighbor
                }
            }
        }
        return accepted
    }

    private fun removeUnsafePixels(
        pixels: IntArray,
        mask: ByteArray,
        seed: BooleanArray,
        nailModel: Model,
        skinModel: Model?,
        envelope: SearchEnvelope,
        width: Int,
        height: Int,
    ): ByteArray {
        val result = mask.copyOf()
        for (index in result.indices) {
            if ((result[index].toInt() and 0xFF) == 0 || seed[index]) continue
            val x = index % width
            val y = index / width
            if (!envelope.contains(x.toFloat(), y.toFloat())) {
                result[index] = 0
                continue
            }
            val current = feature(pixels[index])
            val nailSimilarity = similarity(current, nailModel)
            val skinSimilarity = skinModel?.let { similarity(current, it) } ?: 0f
            if (nailSimilarity < FINAL_NAIL_SIMILARITY ||
                (skinModel != null && skinSimilarity > FINAL_SKIN_SIMILARITY &&
                    nailSimilarity - skinSimilarity < FINAL_NAIL_SKIN_MARGIN)
            ) {
                result[index] = 0
            }
        }
        return keepSeedConnected(result, seed, width, height)
    }

    private fun keepSeedConnected(mask: ByteArray, seed: BooleanArray, width: Int, height: Int): ByteArray {
        val result = ByteArray(mask.size)
        val visited = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        var head = 0
        var tail = 0
        for (index in seed.indices) {
            if (seed[index] && mask[index].toInt() and 0xFF > 0) {
                visited[index] = true
                queue[tail++] = index
                result[index] = SOLID_ALPHA
            }
        }
        while (head < tail) {
            val current = queue[head++]
            val x = current % width
            val y = current / width
            for (neighbor in neighbors(x, y, width, height)) {
                if (!visited[neighbor] && (mask[neighbor].toInt() and 0xFF) > 0) {
                    visited[neighbor] = true
                    result[neighbor] = mask[neighbor]
                    queue[tail++] = neighbor
                }
            }
        }
        return result
    }

    private fun fillSmallGaps(mask: ByteArray, width: Int, height: Int): ByteArray {
        val result = mask.copyOf()
        repeat(GAP_PASSES) {
            val next = result.copyOf()
            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val index = y * width + x
                    if ((result[index].toInt() and 0xFF) > 0) continue
                    var filled = 0
                    for (neighbor in neighbors(x, y, width, height)) {
                        if ((result[neighbor].toInt() and 0xFF) > 0) filled++
                    }
                    if (filled >= GAP_NEIGHBORS) next[index] = SOLID_ALPHA
                }
            }
            next.copyInto(result)
        }
        return result
    }

    private fun buildModel(pixels: IntArray, mask: BooleanArray, limit: Int): Model? {
        val selected = ArrayList<Feature>(min(limit, mask.count { it }))
        val step = max(1, mask.size / limit)
        var index = 0
        while (index < mask.size && selected.size < limit) {
            if (mask[index]) selected += feature(pixels[index])
            index += step
        }
        if (selected.size < MIN_MODEL_SAMPLES) return null
        return Model.from(selected)
    }

    private fun buildSkinModel(
        pixels: IntArray,
        seed: BooleanArray,
        envelope: SearchEnvelope,
        width: Int,
        height: Int,
    ): Model? {
        val samples = ArrayList<Feature>(MAX_SKIN_SAMPLES)
        val border = SKIN_BORDER
        for (y in 0 until height step SKIN_SAMPLE_STEP) {
            for (x in 0 until width step SKIN_SAMPLE_STEP) {
                if (samples.size >= MAX_SKIN_SAMPLES) return Model.from(samples)
                if (envelope.distanceOutside(x.toFloat(), y.toFloat()) < border) continue
                val index = y * width + x
                if (seed[index]) continue
                samples += feature(pixels[index])
            }
        }
        return if (samples.size >= MIN_SKIN_SAMPLES) Model.from(samples) else null
    }

    private fun boundaryPolygon(
        mask: ByteArray,
        width: Int,
        height: Int,
        originX: Int,
        originY: Int,
    ): List<ImageCoordinates.PixelPoint>? {
        val rows = ArrayList<Triple<Int, Int, Int>>()
        for (y in 0 until height) {
            var left = width
            var right = -1
            for (x in 0 until width) {
                if ((mask[y * width + x].toInt() and 0xFF) >= SOLID_ALPHA) {
                    left = min(left, x)
                    right = max(right, x)
                }
            }
            if (right >= left) rows += Triple(y, left, right)
        }
        if (rows.size < MIN_BOUNDARY_ROWS) return null
        val polygon = ArrayList<ImageCoordinates.PixelPoint>(rows.size * 2)
        rows.forEachIndexed { index, row ->
            if (index % BOUNDARY_STEP == 0) {
                polygon += ImageCoordinates.PixelPoint(
                    row.second + originX.toFloat(),
                    row.first + originY.toFloat(),
                )
            }
        }
        rows.indices.reversed().forEach { index ->
            if (index % BOUNDARY_STEP == 0) {
                val row = rows[index]
                polygon += ImageCoordinates.PixelPoint(
                    row.third + originX.toFloat(),
                    row.first + originY.toFloat(),
                )
            }
        }
        return polygon.takeIf { it.size >= 3 }
    }

    private fun feature(pixel: Int): Feature {
        val r = (pixel shr 16 and 0xFF) / 255f
        val g = (pixel shr 8 and 0xFF) / 255f
        val b = (pixel and 0xFF) / 255f
        val maxChannel = max(r, max(g, b))
        val minChannel = min(r, min(g, b))
        val saturation = if (maxChannel <= 0f) 0f else (maxChannel - minChannel) / maxChannel
        return Feature(r, g, b, (0.299f * r + 0.587f * g + 0.114f * b), saturation)
    }

    private fun similarity(value: Feature, model: Model): Float {
        val rgb = hypot(
            hypot(value.r - model.feature.r, value.g - model.feature.g).toDouble(),
            value.b - model.feature.b.toDouble(),
        ).toFloat() / RGB_MAX_DISTANCE
        val luma = abs(value.luma - model.feature.luma)
        val saturation = abs(value.saturation - model.feature.saturation)
        return (1f - (rgb * RGB_WEIGHT + luma * LUMA_WEIGHT + saturation * SATURATION_WEIGHT))
            .coerceIn(0f, 1f)
    }

    private data class Feature(
        val r: Float,
        val g: Float,
        val b: Float,
        val luma: Float,
        val saturation: Float,
    )

    private data class Model(val feature: Feature, val spread: Float) {
        companion object {
            fun from(samples: List<Feature>): Model {
                val mean = Feature(
                    samples.map { it.r }.average().toFloat(),
                    samples.map { it.g }.average().toFloat(),
                    samples.map { it.b }.average().toFloat(),
                    samples.map { it.luma }.average().toFloat(),
                    samples.map { it.saturation }.average().toFloat(),
                )
                val spread = samples.map {
                    abs(it.r - mean.r) + abs(it.g - mean.g) + abs(it.b - mean.b)
                }.average().toFloat().coerceIn(0.02f, 0.35f)
                return Model(mean, spread)
            }
        }
    }

    private data class Frame(
        val baseX: Float,
        val baseY: Float,
        val ux: Float,
        val uy: Float,
        val vx: Float,
        val vy: Float,
        val length: Float,
    ) {
        fun project(x: Float, y: Float): Pair<Float, Float> =
            Pair((x - baseX) * ux + (y - baseY) * uy, (x - baseX) * vx + (y - baseY) * vy)

        companion object {
            fun fromRoi(roi: NailRoi, mask: NailMask): Frame {
                val dx = roi.axisToTip.x - roi.axisFromDip.x
                val dy = roi.axisToTip.y - roi.axisFromDip.y
                val length = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
                return Frame(
                    roi.axisFromDip.x - mask.originX,
                    roi.axisFromDip.y - mask.originY,
                    dx / length,
                    dy / length,
                    -dy / length,
                    dx / length,
                    length,
                )
            }
        }
    }

    private data class SearchEnvelope(
        val minT: Float,
        val maxT: Float,
        val maxHalfWidth: Float,
        val frame: Frame,
    ) {
        fun contains(x: Float, y: Float): Boolean {
            val projection = frame.project(x, y)
            val taper = when {
                projection.first < minT -> 0.55f
                projection.first > maxT -> 0.60f
                else -> 1f
            }
            return projection.first in minT - EXTRA_BASE..maxT + EXTRA_TIP &&
                abs(projection.second) <= maxHalfWidth * taper
        }

        fun distanceOutside(x: Float, y: Float): Float {
            val projection = frame.project(x, y)
            val tDistance = max(minT - projection.first, projection.first - maxT).coerceAtLeast(0f)
            val sDistance = abs(projection.second) - maxHalfWidth
            return max(tDistance, sDistance).coerceAtLeast(0f)
        }

        companion object {
            fun from(roi: NailRoi, frame: Frame, width: Int, height: Int): SearchEnvelope {
                val points = roi.polygon.map { frame.project(it.x - roi.bounds.left, it.y - roi.bounds.top) }
                val minT = points.minOfOrNull { it.first } ?: 0f
                val maxT = points.maxOfOrNull { it.first } ?: frame.length
                val halfWidth = points.maxOfOrNull { abs(it.second) }
                    ?.coerceAtLeast(roi.widthPx * 0.5f)
                    ?: roi.widthPx * 0.5f
                return SearchEnvelope(
                    minT = minT,
                    maxT = maxT,
                    maxHalfWidth = (halfWidth * MAX_LATERAL_EXPANSION).coerceAtMost(max(width, height) * 0.46f),
                    frame = frame,
                )
            }
        }
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

    private companion object {
        const val MIN_SIZE = 12
        const val MIN_SEED_PIXELS = 10
        const val MIN_FINAL_PIXELS = 20
        const val SEED_THRESHOLD = 185
        const val SOLID_ALPHA = 255.toByte()
        const val MAX_MODEL_SAMPLES = 240
        const val MIN_MODEL_SAMPLES = 8
        const val MAX_SKIN_SAMPLES = 120
        const val MIN_SKIN_SAMPLES = 10
        const val SKIN_SAMPLE_STEP = 3
        const val SKIN_BORDER = 2f
        const val NAIL_WEIGHT = 0.68f
        const val CONTINUITY_WEIGHT = 0.32f
        const val SKIN_PENALTY = 0.52f
        const val GROW_THRESHOLD = 0.32f
        const val MIN_NAIL_SIMILARITY = 0.48f
        const val MAX_SKIN_SIMILARITY = 0.90f
        const val MIN_NAIL_SKIN_MARGIN = 0.04f
        const val FINAL_NAIL_SIMILARITY = 0.40f
        const val FINAL_SKIN_SIMILARITY = 0.93f
        const val FINAL_NAIL_SKIN_MARGIN = 0.02f
        const val RGB_MAX_DISTANCE = 1.732f
        const val RGB_WEIGHT = 0.58f
        const val LUMA_WEIGHT = 0.27f
        const val SATURATION_WEIGHT = 0.15f
        const val MAX_LATERAL_EXPANSION = 1.48f
        const val EXTRA_BASE = 0.20f
        const val EXTRA_TIP = 0.30f
        const val GAP_PASSES = 2
        const val GAP_NEIGHBORS = 5
        const val MIN_BOUNDARY_ROWS = 4
        const val BOUNDARY_STEP = 2
    }
}
