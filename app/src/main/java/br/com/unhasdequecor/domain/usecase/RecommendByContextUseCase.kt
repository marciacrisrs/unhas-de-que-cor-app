package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.model.ColorRecommendation
import br.com.unhasdequecor.domain.model.RecommendationContext
import br.com.unhasdequecor.domain.recommendation.RecommendationEngine
import br.com.unhasdequecor.domain.repository.ColorCatalogRepository
import br.com.unhasdequecor.domain.repository.HistoryRepository
import javax.inject.Inject

class RecommendByContextUseCase @Inject constructor(
    private val catalogRepository: ColorCatalogRepository,
    private val historyRepository: HistoryRepository,
    private val engine: RecommendationEngine,
) {
    suspend operator fun invoke(context: RecommendationContext): ColorRecommendation {
        val recent = historyRepository.recentColorIds()
        return engine.recommendByContext(
            catalog = catalogRepository.getAll(),
            context = context,
            recentColorIds = recent,
        )
    }
}
