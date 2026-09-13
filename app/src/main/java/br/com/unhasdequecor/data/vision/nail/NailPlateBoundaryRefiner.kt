package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap

/**
 * Conservative facade for the nail-plate segmentation result.
 *
 * MediaPipe is localization only. The learned segmentation is the source of
 * truth for the visible plate. Geometry and image-edge stages are intentionally
 * bypassed here: they were able to synthesize coverage and introduce artificial
 * scallops along otherwise valid learned boundaries.
 *
 * A future refinement stage must be allowed to improve the learned boundary
 * only when it can demonstrate better plate evidence without expanding onto
 * periungual skin.
 */
class NailPlateBoundaryRefiner {
    fun refine(image: Bitmap, roi: NailRoi, seedMask: NailMask): NailMask? {
        @Suppress("UNUSED_PARAMETER")
        val ignoredImage = image
        @Suppress("UNUSED_PARAMETER")
        val ignoredRoi = roi
        return seedMask.takeIf { it.alpha.any { alpha -> (alpha.toInt() and 255) > 0 } }
    }
}
