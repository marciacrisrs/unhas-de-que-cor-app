package br.com.unhasdequecor.data.vision.nail

/**
 * Compatibility pass-through for the learned segmentation path.
 *
 * The visual baseline is now the learned nail segmentation model itself.
 * Classical color/geometry expansion is intentionally not part of this path:
 * the model output must be evaluated on its own before another heuristic is
 * allowed to change the physical nail boundary.
 */
class NailPlateBoundaryRefiner {
    fun refine(image: android.graphics.Bitmap, roi: NailRoi, seedMask: NailMask): NailMask? = seedMask
}
