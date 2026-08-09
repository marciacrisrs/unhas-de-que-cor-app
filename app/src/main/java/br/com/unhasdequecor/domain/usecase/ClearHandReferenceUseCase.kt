package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.repository.HandReferenceRepository
import javax.inject.Inject

/**
 * Remove a foto própria e restaura a amostra padrão.
 * Nunca deixa o app sem mão de referência (sem ilustração).
 */
class ClearHandReferenceUseCase @Inject constructor(
    private val handReferenceRepository: HandReferenceRepository,
) {
    suspend operator fun invoke(): HandReference? =
        handReferenceRepository.resetToDefaultSample()
}
