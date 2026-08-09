package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.data.catalog.DefaultColorCatalog
import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.domain.model.Mood
import br.com.unhasdequecor.domain.model.Occasion
import br.com.unhasdequecor.domain.model.RecommendationContext
import br.com.unhasdequecor.domain.model.UserPreferences
import br.com.unhasdequecor.domain.recommendation.RecommendationEngine
import br.com.unhasdequecor.domain.repository.ColorCatalogRepository
import br.com.unhasdequecor.domain.repository.HistoryRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RecommendByContextUseCaseTest {

    @Test
    fun `invokes engine with catalog and recent ids`() = runTest {
        val catalogRepository = mockk<ColorCatalogRepository>()
        val historyRepository = object : HistoryRepository {
            override fun observeHistory(): Flow<List<HistoryEntry>> = flowOf(emptyList())
            override fun observeFavorites(): Flow<List<HistoryEntry>> = flowOf(emptyList())
            override suspend fun save(entry: HistoryEntry): Long = 1
            override suspend fun setFavorite(colorId: String, isFavorite: Boolean) = Unit
            override suspend fun isFavorite(colorId: String): Boolean = false
            override suspend fun recentColorIds(limit: Int): Set<String> = setOf("malva_suave")
            override suspend fun distinctColorCount(): Int = 1
        }
        every { catalogRepository.getAll() } returns DefaultColorCatalog.colors

        val useCase = RecommendByContextUseCase(
            catalogRepository = catalogRepository,
            historyRepository = historyRepository,
            engine = RecommendationEngine(),
        )

        val recommendation = useCase(
            RecommendationContext(
                occasion = Occasion.DIA_A_DIA,
                mood = Mood.ROMANTICA,
            ),
        )

        assertThat(recommendation.color.id).isNotEqualTo("malva_suave")
        assertThat(recommendation.color.name).isNotEmpty()
    }
}
