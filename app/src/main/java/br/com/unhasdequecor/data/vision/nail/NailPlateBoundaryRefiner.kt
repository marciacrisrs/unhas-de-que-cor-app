package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap

/** Conservative facade for the learned mask with bounded anatomical completion. */
class NailPlateBoundaryRefiner {
    private val delegate = StrictNailPlateBoundaryRefiner()

    fun refine(image: Bitmap, roi: NailRoi, seedMask: NailMask): NailMask? =
        delegate.refine(image, roi, seedMask)
}
