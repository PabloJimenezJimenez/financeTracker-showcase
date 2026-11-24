package com.pablojimenez.financetracker.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.Combination
import com.pablojimenez.financetracker.domain.CombinationKind
import com.pablojimenez.financetracker.domain.FakeFinanceRepository
import com.pablojimenez.financetracker.domain.Kpi
import com.pablojimenez.financetracker.domain.KpiDirection
import com.pablojimenez.financetracker.domain.Measure
import com.pablojimenez.financetracker.domain.Metric
import com.pablojimenez.financetracker.domain.MetricPeriod
import com.pablojimenez.financetracker.domain.MetricSource
import com.pablojimenez.financetracker.domain.Transaction
import com.pablojimenez.financetracker.ui.kpi.KpiChartBarsKey
import com.pablojimenez.financetracker.ui.kpi.KpiChartProjectionKey
import com.pablojimenez.financetracker.ui.kpi.KpiChartTargetKey
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A KPI's detail screen, opened from its tile on Home, through [FinanceApp]. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rGB-w411dp-h2000dp")
class FinanceAppKpiDetailTest {
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

    private val current by lazy {
        repository.givenAccount(Account(name = "Current", type = AccountType.Current, startingBalanceCents = 0))
    }

    private fun category(name: String) = repository.savedCategories.first { it.name == name }

    /** A Transaction of [cents] (negative for an expense) in the Category [category] on [date]. */
    private fun given(category: String, cents: Long, date: LocalDate) {
        repository.givenTransaction(
            Transaction(accountId = current, categoryId = category(category).id, amountCents = cents, date = date),
        )
    }

    private val budget = Kpi(target = 25_000, direction = KpiDirection.LowerIsBetter)

    /** €100.00 spent this month by the 11th, pinned with a €250.00 budget, lower is better. */
    private fun givenSpendingKpi(): Long {
        given("Food", -6_000, LocalDate.of(2026, 3, 2))
        given("Food", -4_000, LocalDate.of(2026, 3, 11))
        return repository.givenMetric(Metric(name = "Spending", kpi = budget))
    }

    private fun open(id: Long) {
        composeRule.onNodeWithTag("kpi-$id").performClick()
        composeRule.waitForIdle()
    }

    private fun inDetail(text: String, substring: Boolean = false) =
        composeRule.onNode(hasText(text, substring = substring) and hasAnyAncestor(hasTestTag("kpi-detail")))

    @Test
    fun `tapping a KPI tile opens its detail with the name, the value and the full Verdict note, and Back returns Home`() {
        val id = givenSpendingKpi()
        launch()

        open(id)

        composeRule.onNodeWithTag("kpi-detail").assertIsDisplayed()
        inDetail("Spending").assertIsDisplayed()
        composeRule.onNodeWithTag("kpi-value")
            .assert(hasContentDescription("€100.00"))
            .assert(hasText("€100.00 so far, on pace for €281.82 of €250.00: off track"))
        composeRule.onNodeWithTag("nav-home").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithTag("kpi-detail").assertDoesNotExist()
        composeRule.onNodeWithTag("kpi-$id").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-home").assertIsDisplayed()
    }

    @Test
    fun `a KPI with no value shows a dash and the reason`() {
        val spent = repository.givenMetric(Metric(name = "Spent"))
        val pay = repository.givenMetric(Metric(name = "Pay", source = MetricSource.Income))
        val id = repository.givenMetric(
            Metric(
                name = "Share spent",
                combination = Combination(CombinationKind.Ratio, spent, pay),
                kpi = Kpi(target = 200, direction = KpiDirection.LowerIsBetter),
            ),
        )
        launch()

        open(id)

        composeRule.onNodeWithTag("kpi-value")
            .assert(hasText("—"))
            .assert(hasText("Can't divide by zero: Pay is €0.00 in this period"))
    }

    private fun chart() = composeRule.onNodeWithTag("kpi-chart", useUnmergedTree = true)

    private fun hasBars(vararg amounts: Long?) = SemanticsMatcher.expectValue(KpiChartBarsKey, amounts.toList())

    private fun hasTarget(amount: Long?) = SemanticsMatcher.expectValue(KpiChartTargetKey, amount)

    private fun hasProjection(amount: Long?) = SemanticsMatcher.expectValue(KpiChartProjectionKey, amount)

