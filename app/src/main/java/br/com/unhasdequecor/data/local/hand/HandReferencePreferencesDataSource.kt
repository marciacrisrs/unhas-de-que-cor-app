package br.com.unhasdequecor.data.local.hand

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import br.com.unhasdequecor.domain.model.HandReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HandReferencePreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    fun observe(): Flow<HandReference?> = dataStore.data.map { prefs ->
        val path = prefs[KEY_LOCAL_PATH].orEmpty()
        val capturedAt = prefs[KEY_CAPTURED_AT] ?: 0L
        if (path.isBlank() || capturedAt <= 0L) {
            null
        } else {
            HandReference(localPath = path, capturedAtEpochMs = capturedAt)
        }
    }

    suspend fun save(reference: HandReference) {
        dataStore.edit { prefs ->
            prefs[KEY_LOCAL_PATH] = reference.localPath
            prefs[KEY_CAPTURED_AT] = reference.capturedAtEpochMs
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_LOCAL_PATH)
            prefs.remove(KEY_CAPTURED_AT)
        }
    }

    private companion object {
        val KEY_LOCAL_PATH = stringPreferencesKey("hand_local_path")
        val KEY_CAPTURED_AT = longPreferencesKey("hand_captured_at")
    }
}
