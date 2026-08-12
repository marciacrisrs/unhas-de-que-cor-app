package br.com.unhasdequecor.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import br.com.unhasdequecor.data.local.db.AppDatabase
import br.com.unhasdequecor.data.local.db.DatabaseMigrations
import br.com.unhasdequecor.data.local.db.dao.FavoriteDao
import br.com.unhasdequecor.data.local.db.dao.HistoryDao
import br.com.unhasdequecor.data.repository.ColorCatalogRepositoryImpl
import br.com.unhasdequecor.data.repository.HandReferenceRepositoryImpl
import br.com.unhasdequecor.data.repository.HistoryRepositoryImpl
import br.com.unhasdequecor.data.repository.PreferencesRepositoryImpl
import br.com.unhasdequecor.data.vision.HandLandmarkProcessor
import br.com.unhasdequecor.data.vision.MediaPipeHandNailDetector
import br.com.unhasdequecor.data.vision.nail.GeometricNailSegmenter
import br.com.unhasdequecor.data.vision.nail.NailSegmenter
import br.com.unhasdequecor.domain.repository.ColorCatalogRepository
import br.com.unhasdequecor.domain.repository.HandReferenceRepository
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
            .addMigrations(*DatabaseMigrations.ALL)
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
interface RepositoryModule {

    @Binds
    @Singleton
    fun bindColorCatalogRepository(
        impl: ColorCatalogRepositoryImpl,
    ): ColorCatalogRepository

    @Binds
    @Singleton
    fun bindHistoryRepository(
        impl: HistoryRepositoryImpl,
    ): HistoryRepository

    @Binds
    @Singleton
    fun bindPreferencesRepository(
        impl: PreferencesRepositoryImpl,
    ): PreferencesRepository

    @Binds
    @Singleton
    fun bindHandReferenceRepository(
        impl: HandReferenceRepositoryImpl,
    ): HandReferenceRepository

    @Binds
    @Singleton
    fun bindHandLandmarkProcessor(
        impl: MediaPipeHandNailDetector,
    ): HandLandmarkProcessor

    @Binds
    @Singleton
    fun bindNailSegmenter(
        impl: GeometricNailSegmenter,
    ): NailSegmenter
}
