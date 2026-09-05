package br.com.unhasdequecor.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import br.com.unhasdequecor.domain.model.NailStyle
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PreferencesDataSourceTest {

    @Test
    fun `concurrent toggles persist every selected style`() = runTest {
        val source = PreferencesDataSource(InMemoryPreferencesDataStore())
        source.updatePreferredStyles(setOf(NailStyle.CLASSICO))

        val first = async { source.togglePreferredStyle(NailStyle.DELICADO) }
        val second = async { source.togglePreferredStyle(NailStyle.ELEGANTE) }
        first.await()
        second.await()

        assertThat(source.observe().first().preferredStyles).containsExactly(
            NailStyle.CLASSICO,
            NailStyle.DELICADO,
            NailStyle.ELEGANTE,
        )
    }

    @Test
    fun `toggle removes an already selected style`() = runTest {
        val source = PreferencesDataSource(InMemoryPreferencesDataStore())
        source.updatePreferredStyles(setOf(NailStyle.CLASSICO, NailStyle.DELICADO))
        source.togglePreferredStyle(NailStyle.CLASSICO)

        assertThat(source.observe().first().preferredStyles).containsExactly(NailStyle.DELICADO)
    }
}

/**
 * DataStore em memória: o toggle precisa serializar como [DataStore.edit],
 * sem arquivo (Windows/DataStore file rename falha com writers concorrentes).
 */
private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val mutex = Mutex()
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = mutex.withLock {
        val next = transform(state.value)
        state.value = next
        next
    }
}
