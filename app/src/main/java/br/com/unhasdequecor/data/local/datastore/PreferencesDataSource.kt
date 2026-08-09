package br.com.unhasdequecor.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    fun observe(): Flow<UserPreferences> = dataStore.data.map { prefs ->
        val styles = prefs[KEY_STYLES]
            .orEmpty()
            .mapNotNull { runCatching { NailStyle.valueOf(it) }.getOrNull() }
            .toSet()
        UserPreferences(
            preferredStyles = styles,
            displayName = prefs[KEY_DISPLAY_NAME] ?: "Márcia",
        )
    }

    suspend fun updatePreferredStyles(styles: Set<NailStyle>) {
        dataStore.edit { prefs ->
            prefs[KEY_STYLES] = styles.map { it.name }.toSet()
        }
    }

    private companion object {
        val KEY_STYLES = stringSetPreferencesKey("preferred_styles")
        val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
    }
}
