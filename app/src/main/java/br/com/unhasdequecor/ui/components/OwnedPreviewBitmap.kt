package br.com.unhasdequecor.ui.components

import android.graphics.Bitmap

/**
 * Ownership of a still try-on preview bitmap.
 *
 * The decoded photo and detection working copy are owned by the base-assets
 * disposer. A preview must copy those buffers before it can recycle on dispose.
 * If the copy fails (OOM) or the source is already recycled, the preview may
 * alias the protected bitmap but must **not** recycle it.
 */
internal data class OwnedPreviewBitmap(
    val bitmap: Bitmap,
    val owned: Boolean,
)

internal fun tryOnAssetsUsable(decoded: Bitmap, working: Bitmap?): Boolean =
    !decoded.isRecycled && working?.isRecycled != true

internal fun claimPreviewBitmap(
    candidate: Bitmap,
    protected: List<Bitmap>,
    copy: (Bitmap) -> Bitmap? = ::copyPreviewBitmap,
): OwnedPreviewBitmap {
    if (candidate.isRecycled) {
        return OwnedPreviewBitmap(candidate, owned = false)
    }
    if (protected.none { it === candidate }) {
        return OwnedPreviewBitmap(candidate, owned = true)
    }
    val duplicated = copy(candidate)
    return if (duplicated != null && duplicated !== candidate && !duplicated.isRecycled) {
        OwnedPreviewBitmap(duplicated, owned = true)
    } else {
        OwnedPreviewBitmap(candidate, owned = false)
    }
}

internal fun recycleIfOwned(bitmap: Bitmap?, owned: Boolean) {
    if (owned) recyclePreviewBitmapQuietly(bitmap)
}

internal fun recyclePreviewBitmapQuietly(bitmap: Bitmap?) {
    if (bitmap != null && !bitmap.isRecycled) {
        runCatching { bitmap.recycle() }
    }
}

private fun copyPreviewBitmap(source: Bitmap): Bitmap? {
    if (source.isRecycled) return null
    return source.copy(Bitmap.Config.ARGB_8888, false)
}
