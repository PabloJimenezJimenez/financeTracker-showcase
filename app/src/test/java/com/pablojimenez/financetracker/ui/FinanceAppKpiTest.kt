package com.pablojimenez.financetracker.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.FakeFinanceRepository
import com.pablojimenez.financetracker.domain.Kpi
import com.pablojimenez.financetracker.domain.KpiDirection
import com.pablojimenez.financetracker.domain.Metric
import com.pablojimenez.financetracker.domain.MetricPeriod
import com.pablojimenez.financetracker.domain.MetricSource
import com.pablojimenez.financetracker.domain.Transaction
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Money as Spanish formats it: "50,00 €" has a no-break space before the sign. */
private fun euros(text: String) = text.replace(" €", "\u00A0€")

/** KPI pinning in the Metric builder and on the Metrics screen, through [FinanceApp]. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "es-rES-w411dp-h2000dp")
class FinanceAppKpiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repository = FakeFinanceRepository()

    /** Wednesday 2026-03-11 at noon, UTC: day 11 of 31 in March. */
    private val clock = Clock.fixed(Instant.parse("2026-03-11T12:00:00Z"), ZoneOffset.UTC)

    private fun launch() {
        composeRule.setContent {
            FinanceTrackerTheme(dynamicColor = false) {
                FinanceApp(repository = repository, clock = clock)
            }
        }
    }

    private fun openMetrics() {
        composeRule.onNodeWithTag("nav-metrics").performClick()
    }

    private val food get() = repository.savedCategories.first { it.name == "Food" }

    /** €100 spent this month by the 11th, and €150 by February 11th. */
    private fun givenSpending() {
        val current = repository.givenAccount(
            Account(name = "Current", type = AccountType.Current, startingBalanceCents = 0),
        )
        listOf(
            6_000L to LocalDate.of(2026, 3, 2),
            4_000L to LocalDate.of(2026, 3, 11),
            15_000L to LocalDate.of(2026, 2, 11),
        ).forEach { (cents, date) ->
            repository.givenTransaction(
                Transaction(accountId = current, categoryId = food.id, amountCents = -cents, date = date),
            )
        }
    }

    private fun metric(id: Long): Metric = repository.savedMetrics.first { it.id == id }

    @Test
    fun `the switch on a tile pins and unpins its Metric without opening the builder`() {
        val id = repository.givenMetric(Metric(name = "Groceries"))
        launch()
        openMetrics()

        composeRule.onNodeWithTag("pin-$id")
            .assert(hasContentDescription("Pin as KPI"))
            .assertIsOff()
            .performClick()

        composeRule.onNodeWithTag("pin-$id").assertIsOn()
        assertEquals(Kpi(), metric(id).kpi)
        composeRule.onNodeWithText("Edit Metric").assertDoesNotExist()
        composeRule.onNodeWithTag("metric-$id").assert(hasText("KPI"))

        composeRule.onNodeWithTag("pin-$id").performClick()

        composeRule.onNodeWithTag("pin-$id").assertIsOff()
        assertNull(metric(id).kpi)
        composeRule.onNodeWithTag("metric-$id").assert(hasText("KPI").not())
    }

    @Test
    fun `unpinning from the list drops the target and direction`() {
        val id = repository.givenMetric(
            Metric(name = "Groceries", kpi = Kpi(target = 25_000, direction = KpiDirection.LowerIsBetter)),
        )
        launch()
        openMetrics()

        composeRule.onNodeWithTag("pin-$id").performClick()
        composeRule.onNodeWithTag("pin-$id").performClick()

        assertEquals(Kpi(), metric(id).kpi)
    }

    @Test
    fun `a pinned tile shows the KPI mark and its Verdict line, an unpinned one neither`() {
        givenSpending()
        val onPace = repository.givenMetric(
            Metric(name = "Groceries", kpi = Kpi(target = 25_000, direction = KpiDirection.LowerIsBetter)),
        )
        val better = repository.givenMetric(Metric(name = "Spending", kpi = Kpi(direction = KpiDirection.LowerIsBetter)))
        val worse = repository.givenMetric(Metric(name = "Spent", kpi = Kpi(direction = KpiDirection.HigherIsBetter)))
        val noVerdict = repository.givenMetric(Metric(name = "Pinned", kpi = Kpi()))
        val unpinned = repository.givenMetric(Metric(name = "Unpinned"))
        launch()
        openMetrics()

        composeRule.onNodeWithTag("metric-$onPace").assertIsDisplayed()
            .assert(hasText("KPI"))
            .assert(hasText(euros("100,00 € so far, on pace for 281,82 € of 250,00 €: off track")))
        composeRule.onNodeWithTag("metric-$better").assert(hasText("KPI"))
            .assert(hasText(euros("Better than last period (150,00 €)")))
        composeRule.onNodeWithTag("metric-$worse").assert(hasText("KPI"))
            .assert(hasText(euros("Worse than last period (150,00 €)")))
        composeRule.onNodeWithTag("metric-$noVerdict").assert(hasText("KPI"))
            .assert(hasText("than last period", substring = true).not())
            .assert(hasText("so far", substring = true).not())
        composeRule.onNodeWithTag("metric-$unpinned").assert(hasText("KPI").not())
            .assert(hasText("than last period", substring = true).not())
        composeRule.onNodeWithTag("pin-$unpinned").assertIsOff()
        composeRule.onNodeWithTag("pin-$onPace").assertIsOn()
    }

    @Test
    fun `a KPI judged on its current value shows the target and whether it is on track`() {
        givenSpending()
        val custom = repository.givenMetric(
            Metric(
                name = "March so far",
                period = MetricPeriod.Custom(
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31),
                ),
                kpi = Kpi(target = 25_000, direction = KpiDirection.LowerIsBetter),
            ),
        )
        launch()
        openMetrics()

        composeRule.onNodeWithTag("metric-$custom").assert(hasText(euros("Target 250,00 €: on track")))
    }

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun newMetric(name: String) {
        composeRule.onNodeWithText("New Metric").performClick()
        composeRule.onNodeWithTag("metric-name").performTextInput(name)
    }

    private fun direction(direction: KpiDirection) = composeRule.onNodeWithTag("metric-direction-${direction.name}")

    private fun save() {
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `switching Pin as KPI on shows an optional target and the direction, Lower is better for Spending`() {
        launch()
        openMetrics()
        newMetric("Groceries")
        composeRule.onNodeWithTag("metric-target").assertDoesNotExist()
        direction(KpiDirection.LowerIsBetter).assertDoesNotExist()

        tap("metric-pin")

        composeRule.onNodeWithTag("metric-pin").assertIsOn()
        composeRule.onNodeWithTag("metric-target").performScrollTo().assert(hasText("Target (optional)"))
        direction(KpiDirection.LowerIsBetter).performScrollTo().assert(hasText("Lower is better")).assertIsSelected()
        direction(KpiDirection.HigherIsBetter).assert(hasText("Higher is better")).assertIsNotSelected()

        tap("metric-pin")

        composeRule.onNodeWithTag("metric-target").assertDoesNotExist()
        direction(KpiDirection.LowerIsBetter).assertDoesNotExist()
    }

    @Test
    fun `Income pre-selects Higher is better, and Save stores the target in cents`() {
        launch()
        openMetrics()
        newMetric("Pay")
        tap("metric-source-Income")

        tap("metric-pin")
        direction(KpiDirection.HigherIsBetter).performScrollTo().assertIsSelected()
        direction(KpiDirection.LowerIsBetter).assertIsNotSelected()
        composeRule.onNodeWithTag("metric-target").performScrollTo().performTextInput("2100,5")
        save()

        assertEquals(Kpi(target = 210_050, direction = KpiDirection.HigherIsBetter), repository.savedMetrics.single().kpi)
    }

    @Test
    fun `pinned with no target, Save stores the pin and the direction`() {
        launch()
        openMetrics()
        newMetric("Groceries")

        tap("metric-pin")
        save()

        assertEquals(Kpi(direction = KpiDirection.LowerIsBetter), repository.savedMetrics.single().kpi)
    }

    @Test
    fun `a combined Metric has no direction selected, and a target without one is refused until one is picked`() {
        val pay = repository.givenMetric(Metric(name = "Pay", source = MetricSource.Income))
        val spent = repository.givenMetric(Metric(name = "Spent"))
        launch()
        openMetrics()
        newMetric("Share spent")
        tap("metric-source-Combined")
        tap("metric-operand-a")
        composeRule.onNodeWithTag("metric-operand-option-$spent").performScrollTo().performClick()
        tap("metric-operand-b")
        composeRule.onNodeWithTag("metric-operand-option-$pay").performScrollTo().performClick()

        tap("metric-pin")
        direction(KpiDirection.LowerIsBetter).performScrollTo().assertIsNotSelected()
        direction(KpiDirection.HigherIsBetter).assertIsNotSelected()
        composeRule.onNodeWithTag("metric-target").performScrollTo().performTextInput("-12,5")
        save()

        composeRule.onNodeWithText("Pick a direction for the target").assertIsDisplayed()
        composeRule.onNodeWithTag("metric-direction")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Pick a direction for the target"))
        composeRule.onNodeWithText("New Metric").assertIsDisplayed()
        assertEquals(listOf("Pay", "Spent"), repository.savedMetrics.map { it.name })

        tap("metric-direction-LowerIsBetter")
        composeRule.onNodeWithText("Pick a direction for the target").assertDoesNotExist()
        save()

        // A percentage's target is in tenths: −12.5% is −125.
        assertEquals(Kpi(target = -125, direction = KpiDirection.LowerIsBetter), repository.savedMetrics.last().kpi)
    }

    @Test
    fun `clearing the pre-selected direction with a target entered refuses Save`() {
        launch()
        openMetrics()
        newMetric("Coffees")
        tap("metric-measure-Count")
        tap("metric-pin")
        composeRule.onNodeWithTag("metric-target").performScrollTo().performTextInput("12")

        tap("metric-direction-LowerIsBetter")
        direction(KpiDirection.LowerIsBetter).assertIsNotSelected()
        save()

        composeRule.onNodeWithText("Pick a direction for the target").assertIsDisplayed()
        assertEquals(emptyList<Metric>(), repository.savedMetrics)

        tap("metric-direction-HigherIsBetter")
        save()

        assertEquals(Kpi(target = 12, direction = KpiDirection.HigherIsBetter), repository.savedMetrics.single().kpi)
    }

    @Test
    fun `a count target takes only a whole number`() {
        launch()
        openMetrics()
        newMetric("Coffees")
        tap("metric-measure-Count")
        tap("metric-pin")

        composeRule.onNodeWithTag("metric-target").performScrollTo().performTextInput("12,5")

        composeRule.onNodeWithTag("metric-target").assert(hasText("12,5").not())
    }

    @Test
    fun `opening a pinned Metric shows its target and direction, and switching it off saves it unpinned`() {
        val id = repository.givenMetric(
            Metric(name = "Groceries", kpi = Kpi(target = 25_050, direction = KpiDirection.HigherIsBetter)),
        )
        launch()
        openMetrics()
        composeRule.onNodeWithTag("metric-$id").performClick()

        composeRule.onNodeWithTag("metric-pin").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("metric-target").performScrollTo().assert(hasText("250,50"))
        direction(KpiDirection.HigherIsBetter).assertIsSelected()
        direction(KpiDirection.LowerIsBetter).assertIsNotSelected()

        tap("metric-pin")
        save()

        assertNull(metric(id).kpi)
    }

    @Test
    fun `saving an edit of a pinned Metric keeps its KPI`() {
        val kpi = Kpi(target = 25_000, direction = KpiDirection.LowerIsBetter)
        val id = repository.givenMetric(Metric(name = "Groceries", kpi = kpi))
        launch()
        openMetrics()
        composeRule.onNodeWithTag("metric-$id").performClick()

        composeRule.onNodeWithTag("metric-name").performTextReplacement("Food")
        save()

        assertEquals(Metric(id = id, name = "Food", kpi = kpi), metric(id))
    }
}
