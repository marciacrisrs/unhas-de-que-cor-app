package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.model.ColorRecommendation
import br.com.unhasdequecor.domain.model.Mood
import br.com.unhasdequecor.domain.model.NailColor
import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.domain.model.Occasion
import br.com.unhasdequecor.domain.model.RecommendationContext
import br.com.unhasdequecor.domain.model.RecommendationSource
import br.com.unhasdequecor.domain.model.UserPreferences
import br.com.unhasdequecor.domain.recommendation.RecommendationEngine
import br.com.unhasdequecor.domain.time.Clock
import br.com.unhasdequecor.testing.FakeColorCatalogRepository
import br.com.unhasdequecor.testing.FakeHistoryRepository
import br.com.unhasdequecor.testing.FakePreferencesRepository
import br.com.unhasdequecor.testing.TestColorCatalog
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GenerateAndSaveRecommendationUseCaseTest {

    private val history = FakeHistoryRepository()
    private val preferences = FakePreferencesRepository(
        UserPreferences(preferredStyles = setOf(NailStyle.ROMANTICO)),
    )
    private val catalog = FakeColorCatalogRepository()
    private val engine = RecommendationEngine()
    private val clock = Clock { 1_700_000_000_000L }

    private val useCase = GenerateAndSaveRecommendationUseCase(
        recommendByContext = RecommendByContextUseCase(catalog, history, engine),
        recommendForMe = RecommendForMeUseCase(catalog, history, preferences, engine),
        restoreRecommendation = RestoreRecommendationUseCase(catalog, history, engine),
        saveRecommendation = SaveRecommendationUseCase(history, clock),
        historyRepository = history,
        preferencesRepository = preferences,
    )

    @Test
    fun `context recommendation is saved and favorite hydrated`() = runTest {
        history.setFavorite("romantico_rosa", true)

        val generated = useCase(
            source = RecommendationSource.CONTEXT,
            context = RecommendationContext(
                occasion = Occasion.ENCONTRO,
                mood = Mood.ROMANTICA,
            ),
        )

        assertThat(generated.recommendation.source).isEqualTo(RecommendationSource.CONTEXT)
        assertThat(history.distinctColorCount()).isEqualTo(1)
        if (generated.recommendation.color.id == "romantico_rosa") {
            assertThat(generated.isFavorite).isTrue()
        }
    }

    @Test
    fun `for me recommendation persists history`() = runTest {
        val generated = useCase(source = RecommendationSource.FOR_ME)

        assertThat(generated.recommendation.source).isEqualTo(RecommendationSource.FOR_ME)
        assertThat(history.distinctColorCount()).isEqualTo(1)
        assertThat(generated.isFavorite).isFalse()
    }

    @Test
    fun `same idempotency key does not create duplicate history rows`() = runTest {
        useCase(
            source = RecommendationSource.FOR_ME,
            idempotencyKey = "session-fixed",
        )
        useCase(
            source = RecommendationSource.FOR_ME,
            idempotencyKey = "session-fixed",
        )

        val entries = history.observeHistory().first()
        assertThat(entries).hasSize(1)
        assertThat(entries.single().idempotencyKey).isEqualTo("session-fixed")
    }

    @Test
    fun `idempotent retry returns the persisted color instead of a new roll`() = runTest {
        val recommendForMe = mockk<RecommendForMeUseCase>()
        coEvery { recommendForMe() } returnsMany listOf(
            recommendation(TestColorCatalog.colors[0]),
            recommendation(TestColorCatalog.colors[1]),
        )
        val retryUseCase = GenerateAndSaveRecommendationUseCase(
            recommendByContext = RecommendByContextUseCase(catalog, history, engine),
            recommendForMe = recommendForMe,
            restoreRecommendation = RestoreRecommendationUseCase(catalog, history, engine),
            saveRecommendation = SaveRecommendationUseCase(history, clock),
            historyRepository = history,
            preferencesRepository = preferences,
        )

        val first = retryUseCase(
            source = RecommendationSource.FOR_ME,
            idempotencyKey = "session-process-death",
        )
        val retried = retryUseCase(
            source = RecommendationSource.FOR_ME,
            idempotencyKey = "session-process-death",
        )

        assertThat(first.recommendation.color.id).isEqualTo("festa_vermelha")
        assertThat(retried.recommendation.color.id).isEqualTo("festa_vermelha")
        assertThat(history.observeHistory().first().single().colorId).isEqualTo("festa_vermelha")
        coVerify(exactly = 1) { recommendForMe() }
    }

    private fun recommendation(color: NailColor) = ColorRecommendation(
        color = color,
        similarColors = emptyList(),
        source = RecommendationSource.FOR_ME,
        context = RecommendationContext(),
        rationale = "test",
    )
}
