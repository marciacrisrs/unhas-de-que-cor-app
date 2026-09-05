package br.com.unhasdequecor.testing

import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.domain.model.UserPreferences
import br.com.unhasdequecor.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakePreferencesRepository(
    initial: UserPreferences = UserPreferences(),
) : PreferencesRepository {
    private val state = MutableStateFlow(initial)

    override fun observePreferences(): Flow<UserPreferences> = state

    override suspend fun updatePreferredStyles(styles: Set<NailStyle>) {
        state.value = state.value.copy(preferredStyles = styles)
    }

    override suspend fun togglePreferredStyle(style: NailStyle) {
        state.update { prefs ->
            val current = prefs.preferredStyles.toMutableSet()
            if (!current.add(style)) {
                current.remove(style)
            }
            prefs.copy(preferredStyles = current)
        }
    }
}
