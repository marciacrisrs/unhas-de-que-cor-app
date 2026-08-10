package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap

/**
 * Segmenta a unha dentro de uma ROI.
 * Implementação atual: geométrica + refinamento por cor.
 * Futuro: modelo ML sem mudar o restante do pipeline.
 */
fun interface NailSegmenter {
    fun segment(image: Bitmap, roi: NailRoi): NailMask?
}
