package br.com.unhasdequecor.data.local.hand

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceRejection
import br.com.unhasdequecor.domain.model.HandReferenceSaveOutcome
import br.com.unhasdequecor.domain.model.HandReferenceSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HandReferenceFileStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun persist(
        sourceAbsolutePath: String,
        capturedAtEpochMs: Long,
        source: HandReferenceSource = HandReferenceSource.USER,
        sampleId: String? = null,
    ): HandReferenceSaveOutcome {
        val validationError = validateSource(File(sourceAbsolutePath))
        return if (validationError != null) {
            HandReferenceSaveOutcome.Rejected(validationError)
        } else {
            writeHandJpeg(File(sourceAbsolutePath), capturedAtEpochMs, source, sampleId)
        }
    }

    fun copySampleAssetToCache(assetPath: String): File {
        val cacheDir = File(context.cacheDir, CACHE_DIR).also { it.mkdirs() }
        val safeName = assetPath.substringAfterLast('/').ifBlank { "sample.webp" }
        val target = File(cacheDir, "sample_$safeName")
        context.assets.open(assetPath).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
        return target
    }

    fun deleteStoredImage() {
        val directory = File(context.filesDir, DIRECTORY)
        if (directory.isDirectory) {
            directory.listFiles()?.forEach { it.delete() }
        }
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

    private fun validateSource(source: File): HandReferenceRejection? {
        if (!source.isFile) {
            return HandReferenceRejection.INVALID_IMAGE
        }
        if (source.length() > MAX_BYTES) {
            return HandReferenceRejection.TOO_LARGE
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        return when {
            bounds.outWidth <= 0 || bounds.outHeight <= 0 -> HandReferenceRejection.INVALID_IMAGE
            bounds.outWidth < MIN_DIMENSION || bounds.outHeight < MIN_DIMENSION ->
                HandReferenceRejection.TOO_SMALL
            else -> null
        }
    }

    private fun writeHandJpeg(
        source: File,
        capturedAtEpochMs: Long,
        referenceSource: HandReferenceSource,
        sampleId: String?,
    ): HandReferenceSaveOutcome = try {
        val bitmap = decodeSampledBitmap(source)
        if (bitmap == null) {
            HandReferenceSaveOutcome.Rejected(HandReferenceRejection.INVALID_IMAGE)
        } else {
            storeJpeg(bitmap, capturedAtEpochMs, referenceSource, sampleId).also {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    } catch (_: Exception) {
        HandReferenceSaveOutcome.Rejected(HandReferenceRejection.IO_ERROR)
    }

    private fun decodeSampledBitmap(source: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, TARGET_MAX_EDGE)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(source.absolutePath, decodeOptions)
    }

    private fun storeJpeg(
        bitmap: Bitmap,
        capturedAtEpochMs: Long,
        referenceSource: HandReferenceSource,
        sampleId: String?,
    ): HandReferenceSaveOutcome {
        val directory = File(context.filesDir, DIRECTORY).also { it.mkdirs() }
        // Path único a cada save: evita preview/cache preso no hand.jpg antigo.
        val destination = File(directory, "hand_$capturedAtEpochMs.jpg")
        val compressed = FileOutputStream(destination).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }
        return if (compressed) {
            purgeOldHandFiles(directory, keep = destination)
            HandReferenceSaveOutcome.Saved(
                HandReference(
                    localPath = destination.absolutePath,
                    capturedAtEpochMs = capturedAtEpochMs,
                    source = referenceSource,
                    sampleId = sampleId,
                ),
            )
        } else {
            destination.delete()
            HandReferenceSaveOutcome.Rejected(HandReferenceRejection.IO_ERROR)
        }
    }

    private fun purgeOldHandFiles(directory: File, keep: File) {
        directory.listFiles()?.forEach { file ->
            val isHandJpeg = file.name == LEGACY_FILE_NAME ||
                (file.name.startsWith("hand_") && file.name.endsWith(".jpg"))
            if (isHandJpeg && file.absolutePath != keep.absolutePath) {
                file.delete()
            }
        }
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

    companion object {
        const val DIRECTORY = "hand_reference"
        const val LEGACY_FILE_NAME = "hand.jpg"
        const val CACHE_DIR = "hand_capture"
        const val MIN_DIMENSION = 480
        const val MAX_BYTES = 15L * 1024L * 1024L
        const val TARGET_MAX_EDGE = 2048
        const val JPEG_QUALITY = 88
    }
}
