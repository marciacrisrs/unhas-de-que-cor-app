package br.com.unhasdequecor.data.local.hand

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import br.com.unhasdequecor.di.IoDispatcher
import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceRejection
import br.com.unhasdequecor.domain.model.HandReferenceSaveOutcome
import br.com.unhasdequecor.domain.model.HandReferenceSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class HandReferenceFileStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
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

    suspend fun copySampleAssetToCache(assetPath: String): File = withContext(ioDispatcher) {
        val cacheDir = File(context.cacheDir, CACHE_DIR).also { it.mkdirs() }
        val safeName = assetPath.substringAfterLast('/').ifBlank { "sample.webp" }
        val target = File(cacheDir, "sample_$safeName")
        context.assets.open(assetPath).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
        target
    }

    fun deleteStoredImage() {
        val directory = File(context.filesDir, DIRECTORY)
        if (directory.isDirectory) {
            directory.listFiles()?.forEach(::deleteQuietly)
        }
        deleteQuietly(directory)
    }

    suspend fun copyUriStreamToCache(
        input: java.io.InputStream,
        fileName: String = "import.jpg",
    ): File = withContext(ioDispatcher) {
        val cacheDir = File(context.cacheDir, CACHE_DIR).also { it.mkdirs() }
        val target = File(cacheDir, fileName)
        FileOutputStream(target).use { output ->
            input.copyTo(output)
        }
        target
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
        if (!isAllowedSourcePath(source)) {
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

    /** Defesa em profundidade: só aceita arquivos sob cache de captura ou pasta da mão. */
    private fun isAllowedSourcePath(source: File): Boolean {
        val canonical = runCatching { source.canonicalFile }.getOrNull() ?: return false
        val captureRoot = File(context.cacheDir, CACHE_DIR).canonicalFile
        val handRoot = File(context.filesDir, DIRECTORY).canonicalFile
        val path = canonical.path
        return path.startsWith(captureRoot.path + File.separator) ||
            path.startsWith(handRoot.path + File.separator) ||
            path == handRoot.path
    }

    suspend fun clearCaptureCache() = withContext(ioDispatcher) {
        clearCaptureCacheNow()
    }

    /** Limpeza síncrona para teardown de ViewModel (`onCleared`). */
    fun clearCaptureCacheNow() {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        if (!cacheDir.isDirectory) return
        cacheDir.listFiles()?.forEach(::deleteQuietly)
    }

    /** Best-effort delete: usa o Boolean de [File.delete] (Sonar S899). */
    private fun deleteQuietly(file: File) {
        if (file.exists() && !file.delete() && file.exists()) {
            // Arquivo ainda presente (ex.: lock); limpeza fica para o próximo ciclo/OS.
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

    private fun decodeSampledBitmap(source: File): Bitmap? =
        OrientedBitmapDecoder.decodeFile(source.absolutePath, maxEdge = TARGET_MAX_EDGE)

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
            // Não apaga o JPEG anterior aqui: o DataStore ainda aponta para o path antigo
            // até preferences.save. A limpeza roda depois, em purgeObsoleteHandFiles.
            HandReferenceSaveOutcome.Saved(
                HandReference(
                    localPath = destination.absolutePath,
                    capturedAtEpochMs = capturedAtEpochMs,
                    source = referenceSource,
                    sampleId = sampleId,
                ),
            )
        } else {
            deleteQuietly(destination)
            HandReferenceSaveOutcome.Rejected(HandReferenceRejection.IO_ERROR)
        }
    }

    fun purgeObsoleteHandFiles(keepAbsolutePath: String) {
        val directory = File(context.filesDir, DIRECTORY)
        if (!directory.isDirectory) return
        val keep = File(keepAbsolutePath)
        directory.listFiles()?.forEach { file ->
            val isHandJpeg = file.name == LEGACY_FILE_NAME ||
                (file.name.startsWith("hand_") && file.name.endsWith(".jpg"))
            if (isHandJpeg && file.absolutePath != keep.absolutePath) {
                deleteQuietly(file)
            }
        }
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
