package br.com.unhasdequecor.data.local.hand

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceRejection
import br.com.unhasdequecor.domain.model.HandReferenceSaveOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HandReferenceFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun persist(
        sourceAbsolutePath: String,
        capturedAtEpochMs: Long,
    ): HandReferenceSaveOutcome {
        val source = File(sourceAbsolutePath)
        if (!source.isFile) {
            return HandReferenceSaveOutcome.Rejected(HandReferenceRejection.INVALID_IMAGE)
        }
        if (source.length() > MAX_BYTES) {
            return HandReferenceSaveOutcome.Rejected(HandReferenceRejection.TOO_LARGE)
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return HandReferenceSaveOutcome.Rejected(HandReferenceRejection.INVALID_IMAGE)
        }
        if (bounds.outWidth < MIN_DIMENSION || bounds.outHeight < MIN_DIMENSION) {
            return HandReferenceSaveOutcome.Rejected(HandReferenceRejection.TOO_SMALL)
        }

        return try {
            val directory = File(context.filesDir, DIRECTORY).also { it.mkdirs() }
            val destination = File(directory, FILE_NAME)
            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, TARGET_MAX_EDGE)
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(source.absolutePath, decodeOptions)
                ?: return HandReferenceSaveOutcome.Rejected(HandReferenceRejection.INVALID_IMAGE)

            FileOutputStream(destination).use { output ->
                val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                if (!compressed) {
                    return HandReferenceSaveOutcome.Rejected(HandReferenceRejection.IO_ERROR)
                }
            }
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }

            HandReferenceSaveOutcome.Saved(
                HandReference(
                    localPath = destination.absolutePath,
                    capturedAtEpochMs = capturedAtEpochMs,
                ),
            )
        } catch (_: Exception) {
            HandReferenceSaveOutcome.Rejected(HandReferenceRejection.IO_ERROR)
        }
    }

    fun deleteStoredImage() {
        val directory = File(context.filesDir, DIRECTORY)
        File(directory, FILE_NAME).delete()
        directory.delete()
    }

    fun copyUriStreamToCache(input: java.io.InputStream, fileName: String = "import.jpg"): File {
        val cacheDir = File(context.cacheDir, CACHE_DIR).also { it.mkdirs() }
        val target = File(cacheDir, fileName)
        FileOutputStream(target).use { output ->
            input.copyTo(output)
        }
        return target
    }

    fun createCameraCaptureFile(): File {
        val cacheDir = File(context.cacheDir, CACHE_DIR).also { it.mkdirs() }
        return File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
    }

    fun fileExists(path: String): Boolean = File(path).isFile

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var inSampleSize = 1
        var halfWidth = width / 2
        var halfHeight = height / 2
        while (halfWidth / inSampleSize >= maxEdge && halfHeight / inSampleSize >= maxEdge) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    companion object {
        const val DIRECTORY = "hand_reference"
        const val FILE_NAME = "hand.jpg"
        const val CACHE_DIR = "hand_capture"
        const val MIN_DIMENSION = 480
        const val MAX_BYTES = 15L * 1024L * 1024L
        const val TARGET_MAX_EDGE = 2048
        const val JPEG_QUALITY = 88
    }
}
