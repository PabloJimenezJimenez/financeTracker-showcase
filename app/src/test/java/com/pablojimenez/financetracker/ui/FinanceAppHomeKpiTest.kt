package com.pablojimenez.financetracker.ui

import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.CategoryKind
import com.pablojimenez.financetracker.domain.Combination
import com.pablojimenez.financetracker.domain.CombinationKind
import com.pablojimenez.financetracker.domain.FakeFinanceRepository
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.domain.Kpi
import com.pablojimenez.financetracker.domain.KpiDirection
import com.pablojimenez.financetracker.domain.Measure
import com.pablojimenez.financetracker.domain.Metric
import com.pablojimenez.financetracker.domain.MetricPeriod
import com.pablojimenez.financetracker.domain.MetricSource
import com.pablojimenez.financetracker.domain.Transaction
import com.pablojimenez.financetracker.ui.components.VerdictIconKey
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

/** Money as Spanish formats it: "50,00 €" has a no-break space before the sign. */
private fun euros(text: String) = text.replace(" €", "\u00A0€")

/** The Net worth hero, the KPI tiles and the suggested KPIs on Home, through [FinanceApp]. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "es-rES-w411dp-h2000dp")
class FinanceAppHomeKpiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var repository = FakeFinanceRepository()

    /** Wednesday 2026-03-11 at noon, UTC: day 11 of 31 in March. */
    private val clock = Clock.fixed(Instant.parse("2026-03-11T12:00:00Z"), ZoneOffset.UTC)

    private fun launch() {
        composeRule.setContent {
            FinanceTrackerTheme(dynamicColor = false) {
                FinanceApp(repository = repository, clock = clock)
            }
        }
    }

    @Test
    fun `the hero is captioned Net worth and counts archived Accounts too`() {
        repository.givenAccount(Account(name = "Current", type = AccountType.Current, startingBalanceCents = 10_000))
        val old = repository.givenAccount(
            Account(name = "Old", type = AccountType.Savings, startingBalanceCents = 0, archived = true),
        )
        repository.givenTransaction(Transaction(accountId = old, categoryId = null, amountCents = 5_000, date = LocalDate.of(2026, 1, 5)))
        launch()

        composeRule.onNodeWithTag("balance")
            .assert(hasText("Net worth"))
            .assert(hasContentDescription(euros("150,00 €")))
        composeRule.onNodeWithText("Total balance").assertDoesNotExist()
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test
    fun `pinned Metrics sit between the hero and the Accounts, two per row, oldest first, unpinned ones left out`() {
        val account = repository.givenAccount(Account(name = "Current", type = AccountType.Current, startingBalanceCents = 0))
        val first = repository.givenMetric(Metric(name = "Spending", kpi = Kpi()))
        val unpinned = repository.givenMetric(Metric(name = "Not a KPI"))
        val second = repository.givenMetric(Metric(name = "Pay", source = MetricSource.Income, kpi = Kpi()))
        val third = repository.givenMetric(Metric(name = "Coffees", measure = Measure.Count, kpi = Kpi()))
        launch()

        composeRule.onNodeWithTag("kpi-$unpinned").assertDoesNotExist()
        composeRule.onNodeWithText("Not a KPI").assertDoesNotExist()
        val hero = bounds("balance")
        val (a, b, c) = listOf(first, second, third).map { bounds("kpi-$it") }
        assertTrue("the first KPI is under the hero", a.top >= hero.bottom)
        assertEquals("the first two KPIs share a row", a.top, b.top)
        assertTrue("the oldest KPI comes first", a.right <= b.left)
        assertTrue("the third KPI starts the next row", c.top >= a.bottom && c.left == a.left)
        assertTrue("the Accounts come after the KPIs", bounds("account-$account").top >= c.bottom)
        composeRule.onNodeWithTag("kpi-$first").assert(hasText("Spending"))
        composeRule.onNodeWithTag("kpi-$third").assert(hasText("Coffees")).assert(hasText("0"))
    }

    private fun category(name: String) = repository.savedCategories.first { it.name == name }

    private val current by lazy {
        repository.givenAccount(Account(name = "Current", type = AccountType.Current, startingBalanceCents = 0))
    }

    /** A Transaction of [cents] (negative for an expense) in the Category [category] on [date]. */
    private fun given(category: String, cents: Long, date: LocalDate = LocalDate.of(2026, 3, 2)) {
        repository.givenTransaction(
            Transaction(accountId = current, categoryId = category(category).id, amountCents = cents, date = date),
        )
    }

    /** An expense in Food of each of [cents], on its date. */
    private fun givenSpent(vararg cents: Pair<Long, LocalDate>) {
        cents.forEach { (amount, date) -> given("Food", -amount, date) }
    }

    private fun kpi(id: Long) = composeRule.onNodeWithTag("kpi-$id")

    private fun hasIcon(@DrawableRes icon: Int) = SemanticsMatcher.expectValue(VerdictIconKey, icon)

    private val budget = Kpi(target = 25_000, direction = KpiDirection.LowerIsBetter)

    @Test
    fun `a sum with a target that is heading past it shows Off track and a pace bar at so far over target`() {
        givenSpent(6_000L to LocalDate.of(2026, 3, 2), 4_000L to LocalDate.of(2026, 3, 11))
        val id = repository.givenMetric(Metric(name = "Spending", kpi = budget))
        launch()

        kpi(id)
            .assert(hasText("Spending"))
            .assert(hasText(euros("100,00 €")))
            .assert(hasText("Off track"))
            .assert(hasIcon(R.drawable.ic_error))
            .assert(hasText("so far", substring = true).not())
            .assert(hasText("on pace", substring = true).not())
        composeRule.onNodeWithTag("kpi-pace-$id", useUnmergedTree = true)
            .assert(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.4f, 0f..1f)))
        val bar = composeRule.onNodeWithTag("kpi-pace-$id", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val mark = composeRule.onNodeWithTag("kpi-pace-mark-$id", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("the mark sits at day 11 of 31", bar.left + bar.width * 11 / 31, mark.center.x, 1f)
    }

    @Test
    fun `a sum with a target that is heading under it shows On track`() {
        givenSpent(5_000L to LocalDate.of(2026, 3, 2))
        val id = repository.givenMetric(Metric(name = "Spending", kpi = budget))
        launch()

        kpi(id)
            .assert(hasText(euros("50,00 €")))
            .assert(hasText("On track"))
            .assert(hasIcon(R.drawable.ic_check_circle))
        composeRule.onNodeWithTag("kpi-pace-$id", useUnmergedTree = true)
            .assert(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.2f, 0f..1f)))
    }

    @Test
    fun `a ratio with a target shows the value against the target as text and no pace bar`() {
        given("Salary", 100_000)
        given("Food", -32_400)
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

        kpi(id)
            .assert(hasText("32,4%"))
            .assert(hasText("target 20,0%"))
            .assert(hasText("32,4% · target", substring = true).not())
            .assert(hasText("Off track"))
            .assert(hasIcon(R.drawable.ic_error))
        composeRule.onNodeWithTag("kpi-pace-$id", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a sum over custom dates is judged as it stands, with the target as text`() {
        givenSpent(10_000L to LocalDate.of(2026, 3, 2))
        val id = repository.givenMetric(
            Metric(
                name = "March",
                period = MetricPeriod.Custom(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)),
                kpi = budget,
            ),
        )
        launch()

        kpi(id)
            .assert(hasText(euros("target 250,00 €")))
            .assert(hasText("On track"))
            .assert(hasIcon(R.drawable.ic_check_circle))
        composeRule.onNodeWithTag("kpi-pace-$id", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a KPI with only a direction says Better or Worse, with the arrow showing which way the value moved`() {
        // Groceries: 100 € this month by the 11th against 150 € by February 11th, so down.
        givenSpent(10_000L to LocalDate.of(2026, 3, 2), 15_000L to LocalDate.of(2026, 2, 11))
        // Eating out: 30 € this month against nothing, so up.
        given("Restaurants", -3_000)
        val groceries = category("Groceries").id
        val eatingOut = category("Eating out").id
        val transport = category("Transport").id
        val lower = KpiDirection.LowerIsBetter
        val fellBetter = repository.givenMetric(Metric(name = "Groceries", categoryIds = setOf(groceries), kpi = Kpi(direction = lower)))
        val fellWorse = repository.givenMetric(
            Metric(name = "Groceries up", categoryIds = setOf(groceries), kpi = Kpi(direction = KpiDirection.HigherIsBetter)),
        )
        val roseWorse = repository.givenMetric(Metric(name = "Eating out", categoryIds = setOf(eatingOut), kpi = Kpi(direction = lower)))
        val tie = repository.givenMetric(Metric(name = "Transport", categoryIds = setOf(transport), kpi = Kpi(direction = lower)))
        launch()

        kpi(fellBetter).assert(hasText("Better")).assert(hasIcon(R.drawable.ic_trending_down))
        kpi(fellWorse).assert(hasText("Worse")).assert(hasIcon(R.drawable.ic_trending_down))
        kpi(roseWorse).assert(hasText("Worse")).assert(hasIcon(R.drawable.ic_trending_up))
        kpi(tie).assert(hasText("Better")).assert(hasIcon(R.drawable.ic_trending_up))
        composeRule.onNodeWithTag("kpi-pace-$fellBetter", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a KPI with neither target nor direction shows its value alone`() {
        givenSpent(10_000L to LocalDate.of(2026, 3, 2))
        val id = repository.givenMetric(Metric(name = "Spending", kpi = Kpi()))
        launch()

        kpi(id)
            .assert(hasText(euros("100,00 €")))
            .assert(SemanticsMatcher.keyNotDefined(VerdictIconKey))
            .assert(hasContentDescription(euros("Spending, 100,00 €")))
        listOf("On track", "Off track", "Better", "Worse").forEach { word ->
            kpi(id).assert(hasText(word).not())
        }
    }

    @Test
    fun `a KPI with no value shows a dash and the remove icon`() {
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

        kpi(id)
            .assert(hasText("—"))
            .assert(hasIcon(R.drawable.ic_remove))
            .assert(hasContentDescription("Share spent, No value"))
            .assert(hasText("Off track").not())
    }

    @Test
    fun `a KPI tile is one click target read as its name, value and Verdict, and opens its detail`() {
        givenSpent(10_000L to LocalDate.of(2026, 3, 2))
        val id = repository.givenMetric(Metric(name = "Spending", kpi = budget))
        launch()

        kpi(id)
            .assert(hasContentDescription(euros("Spending, 100,00 €, Off track")))
            .assertHasClickAction()
        composeRule.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("kpi-$id")), useUnmergedTree = true)
            .assertCountEquals(0)

        kpi(id).performClick()

        composeRule.onNodeWithTag("kpi-detail").assertIsDisplayed()
    }

    private fun suggestion(text: String) =
        composeRule.onNode(hasText(text) and hasClickAction() and hasAnyAncestor(hasTestTag("kpi-suggestions")))

    @Test
    fun `with no pinned Metric Home offers three suggested KPIs in a full-width tile, and Build a Metric opens the builder`() {
        val account = repository.givenAccount(Account(name = "Cash", type = AccountType.Cash, startingBalanceCents = 0))
        repository.givenMetric(Metric(name = "Not a KPI"))
        launch()

        composeRule.onNodeWithTag("kpi-suggestions").assert(hasText("Suggested KPIs"))
        listOf("Spending this month", "Savings rate", "Groceries this month").forEach { suggestion(it).assertIsDisplayed() }
        val tile = bounds("kpi-suggestions")
        val hero = bounds("balance")
        assertEquals("the tile is as wide as the hero", hero.width, tile.width, 1f)
        assertTrue("the tile is under the hero", tile.top >= hero.bottom)
        assertTrue("the Accounts come after it", bounds("account-$account").top >= tile.bottom)

        suggestion("Build a Metric").performClick()

        composeRule.onNodeWithText("New Metric").assertIsDisplayed()
    }

    @Test
    fun `with a pinned Metric there are no suggestions`() {
        val id = repository.givenMetric(Metric(name = "Spending", kpi = Kpi()))
        launch()

        kpi(id).assertIsDisplayed()
        composeRule.onNodeWithTag("kpi-suggestions").assertDoesNotExist()
    }

    private fun tapSuggestion(text: String) {
        suggestion(text).performClick()
        composeRule.waitForIdle()
    }

    private val lowerIsBetter = Kpi(direction = KpiDirection.LowerIsBetter)

    @Test
    fun `Spending this month saves a pinned Spending sum for this month, and its tile replaces the suggestions`() {
        launch()

        tapSuggestion("Spending this month")

        val saved = repository.savedMetrics.single()
        assertEquals(
            Metric(
                id = saved.id,
                name = "Spending this month",
                source = MetricSource.Spending,
                measure = Measure.Sum,
                period = MetricPeriod.ThisMonth,
                kpi = lowerIsBetter,
            ),
            saved,
        )
        kpi(saved.id).assertIsDisplayed().assert(hasText("Spending this month"))
        composeRule.onNodeWithTag("kpi-suggestions").assertDoesNotExist()
    }

    @Test
    fun `Groceries this month saves the same sum filtered to the top-level Groceries Category`() {
        launch()

        tapSuggestion("Groceries this month")

        val saved = repository.savedMetrics.single()
        assertEquals(
            Metric(id = saved.id, name = "Groceries this month", categoryIds = setOf(category("Groceries").id), kpi = lowerIsBetter),
            saved,
        )
        kpi(saved.id).assertIsDisplayed()
    }

    /** The starter Categories with the top-level Groceries one (seed id [groceriesId]) renamed. */
    private fun withGroceriesRenamed(groceriesId: Long, name: String) =
        FakeFinanceRepository().savedCategories.map { if (it.id == groceriesId) it.copy(name = name) else it }

    @Test
    fun `Groceries this month follows the starter Groceries Category when the owner renames it`() {
        val groceriesId = category("Groceries").id
        repository = FakeFinanceRepository(startingCategories = withGroceriesRenamed(groceriesId, "Supermarket"))
        launch()

        tapSuggestion("Groceries this month")

        assertEquals(setOf(groceriesId), repository.savedMetrics.single().categoryIds)
    }

    @Test
    fun `Groceries this month is not offered without the starter Groceries Category, whatever another is called`() {
        repository = FakeFinanceRepository(
            startingCategories = listOf(
                Category(100, "Groceries", CategoryKind.Spending),
                Category(101, "Food", CategoryKind.Spending, parentId = 100),
                Category(102, "Salary", CategoryKind.Income),
            ),
        )
        launch()

        suggestion("Spending this month").assertIsDisplayed()
        suggestion("Groceries this month").assertDoesNotExist()
    }

    @Test
    fun `Savings rate saves Income, Spending and Savings this month and pins their ratio, shown as a percentage`() {
        given("Salary", 200_000)
        given("Food", -150_000)
        launch()

        tapSuggestion("Savings rate")

        val (income, spending, savings, rate) = repository.savedMetrics.also { assertEquals(4, it.size) }
        assertEquals(Metric(id = income.id, name = "Income this month", source = MetricSource.Income), income)
        assertEquals(Metric(id = spending.id, name = "Spending this month", source = MetricSource.Spending), spending)
        assertEquals(
            Metric(
                id = savings.id,
                name = "Savings this month",
                combination = Combination(CombinationKind.Difference, income.id, spending.id),
            ),
            savings,
        )
        assertEquals(
            Metric(
                id = rate.id,
                name = "Savings rate",
                combination = Combination(CombinationKind.Ratio, savings.id, income.id),
                kpi = Kpi(direction = KpiDirection.HigherIsBetter),
            ),
            rate,
        )
        kpi(rate.id).assert(hasText("25,0%"))
        composeRule.onNodeWithTag("kpi-suggestions").assertDoesNotExist()
    }
}
