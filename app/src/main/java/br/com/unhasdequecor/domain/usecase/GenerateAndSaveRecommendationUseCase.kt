package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.model.ColorRecommendation
import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.domain.model.RecommendationContext
import br.com.unhasdequecor.domain.model.RecommendationSource
import br.com.unhasdequecor.domain.repository.HistoryRepository
import br.com.unhasdequecor.domain.repository.PreferencesRepository
import br.com.unhasdequecor.domain.time.Clock
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
    private val historyRepository: HistoryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        source: RecommendationSource,
        context: RecommendationContext = RecommendationContext(),
    ): GeneratedRecommendation {
        val recommendation = when (source) {
            RecommendationSource.FOR_ME -> recommendForMe()
            RecommendationSource.CONTEXT -> {
                val styles = preferencesRepository.observePreferences().first().preferredStyles
                recommendByContext(context.copy(preferredStyles = styles))
            }
        }
        val color = recommendation.color
        val isFavorite = historyRepository.isFavorite(color.id)
        historyRepository.save(
            HistoryEntry(
                colorId = color.id,
                colorName = color.name,
                colorHex = color.hex,
                tags = color.tags,
                source = recommendation.source,
                occasion = recommendation.context.occasion,
                mood = recommendation.context.mood,
                createdAtEpochMs = clock.now(),
                isFavorite = isFavorite,
            ),
        )
        return GeneratedRecommendation(
            recommendation = recommendation,
            isFavorite = isFavorite,
        )
    }
}
