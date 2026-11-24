package com.pablojimenez.financetracker.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/** A Metric's last periods, through [Metric.history]. */
class HistoryTest {
    /** Wednesday 2026-03-11: day 11 of 31 in March. */
    private val today = LocalDate.of(2026, 3, 11)

    private val food = Category(1, "Food", CategoryKind.Spending)
    private val salary = Category(2, "Salary", CategoryKind.Income)
    private val categories = listOf(food, salary)

    private fun expense(cents: Long, date: LocalDate) =
        Transaction(accountId = 1, categoryId = food.id, amountCents = -cents, date = date)

    private fun income(cents: Long, date: LocalDate) =
        Transaction(accountId = 1, categoryId = salary.id, amountCents = cents, date = date)

    private fun Metric.historyOf(
        transactions: List<Transaction>,
        metrics: List<Metric> = emptyList(),
        periods: Int = 12,
    ): List<PeriodValue> = history(transactions, categories, today, metrics, periods = periods)

    private fun month(year: Int, month: Int): ClosedRange<LocalDate> =
        YearMonth.of(year, month).let { it.atDay(1)..it.atEndOfMonth() }

    @Test
    fun `a spending sum this month gives the calendar months April 2025 through March 2026, oldest first`() {
        val spending = Metric(id = 1, name = "Spending")
        val transactions = listOf(
            expense(1_000, LocalDate.of(2025, 3, 31)),
            expense(2_000, LocalDate.of(2025, 4, 1)),
            expense(3_000, LocalDate.of(2025, 4, 30)),
            expense(5_000, LocalDate.of(2026, 2, 28)),
            expense(6_000, LocalDate.of(2026, 3, 2)),
            expense(4_000, LocalDate.of(2026, 3, 11)),
        )

        val history = spending.historyOf(transactions)

        val expectedRanges = listOf(
            month(2025, 4), month(2025, 5), month(2025, 6), month(2025, 7), month(2025, 8), month(2025, 9),
            month(2025, 10), month(2025, 11), month(2025, 12), month(2026, 1), month(2026, 2), month(2026, 3),
        )
        assertEquals(expectedRanges, history.map { it.range })
        assertEquals(MetricResult.Money(5_000), history.first().value)
        assertEquals(MetricResult.Money(0), history[1].value)
        assertEquals(MetricResult.Money(5_000), history[10].value)
        assertEquals(MetricResult.Money(10_000), history.last().value)
        assertEquals(spending.evaluate(transactions, categories, today), history.last().value)
    }

    @Test
    fun `a past month's average is evaluated as of its last day, so it divides by the whole month`() {
        val perDay = Metric(id = 1, name = "Per day", measure = Measure.AveragePerDay)
        val transactions = listOf(
            expense(10_000, LocalDate.of(2025, 1, 15)),
            expense(2_800, LocalDate.of(2026, 2, 3)),
            expense(1_100, LocalDate.of(2026, 3, 2)),
        )

        val history = perDay.historyOf(transactions)

        // February has 28 days: 28 € over all of them is 1 € a day.
        assertEquals(
            MetricResult.Money(100, dividedBy = DividedBy(28, SubPeriod.Day, AverageDivisor.EverySubPeriod)),
            history[10].value,
        )
        // March so far: 11 € over the 11 days up to today.
        assertEquals(
            MetricResult.Money(100, dividedBy = DividedBy(11, SubPeriod.Day, AverageDivisor.EverySubPeriod)),
            history.last().value,
        )
        assertEquals(perDay.evaluate(transactions, categories, today), history.last().value)
    }

    @Test
    fun `this week steps back by Monday to Sunday weeks`() {
        val weekly = Metric(id = 1, name = "Weekly", period = MetricPeriod.ThisWeek)
        val transactions = listOf(
            expense(1_000, LocalDate.of(2026, 3, 8)),
            expense(2_000, LocalDate.of(2026, 3, 9)),
            expense(3_000, LocalDate.of(2025, 12, 15)),
        )

        val history = weekly.historyOf(transactions)

        assertEquals(12, history.size)
        // Monday 2025-12-22 is 11 weeks before Monday 2026-03-09.
        assertEquals(LocalDate.of(2025, 12, 22)..LocalDate.of(2025, 12, 28), history.first().range)
        assertEquals(LocalDate.of(2026, 3, 2)..LocalDate.of(2026, 3, 8), history[10].range)
        assertEquals(LocalDate.of(2026, 3, 9)..LocalDate.of(2026, 3, 15), history.last().range)
        assertEquals(MetricResult.Money(0), history.first().value)
        assertEquals(MetricResult.Money(1_000), history[10].value)
        assertEquals(MetricResult.Money(2_000), history.last().value)
    }

    @Test
    fun `this year steps back by calendar years`() {
        val yearly = Metric(id = 1, name = "Yearly", period = MetricPeriod.ThisYear)
        val transactions = listOf(expense(1_000, LocalDate.of(2025, 12, 31)), expense(2_000, LocalDate.of(2015, 1, 1)))

        val history = yearly.historyOf(transactions)

        assertEquals((2015..2026).map { LocalDate.of(it, 1, 1)..LocalDate.of(it, 12, 31) }, history.map { it.range })
        assertEquals(MetricResult.Money(2_000), history.first().value)
        assertEquals(MetricResult.Money(1_000), history[10].value)
        assertEquals(MetricResult.Money(0), history.last().value)
    }