    @Test
    fun `the chart exposes the last 12 months oldest first, the target and this month's projection`() {
        given("Food", -15_000, LocalDate.of(2026, 2, 11))
        given("Food", -3_000, LocalDate.of(2025, 4, 30))
        val id = givenSpendingKpi()
        launch()

        open(id)

        chart()
            .assert(hasBars(3_000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 15_000, 10_000))
            .assert(hasTarget(25_000))
            .assert(hasProjection(28_182))
            .assert(hasContentDescription("Bar chart of the last 12 periods; values listed below"))
        composeRule.onNode(hasText("Last 12 months") and hasAnyAncestor(hasTestTag("kpi-detail"))).assertIsDisplayed()
    }

    @Test
    fun `with only a direction the chart has no target, and a ratio has no projection and a gap where it has no value`() {
        given("Salary", 100_000, LocalDate.of(2026, 3, 1))
        given("Food", -32_400, LocalDate.of(2026, 3, 2))
        given("Salary", 100_000, LocalDate.of(2026, 1, 1))
        given("Food", -50_000, LocalDate.of(2026, 1, 2))
        val spent = repository.givenMetric(Metric(name = "Spent"))
        val pay = repository.givenMetric(Metric(name = "Pay", source = MetricSource.Income))
        val id = repository.givenMetric(
            Metric(
                name = "Share spent",
                combination = Combination(CombinationKind.Ratio, spent, pay),
                kpi = Kpi(direction = KpiDirection.LowerIsBetter),
            ),
        )
        launch()

        open(id)

        chart()
            .assert(hasBars(null, null, null, null, null, null, null, null, null, 500, null, 324))
            .assert(hasTarget(null))
            .assert(hasProjection(null))
    }

    @Test
    fun `a KPI over custom dates shows its value but no chart and no period list`() {
        given("Food", -10_000, LocalDate.of(2026, 3, 2))
        val id = repository.givenMetric(
            Metric(
                name = "March",
                period = MetricPeriod.Custom(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)),
                kpi = budget,
            ),
        )
        launch()

        open(id)

