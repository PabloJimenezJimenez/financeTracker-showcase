package com.pablojimenez.financetracker.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AverageDivisor
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.CategoryKind
import com.pablojimenez.financetracker.domain.Combination
import com.pablojimenez.financetracker.domain.CombinationKind
import com.pablojimenez.financetracker.domain.FakeFinanceRepository
import com.pablojimenez.financetracker.domain.Measure
import com.pablojimenez.financetracker.domain.Merchant
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Money as Spanish formats it: "50,00 €" has a no-break space before the sign. */
private fun euros(text: String) = text.replace(' ', '\u00A0')

/** A Metric value: money is read out as its content description, a count or "—" as text. */
private fun hasValue(text: String): SemanticsMatcher = hasContentDescription(text) or hasText(text)

/** The Home · Metrics bottom bar, the Metrics screen and the Metric builder, through [FinanceApp]. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "es-rES-w411dp-h2000dp")
class FinanceAppMetricsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repository = FakeFinanceRepository()

    /** Wednesday 2026-03-11 at noon, UTC. */
    private val clock = Clock.fixed(Instant.parse("2026-03-11T12:00:00Z"), ZoneOffset.UTC)

    private fun launch() {
        composeRule.setContent {
            FinanceTrackerTheme(dynamicColor = false) {
                FinanceApp(repository = repository, clock = clock)
            }
        }
    }

    private fun total() = composeRule.onNodeWithTag("balance")

    private fun pressBack() {
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun openMetrics() {
        composeRule.onNodeWithTag("nav-metrics").performClick()
    }

    @Test
    fun `Home shows a bottom bar with Home selected and Metrics`() {
        launch()

        composeRule.onNodeWithTag("nav-home").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag("nav-metrics").assertIsDisplayed().assertIsNotSelected()
    }

    @Test
    fun `tapping Metrics shows No Metrics yet with the bar still shown and Metrics selected`() {
        launch()

        openMetrics()

        composeRule.onNodeWithText("No Metrics yet").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-metrics").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag("nav-home").assertIsNotSelected()
        total().assertDoesNotExist()
    }

    @Test
    fun `back from Metrics returns to Home`() {
        launch()
        openMetrics()

        pressBack()

        total().assertIsDisplayed()
        composeRule.onNodeWithText("No Metrics yet").assertDoesNotExist()
        composeRule.onNodeWithTag("nav-home").assertIsSelected()
    }

    @Test
    fun `selecting Home in the bar goes back to Home`() {
        launch()
        openMetrics()

        composeRule.onNodeWithTag("nav-home").performClick()

        total().assertIsDisplayed()
        composeRule.onNodeWithText("No Metrics yet").assertDoesNotExist()
        composeRule.onNodeWithTag("nav-home").assertIsSelected()
    }

    @Test
    fun `the bar is hidden in the builder and back again on Metrics`() {
        launch()
        openMetrics()

        composeRule.onNodeWithText("New Metric").performClick()

        composeRule.onNodeWithTag("metric-name").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-home").assertDoesNotExist()
        composeRule.onNodeWithTag("nav-metrics").assertDoesNotExist()

        pressBack()

        composeRule.onNodeWithText("No Metrics yet").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-metrics").assertIsSelected()
    }

    private val groceries get() = repository.savedCategories.first { it.name == "Groceries" }
    private val food get() = repository.savedCategories.first { it.name == "Food" }
    private val clothes get() = repository.savedCategories.first { it.name == "Clothes" }

    private fun givenAccount(name: String) =
        repository.givenAccount(Account(name = name, type = AccountType.Current, startingBalanceCents = 0))

    private fun givenExpense(accountId: Long, categoryId: Long?, cents: Long, date: LocalDate) {
        repository.givenTransaction(
            Transaction(accountId = accountId, categoryId = categoryId, amountCents = -cents, date = date),
        )
    }

    /** Current and Cash, with €89.50 spent this month (€7 Uncategorised) and €99 of Food in February. */
    private fun givenExpenses(): Pair<Long, Long> {
        val current = givenAccount("Current")
        val cash = givenAccount("Cash")
        givenExpense(current, food.id, 1_250, LocalDate.of(2026, 3, 10))
        givenExpense(cash, food.id, 2_000, LocalDate.of(2026, 3, 2))
        givenExpense(current, clothes.id, 5_000, LocalDate.of(2026, 3, 5))
        givenExpense(current, null, 700, LocalDate.of(2026, 3, 9))
        givenExpense(current, food.id, 9_900, LocalDate.of(2026, 2, 15))
        return current to cash
    }

    private fun result() = composeRule.onNodeWithTag("metric-result")

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun pickCategories(vararg ids: Long) {
        tap("metric-categories")
        ids.forEach { composeRule.onNodeWithTag("metric-category-$it").performScrollTo().performClick() }
        composeRule.onNodeWithTag("metric-categories-done").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `a new Metric shows its live result on top as each piece changes, and Save lists it with its value`() {
        val (current, _) = givenExpenses()
        launch()
        openMetrics()
        composeRule.onNodeWithText("New Metric").performClick()

        composeRule.onNodeWithText("New Metric").assertIsDisplayed()
        result().assert(hasText("Spending · Sum · This month"))
            .assert(hasValue(euros("89,50 €")))
            .assert(hasText("Includes ${euros("7,00 €")} Uncategorised"))
        composeRule.onNodeWithTag("metric-name").performTextInput("Groceries")
        pickCategories(groceries.id)
        result().assert(hasValue(euros("32,50 €")))
        composeRule.onNodeWithText("Includes ${euros("7,00 €")} Uncategorised").assertDoesNotExist()
        tap("metric-account-$current")
        result().assert(hasValue(euros("12,50 €")))
        tap("metric-measure-Count")
        result().assert(hasValue("1")).assert(hasText("Spending · Count · This month"))
        tap("metric-period-ThisYear")
        result().assert(hasValue("2"))
        tap("metric-measure-Sum")
        result().assert(hasValue(euros("111,50 €")))
        composeRule.onNodeWithText("Save").performClick()

        val saved = repository.savedMetrics.single()
        assertEquals(
            Metric(
                id = saved.id,
                name = "Groceries",
                categoryIds = setOf(groceries.id),
                accountIds = setOf(current),
                measure = Measure.Sum,
                period = MetricPeriod.ThisYear,
            ),
            saved,
        )
        composeRule.onNodeWithTag("metric-${saved.id}")
            .assertIsDisplayed()
            .assert(hasText("Groceries"))
            .assert(hasValue(euros("111,50 €")))
        composeRule.onNodeWithTag("nav-metrics").assertIsSelected()
    }

    @Test
    fun `tapping a saved Metric reopens it with the same pieces, and saving an edit replaces it`() {
        val (current, _) = givenExpenses()
        val id = repository.givenMetric(
            Metric(
                name = "Groceries",
                categoryIds = setOf(groceries.id),
                accountIds = setOf(current),
                measure = Measure.Count,
                period = MetricPeriod.ThisYear,
            ),
        )
        launch()
        openMetrics()
        composeRule.onNodeWithTag("metric-$id").assert(hasValue("2"))

        composeRule.onNodeWithTag("metric-$id").performClick()

        composeRule.onNodeWithText("Edit Metric").assertIsDisplayed()
        composeRule.onNodeWithTag("metric-name").assert(hasText("Groceries"))
        composeRule.onNodeWithTag("metric-source-Spending").assertIsSelected()
        composeRule.onNodeWithTag("metric-categories").assert(hasAnyDescendant(hasText("Groceries")))
        composeRule.onNodeWithTag("metric-account-$current").assertIsSelected()
        composeRule.onNodeWithTag("metric-account-all").assertIsNotSelected()
        composeRule.onNodeWithTag("metric-measure-Count").assertIsSelected()
        composeRule.onNodeWithTag("metric-period-ThisYear").assertIsSelected()
        result().assert(hasValue("2"))

        composeRule.onNodeWithTag("metric-name").performTextReplacement("Food spending")
        tap("metric-measure-Sum")
        composeRule.onNodeWithText("Save").performClick()

        assertEquals(
            listOf(
                Metric(
                    id = id,
                    name = "Food spending",
                    categoryIds = setOf(groceries.id),
                    accountIds = setOf(current),
                    measure = Measure.Sum,
                    period = MetricPeriod.ThisYear,
                ),
            ),
            repository.savedMetrics,
        )
        composeRule.onNodeWithTag("metric-$id")
            .assert(hasText("Food spending"))
            .assert(hasValue(euros("111,50 €")))
        composeRule.onNodeWithText("Groceries").assertDoesNotExist()
    }

    @Test
    fun `saving a Metric without a name keeps the builder open with Enter a name`() {
        launch()
        openMetrics()
        composeRule.onNodeWithText("New Metric").performClick()

        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Enter a name").assertIsDisplayed()
        composeRule.onNodeWithTag("metric-result").assertIsDisplayed()
        assertEquals(emptyList<Metric>(), repository.savedMetrics)
    }

    @Test
    fun `an average per transaction with nothing spent shows a dash and the reason`() {
        launch()
        openMetrics()
        composeRule.onNodeWithText("New Metric").performClick()

        tap("metric-measure-AveragePerTransaction")

        result().assert(hasText("—")).assert(hasText("No Transactions in this period"))
    }

    @Test
    fun `the Category filter offers only Categories of the source's kind, plus Uncategorised`() {
        val salary = repository.savedCategories.first { it.name == "Salary" }
        launch()
        openMetrics()
        composeRule.onNodeWithText("New Metric").performClick()

        tap("metric-categories")
        composeRule.onNodeWithTag("metric-category-${groceries.id}").assertExists()
        composeRule.onNodeWithTag("metric-category-${salary.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("metric-category-uncategorised").assertExists()
        composeRule.onNodeWithTag("metric-categories-done").performClick()
        composeRule.waitForIdle()
        tap("metric-source-Income")
        tap("metric-categories")

        composeRule.onNodeWithTag("metric-category-${salary.id}").assertExists()
        composeRule.onNodeWithTag("metric-category-${groceries.id}").assertDoesNotExist()
        assertEquals(CategoryKind.Income, salary.kind)
    }

    @Test
    fun `per week is offered for this month but not for this week`() {
        launch()
        openMetrics()
        composeRule.onNodeWithText("New Metric").performClick()

        composeRule.onNodeWithTag("metric-measure-AveragePerWeek").assertExists()
        tap("metric-period-ThisWeek")

        composeRule.onNodeWithTag("metric-measure-AveragePerWeek").assertDoesNotExist()
        composeRule.onNodeWithTag("metric-measure-AveragePerDay").assertExists()
    }

    @Test
    fun `custom dates open the date dialog and start from the first of the month to today`() {
        launch()
        openMetrics()
        composeRule.onNodeWithText("New Metric").performClick()

        tap("metric-period-Custom")
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithTag("metric-period-Custom").assertIsSelected()
        composeRule.onNodeWithTag("metric-from").assert(hasAnyDescendant(hasText("1 mar 2026")))
        composeRule.onNodeWithTag("metric-to").assert(hasAnyDescendant(hasText("11 mar 2026")))
        result().assert(hasText("Spending · Sum · Custom dates"))
    }

    @Test
    fun `averages per day, week or month offer two divisors and the result says which one it used`() {
        givenExpenses()
        launch()
        openMetrics()
        composeRule.onNodeWithText("New Metric").performClick()
        composeRule.onNodeWithTag("metric-name").performTextInput("Daily spending")

        composeRule.onNodeWithTag("metric-divisor-EverySubPeriod").assertDoesNotExist()
        tap("metric-measure-AveragePerTransaction")
        composeRule.onNodeWithTag("metric-divisor-EverySubPeriod").assertDoesNotExist()
        tap("metric-measure-AveragePerDay")

        composeRule.onNodeWithTag("metric-divisor-EverySubPeriod").assert(hasText("Every day")).assertIsSelected()
        composeRule.onNodeWithTag("metric-divisor-SubPeriodsWithTransactions")
            .assert(hasText("Only days with Transactions"))
            .assertIsNotSelected()
        // €89.50 from 03-01 to today 03-11.
        result().assert(hasValue(euros("8,14 €"))).assert(hasText("÷ 11 days"))
        tap("metric-divisor-SubPeriodsWithTransactions")
        // Spending on 03-02, 03-05, 03-09 and 03-10, plus today.
        result().assert(hasValue(euros("17,90 €"))).assert(hasText("÷ 5 days with Transactions"))
        tap("metric-measure-AveragePerWeek")
        composeRule.onNodeWithTag("metric-divisor-EverySubPeriod").assert(hasText("Every week"))
        tap("metric-measure-AveragePerDay")
        composeRule.onNodeWithText("Save").performClick()

        val saved = repository.savedMetrics.single()
        assertEquals(Measure.AveragePerDay, saved.measure)
        assertEquals(AverageDivisor.SubPeriodsWithTransactions, saved.divisor)
        composeRule.onNodeWithTag("metric-${saved.id}")
            .assert(hasValue(euros("17,90 €")))
            .assert(hasText("÷ 5 days with Transactions"))
        composeRule.onNodeWithTag("metric-${saved.id}").performClick()
        composeRule.onNodeWithTag("metric-divisor-SubPeriodsWithTransactions").assertIsSelected()
    }

    private val salary get() = repository.savedCategories.first { it.name == "Salary" }

    /** This month: €2,000 of Salary in, €1,500 spent; saved simple Metrics Income and Spending. */
    private fun givenIncomeAndSpending(): Pair<Long, Long> {
        val current = givenAccount("Current")
        repository.givenTransaction(
            Transaction(accountId = current, categoryId = salary.id, amountCents = 200_000, date = LocalDate.of(2026, 3, 1)),
        )
        givenExpense(current, food.id, 150_000, LocalDate.of(2026, 3, 2))
        val income = repository.givenMetric(Metric(name = "Income", source = MetricSource.Income))
        val spending = repository.givenMetric(Metric(name = "Spending"))
        return income to spending
    }

    private fun givenCombined(name: String, kind: CombinationKind, a: Long, b: Long?) =
        repository.givenMetric(Metric(name = name, combination = Combination(kind, a, b)))

    private fun pickOperand(side: String, id: Long) {
        tap("metric-operand-$side")
        composeRule.onNodeWithTag("metric-operand-option-$id").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun newCombinedMetric() {
        composeRule.onNodeWithText("New Metric").performClick()
        tap("metric-source-Combined")
    }

    @Test
    fun `a combined Metric is built from saved Metrics with a live result, and Save lists it with its value`() {
        val (income, spending) = givenIncomeAndSpending()
        launch()
        openMetrics()

        newCombinedMetric()
        tap("metric-combination-Difference")
        result().assert(hasText("—")).assert(hasText("Choose the Metrics to combine"))
        composeRule.onNodeWithText("This period applies to both Metrics").assertExists()
        composeRule.onNodeWithTag("metric-categories").assertDoesNotExist()
        composeRule.onNodeWithTag("metric-measure-Sum").assertDoesNotExist()
        pickOperand("a", income)
        pickOperand("b", spending)
        result().assert(hasValue(euros("500,00 €")))
        composeRule.onNodeWithTag("metric-name").performTextInput("Saved")
        composeRule.onNodeWithText("Save").performClick()

        val saved = repository.savedMetrics.last()
        assertEquals(Combination(CombinationKind.Difference, income, spending), saved.combination)
        composeRule.onNodeWithTag("metric-${saved.id}").assert(hasValue(euros("500,00 €")))

        newCombinedMetric()
        tap("metric-combination-Ratio")
        pickOperand("a", saved.id)
        pickOperand("b", income)
        result().assert(hasValue("25,0%"))
        composeRule.onNodeWithTag("metric-name").performTextInput("Savings rate")
        composeRule.onNodeWithText("Save").performClick()

        val rate = repository.savedMetrics.last()
        assertEquals(
            Metric(id = rate.id, name = "Savings rate", combination = Combination(CombinationKind.Ratio, saved.id, income)),
            rate,
        )
        composeRule.onNodeWithTag("metric-${rate.id}").assert(hasText("Savings rate")).assert(hasValue("25,0%"))
    }

    @Test
    fun `a refused pair shows a dash with the refusal, and Save keeps the builder open`() {
        val (income, spending) = givenIncomeAndSpending()
        val expenses = repository.givenMetric(Metric(name = "Expenses", measure = Measure.Count))
        launch()
        openMetrics()

        newCombinedMetric()
        composeRule.onNodeWithTag("metric-name").performTextInput("Mixed")
        tap("metric-combination-Difference")
        pickOperand("a", spending)
        pickOperand("b", expenses)
        result().assert(hasText("—")).assert(hasText("A difference needs two Metrics in the same unit"))
        composeRule.onNodeWithText("Save").performClick()

        result().assertIsDisplayed()
        assertEquals(listOf("Income", "Spending", "Expenses"), repository.savedMetrics.map { it.name })
        tap("metric-combination-Ratio")
        pickOperand("a", expenses)
        pickOperand("b", income)
        result().assert(hasText("A ratio works for money ÷ money, count ÷ count or money ÷ count"))
    }

    @Test
    fun `a ratio over a Metric that is zero this period shows a dash and names it`() {
        val current = givenAccount("Current")
        givenExpense(current, food.id, 1_000, LocalDate.of(2026, 3, 2))
        val income = repository.givenMetric(Metric(name = "Income", source = MetricSource.Income))
        val spending = repository.givenMetric(Metric(name = "Spending"))
        val id = givenCombined("Spent of income", CombinationKind.Ratio, spending, income)
        launch()
        openMetrics()

        composeRule.onNodeWithTag("metric-$id")
            .assert(hasText("—"))
            .assert(hasText("Can't divide by zero: Income is ${euros("0,00 €")} in this period"))
    }

    @Test
    fun `reopening a combined Metric shows its kind and sides, and the pickers leave out itself and its users`() {
        val (income, spending) = givenIncomeAndSpending()
        val saved = givenCombined("Saved", CombinationKind.Difference, income, spending)
        val rate = givenCombined("Savings rate", CombinationKind.Ratio, saved, income)
        launch()
        openMetrics()

        composeRule.onNodeWithTag("metric-$saved").performClick()

        composeRule.onNodeWithTag("metric-source-Combined").assertIsSelected()
        composeRule.onNodeWithTag("metric-source-Spending").assertIsNotSelected()
        composeRule.onNodeWithTag("metric-combination-Difference").assertIsSelected()
        composeRule.onNodeWithTag("metric-combination-Ratio").assertIsNotSelected()
        composeRule.onNodeWithTag("metric-operand-a").assert(hasAnyDescendant(hasText("Income")))
        composeRule.onNodeWithTag("metric-operand-b").assert(hasAnyDescendant(hasText("Spending")))
        result().assert(hasValue(euros("500,00 €")))
        listOf("a", "b").forEach { side ->
            tap("metric-operand-$side")
            composeRule.onNodeWithTag("metric-operand-option-$income").assertExists()
            composeRule.onNodeWithTag("metric-operand-option-$spending").assertExists()
            composeRule.onNodeWithTag("metric-operand-option-$saved").assertDoesNotExist()
            composeRule.onNodeWithTag("metric-operand-option-$rate").assertDoesNotExist()
            composeRule.onNodeWithTag("metric-operand-option-$income").performClick()
            composeRule.waitForIdle()
        }
        tap("metric-combination-Change")
        composeRule.onNodeWithTag("metric-operand-b").assertDoesNotExist()
    }

    @Test
    fun `a Metric another uses is not deleted, and the builder says which one uses it`() {
        val (income, spending) = givenIncomeAndSpending()
        val saved = givenCombined("Saved", CombinationKind.Difference, income, spending)
        givenCombined("Savings rate", CombinationKind.Ratio, saved, income)
        val before = repository.savedMetrics
        launch()
        openMetrics()
        composeRule.onNodeWithTag("metric-$saved").performClick()

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete this Metric?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.onNodeWithText("Used by Savings rate")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeRule.onNodeWithText("Edit Metric").assertIsDisplayed()
        assertEquals(before, repository.savedMetrics)
        tap("metric-period-ThisYear")
        composeRule.onNodeWithText("Used by Savings rate").assertDoesNotExist()
    }

    @Test
    fun `deleting an unused Metric closes the builder and Metrics no longer lists it`() {
        val (income, spending) = givenIncomeAndSpending()
        val saved = givenCombined("Saved", CombinationKind.Difference, income, spending)
        val rate = givenCombined("Savings rate", CombinationKind.Ratio, saved, income)
        launch()
        openMetrics()
        composeRule.onNodeWithTag("metric-$rate").performClick()

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.onNodeWithText("Edit Metric").assertDoesNotExist()
        composeRule.onNodeWithTag("metric-$saved").assertIsDisplayed()
        composeRule.onNodeWithTag("metric-$rate").assertDoesNotExist()
        assertEquals(listOf("Income", "Spending", "Saved"), repository.savedMetrics.map { it.name })
    }

    @Test
    fun `a new Metric shows no Delete action`() {
        launch()
        openMetrics()

        composeRule.onNodeWithText("New Metric").performClick()

        composeRule.onNodeWithTag("metric-name").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Delete").assertDoesNotExist()
    }

    @Test
    fun `with no Metric to combine, the side picker says to save a simple Metric first`() {
        launch()
        openMetrics()
        newCombinedMetric()

        tap("metric-operand-a")

        composeRule.onNodeWithText("No other Metrics to combine yet. Save a simple Metric first.").assertIsDisplayed()
    }

    @Test
    fun `the live result is a polite live region, so a new value or reason is announced`() {
        launch()
        openMetrics()

        composeRule.onNodeWithText("New Metric").performClick()

        result().assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    private fun merchant(name: String): Long = repository.savedMerchants.single { it.name == name }.id

    private fun givenMerchantExpense(accountId: Long, merchantId: Long?, cents: Long) {
        repository.givenTransaction(
            Transaction(
                accountId = accountId,
                categoryId = food.id,
                amountCents = -cents,
                date = LocalDate.of(2026, 3, 4),
                merchantId = merchantId,
            ),
        )
    }

    private fun merchantField() = composeRule.onNodeWithTag("metric-merchants")

    private fun openNewMetric() {
        launch()
        openMetrics()
        composeRule.onNodeWithText("New Metric").performClick()
    }

    @Test
    fun `picking two Merchants names them alphabetically and the result sums only their Transactions`() {
        val current = givenAccount("Current")
        givenMerchantExpense(current, merchant("Mercadona"), 1_000)
        givenMerchantExpense(current, merchant("Lidl"), 2_000)
        givenMerchantExpense(current, merchant("Carrefour"), 4_000)
        givenMerchantExpense(current, null, 8_000)
        openNewMetric()
        merchantField().assert(hasAnyDescendant(hasText("All merchants")))
        result().assert(hasValue(euros("150,00 €")))

        tap("metric-merchants")
        composeRule.onNodeWithTag("metric-merchant-${merchant("Mercadona")}").performScrollTo().performClick()
        composeRule.onNodeWithTag("metric-merchant-${merchant("Lidl")}").performScrollTo().performClick()
        composeRule.onNodeWithTag("metric-merchants-done").performClick()
        composeRule.waitForIdle()

        merchantField().assert(hasAnyDescendant(hasText("Lidl, Mercadona")))
        result().assert(hasValue(euros("30,00 €")))
    }

    @Test
    fun `the Merchant search keeps names containing it whatever the case, accents and spacing`() {
        repository.givenMerchant(Merchant(name = "El Mércado Central"))
        repository.givenMerchant(Merchant(name = "Mer Cantil"))
        openNewMetric()
        tap("metric-merchants")

        composeRule.onNodeWithTag("metric-merchant-search").performTextInput("merca")

        composeRule.onNodeWithTag("metric-merchant-${merchant("Mercadona")}").assertExists()
        composeRule.onNodeWithTag("metric-merchant-${merchant("El Mércado Central")}").assertExists()
        composeRule.onNodeWithTag("metric-merchant-${merchant("Mer Cantil")}").assertExists()
        composeRule.onNodeWithTag("metric-merchant-${merchant("Lidl")}").assertDoesNotExist()
        composeRule.onNodeWithTag("metric-merchant-${merchant("Carrefour")}").assertDoesNotExist()
        composeRule.onNodeWithText("No merchants match").assertDoesNotExist()
        composeRule.onNodeWithTag("metric-merchant-search").performTextReplacement("zzz")
        composeRule.onNodeWithText("No merchants match").assertIsDisplayed()
    }

    @Test
    fun `ticking All merchants clears every Merchant pick`() {
        openNewMetric()
        tap("metric-merchants")
        composeRule.onNodeWithTag("metric-merchant-${merchant("Mercadona")}").performScrollTo().performClick()
        composeRule.onNodeWithTag("metric-merchant-${merchant("Lidl")}").performScrollTo().performClick()
        composeRule.onNodeWithTag("metric-merchant-all").assertIsOff()

        composeRule.onNodeWithTag("metric-merchant-all").performScrollTo().performClick()

        composeRule.onNodeWithTag("metric-merchant-all").assertIsOn()
        composeRule.onNodeWithTag("metric-merchant-${merchant("Mercadona")}").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("metric-merchant-${merchant("Lidl")}").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("metric-merchants-done").performClick()
        composeRule.waitForIdle()
        merchantField().assert(hasAnyDescendant(hasText("All merchants")))
    }

    @Test
    fun `a reopened Metric keeps its Merchants ticked and named, whichever the source`() {
        val mercadona = merchant("Mercadona")
        val lidl = merchant("Lidl")
        val id = repository.givenMetric(Metric(name = "Supermarkets", merchantIds = setOf(mercadona, lidl)))
        launch()
        openMetrics()

        composeRule.onNodeWithTag("metric-$id").performClick()

        merchantField().assert(hasAnyDescendant(hasText("Lidl, Mercadona")))
        tap("metric-merchants")
        composeRule.onNodeWithTag("metric-merchant-$mercadona").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("metric-merchant-$lidl").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("metric-merchant-all").assertIsOff()
        composeRule.onNodeWithTag("metric-merchants-done").performClick()
        composeRule.waitForIdle()
        tap("metric-source-Income")
        merchantField().assert(hasAnyDescendant(hasText("Lidl, Mercadona")))
        tap("metric-source-Spending")
        merchantField().assert(hasAnyDescendant(hasText("Lidl, Mercadona")))
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(setOf(mercadona, lidl), repository.savedMetrics.single().merchantIds)
    }

    @Test
    fun `a Merchant filter whose Merchants are all gone reads No merchants left`() {
        val id = repository.givenMetric(Metric(name = "Gone", merchantIds = setOf(999_999L)))
        launch()
        openMetrics()

        composeRule.onNodeWithTag("metric-$id").performClick()

        merchantField().assert(hasAnyDescendant(hasText("No merchants left")))
    }

    @Test
    fun `a combined Metric shows no Merchant field`() {
        openNewMetric()
        merchantField().assertExists()

        tap("metric-source-Combined")

        merchantField().assertDoesNotExist()
    }
}
