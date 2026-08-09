package br.com.unhasdequecor.testing

import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceRejection
import br.com.unhasdequecor.domain.model.HandReferenceSaveOutcome
import br.com.unhasdequecor.domain.model.HandReferenceSource
import br.com.unhasdequecor.domain.repository.HandReferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeHandReferenceRepository(
    initial: HandReference? = null,
) : HandReferenceRepository {

    private val state = MutableStateFlow(initial)
    var nextOutcome: HandReferenceSaveOutcome? = null
    var lastSavedPath: String? = null
    var lastSource: HandReferenceSource? = null
    var lastSampleId: String? = null

    override fun observe(): Flow<HandReference?> = state.asStateFlow()

    override suspend fun save(
        sourceAbsolutePath: String,
        capturedAtEpochMs: Long,
        source: HandReferenceSource,
        sampleId: String?,
    ): HandReferenceSaveOutcome {
        lastSavedPath = sourceAbsolutePath
        lastSource = source
        lastSampleId = sampleId
        val outcome = nextOutcome ?: HandReferenceSaveOutcome.Saved(
            HandReference(
                localPath = "/files/hand_reference/hand.jpg",
                capturedAtEpochMs = capturedAtEpochMs,
                source = source,
                sampleId = sampleId,
            ),
        )
        if (outcome is HandReferenceSaveOutcome.Saved) {
            state.value = outcome.reference
        }
        return outcome
    }

    override suspend fun clear() {
        state.value = null
    }

    override suspend fun ensureDefaultSample(): HandReference? {
        state.value?.let { return it }
        val sample = HandReference(
            localPath = "/files/hand_reference/hand_default.jpg",
            capturedAtEpochMs = 1L,
            source = HandReferenceSource.SAMPLE,
            sampleId = "morena_nude",
        )
        state.value = sample
        lastSource = HandReferenceSource.SAMPLE
        lastSampleId = "morena_nude"
        return sample
    }

    fun emit(reference: HandReference?) {
        state.value = reference
    }

    fun reject(reason: HandReferenceRejection) {
        nextOutcome = HandReferenceSaveOutcome.Rejected(reason)
    }
}
