package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.model.HandReferenceSaveOutcome
import br.com.unhasdequecor.domain.repository.HandReferenceRepository
import br.com.unhasdequecor.domain.time.Clock
import javax.inject.Inject

class SaveHandReferenceUseCase @Inject constructor(
    private val handReferenceRepository: HandReferenceRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(sourceAbsolutePath: String): HandReferenceSaveOutcome =
        handReferenceRepository.save(
            sourceAbsolutePath = sourceAbsolutePath,
            capturedAtEpochMs = clock.now(),
        )
}
