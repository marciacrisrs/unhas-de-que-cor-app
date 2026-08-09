package br.com.unhasdequecor.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import br.com.unhasdequecor.data.local.db.AppDatabase
import br.com.unhasdequecor.data.local.db.dao.FavoriteDao
import br.com.unhasdequecor.data.local.db.dao.HistoryDao
import br.com.unhasdequecor.data.repository.ColorCatalogRepositoryImpl
import br.com.unhasdequecor.data.repository.HistoryRepositoryImpl
import br.com.unhasdequecor.data.repository.PreferencesRepositoryImpl
import br.com.unhasdequecor.domain.repository.ColorCatalogRepository
import br.com.unhasdequecor.domain.repository.HistoryRepository
import br.com.unhasdequecor.domain.repository.PreferencesRepository
import br.com.unhasdequecor.domain.time.Clock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "unhas_preferences")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "unhas_de_que_cor.db")
            // MVP: schema ainda volátil. Trocar por migrations reais antes de release.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideHistoryDao(database: AppDatabase): HistoryDao = database.historyDao()

    @Provides
    fun provideFavoriteDao(database: AppDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindColorCatalogRepository(
        impl: ColorCatalogRepositoryImpl,
    ): ColorCatalogRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(
        impl: HistoryRepositoryImpl,
    ): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(
        impl: PreferencesRepositoryImpl,
    ): PreferencesRepository
}
