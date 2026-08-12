package br.com.unhasdequecor.data.vision

import android.content.Context
import android.graphics.Bitmap
import br.com.unhasdequecor.data.vision.nail.DetectionConfidenceFloor
import br.com.unhasdequecor.data.vision.nail.ImageCoordinates
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detecção de mão via MediaPipe Hand Landmarker (IMAGE).
 * Expõe landmarks brutos ([HandLandmarkProcessor]) para o pipeline de try-on.
 *
 * Em fotos difíceis (contraluz, mão escura, horizontal), avalia variantes
 * (contraste/gamma/brilho/espelho/rotação) e escolhe a de maior
 * [HandLandmarkQuality.rankingScore] (presence + span das tips) — não fica na
 * primeira detecção fraca nem na primeira presence só “forte”.
 */
@Singleton
class MediaPipeHandNailDetector @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : HandLandmarkProcessor {

    @Volatile
    private var landmarker: HandLandmarker? = null

    override fun detectLandmarks(bitmap: Bitmap): HandLandmarks? =
        detectLandmarksOnBitmap(bitmap, remap = { it })

    override fun detectLandmarksWithOrientationFallback(bitmap: Bitmap): OrientedHandLandmarks? {
        val created = ArrayList<Bitmap>(16)
        var best: OrientedHandLandmarks? = null
        var bestLandmarks: HandLandmarks? = null
        try {
            for (variant in HandInferenceVariants.forSource(bitmap)) {
                if (variant.inferenceBitmap !== bitmap &&
                    variant.inferenceBitmap !== variant.displayBitmap
                ) {
                    created += variant.inferenceBitmap
                }
                if (variant.displayBitmap !== bitmap) {
                    created += variant.displayBitmap
                }
                val landmarks = detectLandmarksOnBitmap(
                    bitmap = variant.inferenceBitmap,
                    displayWidth = variant.displayBitmap.width,
                    displayHeight = variant.displayBitmap.height,
                    remap = variant.remapPoint,
                )
                val (nextBest, stop) = HandLandmarkQuality.consider(bestLandmarks, landmarks)
                if (landmarks != null && nextBest === landmarks) {
                    bestLandmarks = landmarks
                    best = OrientedHandLandmarks(
                        bitmap = variant.displayBitmap,
                        landmarks = landmarks,
                    )
                }
                if (stop) {
                    break
                }
            }
            // Mantém o display vencedor; o finally recicla o restante.
            best?.bitmap?.let { winner -> created.removeAll { it === winner } }
            return best
        } finally {
            for (bmp in created.distinct()) {
                if (bmp !== bitmap && bmp !== best?.bitmap && !bmp.isRecycled) {
                    bmp.recycle()
                }
            }
        }
    }

    private fun detectLandmarksOnBitmap(
        bitmap: Bitmap,
        displayWidth: Int = bitmap.width,
        displayHeight: Int = bitmap.height,
        remap: (ImageCoordinates.NormPoint) -> ImageCoordinates.NormPoint,
    ): HandLandmarks? {
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
                val allHands = result.landmarks()
                if (allHands.isEmpty()) return null
                val handednessLists = result.handednesses()
                var bestIndex = 0
                var bestScore = -1f
                for (i in allHands.indices) {
                    val handLandmarks = allHands[i]
                    if (handLandmarks.size < HandLandmarks.MIN_POINTS) continue
                    val handScore = handednessLists.getOrNull(i)
                        ?.firstOrNull()
                        ?.score()
                        ?: 0f
                    val tipScore = averageTipPresence(handLandmarks)
                    val combined = HandPresenceScoring.score(handScore, tipScore)
                    if (combined > bestScore) {
                        bestScore = combined
                        bestIndex = i
                    }
                }
                val landmarks = allHands.getOrNull(bestIndex) ?: return null
                if (landmarks.size < HandLandmarks.MIN_POINTS) return null
                val handednessCat = handednessLists.getOrNull(bestIndex)?.firstOrNull()
                val handedness = when (handednessCat?.categoryName()?.lowercase()) {
                    "left" -> Handedness.LEFT
                    "right" -> Handedness.RIGHT
                    else -> Handedness.UNKNOWN
                }
                val handednessScore = handednessCat?.score() ?: 0f
                val tipPresence = averageTipPresence(landmarks)
                val presenceScore = HandPresenceScoring.score(handednessScore, tipPresence)
                HandLandmarks(
                    points = landmarks.map {
                        remap(ImageCoordinates.NormPoint(it.x(), it.y()))
                    },
                    imageWidth = displayWidth,
                    imageHeight = displayHeight,
                    handedness = handedness,
                    presenceScore = presenceScore,
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
                .setNumHands(2)
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

    private fun averageTipPresence(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
    ): Float {
        var sum = 0f
        var count = 0
        for (idx in HandLandmarks.TIP_INDICES) {
            if (idx >= landmarks.size) continue
            val optional = landmarks[idx].presence()
            if (optional.isPresent) {
                sum += optional.get()
                count += 1
            }
        }
        return if (count == 0) 0f else sum / count
    }

    companion object {
        const val MODEL_ASSET = "hand_landmarker.task"
        /** Mais permissivo: fotos com contraluz / mão retinta falhavam em 0.20. */
        private const val MIN_CONFIDENCE = DetectionConfidenceFloor.MEDIAPIPE_MIN
        private const val MAX_INFERENCE_EDGE = 1280
    }
}