        composeRule.onNodeWithTag("kpi-value")
            .assert(hasContentDescription("€100.00"))
            .assert(hasText("Target €250.00: on track"))
        chart().assertDoesNotExist()
        composeRule.onNodeWithTag("kpi-periods").assertDoesNotExist()
    }

    private fun periodRows(): List<String> =
        composeRule.onAllNodesWithTag("kpi-period").fetchSemanticsNodes().map { node ->
            node.config[SemanticsProperties.Text].joinToString(" | ")
        }

    @Test
    fun `the period list shows the 12 months newest first, the current one so far, a dash before the first Transaction`() {
        // The app's first Transaction is pay on 15 December 2025: from then on a month with no
        // spending is a real zero; before it there is no data, so a gap.
        given("Salary", 100_000, LocalDate.of(2025, 12, 15))
        given("Food", -15_000, LocalDate.of(2026, 2, 11))
        val id = givenSpendingKpi()
        launch()

        open(id)

        val rows = periodRows()
        assertEquals(12, rows.size)
        assertEquals(
            listOf(
                "March 2026 · so far | €100.00",
                "February 2026 | €150.00",
                "January 2026 | €0.00",
                "December 2025 | €0.00",
                "November 2025 | —",
            ),
            rows.take(5),
        )
        assertEquals("April 2025 | —", rows.last())
        chart().assert(hasBars(null, null, null, null, null, null, null, null, 0, 0, 15_000, 10_000))
    }

    @Test
    fun `weeks are listed by their Monday, years by their number, and a period with no value or before the first Transaction shows a dash`() {
        given("Food", -2_000, LocalDate.of(2026, 3, 3))
        val weekly = repository.givenMetric(
            Metric(name = "Per expense", measure = Measure.AveragePerTransaction, period = MetricPeriod.ThisWeek, kpi = Kpi()),
        )
        val yearly = repository.givenMetric(Metric(name = "Yearly", period = MetricPeriod.ThisYear, kpi = Kpi()))
        launch()

        open(weekly)

        assertEquals(
            listOf("Week of 9 Mar · so far | —", "Week of 2 Mar | €20.00", "Week of 23 Feb | —"),
            periodRows().take(3),
        )
        composeRule.onNode(hasText("Last 12 weeks") and hasAnyAncestor(hasTestTag("kpi-detail"))).assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back").performClick()
        open(yearly)

        assertEquals(listOf("2026 · so far | €20.00", "2025 | —"), periodRows().take(2))
        assertEquals("2015 | —", periodRows().last())
        composeRule.onNode(hasText("Last 12 years") and hasAnyAncestor(hasTestTag("kpi-detail"))).assertIsDisplayed()
    }

    private fun metric(id: Long): Metric? = repository.savedMetrics.firstOrNull { it.id == id }

    private fun save() {
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()
    }

    private fun assertBackHome() {
        composeRule.onNodeWithTag("kpi-detail").assertDoesNotExist()
        composeRule.onNodeWithTag("balance").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-home").assertIsDisplayed()
    }

    @Test
    fun `Edit Metric opens the builder on that Metric, and after saving a new name the detail shows it`() {
        val id = givenSpendingKpi()
        launch()
        open(id)

        composeRule.onNodeWithTag("kpi-edit").assert(hasText("Edit Metric")).performClick()

        composeRule.onNodeWithTag("metric-name").assert(hasText("Spending")).performTextReplacement("Food budget")
        save()

        composeRule.onNodeWithTag("kpi-detail").assertIsDisplayed()
        inDetail("Food budget").assertIsDisplayed()
        assertEquals(Metric(id = id, name = "Food budget", kpi = budget), metric(id))
    }

    @Test
    fun `Remove from KPIs unpins the Metric and returns Home, where its tile is gone and Metrics still lists it`() {
        val other = repository.givenMetric(Metric(name = "Coffees", measure = Measure.Count, kpi = Kpi()))
        val id = givenSpendingKpi()
        launch()
        open(id)

        composeRule.onNodeWithTag("kpi-remove").assert(hasText("Remove from KPIs")).performClick()
        composeRule.waitForIdle()

        assertEquals(Metric(id = id, name = "Spending", kpi = null), metric(id))
        assertBackHome()
        composeRule.onNodeWithTag("kpi-$id").assertDoesNotExist()
        composeRule.onNodeWithTag("kpi-$other").assertIsDisplayed()
        composeRule.onNodeWithTag("kpi-suggestions").assertDoesNotExist()

        composeRule.onNodeWithTag("nav-metrics").performClick()

        composeRule.onNodeWithTag("metric-$id").assertIsDisplayed().assert(hasText("KPI").not())
        composeRule.onNodeWithTag("pin-$id").assertIsOff()
    }

    @Test
    fun `removing the last KPI brings back the suggestions`() {
        val id = givenSpendingKpi()
        launch()
        open(id)

        composeRule.onNodeWithTag("kpi-remove").performClick()
        composeRule.waitForIdle()

        assertBackHome()
        composeRule.onNodeWithTag("kpi-suggestions").assertIsDisplayed()
    }

    @Test
    fun `unpinning the Metric in the builder closes the detail back to Home`() {
        val id = givenSpendingKpi()
        launch()
        open(id)
        composeRule.onNodeWithTag("kpi-edit").performClick()

        composeRule.onNodeWithTag("metric-pin").performScrollTo().performClick()
        save()

        assertEquals(null, metric(id)?.kpi)
        assertBackHome()
        composeRule.onNodeWithTag("kpi-$id").assertDoesNotExist()
    }

    @Test
    fun `deleting the Metric in the builder closes the detail back to Home`() {
        val id = givenSpendingKpi()
        launch()
        open(id)
        composeRule.onNodeWithTag("kpi-edit").performClick()

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()

        assertEquals(null, metric(id))
        assertBackHome()
    }

    @Test
    fun `a long name in the top bar stays on one line and ends with an ellipsis`() {
        val id = repository.givenMetric(
            Metric(name = "Groceries, eating out and everything else I spend on food each month", kpi = budget),
        )
        launch()

        open(id)

        val title = composeRule.onNode(
            hasText("Groceries", substring = true) and hasAnyAncestor(hasTestTag("kpi-detail")),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        title.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertEquals(1, layouts.single().lineCount)
        assertTrue("the title is cut with an ellipsis", layouts.single().isLineEllipsized(0))
    }

    @Test
    @Config(qualifiers = "en-rGB-w411dp-h2000dp", fontScale = 2f)
    fun `at a large font size Remove from KPIs wraps under Edit Metric`() {
        val id = givenSpendingKpi()
        launch()

        open(id)

        val edit = composeRule.onNodeWithTag("kpi-edit").fetchSemanticsNode().boundsInRoot
        val remove = composeRule.onNodeWithTag("kpi-remove").fetchSemanticsNode().boundsInRoot
        assertTrue("Remove from KPIs at $remove is under Edit Metric at $edit", remove.top >= edit.bottom)
        assertEquals("both start at the margin", edit.left, remove.left, 1f)
    }
}
