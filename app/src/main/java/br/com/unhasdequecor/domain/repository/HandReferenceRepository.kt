package br.com.unhasdequecor.domain.repository

import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceSaveOutcome
import br.com.unhasdequecor.domain.model.HandReferenceSource
import kotlinx.coroutines.flow.Flow

interface HandReferenceRepository {
    fun observe(): Flow<HandReference?>

    suspend fun save(
        sourceAbsolutePath: String,
        capturedAtEpochMs: Long,
        source: HandReferenceSource = HandReferenceSource.USER,
    ): HandReferenceSaveOutcome

    suspend fun clear()
}
