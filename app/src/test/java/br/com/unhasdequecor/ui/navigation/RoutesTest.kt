package br.com.unhasdequecor.ui.navigation

import br.com.unhasdequecor.domain.model.Mood
import br.com.unhasdequecor.domain.model.Occasion
import br.com.unhasdequecor.domain.model.RecommendationSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RoutesTest {

    @Test
    fun `resultForMe uses none sentinels including colorId`() {
        assertThat(Routes.resultForMe()).isEqualTo("result/for_me/none/none/none")
        assertThat(ResultSources.toDomain(ResultSources.FOR_ME))
            .isEqualTo(RecommendationSource.FOR_ME)
    }

    @Test
    fun `resultByContext encodes enums with empty colorId`() {
        val route = Routes.resultByContext(Occasion.FESTA, Mood.ENERGETICA)
        assertThat(route).isEqualTo("result/context/FESTA/ENERGETICA/none")
        assertThat(Routes.parseOccasion("FESTA")).isEqualTo(Occasion.FESTA)
        assertThat(Routes.parseMood("ENERGETICA")).isEqualTo(Mood.ENERGETICA)
        assertThat(Routes.parseOccasion("none")).isNull()
        assertThat(Routes.parseColorId("none")).isNull()
    }

    @Test
    fun `resultFromHistory restores color and context`() {
        val route = Routes.resultFromHistory(
            source = RecommendationSource.CONTEXT,
            occasion = Occasion.ENCONTRO,
            mood = Mood.ROMANTICA,
            colorId = "romantico_rosa",
        )
        assertThat(route).isEqualTo("result/context/ENCONTRO/ROMANTICA/romantico_rosa")
        assertThat(Routes.parseColorId("romantico_rosa")).isEqualTo("romantico_rosa")
    }

    @Test
    fun `resultFromHistory allows null occasion and mood`() {
        val route = Routes.resultFromHistory(
            source = RecommendationSource.FOR_ME,
            occasion = null,
            mood = null,
            colorId = "festa_vermelha",
        )
        assertThat(route).isEqualTo("result/for_me/none/none/festa_vermelha")
    }

    @Test
    fun `resultForColor opens try-on for catalog color`() {
        assertThat(Routes.resultForColor("malva_suave"))
            .isEqualTo("result/for_me/none/none/malva_suave")
        assertThat(Routes.parseColorId("malva_suave")).isEqualTo("malva_suave")
    }

    @Test
    fun `liveTryOn encodes catalog colorId`() {
        assertThat(Routes.liveTryOn("festa_vermelha"))
            .isEqualTo("live_try_on/festa_vermelha")
        assertThat(Routes.LIVE_TRY_ON).isEqualTo("live_try_on/{colorId}")
    }
}
