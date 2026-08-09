package br.com.unhasdequecor.data.vision

import android.content.Context
import android.graphics.Bitmap
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
}

@Singleton
class MediaPipeHandNailDetector @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : HandNailDetector {

    @Volatile
    private var landmarker: HandLandmarker? = null

    override fun detect(bitmap: Bitmap): List<NailOverlayAnchor>? {
        return runCatching {
            val marker = landmarker() ?: return null
            val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) {
                bitmap
            } else {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            }
            val mpImage = BitmapImageBuilder(argb).build()
            val result = marker.detect(mpImage)
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

    private fun landmarker(): HandLandmarker? {
        landmarker?.let { return it }
        synchronized(this) {
            landmarker?.let { return it }
            if (!modelAvailable()) {
                return null
            }
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
            return HandLandmarker.createFromOptions(context, options).also { landmarker = it }
        }
    }

    private fun modelAvailable(): Boolean = runCatching {
        context.assets.open(MODEL_ASSET).close()
        true
    }.getOrDefault(false)

    companion object {
        const val MODEL_ASSET = "hand_landmarker.task"
        private const val MIN_CONFIDENCE = 0.45f
    }
}
