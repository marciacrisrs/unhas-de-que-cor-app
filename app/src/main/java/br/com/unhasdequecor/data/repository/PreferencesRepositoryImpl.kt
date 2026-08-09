package br.com.unhasdequecor.data.repository

import br.com.unhasdequecor.data.local.datastore.PreferencesDataSource
import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.domain.model.UserPreferences
import br.com.unhasdequecor.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val dataSource: PreferencesDataSource,
) : PreferencesRepository {
    override fun observePreferences(): Flow<UserPreferences> = dataSource.observe()

    override suspend fun updatePreferredStyles(styles: Set<NailStyle>) {
        dataSource.updatePreferredStyles(styles)
    }
}
