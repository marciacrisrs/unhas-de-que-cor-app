package br.com.unhasdequecor.testing

import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceRejection
import br.com.unhasdequecor.domain.model.HandReferenceSaveOutcome
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

    override fun observe(): Flow<HandReference?> = state.asStateFlow()

    override suspend fun save(
        sourceAbsolutePath: String,
        capturedAtEpochMs: Long,
    ): HandReferenceSaveOutcome {
        lastSavedPath = sourceAbsolutePath
        val outcome = nextOutcome ?: HandReferenceSaveOutcome.Saved(
            HandReference(
                localPath = "/files/hand_reference/hand.jpg",
                capturedAtEpochMs = capturedAtEpochMs,
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

    fun emit(reference: HandReference?) {
        state.value = reference
    }

    fun reject(reason: HandReferenceRejection) {
        nextOutcome = HandReferenceSaveOutcome.Rejected(reason)
    }
}
