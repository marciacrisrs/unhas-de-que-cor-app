package br.com.unhasdequecor.domain.repository

import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceSaveOutcome
import kotlinx.coroutines.flow.Flow

interface HandReferenceRepository {
    fun observe(): Flow<HandReference?>

    suspend fun save(
        sourceAbsolutePath: String,
        capturedAtEpochMs: Long,
    ): HandReferenceSaveOutcome

    suspend fun clear()
}
