package br.com.unhasdequecor.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.com.unhasdequecor.domain.model.NailStyle
import br.com.unhasdequecor.ui.theme.UnhasDeQueCorTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Smoke Compose: toque na linha do histórico dispara onClick (jornada → Result).
 * Roda com connectedAndroidTest / emulador; não faz parte do verifyCi unitário.
 */
class HistoryRowClickTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingRowInvokesCallback() {
        var clicked = false
        composeRule.setContent {
            UnhasDeQueCorTheme {
                HistoryRow(
                    model = HistoryRowModel(
                        colorName = "Malva Suave",
                        colorHex = 0xFFB48A9A,
                        tags = listOf(NailStyle.ROMANTICO),
                        dateLabel = "09/08/2026",
                        isFavorite = false,
                    ),
                    onFavoriteClick = {},
                    onClick = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithText("Malva Suave").performClick()
        assertTrue(clicked)
    }
}
