package com.pablojimenez.financetracker.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.formatCents
import com.pablojimenez.financetracker.ui.components.TransactionListDay
import com.pablojimenez.financetracker.ui.components.TransactionListRow
import com.pablojimenez.financetracker.ui.components.assertMoneyTextFits
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun showHome(state: HomeState) {
        composeRule.setContent {
            FinanceTrackerTheme(dynamicColor = false) {
                HomeScreen(state = state, onAddAccount = {}, onAddTransaction = {}, onAddTransfer = {}, onOpenTransaction = {}, onOpenAccount = {}, onOpenKpi = {}, onReorderKpis = {}, onSuggestion = {}, onBuildMetric = {}, onOpenSettings = {})
            }
        }
    }

    @Test
    fun `shows empty state when there are no transactions`() {
        showHome(HomeState())

        composeRule.onNodeWithText("No transactions yet").assertIsDisplayed()
    }

    @Test
    fun `hero tile announces the Net worth`() {
        showHome(HomeState(netWorthCents = 160_000))

        composeRule.onNodeWithTag("balance")
            .assertIsDisplayed()
            .assert(hasText("Net worth"))
            .assert(hasContentDescription(formatCents(160_000)))
    }

    @Test
    fun `hero balance fits the tile at 200 percent font size`() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                FinanceTrackerTheme(dynamicColor = false) {
                    HomeScreen(state = HomeState(netWorthCents = 123_456_789), onAddAccount = {}, onAddTransaction = {}, onAddTransfer = {}, onOpenTransaction = {}, onOpenAccount = {}, onOpenKpi = {}, onReorderKpis = {}, onSuggestion = {}, onBuildMetric = {}, onOpenSettings = {})
                }
            }
        }

        composeRule.assertMoneyTextFits(textTag = "balance-amount", containerTag = "balance")
        composeRule.onNodeWithTag("balance").assert(hasContentDescription(formatCents(123_456_789)))
    }

    @Test
    fun `a transaction without a category shows as Uncategorised`() {
        showHome(
            HomeState(
                days = listOf(
                    TransactionListDay(
                        LocalDate.of(2026, 3, 10),
                        listOf(TransactionListRow(7, category = null, merchant = null, amountCents = -999)),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag("transaction-7").assert(hasText("Uncategorised"))
    }
}
