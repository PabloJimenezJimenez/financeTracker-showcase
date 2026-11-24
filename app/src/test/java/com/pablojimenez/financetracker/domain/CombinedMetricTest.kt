package com.pablojimenez.financetracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Combined Metrics: ratio, difference and change of saved Metrics, through [Metric.evaluate]. */
class CombinedMetricTest {
    /** Wednesday 2026-03-11. */
    private val today = LocalDate.of(2026, 3, 11)

    private val food = Category(1, "Food", CategoryKind.Spending)
    private val eatingOut = Category(2, "Eating out", CategoryKind.Spending)
    private val salary = Category(3, "Salary", CategoryKind.Income)
    private val categories = listOf(food, eatingOut, salary)

    private fun transaction(cents: Long, categoryId: Long, date: LocalDate = today) =
        Transaction(accountId = 1, categoryId = categoryId, amountCents = cents, date = date)

    /** This month: €2,000 income and €1,500 spent in 4 expenses. In January: €1,000 in, €300 out. */
    private val month = listOf(
        transaction(200_000, salary.id, LocalDate.of(2026, 3, 1)),
        transaction(-40_000, food.id, LocalDate.of(2026, 3, 2)),
        transaction(-30_000, food.id, LocalDate.of(2026, 3, 5)),
        transaction(-50_000, eatingOut.id, LocalDate.of(2026, 3, 9)),
        transaction(-30_000, food.id, LocalDate.of(2026, 3, 10)),
        transaction(100_000, salary.id, LocalDate.of(2026, 1, 15)),
        transaction(-30_000, food.id, LocalDate.of(2026, 1, 20)),
    )

    private val income = Metric(id = 1, name = "Income", source = MetricSource.Income, period = MetricPeriod.ThisYear)
    private val spending = Metric(id = 2, name = "Spending", period = MetricPeriod.ThisYear)
    private val expenses = Metric(id = 3, name = "Expenses", measure = Measure.Count, period = MetricPeriod.ThisYear)
    private val saved = combined(10, "Saved", CombinationKind.Difference, income.id, spending.id)
    private val savingsRate = combined(11, "Savings rate", CombinationKind.Ratio, saved.id, income.id)

    private fun combined(
        id: Long,
        name: String,
        kind: CombinationKind,
        a: Long,
        b: Long? = null,
        period: MetricPeriod = MetricPeriod.ThisMonth,
    ) = Metric(id = id, name = name, period = period, combination = Combination(kind, a, b))

    private val savedMetrics = listOf(income, spending, expenses, saved, savingsRate)

    private fun Metric.valueOf(
        transactions: List<Transaction>,
        metrics: List<Metric> = savedMetrics,
        on: LocalDate = today,
    ): MetricResult = evaluate(transactions, categories, on, metrics)

    @Test
    fun `a difference measures both sides over its own period, not theirs`() {
        assertEquals(MetricResult.Money(cents = 50_000), saved.valueOf(month))
    }

    @Test
    fun `a ratio of money to money is a percentage, and a combined Metric can be a side`() {
        assertEquals(MetricResult.Percentage(tenths = 250), savingsRate.valueOf(month))
    }

    @Test
    fun `money over a count is money per Transaction, rounded half-up to the cent`() {
        val perExpense = combined(20, "Per expense", CombinationKind.Ratio, spending.id, expenses.id)
        val threeExpenses = listOf(
            transaction(-1, food.id),
            transaction(-2, food.id),
            transaction(-2, food.id),
        )
        val twoExpenses = listOf(transaction(-2, food.id), transaction(-3, food.id))

        assertEquals(MetricResult.Money(cents = 37_500), perExpense.valueOf(month))
        // 5 ÷ 3 = 1.67 cents and 5 ÷ 2 = 2.5 cents.
        assertEquals(MetricResult.Money(cents = 2), perExpense.valueOf(threeExpenses))
        assertEquals(MetricResult.Money(cents = 3), perExpense.valueOf(twoExpenses))
    }

    @Test
    fun `a count over a count is a percentage`() {
        val eatingOutCount = Metric(id = 4, name = "Eating out", categoryIds = setOf(eatingOut.id), measure = Measure.Count)
        val share = combined(20, "Share eating out", CombinationKind.Ratio, eatingOutCount.id, expenses.id)

        assertEquals(MetricResult.Percentage(tenths = 250), share.valueOf(month, savedMetrics + eatingOutCount))
    }

