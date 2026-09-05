package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.domain.repository.PreferencesRepository
import javax.inject.Inject

class UpdatePreferredStylesUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) {
    suspend operator fun invoke(styles: Set<NailStyle>) {
        preferencesRepository.updatePreferredStyles(styles)
    }

    suspend fun toggle(style: NailStyle) {
        preferencesRepository.togglePreferredStyle(style)
    }
}
