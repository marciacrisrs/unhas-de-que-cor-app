package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.model.ColorRecommendation
import br.com.unhasdequecor.domain.model.RecommendationContext
import br.com.unhasdequecor.domain.model.RecommendationSource
import br.com.unhasdequecor.domain.repository.HistoryRepository
import br.com.unhasdequecor.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class GeneratedRecommendation(
    val recommendation: ColorRecommendation,
    val isFavorite: Boolean,
)

/**
 * Orquestra recomendação + persistência no histórico e devolve o estado de favorito.
 */
class GenerateAndSaveRecommendationUseCase @Inject constructor(
    private val recommendByContext: RecommendByContextUseCase,
    private val recommendForMe: RecommendForMeUseCase,
    private val saveRecommendation: SaveRecommendationUseCase,
    private val historyRepository: HistoryRepository,
    private val preferencesRepository: PreferencesRepository,
) {
    suspend operator fun invoke(
        source: RecommendationSource,
        context: RecommendationContext = RecommendationContext(),
        idempotencyKey: String? = null,
    ): GeneratedRecommendation {
        val recommendation = when (source) {
            RecommendationSource.FOR_ME -> recommendForMe()
            RecommendationSource.CONTEXT -> {
                val styles = preferencesRepository.observePreferences().first().preferredStyles
                recommendByContext(context.copy(preferredStyles = styles))
            }
        }
        val isFavorite = historyRepository.isFavorite(recommendation.color.id)
        saveRecommendation(
            recommendation = recommendation,
            idempotencyKey = idempotencyKey,
        )
        return GeneratedRecommendation(
            recommendation = recommendation,
            isFavorite = isFavorite,
        )
    }
}
