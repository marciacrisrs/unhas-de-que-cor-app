package br.com.unhasdequecor.data.vision

import android.graphics.Bitmap
import br.com.unhasdequecor.data.vision.nail.NailOverlayAnchor

/**
 * Detecta âncoras de unhas a partir de uma foto (legado / amostras).
 * O try-on do usuário usa [HandLandmarkProcessor] + pipeline de unhas.
 */
interface HandNailDetector {
    fun detect(bitmap: Bitmap): List<NailOverlayAnchor>?

    /**
     * Tenta a bitmap e, se falhar, rotações 90/180/270 (fotos salvas sem EXIF).
     * Devolve a bitmap na orientação em que a mão foi detectada + âncoras.
     */
    fun detectWithOrientationFallback(bitmap: Bitmap): DetectedHand? {
        val anchors = detect(bitmap) ?: return null
        return DetectedHand(bitmap = bitmap, anchors = anchors)
    }
}

data class DetectedHand(
    val bitmap: Bitmap,
    val anchors: List<NailOverlayAnchor>,
)
