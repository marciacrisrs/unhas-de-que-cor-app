package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production nail-plate segmenter: learned localization followed by strict
 * plate expansion and skin rejection.
 */
@Singleton
class StrictTfliteNailPlateSegmenter @Inject constructor(
    private val learnedSegmenter: TfliteNailPlateSegmenter,
    private val boundaryRefiner: StrictNailPlateBoundaryRefiner,
) : NailSegmenter {
    override fun segment(image: Bitmap, roi: NailRoi): NailMask? {
        val learned = learnedSegmenter.segment(image, roi) ?: return null
        return boundaryRefiner.refine(image, roi, learned)
    }
}
