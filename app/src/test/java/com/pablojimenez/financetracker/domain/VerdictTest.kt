package com.pablojimenez.financetracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** A KPI's Verdict, through [Metric.verdict]. */
class VerdictTest {
    /** Wednesday 2026-03-11: day 11 of 31 in March. */
    private val today = LocalDate.of(2026, 3, 11)

    private val food = Category(1, "Food", CategoryKind.Spending)
    private val salary = Category(2, "Salary", CategoryKind.Income)
    private val categories = listOf(food, salary)

    private fun expense(cents: Long, date: LocalDate = today) =
        Transaction(accountId = 1, categoryId = food.id, amountCents = -cents, date = date)

    private fun income(cents: Long, date: LocalDate = today) =
        Transaction(accountId = 1, categoryId = salary.id, amountCents = cents, date = date)

    private fun Metric.verdictOf(
        transactions: List<Transaction>,
        metrics: List<Metric> = emptyList(),
        on: LocalDate = today,
    ): Verdict? = verdict(transactions, categories, on, metrics)

    private val lower = KpiDirection.LowerIsBetter
    private val higher = KpiDirection.HigherIsBetter

    @Test
    fun `a spending sum this month is judged on its pace, so 100 euros by the 11th is on pace for 28182 cents and off track for 250 euros`() {
        val groceries = Metric(id = 1, name = "Groceries", kpi = Kpi(target = 25_000, direction = lower))

        assertEquals(
            Verdict.OnPace(
                soFar = MetricResult.Money(10_000),
                projection = MetricResult.Money(28_182),
                target = MetricResult.Money(25_000),
                onTrack = false,
            ),
            groceries.verdictOf(listOf(expense(6_000, LocalDate.of(2026, 3, 2)), expense(4_000))),
        )
    }

    @Test
    fun `income this week is projected on its pace and on track when the projection reaches the target`() {
        // Wednesday is day 3 of 7: €900 so far is on pace for €2,100.
        val pay = Metric(
            id = 1,
            name = "Pay",
            source = MetricSource.Income,
            period = MetricPeriod.ThisWeek,
            kpi = Kpi(target = 210_000, direction = higher),
        )
        val week = listOf(income(40_000, LocalDate.of(2026, 3, 9)), income(50_000))

        assertEquals(
            Verdict.OnPace(
                soFar = MetricResult.Money(90_000),
                projection = MetricResult.Money(210_000),
                target = MetricResult.Money(210_000),
                onTrack = true,
            ),
            pay.verdictOf(week),
        )
        assertEquals(false, pay.copy(kpi = Kpi(210_001, higher)).verdictOf(week).let { (it as Verdict.OnPace).onTrack })
    }

    @Test
    fun `a count this year is projected to a whole number, rounded half-up`() {
        // 2026-03-11 is day 70 of 365: 3 expenses are on pace for 15.64, so 16.
        val expenses = Metric(
            id = 1,
            name = "Expenses",
            measure = Measure.Count,
            period = MetricPeriod.ThisYear,
            kpi = Kpi(target = 16, direction = lower),
        )
        val year = listOf(expense(100, LocalDate.of(2026, 1, 5)), expense(200, LocalDate.of(2026, 2, 5)), expense(300))

        assertEquals(
            Verdict.OnPace(
                soFar = MetricResult.Count(3),
                projection = MetricResult.Count(16),
                target = MetricResult.Count(16),
                onTrack = true,
            ),
            expenses.verdictOf(year),
        )
    }

    @Test
    fun `money is projected to the cent, rounded half-up`() {
        // On March 2nd, 1 cent is on pace for 15.5 cents, so 16.
        val groceries = Metric(id = 1, name = "Groceries", kpi = Kpi(target = 15, direction = lower))
        val on = LocalDate.of(2026, 3, 2)

        assertEquals(
            Verdict.OnPace(
                soFar = MetricResult.Money(1),
                projection = MetricResult.Money(16),
                target = MetricResult.Money(15),
                onTrack = false,
            ),
            groceries.verdictOf(listOf(expense(1, on)), on = on),
        )
    }

    @Test
    fun `an average is judged on its current value against the target, with no projection`() {
        // €100 over 11 days is €9.09 a day.
        val daily = Metric(
            id = 1,
            name = "Daily",
            measure = Measure.AveragePerDay,
            kpi = Kpi(target = 909, direction = lower),
        )

        assertEquals(
            Verdict.AgainstTarget(value = MetricResult.Money(909), target = MetricResult.Money(909), onTrack = true),
            daily.verdictOf(listOf(expense(6_000, LocalDate.of(2026, 3, 1)), expense(4_000))),
        )
    }

    @Test
    fun `a combined Metric is judged on its current value, with no projection`() {
        val pay = Metric(id = 1, name = "Pay", source = MetricSource.Income)
        val spent = Metric(id = 2, name = "Spent")
        // €1,000 in and €400 out this month: 40.0% spent.
        val share = Metric(
            id = 3,
            name = "Share spent",
            combination = Combination(CombinationKind.Ratio, a = spent.id, b = pay.id),
            kpi = Kpi(target = 399, direction = lower),
        )
        val month = listOf(income(100_000, LocalDate.of(2026, 3, 1)), expense(40_000))

        assertEquals(
            Verdict.AgainstTarget(
                value = MetricResult.Percentage(400),
                target = MetricResult.Percentage(399),
                onTrack = false,
            ),
            share.verdictOf(month, metrics = listOf(pay, spent, share)),
        )
    }