    @Test
    fun `percentages are rounded half-up to 1 decimal`() {
        val eatingOutSpending = Metric(id = 4, name = "Eating out", categoryIds = setOf(eatingOut.id))
        val share = combined(20, "Share eating out", CombinationKind.Ratio, eatingOutSpending.id, spending.id)
        val metrics = savedMetrics + eatingOutSpending
        // 1 ÷ 16 = 6.25%, 1 ÷ 3 = 33.33…%, 2 ÷ 3 = 66.66…%.
        fun shareOf(eatingOutCents: Long, foodCents: Long) =
            share.valueOf(listOf(transaction(-eatingOutCents, eatingOut.id), transaction(-foodCents, food.id)), metrics)

        assertEquals(MetricResult.Percentage(tenths = 63), shareOf(100, 1_500))
        assertEquals(MetricResult.Percentage(tenths = 333), shareOf(100, 200))
        assertEquals(MetricResult.Percentage(tenths = 667), shareOf(200, 100))
    }

    private val noIncome = month.filter { it.categoryId != salary.id }

    @Test
    fun `a ratio whose divisor is zero has no value, with the reason naming the divisor`() {
        assertEquals(
            MetricResult.NoValue(NoValueReason.DivideByZero(divisor = "Income", zero = MetricResult.Money(0))),
            savingsRate.valueOf(noIncome),
        )
        val perExpense = combined(20, "Per expense", CombinationKind.Ratio, spending.id, expenses.id)
        assertEquals(
            MetricResult.NoValue(NoValueReason.DivideByZero(divisor = "Expenses", zero = MetricResult.Count(0))),
            perExpense.valueOf(emptyList()),
        )
    }

    @Test
    fun `a side with no value leaves the combined Metric with no value, for that side's reason`() {
        val perExpense = Metric(id = 4, name = "Per expense", measure = Measure.AveragePerTransaction)
        val metrics = savedMetrics + perExpense
        val aSideless = combined(20, "Less income", CombinationKind.Difference, perExpense.id, income.id)
        val bSideless = combined(21, "Of income", CombinationKind.Ratio, income.id, perExpense.id)
        val nested = combined(22, "Nested", CombinationKind.Difference, savingsRate.id, savingsRate.id)

        val noTransactions = MetricResult.NoValue(NoValueReason.NoTransactions)
        assertEquals(noTransactions, aSideless.valueOf(emptyList(), metrics))
        assertEquals(noTransactions, bSideless.valueOf(emptyList(), metrics))
        assertEquals(
            MetricResult.NoValue(NoValueReason.DivideByZero(divisor = "Income", zero = MetricResult.Money(0))),
            nested.valueOf(noIncome, metrics),
        )
    }

    private fun refused(reason: NoValueReason) = MetricResult.NoValue(reason)

    @Test
    fun `a difference of money and a count is refused`() {
        val mixed = combined(20, "Mixed", CombinationKind.Difference, spending.id, expenses.id)

        assertEquals(refused(NoValueReason.DifferenceNeedsSameUnit), mixed.valueOf(month))
    }

    @Test
    fun `a ratio of a count to money, or of a percentage to anything, is refused`() {
        val countOverMoney = combined(20, "Count over money", CombinationKind.Ratio, expenses.id, spending.id)
        val percentageOverMoney = combined(21, "Rate over income", CombinationKind.Ratio, savingsRate.id, income.id)
        val percentageOverPercentage = combined(22, "Rate over rate", CombinationKind.Ratio, savingsRate.id, savingsRate.id)
        val moneyOverPercentage = combined(23, "Income over rate", CombinationKind.Ratio, income.id, savingsRate.id)

        listOf(countOverMoney, percentageOverMoney, percentageOverPercentage, moneyOverPercentage).forEach {
            assertEquals(it.name, refused(NoValueReason.RatioUnits), it.valueOf(month))
        }
    }

    @Test
    fun `a change can't be a side of another combination`() {
        val incomeChange = combined(20, "Income change", CombinationKind.Change, income.id)
        val metrics = savedMetrics + incomeChange

        listOf(
            combined(21, "Ratio", CombinationKind.Ratio, incomeChange.id, income.id),
            combined(22, "Difference", CombinationKind.Difference, income.id, incomeChange.id),
            combined(23, "Change of change", CombinationKind.Change, incomeChange.id),
        ).forEach {
            assertEquals(it.name, refused(NoValueReason.ChangeCombinedAgain), it.valueOf(month, metrics))
        }
    }

