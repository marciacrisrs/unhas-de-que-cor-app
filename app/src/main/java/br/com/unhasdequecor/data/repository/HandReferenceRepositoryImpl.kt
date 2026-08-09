package br.com.unhasdequecor.data.repository

import br.com.unhasdequecor.data.local.hand.HandReferenceFileStore
import br.com.unhasdequecor.data.local.hand.HandReferencePreferencesDataSource
import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceSaveOutcome
import br.com.unhasdequecor.domain.model.HandReferenceSource
import br.com.unhasdequecor.domain.repository.HandReferenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HandReferenceRepositoryImpl @Inject constructor(
    private val preferences: HandReferencePreferencesDataSource,
    private val fileStore: HandReferenceFileStore,
) : HandReferenceRepository {

    override fun observe(): Flow<HandReference?> = preferences.observe().map { reference ->
        if (reference != null && fileStore.fileExists(reference.localPath)) {
            reference
        } else {
            null
        }
    }

    override suspend fun save(
        sourceAbsolutePath: String,
        capturedAtEpochMs: Long,
        source: HandReferenceSource,
    ): HandReferenceSaveOutcome = withContext(Dispatchers.IO) {
        when (val outcome = fileStore.persist(sourceAbsolutePath, capturedAtEpochMs, source)) {
            is HandReferenceSaveOutcome.Saved -> {
                preferences.save(outcome.reference)
                outcome
            }
            is HandReferenceSaveOutcome.Rejected -> outcome
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        fileStore.deleteStoredImage()
        preferences.clear()
    }
}
