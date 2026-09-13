package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap

/**
 * Compatibility facade for the strict nail-plate boundary refiner.
 *
 * The strict implementation owns the production refinement rules. Keeping
 * this facade preserves existing callers while avoiding a second, divergent
 * refinement implementation.
 */
class NailPlateBoundaryRefiner {
    private val delegate = StrictNailPlateBoundaryRefiner()

    fun refine(image: Bitmap, roi: NailRoi, seedMask: NailMask): NailMask? =
        delegate.refine(image, roi, seedMask)
}
