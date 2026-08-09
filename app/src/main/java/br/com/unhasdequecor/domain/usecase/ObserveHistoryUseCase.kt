package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
) {
    operator fun invoke(favoritesOnly: Boolean = false): Flow<List<HistoryEntry>> {
        return if (favoritesOnly) {
            historyRepository.observeFavorites()
        } else {
            historyRepository.observeHistory()
        }
    }
}
