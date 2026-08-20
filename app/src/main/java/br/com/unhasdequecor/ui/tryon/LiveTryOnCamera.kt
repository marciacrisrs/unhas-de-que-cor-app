package br.com.unhasdequecor.ui.tryon

/**
 * Escolha da lente do Live Try-On: frente quando existir, senão traseira.
 * Sem isso, [androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA] derruba
 * o app em tablets/emuladores só com câmera traseira.
 */
internal enum class LiveTryOnLens {
    FRONT,
    BACK,
}

internal object LiveTryOnCamera {
    fun lens(hasFront: Boolean, hasBack: Boolean): LiveTryOnLens? = when {
        hasFront -> LiveTryOnLens.FRONT
        hasBack -> LiveTryOnLens.BACK
        else -> null
    }

    fun shouldMirror(lens: LiveTryOnLens): Boolean = lens == LiveTryOnLens.FRONT
}
