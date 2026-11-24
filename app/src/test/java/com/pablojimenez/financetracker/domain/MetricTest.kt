package com.pablojimenez.financetracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MetricTest {
    /** Wednesday 2026-03-11. */
    private val today = LocalDate.of(2026, 3, 11)

    private val groceries = Category(1, "Groceries", CategoryKind.Spending)
    private val food = Category(2, "Food", CategoryKind.Spending, parentId = 1)
    private val household = Category(3, "Household", CategoryKind.Spending, parentId = 1)
    private val clothes = Category(4, "Clothes", CategoryKind.Spending)
    private val salary = Category(5, "Salary", CategoryKind.Income)
    private val categories = listOf(groceries, food, household, clothes, salary)

    private val accountA = 10L
    private val accountB = 11L
    private val accountC = 12L

    private fun transaction(
        cents: Long,
        categoryId: Long?,
        date: LocalDate = today,
        accountId: Long = accountA,
        transferId: Long? = null,
        merchantId: Long? = null,
    ) = Transaction(
        accountId = accountId,
        categoryId = categoryId,
        amountCents = cents,
        date = date,
        merchantId = merchantId,
        transferId = transferId,
    )

    private fun spending(
        measure: Measure = Measure.Sum,
        period: MetricPeriod = MetricPeriod.ThisMonth,
        categoryIds: Set<Long> = emptySet(),
        uncategorised: Boolean = false,
        accountIds: Set<Long> = emptySet(),
        merchantIds: Set<Long> = emptySet(),
    ) = Metric(
        name = "Spending",
        source = MetricSource.Spending,
        categoryIds = categoryIds,
        uncategorised = uncategorised,
        accountIds = accountIds,
        merchantIds = merchantIds,
        measure = measure,
        period = period,
    )

    private fun Metric.valueOf(vararg transactions: Transaction): MetricResult =
        evaluate(transactions.toList(), categories, today)

    @Test
    fun `a spending sum ignores transfers and income, lowers by refunds, and calls out Uncategorised`() {
        val result = spending().valueOf(
            transaction(-4_000, food.id),
            transaction(-1_000, clothes.id),
            transaction(1_500, clothes.id),
            transaction(-700, null),
            transaction(-30_000, null, transferId = 7),
            transaction(30_000, null, accountId = accountB, transferId = 7),
            transaction(250_000, salary.id),
        )

        assertEquals(MetricResult.Money(cents = 4_200, uncategorisedCents = 700), result)
    }

    @Test
    fun `an income sum counts income Categories and Uncategorised money in, but no spending or transfers`() {
        val income = spending().copy(source = MetricSource.Income)

        val result = income.valueOf(
            transaction(250_000, salary.id),
            transaction(1_500, clothes.id),
            transaction(-4_000, food.id),
            transaction(300, null),
            transaction(30_000, null, transferId = 7),
        )

        assertEquals(MetricResult.Money(cents = 250_300, uncategorisedCents = 300), result)
    }

    @Test
    fun `a Category filter keeps the parent and its children, OR inside a group and AND across groups`() {
        val metric = spending(categoryIds = setOf(groceries.id), accountIds = setOf(accountA, accountB))

        val result = metric.valueOf(
            transaction(-100, groceries.id, accountId = accountA),
            transaction(-200, food.id, accountId = accountB),
            transaction(-400, household.id, accountId = accountA),
            transaction(-800, food.id, accountId = accountC),
            transaction(-1_600, clothes.id, accountId = accountA),
            transaction(-3_200, null, accountId = accountA),
        )

        assertEquals(MetricResult.Money(cents = 700), result)
    }

    @Test
    fun `a filter of only Uncategorised keeps only Transactions with no Category`() {
        val metric = spending(uncategorised = true)

        val result = metric.valueOf(
            transaction(-100, null),
            transaction(-200, food.id),
            transaction(-400, null, accountId = accountC),
        )

        assertEquals(MetricResult.Money(cents = 500, uncategorisedCents = 500), result)
    }

    @Test
    fun `Uncategorised can be picked together with a Category`() {
        val metric = spending(categoryIds = setOf(clothes.id), uncategorised = true)

        val result = metric.valueOf(
            transaction(-100, null),
            transaction(-200, food.id),
            transaction(-400, clothes.id),
        )

        assertEquals(MetricResult.Money(cents = 500, uncategorisedCents = 100), result)
    }

    @Test
    fun `picking a child Category keeps only that child`() {
        val metric = spending(categoryIds = setOf(food.id))

        val result = metric.valueOf(
            transaction(-100, groceries.id),
            transaction(-200, food.id),
            transaction(-400, household.id),
        )

        assertEquals(MetricResult.Money(cents = 200), result)
    }

    @Test
    fun `a Merchant filter keeps only Transactions at the picked Merchants, never one with no Merchant`() {
        val transactions = arrayOf(
            transaction(-100, clothes.id, merchantId = 1),
            transaction(-200, clothes.id, merchantId = 2),
            transaction(-400, clothes.id, merchantId = 3),
            transaction(-800, clothes.id, merchantId = null),
        )

        assertEquals(MetricResult.Money(cents = 300), spending(merchantIds = setOf(1, 2)).valueOf(*transactions))
        assertEquals(MetricResult.Money(cents = 1_500), spending(merchantIds = emptySet()).valueOf(*transactions))
    }

    @Test
    fun `a Merchant filter combines with the Category and Account filters, each one required`() {
        val mercadona = 1L
        val lidl = 2L
        val metric = spending(categoryIds = setOf(groceries.id), accountIds = setOf(accountA), merchantIds = setOf(mercadona))

        val result = metric.valueOf(
            transaction(-100, food.id, accountId = accountA, merchantId = mercadona),
            transaction(-200, clothes.id, accountId = accountA, merchantId = mercadona),
            transaction(-400, food.id, accountId = accountB, merchantId = mercadona),
            transaction(-800, food.id, accountId = accountA, merchantId = lidl),
            transaction(-1_600, groceries.id, accountId = accountA, merchantId = mercadona),
        )

        assertEquals(MetricResult.Money(cents = 1_700), result)
    }

    /** Spending of 1, 2, 4, 8 cents on [dates], in that order, so the sum tells which counted. */
    private fun Metric.sumOn(vararg dates: LocalDate): MetricResult =
        valueOf(*dates.mapIndexed { i, date -> transaction(-(1L shl i), clothes.id, date) }.toTypedArray())

    private fun date(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    @Test
    fun `this week runs from Monday 03-09 to Sunday 03-15 when today is Wednesday 03-11`() {
        val result = spending(period = MetricPeriod.ThisWeek)
            .sumOn(date(3, 8), date(3, 9), date(3, 15), date(3, 16))

        assertEquals(MetricResult.Money(cents = 2 + 4), result)
    }

    @Test
    fun `this month is March`() {
        val result = spending(period = MetricPeriod.ThisMonth)
            .sumOn(date(2, 28), date(3, 1), date(3, 31), date(4, 1))

        assertEquals(MetricResult.Money(cents = 2 + 4), result)
    }

    @Test
    fun `this year is 2026`() {
        val result = spending(period = MetricPeriod.ThisYear)
            .sumOn(date(12, 31, year = 2025), date(1, 1), date(12, 31), date(1, 1, year = 2027))

        assertEquals(MetricResult.Money(cents = 2 + 4), result)
    }

    @Test
    fun `custom dates include both ends`() {
        val result = spending(period = MetricPeriod.Custom(date(1, 10), date(2, 20)))
            .sumOn(date(1, 9), date(1, 10), date(2, 20), date(2, 21))

        assertEquals(MetricResult.Money(cents = 2 + 4), result)
    }

    @Test
    fun `count gives the number of matching expenses, not refunds, income or transfers`() {
        val result = spending(measure = Measure.Count).valueOf(
            transaction(-100, food.id),
            transaction(-200, null),
            transaction(-300, clothes.id),
            transaction(50, clothes.id),
            transaction(250_000, salary.id),
            transaction(-30_000, null, transferId = 7),
        )

        assertEquals(MetricResult.Count(3), result)
    }

    @Test
    fun `an income count gives the number of incomes`() {
        val result = spending(measure = Measure.Count).copy(source = MetricSource.Income).valueOf(
            transaction(250_000, salary.id),
            transaction(300, null),
            transaction(-100, food.id),
            transaction(50, clothes.id),
        )

        assertEquals(MetricResult.Count(2), result)
    }

    @Test
    fun `average per Transaction is the sum over the count, rounded half-up to the cent`() {
        val result = spending(measure = Measure.AveragePerTransaction).valueOf(
            transaction(-100, food.id),
            transaction(-251, clothes.id),
            transaction(50, clothes.id),
        )

        // (100 + 251 - 50) / 2 = 150.5
        assertEquals(MetricResult.Money(cents = 151), result)
    }

    @Test
    fun `average per Transaction has no value when nothing was spent in the period`() {
        val result = spending(measure = Measure.AveragePerTransaction).valueOf(
            transaction(50, clothes.id),
            transaction(-100, food.id, date = date(2, 1)),
        )

        assertEquals(MetricResult.NoValue(NoValueReason.NoTransactions), result)
    }

    private val onlyWithTransactions = AverageDivisor.SubPeriodsWithTransactions

    private fun average(measure: Measure, period: MetricPeriod, divisor: AverageDivisor = AverageDivisor.EverySubPeriod) =
        spending(measure = measure, period = period).copy(divisor = divisor)

    private fun money(cents: Long, count: Int, subPeriod: SubPeriod, divisor: AverageDivisor) =
        MetricResult.Money(cents = cents, dividedBy = DividedBy(count, subPeriod, divisor))

    @Test
    fun `a new Metric divides its averages by every sub-period`() {
        assertEquals(AverageDivisor.EverySubPeriod, Metric(name = "Spending").divisor)
    }

    @Test
    fun `every month counts a gap month and the unfinished current one`() {
        val result = average(Measure.AveragePerMonth, MetricPeriod.ThisYear).valueOf(
            transaction(-10_000, food.id, date = date(1, 5)),
            transaction(-20_000, clothes.id, date = date(1, 28)),
        )

        // €300 in January, nothing in February, nothing yet in March: €300 / 3 months.
        assertEquals(money(10_000, 3, SubPeriod.Month, AverageDivisor.EverySubPeriod), result)
    }

    @Test
    fun `only months with Transactions skips the gap month but counts the unfinished current one`() {
        val result = average(Measure.AveragePerMonth, MetricPeriod.ThisYear, onlyWithTransactions).valueOf(
            transaction(-10_000, food.id, date = date(1, 5)),
            transaction(-20_000, clothes.id, date = date(1, 28)),
        )

        // €300 in January, nothing in February, nothing yet in March: €300 / 2 months.
        assertEquals(money(15_000, 2, SubPeriod.Month, onlyWithTransactions), result)
    }

    @Test
    fun `every month starts at the app's first Transaction of any kind when the period starts before it`() {
        val result = average(Measure.AveragePerMonth, MetricPeriod.ThisYear).valueOf(
            // The app's first Transaction: income on another Account, which the Metric does not keep.
            transaction(250_000, salary.id, date = date(2, 10), accountId = accountB),
            transaction(-30_000, food.id, date = date(3, 2)),
        )

        // February and March: €300 / 2 months, not / 3 from January.
        assertEquals(money(15_000, 2, SubPeriod.Month, AverageDivisor.EverySubPeriod), result)
    }

    @Test
    fun `every day never starts before the period, however old the app's first Transaction`() {
        val result = average(Measure.AveragePerDay, MetricPeriod.ThisMonth).valueOf(
            transaction(-5_000, food.id, date = date(1, 3)),
            transaction(-1_100, food.id, date = date(3, 4)),
        )

        // 03-01 to today 03-11: €11 / 11 days.
        assertEquals(money(100, 11, SubPeriod.Day, AverageDivisor.EverySubPeriod), result)
    }

    @Test
    fun `every week counts each week clipped to the month through the current one`() {
        val result = average(Measure.AveragePerWeek, MetricPeriod.ThisMonth).valueOf(
            transaction(-9_000, food.id, date = date(2, 27)),
            transaction(-600, food.id, date = date(3, 2)),
        )

        // 03-01 (the March part of the week from 02-23), 03-02..08 and the current 03-09..15.
        assertEquals(money(200, 3, SubPeriod.Week, AverageDivisor.EverySubPeriod), result)
    }

    @Test
    fun `only weeks with Transactions counts each clipped week with data, plus the current one`() {
        val result = average(Measure.AveragePerWeek, MetricPeriod.ThisMonth, onlyWithTransactions).valueOf(
            // Sunday 03-01 is the March part of the week from Monday 02-23.
            transaction(-100, food.id, date = date(3, 1)),
            transaction(-200, food.id, date = date(3, 2)),
            transaction(-300, food.id, date = date(3, 8)),
            transaction(-9_000, food.id, date = date(2, 27)),
        )

        // Weeks with data: 03-01 and 03-02..08, plus the current week of 03-09; €6 / 3.
        assertEquals(money(200, 3, SubPeriod.Week, onlyWithTransactions), result)
    }

    @Test
    fun `only days with Transactions counts the days with data plus today`() {
        val result = average(Measure.AveragePerDay, MetricPeriod.ThisWeek, onlyWithTransactions).valueOf(
            transaction(-300, food.id, date = date(3, 9)),
        )

        assertEquals(money(150, 2, SubPeriod.Day, onlyWithTransactions), result)
    }

    @Test
    fun `a past custom range per day divides by every day from the first Transaction to its end`() {
        val metric = average(Measure.AveragePerDay, MetricPeriod.Custom(date(1, 10), date(1, 20)))

        val result = metric.valueOf(
            transaction(-100, food.id, date = date(1, 5)),
            transaction(-1_100, food.id, date = date(1, 15)),
        )

        // 01-10 to 01-20, today being outside the range: €11 / 11 days.
        assertEquals(money(100, 11, SubPeriod.Day, AverageDivisor.EverySubPeriod), result)
    }

    @Test
    fun `a past custom range per day with only days with Transactions divides by those days`() {
        val metric = average(Measure.AveragePerDay, MetricPeriod.Custom(date(1, 10), date(1, 20)), onlyWithTransactions)

        val result = metric.valueOf(
            transaction(-100, food.id, date = date(1, 12)),
            transaction(-200, food.id, date = date(1, 12)),
            transaction(-301, food.id, date = date(1, 15)),
        )

        assertEquals(money(301, 2, SubPeriod.Day, onlyWithTransactions), result)
        assertEquals(MetricResult.NoValue(NoValueReason.NoTransactions), metric.valueOf())
    }

    @Test
    fun `every day has no value before the app has any Transaction`() {
        val result = average(Measure.AveragePerDay, MetricPeriod.ThisMonth).valueOf()

        assertEquals(MetricResult.NoValue(NoValueReason.NoTransactions), result)
    }

    @Test
    fun `a sub-period not shorter than the period is not offered`() {
        val always = listOf(Measure.Sum, Measure.Count, Measure.AveragePerTransaction)

        assertEquals(always + Measure.AveragePerDay, MetricPeriod.ThisWeek.measures)
        assertEquals(always + Measure.AveragePerDay + Measure.AveragePerWeek, MetricPeriod.ThisMonth.measures)
        assertEquals(
            always + Measure.AveragePerDay + Measure.AveragePerWeek + Measure.AveragePerMonth,
            MetricPeriod.ThisYear.measures,
        )
    }

    @Test
    fun `a custom range offers per week beyond 7 days and per month across calendar months`() {
        val always = listOf(Measure.Sum, Measure.Count, Measure.AveragePerTransaction)
        fun custom(start: LocalDate, end: LocalDate) = MetricPeriod.Custom(start, end).measures

        assertEquals(always, custom(date(3, 2), date(3, 2)))
        assertEquals(always + Measure.AveragePerDay, custom(date(3, 2), date(3, 8)))
        assertEquals(always + Measure.AveragePerDay + Measure.AveragePerWeek, custom(date(3, 2), date(3, 9)))
        assertEquals(always + Measure.AveragePerDay + Measure.AveragePerMonth, custom(date(1, 31), date(2, 1)))
    }
}
