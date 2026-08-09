package br.com.unhasdequecor.data.local.hand

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import br.com.unhasdequecor.domain.model.HandReference
import br.com.unhasdequecor.domain.model.HandReferenceSource
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
            val source = prefs[KEY_SOURCE]
                ?.let { runCatching { HandReferenceSource.valueOf(it) }.getOrNull() }
                ?: HandReferenceSource.USER
            HandReference(
                localPath = path,
                capturedAtEpochMs = capturedAt,
                source = source,
                sampleId = prefs[KEY_SAMPLE_ID],
            )
        }
    }

    suspend fun save(reference: HandReference) {
        dataStore.edit { prefs ->
            prefs[KEY_LOCAL_PATH] = reference.localPath
            prefs[KEY_CAPTURED_AT] = reference.capturedAtEpochMs
            prefs[KEY_SOURCE] = reference.source.name
            val sampleId = reference.sampleId
            if (sampleId.isNullOrBlank()) {
                prefs.remove(KEY_SAMPLE_ID)
            } else {
                prefs[KEY_SAMPLE_ID] = sampleId
            }
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_LOCAL_PATH)
            prefs.remove(KEY_CAPTURED_AT)
            prefs.remove(KEY_SOURCE)
            prefs.remove(KEY_SAMPLE_ID)
        }
    }

    private companion object {
        val KEY_LOCAL_PATH = stringPreferencesKey("hand_local_path")
        val KEY_CAPTURED_AT = longPreferencesKey("hand_captured_at")
        val KEY_SOURCE = stringPreferencesKey("hand_source")
        val KEY_SAMPLE_ID = stringPreferencesKey("hand_sample_id")
    }
}
