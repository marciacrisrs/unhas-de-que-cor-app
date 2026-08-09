package br.com.unhasdequecor.domain.model

enum class HandReferenceSource {
    USER,
    SAMPLE,
}

data class HandReference(
    val localPath: String,
    val capturedAtEpochMs: Long,
    val source: HandReferenceSource = HandReferenceSource.USER,
    val sampleId: String? = null,
)

sealed interface HandReferenceSaveOutcome {
    data class Saved(val reference: HandReference) : HandReferenceSaveOutcome

    data class Rejected(val reason: HandReferenceRejection) : HandReferenceSaveOutcome
}

enum class HandReferenceRejection {
    INVALID_IMAGE,
    TOO_SMALL,
    TOO_LARGE,
    IO_ERROR,
}
