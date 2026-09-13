package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap

/**
 * Conservative nail-plate refinement facade.
 *
 * MediaPipe remains localization only and the learned segmentation stays the
 * source of truth for the visible plate. The strict stage may recover pixels
 * that are confidently missing, while the regularizer removes small raster
 * irregularities without inventing a new image-driven contour.
 *
 * The edge specialist is intentionally not part of the live path: local image
 * gradients can lock the boundary onto skin texture, reflections and laptop
 * edges, producing the artificial waves seen in the try-on preview.
 */
class NailPlateBoundaryRefiner {
    private val delegate = StrictNailPlateBoundaryRefiner()
    private val contourRegularizer = NailContourRegularizer()

    fun refine(image: Bitmap, roi: NailRoi, seedMask: NailMask): NailMask? =
        delegate.refine(image, roi, seedMask)?.let { completed ->
            contourRegularizer.regularize(roi, completed)
        }
}
