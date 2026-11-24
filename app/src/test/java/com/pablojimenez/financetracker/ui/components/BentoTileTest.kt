package com.pablojimenez.financetracker.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.formatCents
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BentoTileTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var clicks = 0

    private fun showTile(onClick: (() -> Unit)?) {
        composeRule.setContent {
            FinanceTrackerTheme(darkTheme = false, dynamicColor = false) {
                BentoTile(
                    caption = "Groceries",
                    modifier = Modifier.testTag(TAG),
                    span = TileSpan.Wide,
                    onClick = onClick,
                ) {
                    AnimatedMoneyText(cents = 42_000)
                }
            }
        }
    }

    @Test
    fun `clicking the tile runs onClick once`() {
        showTile(onClick = { clicks++ })

        composeRule.onNodeWithTag(TAG).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `clicking the caption runs the tile's onClick`() {
        showTile(onClick = { clicks++ })

        composeRule.onNodeWithText("Groceries", useUnmergedTree = true).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `clickable tile is one node with its caption and value`() {
        showTile(onClick = { clicks++ })

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        composeRule.onNodeWithTag(TAG)
            .assert(hasClickAction())
            .assert(hasText("Groceries"))
            .assert(hasContentDescription(formatCents(42_000)))
    }

    @Test
    fun `tile without onClick has no click action`() {
        showTile(onClick = null)

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
        composeRule.onNodeWithTag(TAG)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            .assert(hasText("Groceries"))
            .assert(hasContentDescription(formatCents(42_000)))
    }

    private companion object {
        const val TAG = "tile"
    }
}
