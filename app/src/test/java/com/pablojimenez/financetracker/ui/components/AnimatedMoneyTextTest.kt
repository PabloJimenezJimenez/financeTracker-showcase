package com.pablojimenez.financetracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.formatCents
import com.pablojimenez.financetracker.ui.theme.FinanceMotion
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.LocalReduceMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnimatedMoneyTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var cents by mutableLongStateOf(160_050L)

    private fun showMoney(reduceMotion: Boolean) {
        composeRule.setContent {
            FinanceTrackerTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                    AnimatedMoneyText(cents = cents, modifier = Modifier.testTag(TAG))
                }
            }
        }
    }

    private fun announced(): String? = composeRule.onNodeWithTag(TAG).fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.ContentDescription)?.single()

    /** Every text composed under the money text, read from the unmerged tree in order. */
    private fun composedTexts(): List<String> {
        val root = composeRule.onNodeWithTag(TAG, useUnmergedTree = true).fetchSemanticsNode()
        fun SemanticsNode.texts(): List<String> =
            config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                children.flatMap { it.texts() }
        return root.texts()
    }

    @Test
    fun `announces the formatted value as one node`() {
        showMoney(reduceMotion = false)

        composeRule.onAllNodesWithContentDescription(formatCents(160_050)).assertCountEquals(1)
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .assertCountEquals(0)
        assertEquals(formatCents(160_050), composedTexts().joinToString(""))
    }

    /** Changes the value and lets the recomposer schedule its next frame, without advancing the clock. */
    private fun changeTo(value: Long) {
        cents = value
        composeRule.waitForIdle()
    }

    @Test
    fun `announces only the new value while the digits roll`() {
        showMoney(reduceMotion = false)
        composeRule.mainClock.autoAdvance = false

        changeTo(-90_000)
        val announcements = mutableListOf(announced())
        repeat(25) {
            composeRule.mainClock.advanceTimeByFrame()
            announcements += announced()
        }

        assertEquals(listOf(formatCents(160_050), formatCents(-90_000)), announcements.distinct())
        assertEquals(formatCents(-90_000), announcements[1])
        composeRule.mainClock.autoAdvance = true
        composeRule.onAllNodesWithContentDescription(formatCents(-90_000)).assertCountEquals(1)
    }

    @Test
    fun `reduce motion shows the new value on the next frame`() {
        showMoney(reduceMotion = true)
        composeRule.mainClock.autoAdvance = false

        changeTo(-90_000)
        composeRule.mainClock.advanceTimeByFrame()

        assertEquals(listOf(formatCents(-90_000)), composedTexts())
        assertEquals(formatCents(-90_000), announced())
    }

    @Test
    fun `outgoing digits roll out over the medium duration`() {
        showMoney(reduceMotion = false)
        composeRule.mainClock.autoAdvance = false

        changeTo(-90_000)
        // The first frame recomposes with the new value; the roll's clock starts on the second.
        repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.mainClock.advanceTimeBy(FinanceMotion.DURATION_SHORT_MS.toLong())
        val midway = composedTexts()

        assertNotEquals(formatCents(-90_000), midway.joinToString(""))
        assertTrue(
            "outgoing digits are still composed: $midway",
            midway.size > formatCents(-90_000).length,
        )

        composeRule.mainClock.advanceTimeBy(
            (FinanceMotion.DURATION_MEDIUM_MS - FinanceMotion.DURATION_SHORT_MS).toLong(),
        )
        assertEquals(formatCents(-90_000), composedTexts().joinToString(""))
    }

    private fun showMoneyIn(width: Dp, reduceMotion: Boolean) {
        composeRule.setContent {
            FinanceTrackerTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                    Box(Modifier.width(width).testTag(CONTAINER)) {
                        AnimatedMoneyText(
                            cents = cents,
                            modifier = Modifier.testTag(TAG),
                            style = MaterialTheme.typography.displaySmall,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a very large amount fits its width on one line`() {
        cents = 9_007_199_254_740_993
        showMoneyIn(width = 200.dp, reduceMotion = false)

        composeRule.assertMoneyTextFits(TAG, CONTAINER)
        assertEquals(formatCents(9_007_199_254_740_993), composedTexts().joinToString(""))
    }

    @Test
    fun `a very large amount fits its width on one line with reduce motion`() {
        cents = 9_007_199_254_740_993
        showMoneyIn(width = 200.dp, reduceMotion = true)

        composeRule.assertMoneyTextFits(TAG, CONTAINER)
    }

    @Test
    fun `an amount that grows past its width still fits after the roll`() {
        cents = 100
        showMoneyIn(width = 200.dp, reduceMotion = false)

        composeRule.runOnIdle { cents = 9_007_199_254_740_993 }

        composeRule.assertMoneyTextFits(TAG, CONTAINER)
    }

    private companion object {
        const val TAG = "money"
        const val CONTAINER = "container"
    }
}
