package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production nail-plate segmenter backed by the learned TFLite segmenter.
 *
 * TfliteNailPlateSegmenter applies the strict boundary refinement internally,
 * so this production facade must not refine the same mask a second time.
 */
@Singleton
class StrictTfliteNailPlateSegmenter @Inject constructor(
    private val learnedSegmenter: TfliteNailPlateSegmenter,
) : NailSegmenter {
    override fun segment(image: Bitmap, roi: NailRoi): NailMask? =
        learnedSegmenter.segment(image, roi)
}
