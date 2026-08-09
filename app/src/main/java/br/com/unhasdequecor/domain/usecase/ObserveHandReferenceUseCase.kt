package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.repository.HandReferenceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveHandReferenceUseCase @Inject constructor(
    private val handReferenceRepository: HandReferenceRepository,
) {
    operator fun invoke(): Flow<HandReference?> = handReferenceRepository.observe()
}
