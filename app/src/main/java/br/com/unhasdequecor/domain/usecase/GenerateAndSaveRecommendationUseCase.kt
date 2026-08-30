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
 *
 * Com [idempotencyKey], um retry (process-death da tela Result) devolve a cor já
 * gravada em vez de sortear outra e ignorar o insert.
 */
class GenerateAndSaveRecommendationUseCase @Inject constructor(
    private val recommendByContext: RecommendByContextUseCase,
    private val recommendForMe: RecommendForMeUseCase,
    private val restoreRecommendation: RestoreRecommendationUseCase,
    private val saveRecommendation: SaveRecommendationUseCase,
    private val historyRepository: HistoryRepository,
    private val preferencesRepository: PreferencesRepository,
) {
    suspend operator fun invoke(
        source: RecommendationSource,
        context: RecommendationContext = RecommendationContext(),
        idempotencyKey: String? = null,
    ): GeneratedRecommendation {
        val resolvedContext = resolvedContext(source, context)
        if (!idempotencyKey.isNullOrBlank()) {
            val existing = historyRepository.findByIdempotencyKey(idempotencyKey)
            if (existing != null) {
                restoreRecommendation(
                    colorId = existing.colorId,
                    source = source,
                    context = resolvedContext,
                )?.let { return it }
            }
        }
        val recommendation = when (source) {
            RecommendationSource.FOR_ME -> recommendForMe()
            RecommendationSource.CONTEXT -> recommendByContext(resolvedContext)
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

    private suspend fun resolvedContext(
        source: RecommendationSource,
        context: RecommendationContext,
    ): RecommendationContext = when (source) {
        RecommendationSource.FOR_ME -> context
        RecommendationSource.CONTEXT -> {
            val styles = preferencesRepository.observePreferences().first().preferredStyles
            context.copy(preferredStyles = styles)
        }
    }
}
