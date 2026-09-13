package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap

/**
 * Compatibility facade for the strict nail-plate completion stage.
 *
 * MediaPipe remains localization only. The learned mask is the seed; this
 * stage completes the visible plate using the finger axis and local appearance
 * while keeping a conservative skin rejection rule. A final contour
 * regularization pass removes raster-scale spikes without inventing coverage.
 */
class NailPlateBoundaryRefiner {
    private val delegate = StrictNailPlateBoundaryRefiner()
    private val contourRegularizer = NailContourRegularizer()

    fun refine(image: Bitmap, roi: NailRoi, seedMask: NailMask): NailMask? =
        delegate.refine(image, roi, seedMask)?.let { contourRegularizer.regularize(roi, it) }
}
