package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.model.HandReferenceRejection
import br.com.unhasdequecor.domain.model.HandReferenceSaveOutcome
import br.com.unhasdequecor.domain.model.HandReferenceSource
import br.com.unhasdequecor.domain.model.HandSampleCatalog
import br.com.unhasdequecor.domain.repository.HandReferenceRepository
import br.com.unhasdequecor.domain.time.Clock
import javax.inject.Inject

class UseSampleHandReferenceUseCase @Inject constructor(
    private val handReferenceRepository: HandReferenceRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        sampleId: String,
        sampleAbsolutePath: String,
    ): HandReferenceSaveOutcome {
        if (HandSampleCatalog.findById(sampleId) == null) {
            return HandReferenceSaveOutcome.Rejected(HandReferenceRejection.INVALID_IMAGE)
        }
        return handReferenceRepository.save(
            sourceAbsolutePath = sampleAbsolutePath,
            capturedAtEpochMs = clock.now(),
            source = HandReferenceSource.SAMPLE,
            sampleId = sampleId,
        )
    }
}
