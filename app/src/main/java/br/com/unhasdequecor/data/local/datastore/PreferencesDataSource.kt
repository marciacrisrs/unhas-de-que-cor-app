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
            displayName = prefs[KEY_DISPLAY_NAME].orEmpty(),
        )
    }

    suspend fun updatePreferredStyles(styles: Set<NailStyle>) {
        dataStore.edit { prefs ->
            prefs[KEY_STYLES] = styles.map { it.name }.toSet()
        }
    }

    /**
     * Liga/desliga um estilo na transação do DataStore. Assim dois toques
     * rápidos não leem o mesmo snapshot e apagam o primeiro.
     */
    suspend fun togglePreferredStyle(style: NailStyle) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_STYLES].orEmpty().toMutableSet()
            val name = style.name
            if (!current.add(name)) {
                current.remove(name)
            }
            prefs[KEY_STYLES] = current
        }
    }

    private companion object {
        val KEY_STYLES = stringSetPreferencesKey("preferred_styles")
        val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
    }
}
