package br.com.unhasdequecor.ui.components

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import br.com.unhasdequecor.ui.theme.UnhasDeQueCorTheme
import org.junit.Rule
import org.junit.Test

class AsyncContentAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingStateIsAnnouncedAndSpinnerIsNotAnAccessibilityNode() {
        composeRule.setContent {
            UnhasDeQueCorTheme {
                LoadingContent()
            }
        }

        composeRule
            .onNodeWithContentDescription("Carregando")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
    }

    @Test
    fun errorMessageIsAnnouncedAsPoliteLiveRegion() {
        val message = "Não consegui processar essa imagem. Tente outra foto."

        composeRule.setContent {
            UnhasDeQueCorTheme {
                ErrorContent(message = message)
            }
        }

        composeRule
            .onNodeWithText(message)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
    }
}
