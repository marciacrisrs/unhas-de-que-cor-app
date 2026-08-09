package br.com.unhasdequecor.domain.recommendation

import br.com.unhasdequecor.domain.model.ColorRecommendation
import br.com.unhasdequecor.domain.model.Mood
import br.com.unhasdequecor.domain.model.Occasion
import br.com.unhasdequecor.domain.model.RecommendationContext
import br.com.unhasdequecor.domain.model.Season
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sessão em memória para passar o contexto selecionado até a tela de resultado.
 */
@Singleton
class RecommendationSession @Inject constructor() {
    var occasion: Occasion? = null
    var mood: Mood? = null
    var season: Season? = null
    var lastRecommendation: ColorRecommendation? = null

    fun toContext(preferredStyles: Set<br.com.unhasdequecor.domain.model.NailStyle>): RecommendationContext =
        RecommendationContext(
            occasion = occasion,
            mood = mood,
            season = season,
            preferredStyles = preferredStyles,
        )

    fun clearSelection() {
        occasion = null
        mood = null
        season = null
    }
}
