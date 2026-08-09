package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.model.HandReferenceSaveOutcome
import br.com.unhasdequecor.domain.model.HandReferenceSource
import br.com.unhasdequecor.domain.repository.HandReferenceRepository
import br.com.unhasdequecor.domain.time.Clock
import javax.inject.Inject

/**
 * Persiste a mão de exemplo empacotada no app (para quem não quer tirar foto ainda).
 * O caminho absoluto do asset já materializado em arquivo é fornecido pela camada data/UI.
 */
class UseSampleHandReferenceUseCase @Inject constructor(
    private val handReferenceRepository: HandReferenceRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(sampleAbsolutePath: String): HandReferenceSaveOutcome =
        handReferenceRepository.save(
            sourceAbsolutePath = sampleAbsolutePath,
            capturedAtEpochMs = clock.now(),
            source = HandReferenceSource.SAMPLE,
        )
}
