package br.com.unhasdequecor.ui.navigation

import br.com.unhasdequecor.domain.model.Mood
import br.com.unhasdequecor.domain.model.Occasion
import br.com.unhasdequecor.domain.model.RecommendationSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RoutesTest {

    @Test
    fun `resultForMe uses none sentinels`() {
        assertThat(Routes.resultForMe()).isEqualTo("result/for_me/none/none")
        assertThat(ResultSources.toDomain(ResultSources.FOR_ME))
            .isEqualTo(RecommendationSource.FOR_ME)
    }

    @Test
    fun `resultByContext encodes enums`() {
        val route = Routes.resultByContext(Occasion.FESTA, Mood.ENERGETICA)
        assertThat(route).isEqualTo("result/context/FESTA/ENERGETICA")
        assertThat(Routes.parseOccasion("FESTA")).isEqualTo(Occasion.FESTA)
        assertThat(Routes.parseMood("ENERGETICA")).isEqualTo(Mood.ENERGETICA)
        assertThat(Routes.parseOccasion("none")).isNull()
    }
}
