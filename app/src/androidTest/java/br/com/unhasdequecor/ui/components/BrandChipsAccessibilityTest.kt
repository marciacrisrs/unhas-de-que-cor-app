package br.com.unhasdequecor.ui.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import br.com.unhasdequecor.ui.theme.UnhasDeQueCorTheme
import org.junit.Rule
import org.junit.Test

class BrandChipsAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun styleChipExposesCheckboxSemantics() {
        composeRule.setContent {
            UnhasDeQueCorTheme {
                StyleChip(
                    label = "Elegante",
                    selected = true,
                    onClick = {},
                )
            }
        }

        composeRule
            .onNodeWithText("Elegante")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Role,
                    Role.Checkbox,
                ),
            )
    }
}
