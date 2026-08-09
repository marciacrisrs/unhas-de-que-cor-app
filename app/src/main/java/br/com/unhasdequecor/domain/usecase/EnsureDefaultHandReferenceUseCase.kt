package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.repository.HandReferenceRepository
import javax.inject.Inject

class EnsureDefaultHandReferenceUseCase @Inject constructor(
    private val handReferenceRepository: HandReferenceRepository,
) {
    suspend operator fun invoke(): HandReference? = handReferenceRepository.ensureDefaultSample()
}