    @Test
    fun `a Metric can't be built from itself, directly or through another`() {
        val direct = combined(20, "Direct", CombinationKind.Difference, 20, income.id)
        // Loop uses Through, which uses Loop.
        val loop = combined(21, "Loop", CombinationKind.Ratio, 22, income.id)
        val through = combined(22, "Through", CombinationKind.Difference, income.id, 21)
        val metrics = savedMetrics + direct + loop + through

        assertEquals(refused(NoValueReason.BuiltFromItself), direct.valueOf(month, metrics))
        assertEquals(refused(NoValueReason.BuiltFromItself), loop.valueOf(month, metrics))
        assertEquals(refused(NoValueReason.BuiltFromItself), through.valueOf(month, metrics))
    }

    @Test
    fun `a refusal stands even when a side has no value`() {
        val mixed = combined(20, "Mixed", CombinationKind.Difference, savingsRate.id, expenses.id)

        assertEquals(refused(NoValueReason.DifferenceNeedsSameUnit), mixed.valueOf(noIncome))
    }

    @Test
    fun `a combination without its sides asks to choose them`() {
        val choose = MetricResult.NoValue(NoValueReason.ChooseMetrics)

        assertEquals(choose, combined(20, "New", CombinationKind.Ratio, 0).valueOf(month))
        assertEquals(choose, combined(20, "New", CombinationKind.Difference, income.id).valueOf(month))
        assertEquals(choose, combined(20, "New", CombinationKind.Change, 0).valueOf(month))
    }

    @Test
    fun `a difference of two percentages is in percentage points`() {
        val eatingOutSpending = Metric(id = 4, name = "Eating out", categoryIds = setOf(eatingOut.id))
        val eatingOutShare = combined(20, "Eating out share", CombinationKind.Ratio, eatingOutSpending.id, spending.id)
        // Savings rate 25.0% less eating out's 33.3% share of spending: 25 − 33.33… = −8.3 points.
        val gap = combined(21, "Gap", CombinationKind.Difference, savingsRate.id, eatingOutShare.id)

        assertEquals(
            MetricResult.Points(tenths = -83),
            gap.valueOf(month, savedMetrics + eatingOutSpending + eatingOutShare),
        )
    }

    private fun spent(cents: Long, date: LocalDate) = transaction(-cents, food.id, date)

    private fun date(text: String) = LocalDate.parse(text)

    private fun spendingChange(period: MetricPeriod) = combined(20, "Spending change", CombinationKind.Change, spending.id, period = period)

    @Test
    fun `this month's change compares the 1st to today with the same days of last month`() {
        val result = spendingChange(MetricPeriod.ThisMonth).valueOf(
            listOf(
                spent(100, date("2026-03-01")),
                spent(200, date("2026-03-11")),
                spent(1_000, date("2026-03-12")),
                spent(100, date("2026-02-01")),
                spent(100, date("2026-02-11")),
                spent(5_000, date("2026-02-12")),
                spent(7_000, date("2026-01-31")),
            ),
        )

        // 300 against 200.
        assertEquals(MetricResult.PercentChange(tenths = 500), result)
    }

    @Test
    fun `this week's change compares Monday to today with the same days of last week`() {
        val result = spendingChange(MetricPeriod.ThisWeek).valueOf(
            listOf(
                spent(100, date("2026-03-09")),
                spent(100, date("2026-03-11")),
                spent(999, date("2026-03-12")),
                spent(999, date("2026-03-08")),
                spent(400, date("2026-03-02")),
                spent(400, date("2026-03-04")),
                spent(999, date("2026-03-05")),
            ),
        )

        // 200 against 800.
        assertEquals(MetricResult.PercentChange(tenths = -750), result)
    }

    @Test
    fun `this year's change compares January 1st to today with the same days of last year`() {
        val result = spendingChange(MetricPeriod.ThisYear).valueOf(
            listOf(
                spent(300, date("2026-01-01")),
                spent(300, date("2026-03-11")),
                spent(999, date("2026-03-12")),
                spent(200, date("2025-01-01")),
                spent(200, date("2025-03-11")),
                spent(999, date("2025-03-12")),
                spent(999, date("2024-12-31")),
            ),
        )

        // 600 against 400.
        assertEquals(MetricResult.PercentChange(tenths = 500), result)
    }

    @Test
    fun `on the 31st, this month's change compares with all of a shorter previous month`() {
        val result = spendingChange(MetricPeriod.ThisMonth).valueOf(
            listOf(
                spent(100, date("2026-03-01")),
                spent(350, date("2026-03-31")),
                spent(100, date("2026-02-01")),
                spent(200, date("2026-02-28")),
                spent(999, date("2026-01-31")),
            ),
            on = date("2026-03-31"),
        )

        // 450 against 300.
        assertEquals(MetricResult.PercentChange(tenths = 500), result)
    }

