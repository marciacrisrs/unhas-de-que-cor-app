package br.com.unhasdequecor.data.vision

import android.graphics.Bitmap

data class OrientedHandLandmarks(
    val bitmap: Bitmap,
    val landmarks: HandLandmarks,
)

interface HandLandmarkProcessor {
    fun detectLandmarks(bitmap: Bitmap): HandLandmarks?

    /**
     * Live frames favor latency: the caller may choose a cheaper path that
     * avoids photo-oriented enhancement/rotation fan-out on every frame.
     * Implementations should keep this path semantically equivalent for a
     * normally oriented frame and reserve expensive recovery for weak frames.
     */
    fun detectLandmarksForLive(bitmap: Bitmap): OrientedHandLandmarks? {
        val landmarks = detectLandmarks(bitmap) ?: return null
        return OrientedHandLandmarks(bitmap = bitmap, landmarks = landmarks)
    }

    fun detectLandmarksWithOrientationFallback(bitmap: Bitmap): OrientedHandLandmarks? {
        val landmarks = detectLandmarks(bitmap) ?: return null
        return OrientedHandLandmarks(bitmap = bitmap, landmarks = landmarks)
    }
}
