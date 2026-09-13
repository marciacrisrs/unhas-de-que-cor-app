package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap

/** Completes a conservative learned seed using axis-aware plate evidence. */
class NailPlateBoundaryRefiner {
    private val delegate = StrictNailPlateBoundaryRefiner()

    fun refine(image: Bitmap, roi: NailRoi, seedMask: NailMask): NailMask? =
        delegate.refine(image, roi, seedMask)
}
