package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.repository.HistoryRepository
import javax.inject.Inject

class GetDistinctColorCountUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
) {
    suspend operator fun invoke(): Int = historyRepository.distinctColorCount()
}
