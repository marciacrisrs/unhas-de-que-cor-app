package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.model.Mood
import br.com.unhasdequecor.domain.model.Occasion
import br.com.unhasdequecor.domain.model.RecommendationContext
import br.com.unhasdequecor.domain.recommendation.RecommendationEngine
import br.com.unhasdequecor.testing.FakeColorCatalogRepository
import br.com.unhasdequecor.testing.FakeHistoryRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RecommendByContextUseCaseTest {

    @Test
    fun `invokes engine with catalog and recent ids`() = runTest {
        val history = FakeHistoryRepository()
        val catalog = FakeColorCatalogRepository()
        // Seed a recent color to bias away from it when alternatives exist.
        val first = catalog.getAll().first()
        history.save(
            br.com.unhasdequecor.domain.model.HistoryEntry(
                colorId = first.id,
                colorName = first.name,
                colorHex = first.hex,
                tags = first.tags,
                source = br.com.unhasdequecor.domain.model.RecommendationSource.FOR_ME,
                occasion = null,
                mood = null,
                createdAtEpochMs = 1L,
            ),
        )

        val useCase = RecommendByContextUseCase(
            catalogRepository = catalog,
            historyRepository = history,
            engine = RecommendationEngine(),
        )

        val recommendation = useCase(
            RecommendationContext(
                occasion = Occasion.DIA_A_DIA,
                mood = Mood.NEUTRA,
            ),
        )

        assertThat(recommendation.color.name).isNotEmpty()
    }
}
