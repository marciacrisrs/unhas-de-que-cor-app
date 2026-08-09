package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.model.RecommendationContext
import br.com.unhasdequecor.domain.model.RecommendationSource
import br.com.unhasdequecor.domain.recommendation.RecommendationEngine
import br.com.unhasdequecor.domain.repository.ColorCatalogRepository
import br.com.unhasdequecor.domain.repository.HistoryRepository
import javax.inject.Inject

/**
 * Restaura uma recomendação já persistida sem gravar novo histórico.
 */
class RestoreRecommendationUseCase @Inject constructor(
    private val catalogRepository: ColorCatalogRepository,
    private val historyRepository: HistoryRepository,
    private val engine: RecommendationEngine,
) {
    suspend operator fun invoke(
        colorId: String,
        source: RecommendationSource,
        context: RecommendationContext,
    ): GeneratedRecommendation? {
        val catalog = catalogRepository.getAll()
        val color = catalogRepository.getById(colorId) ?: return null
        val recommendation = engine.compose(
            selected = color,
            catalog = catalog,
            context = context,
            source = source,
        )
        return GeneratedRecommendation(
            recommendation = recommendation,
            isFavorite = historyRepository.isFavorite(colorId),
        )
    }
}
