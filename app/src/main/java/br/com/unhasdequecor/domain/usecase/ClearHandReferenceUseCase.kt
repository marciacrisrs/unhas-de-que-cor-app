package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.repository.HandReferenceRepository
import javax.inject.Inject

class ClearHandReferenceUseCase @Inject constructor(
    private val handReferenceRepository: HandReferenceRepository,
) {
    suspend operator fun invoke() {
        handReferenceRepository.clear()
    }
}