    @Test
    fun `a sum over a custom period is judged on its current value, even before the period ends`() {
        val trip = Metric(
            id = 1,
            name = "Trip",
            period = MetricPeriod.Custom(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)),
            kpi = Kpi(target = 10_000, direction = lower),
        )

        assertEquals(
            Verdict.AgainstTarget(value = MetricResult.Money(10_000), target = MetricResult.Money(10_000), onTrack = true),
            trip.verdictOf(listOf(expense(10_000))),
        )
    }

    @Test
    fun `with a direction and no target, this month so far is compared with the same days of last month`() {
        // €100 by March 11th against €150 by February 11th (€50 more came later in February).
        val groceries = Metric(id = 1, name = "Groceries", kpi = Kpi(direction = lower))
        val transactions = listOf(
            expense(10_000),
            expense(15_000, LocalDate.of(2026, 2, 11)),
            expense(5_000, LocalDate.of(2026, 2, 12)),
        )

        assertEquals(
            Verdict.AgainstPrevious(value = MetricResult.Money(10_000), previous = MetricResult.Money(15_000), better = true),
            groceries.verdictOf(transactions),
        )
        assertEquals(
            Verdict.AgainstPrevious(value = MetricResult.Money(10_000), previous = MetricResult.Money(15_000), better = false),
            groceries.copy(kpi = Kpi(direction = higher)).verdictOf(transactions),
        )
    }

    @Test
    fun `a tie with the previous period counts as better`() {
        val pay = Metric(id = 1, name = "Pay", source = MetricSource.Income, kpi = Kpi(direction = higher))
        val transactions = listOf(income(50_000), income(50_000, LocalDate.of(2026, 2, 1)))

        assertEquals(
            Verdict.AgainstPrevious(value = MetricResult.Money(50_000), previous = MetricResult.Money(50_000), better = true),
            pay.verdictOf(transactions),
        )
    }

    @Test
    fun `a finished custom range is compared with as many days right before it`() {
        // March 1st to 10th against February 19th to 28th.
        val trip = Metric(
            id = 1,
            name = "Trip",
            period = MetricPeriod.Custom(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10)),
            kpi = Kpi(direction = lower),
        )
        val transactions = listOf(
            expense(3_000, LocalDate.of(2026, 3, 10)),
            expense(2_000, LocalDate.of(2026, 2, 19)),
            expense(9_000, LocalDate.of(2026, 2, 18)),
        )

        assertEquals(
            Verdict.AgainstPrevious(value = MetricResult.Money(3_000), previous = MetricResult.Money(2_000), better = false),
            trip.verdictOf(transactions),
        )
    }

    @Test
    fun `a change is compared with the change as it stood on the same day last month`() {
        val spending = Metric(id = 1, name = "Spending")
        val change = Metric(
            id = 2,
            name = "Spending vs last month",
            combination = Combination(CombinationKind.Change, a = spending.id),
            kpi = Kpi(direction = lower),
        )
        // March 1st–11th €100 against February 1st–11th €50: +100.0%. February against January 1st–11th
        // (€100) was −50.0%.
        val transactions = listOf(
            expense(10_000),
            expense(5_000, LocalDate.of(2026, 2, 11)),
            expense(10_000, LocalDate.of(2026, 1, 11)),
        )

        assertEquals(
            Verdict.AgainstPrevious(
                value = MetricResult.PercentChange(1_000),
                previous = MetricResult.PercentChange(-500),
                better = false,
            ),
            change.verdictOf(transactions, metrics = listOf(spending, change)),
        )
    }

    @Test
    fun `there is no Verdict when the Metric is not pinned, or pinned with neither target nor direction`() {
        val groceries = Metric(id = 1, name = "Groceries")
        val transactions = listOf(expense(10_000), expense(5_000, LocalDate.of(2026, 2, 11)))

        assertNull(groceries.verdictOf(transactions))
        assertNull(groceries.copy(kpi = Kpi()).verdictOf(transactions))
    }

    @Test
    fun `there is no Verdict when the value it needs has none`() {
        val average = Metric(id = 1, name = "Average", measure = Measure.AveragePerTransaction)
        val onlyLastMonth = listOf(expense(5_000, LocalDate.of(2026, 2, 11)))
        val onlyThisMonth = listOf(expense(5_000))

        assertNull(average.copy(kpi = Kpi(target = 1_000, direction = lower)).verdictOf(onlyLastMonth))
        assertNull(average.copy(kpi = Kpi(direction = lower)).verdictOf(onlyLastMonth))
        assertNull(average.copy(kpi = Kpi(direction = lower)).verdictOf(onlyThisMonth))
    }

    @Test
    fun `a KPI with a target and no direction can't be built`() {
        assertThrows(IllegalArgumentException::class.java) { Kpi(target = 25_000) }
    }
}
