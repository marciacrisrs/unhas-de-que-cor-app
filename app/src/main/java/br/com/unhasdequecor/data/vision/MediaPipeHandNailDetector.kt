package br.com.unhasdequecor.data.vision

import android.content.Context
import android.graphics.Bitmap
import br.com.unhasdequecor.data.local.hand.OrientedBitmapDecoder
import br.com.unhasdequecor.ui.components.NailLandmarkMapper
import br.com.unhasdequecor.ui.components.NailOverlayAnchor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface HandNailDetector {
    fun detect(bitmap: Bitmap): List<NailOverlayAnchor>?

    /**
     * Tenta a bitmap e, se falhar, rotações 90/180/270 (fotos salvas sem EXIF).
     * Devolve a bitmap na orientação em que a mão foi detectada + âncoras.
     */
    fun detectWithOrientationFallback(bitmap: Bitmap): DetectedHand? {
        val anchors = detect(bitmap) ?: return null
        return DetectedHand(bitmap = bitmap, anchors = anchors)
    }
}

data class DetectedHand(
    val bitmap: Bitmap,
    val anchors: List<NailOverlayAnchor>,
)

@Singleton
class MediaPipeHandNailDetector @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : HandNailDetector {

    @Volatile
    private var landmarker: HandLandmarker? = null

    override fun detect(bitmap: Bitmap): List<NailOverlayAnchor>? =
        detectOnBitmap(bitmap)

    override fun detectWithOrientationFallback(bitmap: Bitmap): DetectedHand? {
        detectOnBitmap(bitmap)?.let { anchors ->
            return DetectedHand(bitmap = bitmap, anchors = anchors)
        }
        for (degrees in ROTATION_FALLBACKS) {
            val rotated = OrientedBitmapDecoder.rotate(bitmap, degrees)
            val anchors = detectOnBitmap(rotated)
            if (anchors != null) {
                if (rotated !== bitmap && !bitmap.isRecycled) {
                    // Mantém a rotacionada para o preview; a original pode ser reciclada pelo caller.
                }
                return DetectedHand(bitmap = rotated, anchors = anchors)
            }
            if (rotated !== bitmap && !rotated.isRecycled) {
                rotated.recycle()
            }
        }
        return null
    }

    private fun detectOnBitmap(bitmap: Bitmap): List<NailOverlayAnchor>? {
        return runCatching {
            val marker = landmarker() ?: return null
            val working = prepareForInference(bitmap)
            val mpImage = BitmapImageBuilder(working).build()
            val result = marker.detect(mpImage)
            if (working !== bitmap && !working.isRecycled) {
                working.recycle()
            }
            val landmarks = result.landmarks().firstOrNull() ?: return null
            NailLandmarkMapper.fromNormalizedLandmarks(
                landmarks = landmarks.map {
                    NailLandmarkMapper.NormalizedPoint(it.x(), it.y())
                },
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
            )
        }.getOrNull()
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

    private fun landmarker(): HandLandmarker? {
        landmarker?.let { return it }
        synchronized(this) {
            landmarker?.let { return it }
            return createLandmarker()
        }
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
