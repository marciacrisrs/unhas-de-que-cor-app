package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.model.Mood
import br.com.unhasdequecor.domain.model.Occasion
import br.com.unhasdequecor.domain.model.RecommendationContext
import br.com.unhasdequecor.domain.model.RecommendationSource
import br.com.unhasdequecor.domain.recommendation.RecommendationEngine
import br.com.unhasdequecor.testing.FakeColorCatalogRepository
import br.com.unhasdequecor.testing.FakeHistoryRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RestoreRecommendationUseCaseTest {

    @Test
    fun `restores color without requiring a new history write`() = runTest {
        val history = FakeHistoryRepository()
        val catalog = FakeColorCatalogRepository()
        val useCase = RestoreRecommendationUseCase(
            catalogRepository = catalog,
            historyRepository = history,
            engine = RecommendationEngine(),
        )
        history.setFavorite("romantico_rosa", true)

        val restored = useCase(
            colorId = "romantico_rosa",
            source = RecommendationSource.CONTEXT,
            context = RecommendationContext(
                occasion = Occasion.ENCONTRO,
                mood = Mood.ROMANTICA,
            ),
        )

        assertThat(restored).isNotNull()
        assertThat(restored!!.recommendation.color.id).isEqualTo("romantico_rosa")
        assertThat(restored.isFavorite).isTrue()
        assertThat(history.distinctColorCount()).isEqualTo(0)
        assertThat(restored.recommendation.similarColors).isNotEmpty()
    }

    @Test
    fun `unknown color returns null`() = runTest {
        val useCase = RestoreRecommendationUseCase(
            catalogRepository = FakeColorCatalogRepository(),
            historyRepository = FakeHistoryRepository(),
            engine = RecommendationEngine(),
        )
        val restored = useCase(
            colorId = "nao_existe",
            source = RecommendationSource.FOR_ME,
            context = RecommendationContext(),
        )
        assertThat(restored).isNull()
    }
}
