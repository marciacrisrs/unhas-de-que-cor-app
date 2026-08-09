package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.repository.HistoryRepository
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
) {
    suspend operator fun invoke(colorId: String, currentlyFavorite: Boolean) {
        historyRepository.setFavorite(colorId, !currentlyFavorite)
    }
}