    @Test
    fun `a finished custom range compares with as many days right before it`() {
        val tenDays = MetricPeriod.Custom(date("2026-02-10"), date("2026-02-19"))

        val result = spendingChange(tenDays).valueOf(
            listOf(
                spent(100, date("2026-02-10")),
                spent(100, date("2026-02-19")),
                spent(50, date("2026-01-31")),
                spent(50, date("2026-02-09")),
                spent(999, date("2026-01-30")),
            ),
        )

        // 200 against 100.
        assertEquals(MetricResult.PercentChange(tenths = 1_000), result)
    }

    @Test
    fun `an unfinished custom range compares its start to today with as many days right before it`() {
        val running = MetricPeriod.Custom(date("2026-03-05"), date("2026-03-20"))

        val result = spendingChange(running).valueOf(
            listOf(
                spent(100, date("2026-03-05")),
                spent(100, date("2026-03-11")),
                spent(999, date("2026-03-12")),
                spent(400, date("2026-02-26")),
                spent(400, date("2026-03-04")),
                spent(999, date("2026-02-25")),
            ),
        )

        // 200 against 800, over 03-05..03-11 and 02-26..03-04.
        assertEquals(MetricResult.PercentChange(tenths = -750), result)
    }

    @Test
    fun `a change is signed, divided by the size of the previous value, and rounded to 1 decimal`() {
        val saved = combined(20, "Saved change", CombinationKind.Change, saved.id)
        val result = saved.valueOf(
            listOf(
                transaction(10_000, salary.id, date("2026-03-01")),
                spent(10_300, date("2026-03-02")),
                transaction(10_000, salary.id, date("2026-02-01")),
                spent(10_600, date("2026-02-02")),
            ),
        )

        // From −€6 to −€3: (−3 − −6) ÷ |−6| = +50.0%.
        assertEquals(MetricResult.PercentChange(tenths = 500), result)
        val third = spendingChange(MetricPeriod.ThisMonth).valueOf(
            listOf(spent(200, date("2026-03-02")), spent(300, date("2026-02-02"))),
        )
        // 2 against 3: −33.33…%.
        assertEquals(MetricResult.PercentChange(tenths = -333), third)
    }

    @Test
    fun `the change of a percentage is in percentage points`() {
        val rateChange = combined(20, "Savings rate change", CombinationKind.Change, savingsRate.id)

        val result = rateChange.valueOf(
            month + listOf(
                transaction(100_000, salary.id, date("2026-02-01")),
                spent(80_000, date("2026-02-11")),
            ),
        )

        // 25.0% against 20.0%.
        assertEquals(MetricResult.Points(tenths = 50), result)
    }

    @Test
    fun `a change whose previous period is zero has no value, naming the Metric`() {
        val eatingOutSpending = Metric(id = 4, name = "Eating out", categoryIds = setOf(eatingOut.id))
        val change = combined(20, "Eating out change", CombinationKind.Change, eatingOutSpending.id)

        assertEquals(
            MetricResult.NoValue(NoValueReason.PreviousPeriodZero("Eating out")),
            change.valueOf(month, savedMetrics + eatingOutSpending),
        )
    }

    @Test
    fun `a custom range that hasn't started compares all of it with as many days right before it`() {
        val upcoming = MetricPeriod.Custom(date("2026-03-20"), date("2026-03-24"))

        val result = spendingChange(upcoming).valueOf(
            listOf(
                spent(300, date("2026-03-20")),
                spent(300, date("2026-03-24")),
                spent(200, date("2026-03-15")),
                spent(200, date("2026-03-19")),
                spent(999, date("2026-03-14")),
            ),
        )

        // 600 over 03-20..03-24 against 400 over 03-15..03-19.
        assertEquals(MetricResult.PercentChange(tenths = 500), result)
    }

    @Test(timeout = 10_000)
    fun `a long chain of Metrics that each use the one before twice is evaluated quickly`() {
        // Each link doubles the one before: 40 links would be 2^40 walks without reuse.
        val links = (1..40).map { combined(100L + it, "Link $it", CombinationKind.Difference, 99L + it, 99L + it) }
        val first = Metric(id = 100, name = "Spending")
        val doubled = links.last().copy(id = 0, combination = Combination(CombinationKind.Ratio, 140, 100))

        // Every difference is X − X = 0, so the last link is €0.00 and the ratio is 0.0%.
        assertEquals(
            MetricResult.Percentage(tenths = 0),
            doubled.valueOf(month, listOf(first) + links),
        )
    }
}