    @Test
    fun `a Metric over custom dates has no history`() {
        val custom = Metric(
            id = 1,
            name = "March",
            period = MetricPeriod.Custom(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)),
        )

        assertEquals(emptyList<PeriodValue>(), custom.historyOf(listOf(expense(1_000, today))))
    }

    @Test
    fun `a combined Metric measures its sides over each period, and a period where it has no value gives the reason`() {
        val spent = Metric(id = 1, name = "Spent")
        val pay = Metric(id = 2, name = "Pay", source = MetricSource.Income)
        val share = Metric(id = 3, name = "Share spent", combination = Combination(CombinationKind.Ratio, 1, 2))
        val metrics = listOf(spent, pay, share)
        val transactions = listOf(
            income(100_000, LocalDate.of(2026, 1, 31)),
            expense(25_000, LocalDate.of(2026, 1, 2)),
            income(200_000, LocalDate.of(2026, 3, 1)),
            expense(50_000, LocalDate.of(2026, 2, 10)),
            expense(30_000, LocalDate.of(2026, 3, 10)),
        )

        val history = share.historyOf(transactions, metrics)

        assertEquals(MetricResult.Percentage(250), history[9].value)
        assertEquals(
            MetricResult.NoValue(NoValueReason.DivideByZero("Pay", MetricResult.Money(0))),
            history[10].value,
        )
        assertEquals(MetricResult.Percentage(150), history.last().value)
    }

    @Test
    fun `only this month's entry of a sum has a projection, so far times the month's days over the days gone by`() {
        val spending = Metric(id = 1, name = "Spending", kpi = Kpi(target = 25_000, direction = KpiDirection.LowerIsBetter))
        val transactions = listOf(
            expense(6_000, LocalDate.of(2026, 3, 2)),
            expense(4_000, today),
            expense(9_000, LocalDate.of(2026, 2, 2)),
        )

        val history = spending.historyOf(transactions)

        // €100 by day 11 of 31 is on pace for €281.82.
        assertEquals(MetricResult.Money(28_182), history.last().projection)
        assertEquals(List(11) { null }, history.dropLast(1).map { it.projection })
        val verdict = spending.verdict(transactions, categories, today) as Verdict.OnPace
        assertEquals(verdict.projection, history.last().projection)
    }

    @Test
    fun `a count this year is projected to a whole number, rounded half-up, with or without a target`() {
        // 2026-03-11 is day 70 of 365: 3 expenses are on pace for 15.64, so 16.
        val expenses = Metric(id = 1, name = "Expenses", measure = Measure.Count, period = MetricPeriod.ThisYear)
        val transactions = listOf(
            expense(100, LocalDate.of(2026, 1, 5)),
            expense(100, LocalDate.of(2026, 2, 5)),
            expense(100, LocalDate.of(2026, 3, 5)),
        )

        assertEquals(MetricResult.Count(16), expenses.historyOf(transactions).last().projection)
    }

    @Test
    fun `averages and combined Metrics have no projection`() {
        val spent = Metric(id = 1, name = "Spent")
        val pay = Metric(id = 2, name = "Pay", source = MetricSource.Income)
        val difference = Metric(id = 3, name = "Saved", combination = Combination(CombinationKind.Difference, 2, 1))
        val perDay = Metric(id = 4, name = "Per day", measure = Measure.AveragePerDay)
        val perTransaction = Metric(id = 5, name = "Per expense", measure = Measure.AveragePerTransaction)
        val metrics = listOf(spent, pay, difference, perDay, perTransaction)
        val transactions = listOf(expense(1_000, today), income(5_000, today))

        listOf(difference, perDay, perTransaction).forEach { metric ->
            assertEquals(metric.name, List(12) { null }, metric.historyOf(transactions, metrics).map { it.projection })
        }
    }

    private val gap = MetricResult.NoValue(NoValueReason.NoTransactions)

    @Test
    fun `periods that end before the app's first Transaction are gaps, and a real zero after it stays zero`() {
        val groceries = Metric(id = 1, name = "Groceries", categoryIds = setOf(food.id), measure = Measure.Count)
        // The app's first Transaction is income on 2025-12-31: any Account, any Category counts.
        val transactions = listOf(income(100_000, LocalDate.of(2025, 12, 31)), expense(1_000, LocalDate.of(2026, 2, 3)))

        val history = groceries.historyOf(transactions)

        assertEquals(List(8) { gap }, history.take(8).map { it.value })
        assertEquals(
            listOf(MetricResult.Count(0), MetricResult.Count(0), MetricResult.Count(1), MetricResult.Count(0)),
            history.drop(8).map { it.value },
        )
    }

    @Test
    fun `with no Transaction at all every past period is a gap, and the current one is still evaluated`() {
        val spending = Metric(id = 1, name = "Spending")

        val history = spending.historyOf(emptyList())

        assertEquals(List(11) { gap }, history.dropLast(1).map { it.value })
        assertEquals(MetricResult.Money(0), history.last().value)
    }
}
