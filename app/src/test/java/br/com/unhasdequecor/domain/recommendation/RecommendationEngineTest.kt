package br.com.unhasdequecor.domain.recommendation

import br.com.unhasdequecor.domain.model.Mood
import br.com.unhasdequecor.domain.model.NailColor
import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.domain.model.Occasion
import br.com.unhasdequecor.domain.model.RecommendationContext
import br.com.unhasdequecor.domain.model.RecommendationSource
import br.com.unhasdequecor.domain.model.Season
import br.com.unhasdequecor.testing.TestColorCatalog
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class RecommendationEngineTest {

    private val engine = RecommendationEngine()
    private val catalog = TestColorCatalog.colors

    @Test
    fun `recommendByContext prefers matching occasion and mood`() {
        val context = RecommendationContext(
            occasion = Occasion.FESTA,
            mood = Mood.ENERGETICA,
            preferredStyles = setOf(NailStyle.ELEGANTE),
        )

        val result = engine.recommendByContext(
            catalog = catalog,
            context = context,
            recentColorIds = emptySet(),
            random = Random(42),
        )

        assertThat(result.source).isEqualTo(RecommendationSource.CONTEXT)
        assertThat(result.color.occasions).contains(Occasion.FESTA)
        assertThat(result.similarColors).isNotEmpty()
        assertThat(result.rationale).isNotEmpty()
    }

    @Test
    fun `recent colors receive score penalty`() {
        val color = catalog.first()
        val context = RecommendationContext(occasion = color.occasions.firstOrNull())

        val freshScore = engine.score(color, context, recentColorIds = emptySet())
        val recentScore = engine.score(color, context, recentColorIds = setOf(color.id))

        assertThat(recentScore).isLessThan(freshScore)
    }

    @Test
    fun `recommendForMe avoids recent colors when possible`() {
        val recent = catalog.take(catalog.size - 1).map { it.id }.toSet()
        val result = engine.recommendForMe(
            catalog = catalog,
            preferredStyles = emptySet(),
            recentColorIds = recent,
            random = Random(7),
        )

        assertThat(result.source).isEqualTo(RecommendationSource.FOR_ME)
        assertThat(result.color.id).isEqualTo(catalog.last().id)
    }

    @Test
    fun `recommendForMe respects preferred styles`() {
        val result = engine.recommendForMe(
            catalog = catalog,
            preferredStyles = setOf(NailStyle.MINIMALISTA),
            recentColorIds = emptySet(),
            random = Random(1),
        )

        assertThat(result.color.tags).contains(NailStyle.MINIMALISTA)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty catalog throws`() {
        engine.recommendByContext(
            catalog = emptyList(),
            context = RecommendationContext(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `recommendForMe empty catalog throws`() {
        engine.recommendForMe(
            catalog = emptyList(),
            preferredStyles = emptySet(),
        )
    }

    @Test
    fun `season match increases score`() {
        val color = NailColor(
            id = "inverno",
            name = "Inverno",
            hex = 0xFF112233,
            tags = listOf(NailStyle.ELEGANTE),
            description = "Frio",
            tip = "Casacos",
            seasons = setOf(Season.INVERNO),
        )
        val matching = engine.score(
            color,
            RecommendationContext(season = Season.INVERNO),
            recentColorIds = emptySet(),
        )
        val missing = engine.score(
            color,
            RecommendationContext(season = Season.VERAO),
            recentColorIds = emptySet(),
        )
        assertThat(matching).isGreaterThan(missing)
    }

    @Test
    fun `empty context uses generic rationale`() {
        val result = engine.recommendByContext(
            catalog = catalog,
            context = RecommendationContext(),
            random = Random(3),
        )
        assertThat(result.rationale).contains("momento atual")
    }

    @Test
    fun `recommendForMe falls back to catalog when all colors are recent`() {
        val allRecent = catalog.map { it.id }.toSet()
        val result = engine.recommendForMe(
            catalog = catalog,
            preferredStyles = emptySet(),
            recentColorIds = allRecent,
            random = Random(11),
        )
        assertThat(catalog.map { it.id }).contains(result.color.id)
    }
}
