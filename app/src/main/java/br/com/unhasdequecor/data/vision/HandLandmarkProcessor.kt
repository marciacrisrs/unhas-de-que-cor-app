package br.com.unhasdequecor.data.vision

import android.graphics.Bitmap

data class OrientedHandLandmarks(
    val bitmap: Bitmap,
    val landmarks: HandLandmarks,
)

interface HandLandmarkProcessor {
    fun detectLandmarks(bitmap: Bitmap): HandLandmarks?

    fun detectLandmarksWithOrientationFallback(bitmap: Bitmap): OrientedHandLandmarks? {
        val landmarks = detectLandmarks(bitmap) ?: return null
        return OrientedHandLandmarks(bitmap = bitmap, landmarks = landmarks)
    }
}
