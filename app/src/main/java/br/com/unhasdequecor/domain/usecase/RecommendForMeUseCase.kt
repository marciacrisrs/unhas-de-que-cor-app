package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.model.ColorRecommendation
import br.com.unhasdequecor.domain.recommendation.RecommendationEngine
import br.com.unhasdequecor.domain.repository.ColorCatalogRepository
import br.com.unhasdequecor.domain.repository.HistoryRepository
import br.com.unhasdequecor.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RecommendForMeUseCase @Inject constructor(
    private val catalogRepository: ColorCatalogRepository,
    private val historyRepository: HistoryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val engine: RecommendationEngine,
) {
    suspend operator fun invoke(): ColorRecommendation {
        val preferences = preferencesRepository.observePreferences().first()
        val recent = historyRepository.recentColorIds()
        return engine.recommendForMe(
            catalog = catalogRepository.getAll(),
            preferredStyles = preferences.preferredStyles,
            recentColorIds = recent,
        )
    }
}
