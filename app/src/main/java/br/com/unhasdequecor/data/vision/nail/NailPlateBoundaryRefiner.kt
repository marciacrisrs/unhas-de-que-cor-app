package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap

/**
 * Compatibility facade for the strict nail-plate completion stage.
 *
 * MediaPipe remains localization only. The learned mask is the seed; this
 * stage completes the visible plate using the finger axis and local appearance
 * while keeping a conservative skin rejection rule. The contour specialist
 * then locks the final boundary to local image evidence.
 */
class NailPlateBoundaryRefiner {
    private val delegate = StrictNailPlateBoundaryRefiner()
    private val contourRegularizer = NailContourRegularizer()
    private val contourEdgeSpecialist = NailContourEdgeSpecialist()

    fun refine(image: Bitmap, roi: NailRoi, seedMask: NailMask): NailMask? =
        delegate.refine(image, roi, seedMask)?.let { completed ->
            val regularized = contourRegularizer.regularize(roi, completed)
            contourEdgeSpecialist.refine(image, roi, regularized)
        }
}
