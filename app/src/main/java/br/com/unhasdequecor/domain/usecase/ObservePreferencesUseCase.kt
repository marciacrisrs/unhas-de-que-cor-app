package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.model.UserPreferences
import br.com.unhasdequecor.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePreferencesUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) {
    operator fun invoke(): Flow<UserPreferences> = preferencesRepository.observePreferences()
}
