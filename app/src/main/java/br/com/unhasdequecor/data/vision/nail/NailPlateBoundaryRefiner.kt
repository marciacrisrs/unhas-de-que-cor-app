package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap

/** Completes the learned seed with an image-guided, globally coherent contour. */
class NailPlateBoundaryRefiner {
    private val delegate = StrictNailPlateBoundaryRefiner()
    private val contourSpecialist = NailContourEdgeSpecialist()
    private val contourRasterizer = NailContourRasterizer()

    fun refine(image: Bitmap, roi: NailRoi, seedMask: NailMask): NailMask? =
        delegate.refine(image, roi, seedMask)?.let { completed ->
            val traced = contourSpecialist.refine(image, roi, completed)
            contourRasterizer.rasterize(traced)
        }
}
