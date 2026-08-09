package br.com.unhasdequecor.domain.usecase

import app.cash.turbine.test
import br.com.unhasdequecor.domain.model.ColorRecommendation
import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.domain.model.Mood
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
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DomainUseCasesTest {

    private val history = FakeHistoryRepository()
    private val preferences = FakePreferencesRepository(
        UserPreferences(preferredStyles = setOf(NailStyle.MINIMALISTA)),
    )
    private val catalog = FakeColorCatalogRepository()
    private val engine = RecommendationEngine()
    private val fixedNowMs = 1_700_000_000_042L
    private val clock = Clock { fixedNowMs }

    @Test
    fun `save recommendation persists entry with favorite state and clock`() = runTest {
        val color = TestColorCatalog.colors.first()
        history.setFavorite(color.id, true)
        val recommendation = ColorRecommendation(
            color = color,
            similarColors = emptyList(),
            source = RecommendationSource.CONTEXT,
            context = RecommendationContext(occasion = Occasion.FESTA, mood = Mood.ENERGETICA),
            rationale = "teste",
        )

        val id = SaveRecommendationUseCase(history, clock)(recommendation)

        assertThat(id).isEqualTo(1L)
        history.observeHistory().test {
            val saved = awaitItem().single()
            assertThat(saved.colorId).isEqualTo(color.id)
            assertThat(saved.isFavorite).isTrue()
            assertThat(saved.createdAtEpochMs).isEqualTo(fixedNowMs)
            assertThat(saved.occasion).isEqualTo(Occasion.FESTA)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recommend for me uses preferences and catalog`() = runTest {
        val recommendation = RecommendForMeUseCase(catalog, history, preferences, engine)()

        assertThat(recommendation.source).isEqualTo(RecommendationSource.FOR_ME)
        assertThat(recommendation.color.tags).contains(NailStyle.MINIMALISTA)
    }

    @Test
    fun `toggle favorite flips repository flag`() = runTest {
        val useCase = ToggleFavoriteUseCase(history)
        useCase("dia_nude", currentlyFavorite = false)
        assertThat(history.isFavorite("dia_nude")).isTrue()
        useCase("dia_nude", currentlyFavorite = true)
        assertThat(history.isFavorite("dia_nude")).isFalse()
    }

    @Test
    fun `observe history switches between all and favorites`() = runTest {
        val color = TestColorCatalog.colors.first()
        history.save(
            HistoryEntry(
                colorId = color.id,
                colorName = color.name,
                colorHex = color.hex,
                tags = color.tags,
                source = RecommendationSource.FOR_ME,
                occasion = null,
                mood = null,
                createdAtEpochMs = 1L,
            ),
        )
        history.setFavorite(color.id, true)

        val useCase = ObserveHistoryUseCase(history)
        useCase(favoritesOnly = false).test {
            assertThat(awaitItem()).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
        useCase(favoritesOnly = true).test {
            assertThat(awaitItem().single().colorId).isEqualTo(color.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observe and update preferences`() = runTest {
        val observe = ObservePreferencesUseCase(preferences)
        val update = UpdatePreferredStylesUseCase(preferences)

        observe().test {
            assertThat(awaitItem().preferredStyles).containsExactly(NailStyle.MINIMALISTA)
            update(setOf(NailStyle.ELEGANTE))
            assertThat(awaitItem().preferredStyles).containsExactly(NailStyle.ELEGANTE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `distinct color count reflects unique history colors`() = runTest {
        val first = TestColorCatalog.colors[0]
        val second = TestColorCatalog.colors[1]
        listOf(first, second, first).forEachIndexed { index, color ->
            history.save(
                HistoryEntry(
                    colorId = color.id,
                    colorName = color.name,
                    colorHex = color.hex,
                    tags = color.tags,
                    source = RecommendationSource.CONTEXT,
                    occasion = null,
                    mood = null,
                    createdAtEpochMs = index.toLong(),
                ),
            )
        }

        assertThat(GetDistinctColorCountUseCase(history)()).isEqualTo(2)
    }
}
