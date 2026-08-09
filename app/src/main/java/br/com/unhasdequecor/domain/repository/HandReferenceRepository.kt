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
        sampleId: String? = null,
    ): HandReferenceSaveOutcome

    suspend fun clear()

    /**
     * Garante uma mão de referência (amostra padrão) quando não há foto válida.
     * @return a referência vigente após o ensure, ou null se falhar ao materializar a amostra.
     */
    suspend fun ensureDefaultSample(): HandReference?
}
