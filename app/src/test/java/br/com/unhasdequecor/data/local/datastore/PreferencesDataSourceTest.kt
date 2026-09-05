package br.com.unhasdequecor.data.local.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import br.com.unhasdequecor.domain.model.NailStyle
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreferencesDataSourceTest {

    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    @Test
    fun `concurrent toggles persist every selected style`() = runTest {
        val source = PreferencesDataSource(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { File(tmp.root, "prefs.preferences_pb") },
            ),
        )
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
        val source = PreferencesDataSource(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { File(tmp.root, "prefs-remove.preferences_pb") },
            ),
        )
        source.updatePreferredStyles(setOf(NailStyle.CLASSICO, NailStyle.DELICADO))
        source.togglePreferredStyle(NailStyle.CLASSICO)

        assertThat(source.observe().first().preferredStyles).containsExactly(NailStyle.DELICADO)
    }
}
