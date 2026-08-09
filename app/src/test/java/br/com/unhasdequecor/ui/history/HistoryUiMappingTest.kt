package br.com.unhasdequecor.ui.history

import br.com.unhasdequecor.domain.model.HistoryEntry
import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.domain.model.RecommendationSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class HistoryUiMappingTest {

    @Test
    fun `groups entries by month and counts distinct colors`() {
        val may = calendarMillis(2024, Calendar.MAY, 17)
        val april = calendarMillis(2024, Calendar.APRIL, 3)
        val entries = listOf(
            history(1, "a", may, favorite = true),
            history(2, "b", may, favorite = false),
            history(3, "a", april, favorite = true),
        )

        val ui = entries.toHistoryUiState()

        assertThat(ui.distinctColorCount).isEqualTo(2)
        assertThat(ui.groups).hasSize(2)
        assertThat(ui.groups.first().entries).hasSize(2)
        assertThat(ui.isEmpty).isFalse()
    }

    private fun history(
        id: Long,
        colorId: String,
        epoch: Long,
        favorite: Boolean,
    ) = HistoryEntry(
        id = id,
        colorId = colorId,
        colorName = colorId.uppercase(),
        colorHex = 0xFF000000,
        tags = listOf(NailStyle.ELEGANTE),
        source = RecommendationSource.CONTEXT,
        occasion = null,
        mood = null,
        createdAtEpochMs = epoch,
        isFavorite = favorite,
    )

    private fun calendarMillis(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month, day, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
