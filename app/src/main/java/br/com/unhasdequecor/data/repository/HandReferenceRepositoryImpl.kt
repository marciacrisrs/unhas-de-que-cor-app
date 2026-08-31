package br.com.unhasdequecor.data.repository

import android.content.Context
import android.net.Uri
import br.com.unhasdequecor.data.local.hand.HandReferenceFileStore
import br.com.unhasdequecor.data.local.hand.HandReferencePreferencesDataSource
import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceSaveOutcome
import br.com.unhasdequecor.domain.model.HandReferenceSource
import br.com.unhasdequecor.domain.model.HandSampleCatalog
import br.com.unhasdequecor.domain.repository.HandReferenceRepository
import br.com.unhasdequecor.domain.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HandReferenceRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: HandReferencePreferencesDataSource,
    private val fileStore: HandReferenceFileStore,
    private val clock: Clock,
) : HandReferenceRepository {

    /**
     * Serializes persist + DataStore + purge. Sem isso, dois saves simultâneos
     * (ex.: double-tap no picker) apontam o DataStore para um JPEG que o outro
     * save já apagou em [HandReferenceFileStore.purgeObsoleteHandFiles].
     */
    private val writeMutex = Mutex()

    override fun observe(): Flow<HandReference?> = preferences.observe().map { reference ->
        when {
            reference == null -> null
            fileStore.fileExists(reference.localPath) -> reference
            else -> {
                // Backup/restore sem a foto: path no DataStore fica órfão.
                // emitimos null; ensureDefaultSample reinsere a amostra.
                null
            }
        }
    }

    override suspend fun save(
        sourceAbsolutePath: String,
        capturedAtEpochMs: Long,
        source: HandReferenceSource,
        sampleId: String?,
    ): HandReferenceSaveOutcome = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            saveUnlocked(
                sourceAbsolutePath = sourceAbsolutePath,
                capturedAtEpochMs = capturedAtEpochMs,
                source = source,
                sampleId = sampleId,
            )
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            fileStore.deleteStoredImage()
            preferences.clear()
        }
    }

    override suspend fun ensureDefaultSample(): HandReference? = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val current = observe().first()
            if (current != null) {
                current
            } else {
                persistDefaultSampleUnlocked()
            }
        }
    }

    override suspend fun resetToDefaultSample(): HandReference? = withContext(Dispatchers.IO) {
        // Salva a amostra diretamente (sem preferences.clear) para o Flow não emitir null.
        writeMutex.withLock { persistDefaultSampleUnlocked() }
    }

    override suspend fun stageFromContentUri(uriString: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                fileStore.copyUriStreamToCache(input)
            }
        }.getOrNull()?.absolutePath
    }

    override suspend fun stageSampleAsset(assetPath: String): String? = withContext(Dispatchers.IO) {
        runCatching { fileStore.copySampleAssetToCache(assetPath) }.getOrNull()?.absolutePath
    }

    override fun createCameraCapturePath(): String = fileStore.createCameraCaptureFile().absolutePath

    override suspend fun clearStagingCache() = fileStore.clearCaptureCache()

    override fun clearStagingCacheNow() = fileStore.clearCaptureCacheNow()

    private suspend fun persistDefaultSampleUnlocked(): HandReference? {
        val sample = HandSampleCatalog.defaultOption
        val prepared = fileStore.copySampleAssetToCache(sample.assetPath)
        return when (
            val outcome = saveUnlocked(
                sourceAbsolutePath = prepared.absolutePath,
                capturedAtEpochMs = clock.now(),
                source = HandReferenceSource.SAMPLE,
                sampleId = sample.id,
            )
        ) {
            is HandReferenceSaveOutcome.Saved -> outcome.reference
            is HandReferenceSaveOutcome.Rejected -> null
        }
    }

    private suspend fun saveUnlocked(
        sourceAbsolutePath: String,
        capturedAtEpochMs: Long,
        source: HandReferenceSource,
        sampleId: String?,
    ): HandReferenceSaveOutcome {
        val outcome = fileStore.persist(
            sourceAbsolutePath = sourceAbsolutePath,
            capturedAtEpochMs = capturedAtEpochMs,
            source = source,
            sampleId = sampleId,
        )
        when (outcome) {
            is HandReferenceSaveOutcome.Saved -> {
                preferences.save(outcome.reference)
                // Só depois do DataStore apontar para o path novo.
                fileStore.purgeObsoleteHandFiles(outcome.reference.localPath)
            }
            is HandReferenceSaveOutcome.Rejected -> Unit
        }
        // Staging (câmera/galeria/amostra) já foi consumido pelo persist acima.
        fileStore.clearCaptureCache()
        return outcome
    }
}
