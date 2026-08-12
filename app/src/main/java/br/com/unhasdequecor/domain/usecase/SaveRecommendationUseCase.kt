package br.com.unhasdequecor.domain.usecase

import br.com.unhasdequecor.domain.model.ColorRecommendation
import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.domain.repository.HistoryRepository
import br.com.unhasdequecor.domain.time.Clock
import javax.inject.Inject

class SaveRecommendationUseCase @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        recommendation: ColorRecommendation,
        createdAtEpochMs: Long = clock.now(),
        idempotencyKey: String? = null,
    ): Long {
        val color = recommendation.color
        return historyRepository.save(
            HistoryEntry(
                colorId = color.id,
                colorName = color.name,
                colorHex = color.hex,
                tags = color.tags,
                source = recommendation.source,
                occasion = recommendation.context.occasion,
                mood = recommendation.context.mood,
                createdAtEpochMs = createdAtEpochMs,
                isFavorite = historyRepository.isFavorite(color.id),
                idempotencyKey = idempotencyKey,
            ),
        )
    }
}
