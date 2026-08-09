package br.com.unhasdequecor.data.vision

import android.content.Context
import android.graphics.Bitmap
import br.com.unhasdequecor.data.local.hand.OrientedBitmapDecoder
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates
import br.com.unhasdequecor.ui.components.NailLandmarkMapper
import br.com.unhasdequecor.ui.components.NailOverlayAnchor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detecção de mão via MediaPipe Hand Landmarker (IMAGE).
 * Expõe landmarks brutos ([HandLandmarkProcessor]) e âncoras legadas ([HandNailDetector]).
 */
@Singleton
class MediaPipeHandNailDetector @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : HandLandmarkProcessor, HandNailDetector {

    @Volatile
    private var landmarker: HandLandmarker? = null

    override fun detect(bitmap: Bitmap): List<NailOverlayAnchor>? {
        val landmarks = detectLandmarks(bitmap) ?: return null
        return NailLandmarkMapper.fromNormalizedLandmarks(
            landmarks = landmarks.points.map {
                NailLandmarkMapper.NormalizedPoint(it.x, it.y)
            },
            imageWidth = landmarks.imageWidth,
            imageHeight = landmarks.imageHeight,
        )
    }

    override fun detectWithOrientationFallback(bitmap: Bitmap): DetectedHand? {
        val oriented = detectLandmarksWithOrientationFallback(bitmap) ?: return null
        val anchors = NailLandmarkMapper.fromNormalizedLandmarks(
            landmarks = oriented.landmarks.points.map {
                NailLandmarkMapper.NormalizedPoint(it.x, it.y)
            },
            imageWidth = oriented.landmarks.imageWidth,
            imageHeight = oriented.landmarks.imageHeight,
        ) ?: return null
        return DetectedHand(bitmap = oriented.bitmap, anchors = anchors)
    }

    override fun detectLandmarks(bitmap: Bitmap): HandLandmarks? =
        detectLandmarksOnBitmap(bitmap)

    override fun detectLandmarksWithOrientationFallback(bitmap: Bitmap): OrientedHandLandmarks? {
        detectLandmarksOnBitmap(bitmap)?.let {
            return OrientedHandLandmarks(bitmap = bitmap, landmarks = it)
        }
        for (degrees in ROTATION_FALLBACKS) {
            val rotated = OrientedBitmapDecoder.rotate(bitmap, degrees)
            val landmarks = detectLandmarksOnBitmap(rotated)
            if (landmarks != null) {
                return OrientedHandLandmarks(bitmap = rotated, landmarks = landmarks)
            }
            if (rotated !== bitmap && !rotated.isRecycled) {
                rotated.recycle()
            }
        }
        return null
    }

    private fun detectLandmarksOnBitmap(bitmap: Bitmap): HandLandmarks? {
        // HandLandmarker IMAGE não é thread-safe: serializa init + detect.
        synchronized(this) {
            return runCatching {
                val marker = landmarkerUnlocked() ?: return null
                val working = prepareForInference(bitmap)
                val mpImage = BitmapImageBuilder(working).build()
                val result = marker.detect(mpImage)
                if (working !== bitmap && !working.isRecycled) {
                    working.recycle()
                }
                val landmarks = result.landmarks().firstOrNull() ?: return null
                if (landmarks.size < HandLandmarks.MIN_POINTS) return null
                val handedness = result.handednesses()
                    .firstOrNull()
                    ?.firstOrNull()
                    ?.categoryName()
                    ?.let { name ->
                        when (name.lowercase()) {
                            "left" -> Handedness.LEFT
                            "right" -> Handedness.RIGHT
                            else -> Handedness.UNKNOWN
                        }
                    } ?: Handedness.UNKNOWN
                val score = result.handednesses()
                    .firstOrNull()
                    ?.firstOrNull()
                    ?.score()
                    ?: 1f
                HandLandmarks(
                    points = landmarks.map {
                        ImageCoordinates.NormPoint(it.x(), it.y())
                    },
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    handedness = handedness,
                    presenceScore = score,
                )
            }.getOrNull()
        }
    }

    private fun prepareForInference(bitmap: Bitmap): Bitmap {
        val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        }
        val maxEdge = maxOf(argb.width, argb.height)
        if (maxEdge <= MAX_INFERENCE_EDGE) {
            return argb
        }
        val scale = MAX_INFERENCE_EDGE.toFloat() / maxEdge.toFloat()
        val w = (argb.width * scale).toInt().coerceAtLeast(1)
        val h = (argb.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(argb, w, h, true)
        if (argb !== bitmap && argb !== scaled && !argb.isRecycled) {
            argb.recycle()
        }
        return scaled
    }

    /** Chamar apenas sob `synchronized(this)`. */
    private fun landmarkerUnlocked(): HandLandmarker? {
        landmarker?.let { return it }
        return createLandmarker()
    }

    private fun createLandmarker(): HandLandmarker? {
        if (!modelAvailable()) {
            return null
        }
        return runCatching {
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_ASSET)
                        .build(),
                )
                .setRunningMode(RunningMode.IMAGE)
                .setNumHands(1)
                .setMinHandDetectionConfidence(MIN_CONFIDENCE)
                .setMinHandPresenceConfidence(MIN_CONFIDENCE)
                .setMinTrackingConfidence(MIN_CONFIDENCE)
                .build()
            HandLandmarker.createFromOptions(context, options).also { landmarker = it }
        }.getOrNull()
    }

    private fun modelAvailable(): Boolean = runCatching {
        context.assets.open(MODEL_ASSET).close()
        true
    }.getOrDefault(false)

    companion object {
        const val MODEL_ASSET = "hand_landmarker.task"
        private const val MIN_CONFIDENCE = 0.25f
        private const val MAX_INFERENCE_EDGE = 1280
        private val ROTATION_FALLBACKS = floatArrayOf(90f, 270f, 180f)
    }
}
