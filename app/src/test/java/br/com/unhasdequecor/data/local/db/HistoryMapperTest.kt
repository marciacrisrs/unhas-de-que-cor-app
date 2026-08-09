package br.com.unhasdequecor.data.local.db

import br.com.unhasdequecor.data.local.db.entity.HistoryEntity
import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.domain.model.Mood
import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.domain.model.Occasion
import br.com.unhasdequecor.domain.model.RecommendationSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HistoryMapperTest {

    @Test
    fun `round trip keeps domain fields`() {
        val domain = HistoryEntry(
            id = 9L,
            colorId = "festa_vermelha",
            colorName = "Vermelho Festa",
            colorHex = 0xFFC6283A,
            tags = listOf(NailStyle.ELEGANTE, NailStyle.MARCANTE),
            source = RecommendationSource.CONTEXT,
            occasion = Occasion.FESTA,
            mood = Mood.ENERGETICA,
            createdAtEpochMs = 100L,
            isFavorite = true,
        )

        val entity = domain.toEntity()
        val back = entity.toDomain()

        assertThat(back).isEqualTo(domain)
        assertThat(entity.tagsCsv).isEqualTo("ELEGANTE,MARCANTE")
    }

    @Test
    fun `toDomain ignores unknown tags and enums`() {
        val entity = HistoryEntity(
            id = 1L,
            colorId = "x",
            colorName = "X",
            colorHex = 1L,
            tagsCsv = "ELEGANTE,UNKNOWN,MINIMALISTA",
            source = "FOR_ME",
            occasion = "NOT_AN_OCCASION",
            mood = "NEUTRA",
            createdAtEpochMs = 2L,
            isFavorite = false,
        )

        val domain = entity.toDomain()

        assertThat(domain.tags).containsExactly(NailStyle.ELEGANTE, NailStyle.MINIMALISTA)
        assertThat(domain.occasion).isNull()
        assertThat(domain.mood).isEqualTo(Mood.NEUTRA)
        assertThat(domain.source).isEqualTo(RecommendationSource.FOR_ME)
    }
}
