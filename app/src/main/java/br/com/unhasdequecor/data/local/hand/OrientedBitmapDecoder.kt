package br.com.unhasdequecor.data.local.hand

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Decodifica JPEG/PNG aplicando a orientação EXIF (câmera costuma gravar deitada).
 */
object OrientedBitmapDecoder {
    fun decodeFile(path: String, maxEdge: Int = 0): Bitmap? {
        val file = File(path)
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = if (maxEdge > 0) {
                calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            } else {
                1
            }
        }
        val raw = BitmapFactory.decodeFile(path, options) ?: return null
        return applyExifOrientation(raw, path)
    }

    fun applyExifOrientation(bitmap: Bitmap, path: String): Bitmap {
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        return rotated
    }

    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var inSampleSize = 1
        val halfWidth = width / 2
        val halfHeight = height / 2
        while (halfWidth / inSampleSize >= maxEdge && halfHeight / inSampleSize >= maxEdge) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
