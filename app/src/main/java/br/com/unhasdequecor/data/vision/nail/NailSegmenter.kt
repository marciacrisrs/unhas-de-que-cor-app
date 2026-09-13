package br.com.unhasdequecor.data.vision.nail

import android.graphics.Bitmap

/**
 * Segmenta a unha dentro de uma ROI.
 * A ROI/MediaPipe fornece apenas o prior espacial; a implementação deve
 * recuperar a lâmina ungueal a partir da evidência da imagem.
 */
fun interface NailSegmenter {
    fun segment(image: Bitmap, roi: NailRoi): NailMask?
}
