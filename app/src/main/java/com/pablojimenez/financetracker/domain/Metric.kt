package com.pablojimenez.financetracker.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** What a simple Metric starts from. */
enum class MetricSource { Spending, Income }

/** How a Metric measures the Transactions it keeps. */
enum class Measure {
    Sum,
    Count,
    AveragePerTransaction,
    AveragePerDay,
    AveragePerWeek,
    AveragePerMonth,
    ;

    /** The sub-period an average per day, week or month divides by; null for the other measures. */
    val subPeriod: SubPeriod?
        get() = when (this) {
            AveragePerDay -> SubPeriod.Day
            AveragePerWeek -> SubPeriod.Week
            AveragePerMonth -> SubPeriod.Month
            else -> null
        }
}

/** What an average per day, week or month divides by. Weeks run Monday to Sunday. */
enum class SubPeriod {
    Day,
    Week,
    Month,
    ;

    /** What identifies the sub-period of [date]: the day, its week's Monday, or its month. */
    internal fun of(date: LocalDate): Any = when (this) {
        Day -> date
        Week -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Month -> YearMonth.from(date)
    }

    /** How many of these sub-periods [from] to [through] touch, both ends' included. */
    internal fun countBetween(from: LocalDate, through: LocalDate): Long = 1 + when (this) {
        Day -> ChronoUnit.DAYS.between(from, through)
        Week -> ChronoUnit.WEEKS.between(of(from) as LocalDate, of(through) as LocalDate)
        Month -> ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(through))
    }
}

/** Which sub-periods an average per day, week or month divides by. */
enum class AverageDivisor {
    /**
     * Every sub-period, empty ones included, from the first one in which the app has any
     * Transaction (never before the period starts) through the current unfinished one: the real
     * rate.
     */
    EverySubPeriod,

    /** Only the sub-periods holding a Transaction the Metric keeps, plus the current unfinished one. */
    SubPeriodsWithTransactions,
}

/** What an average was divided by: [count] [subPeriod]s, counted the [divisor] way. */
data class DividedBy(val count: Int, val subPeriod: SubPeriod, val divisor: AverageDivisor)

/** The calendar period a Metric covers, relative to today unless [Custom]. */
sealed interface MetricPeriod {
    data object ThisWeek : MetricPeriod {
        override val measures = always + Measure.AveragePerDay
    }

    data object ThisMonth : MetricPeriod {
        override val measures = always + Measure.AveragePerDay + Measure.AveragePerWeek
    }

    data object ThisYear : MetricPeriod {
        override val measures = always + Measure.AveragePerDay + Measure.AveragePerWeek + Measure.AveragePerMonth
    }

    /** From [start] to [end], both included. */
    data class Custom(val start: LocalDate, val end: LocalDate) : MetricPeriod {
        init {
            require(!end.isBefore(start)) { "A custom period ends on or after its start" }
        }

        override val measures: List<Measure>
            get() {
                val days = ChronoUnit.DAYS.between(start, end) + 1
                return always +
                    listOfNotNull(
                        Measure.AveragePerDay.takeIf { days > 1 },
                        Measure.AveragePerWeek.takeIf { days > DAYS_PER_WEEK },
                        Measure.AveragePerMonth.takeIf { YearMonth.from(start) != YearMonth.from(end) },
                    )
            }
    }

    /**
     * The measures this period offers, in order: an average per day, week or month only when
     * that sub-period is shorter than the period.
     */
    val measures: List<Measure>

    /**
     * The days this period covers when today is [today]: weeks run Monday to Sunday, and months
     * and years are calendar ones.
     */
    fun range(today: LocalDate): ClosedRange<LocalDate> = when (this) {
        ThisWeek -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it..it.plusDays(6) }
        ThisMonth -> today.withDayOfMonth(1)..today.with(TemporalAdjusters.lastDayOfMonth())
        ThisYear -> today.withDayOfYear(1)..today.with(TemporalAdjusters.lastDayOfYear())
        is Custom -> start..end
    }

    private companion object {
        val always = listOf(Measure.Sum, Measure.Count, Measure.AveragePerTransaction)
        const val DAYS_PER_WEEK = 7
    }
}

/**
 * A Metric: a value computed from Transactions over a [period].
 *
 * A simple Metric starts from [source], keeps the Transactions that match its filters and
 * measures them with [measure].
 *
 * The Category filter is [categoryIds] (a parent includes its children) plus [uncategorised]
 * for Transactions with no Category; the Account filter is [accountIds]; the Merchant filter is
 * [merchantIds], which a Transaction with no Merchant never passes. A Transaction passes a
 * filter when it matches any of its picks, and must pass all three; a filter with no picks keeps
 * all.
 * An average per day, week or month divides by the sub-periods [divisor] says.
 *
 * A combined Metric has a [combination] of saved Metrics instead: then [source], the filters,
 * [measure] and [divisor] are ignored, and [period] applies to both sides.
 *
 * A pinned Metric is a KPI and has a [kpi]; null when it is not pinned. Its Verdict is
 * [verdict].
 *
 * Only the definition is saved; the value is always computed again by [evaluate]. [id] is 0
 * until the repository saves the Metric.
 */
data class Metric(
    val id: Long = 0,
    val name: String,
    val source: MetricSource = MetricSource.Spending,
    val categoryIds: Set<Long> = emptySet(),
    val uncategorised: Boolean = false,
    val accountIds: Set<Long> = emptySet(),
    val merchantIds: Set<Long> = emptySet(),
    val measure: Measure = Measure.Sum,
    val period: MetricPeriod = MetricPeriod.ThisMonth,
    val divisor: AverageDivisor = AverageDivisor.EverySubPeriod,
    val combination: Combination? = null,
    val kpi: Kpi? = null,
) {
    /**
     * This Metric's value over [transactions] on [today], with [categories] giving their kinds and
     * [metrics], the saved Metrics, giving a combined Metric's sides by id.
     */
    fun evaluate(
        transactions: List<Transaction>,
        categories: List<Category>,
        today: LocalDate,
        metrics: List<Metric> = emptyList(),
    ): MetricResult =
        if (combination == null) {
            evaluateSimple(transactions, categories, today, period)
        } else {
            Combiner(metrics).evaluate(this, transactions, categories, today)
        }

    /**
     * Why this Metric's definition can have no value in any period, given the saved [metrics]:
     * a combined Metric's sides not chosen yet, or a refused combination. Null when it is sound.
     */
    fun definitionProblem(metrics: List<Metric>): NoValueReason? =
        if (combination == null) null else Combiner(metrics).problem(this)

    /**
     * The scale of this Metric's values, and so of a KPI target, given the saved [metrics]; null
     * when its definition can have no value.
     */
    fun resultScale(metrics: List<Metric>): ResultScale? = Combiner(metrics).scale(this)

    /** A simple Metric's value over [period], whatever its own period is. */
    internal fun evaluateSimple(
        transactions: List<Transaction>,
        categories: List<Category>,
        today: LocalDate,
        period: MetricPeriod,
    ): MetricResult {
        val kinds = categories.associate { it.id to it.kind }
        // A picked parent brings its children along.
        val picked = categoryIds + categories.filter { it.parentId in categoryIds }.map { it.id }
        val anyCategory = categoryIds.isEmpty() && !uncategorised
        fun inCategoryFilter(categoryId: Long?) =
            anyCategory || if (categoryId == null) uncategorised else categoryId in picked
        val days = period.range(today)
        val kept = transactions.filter { transaction ->
            transaction.transferId == null &&
                transaction.date in days &&
                kindOf(transaction, kinds) == source.kind &&
                inCategoryFilter(transaction.categoryId) &&
                (accountIds.isEmpty() || transaction.accountId in accountIds) &&
                (merchantIds.isEmpty() || transaction.merchantId in merchantIds)
        }
        val sum = source.positive(kept.sumOf { it.amountCents })
        // Count and the per-Transaction divisor take only money moving the source's way: a
        // Refund lowers spending but is not one more expense.
        val counted = kept.count { source.positive(it.amountCents) > 0 }
        return when (measure) {
            Measure.Sum -> MetricResult.Money(
                cents = sum,
                uncategorisedCents = source.positive(kept.filter { it.categoryId == null }.sumOf { it.amountCents }),
            )
            Measure.Count -> MetricResult.Count(counted)
            Measure.AveragePerTransaction -> average(sum, counted)?.let { MetricResult.Money(it) }
            Measure.AveragePerDay, Measure.AveragePerWeek, Measure.AveragePerMonth -> {
                val subPeriod = checkNotNull(measure.subPeriod)
                val count = when (divisor) {
                    AverageDivisor.EverySubPeriod -> {
                        // From the app's first Transaction of any kind, never before the period,
                        // through today or the period's end.
                        val first = transactions.minOfOrNull { it.date }
                        val from = first?.let { maxOf(it, days.start) }
                        val through = minOf(today, days.endInclusive)
                        if (from == null || through < from) 0 else subPeriod.countBetween(from, through).toInt()
                    }
                    AverageDivisor.SubPeriodsWithTransactions -> {
                        // A week clipped by the period keeps its Monday, so it is one sub-period.
                        val withData = kept.map { subPeriod.of(it.date) }.toSet()
                        (withData + listOfNotNull(subPeriod.of(today).takeIf { today in days })).size
                    }
                }
                average(sum, count)?.let { MetricResult.Money(it, dividedBy = DividedBy(count, subPeriod, divisor)) }
            }
        } ?: MetricResult.NoValue(NoValueReason.NoTransactions)
    }
}

/** [cents] over [divisor] rounded half-up to the cent, or null when there is nothing to divide by. */
private fun average(cents: Long, divisor: Int): Long? =
    if (divisor == 0) {
        null
    } else {
        BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(divisor.toLong()), 0, RoundingMode.HALF_UP).longValueExact()
    }

/** The whole numbers a Metric's value is counted in. */
enum class ResultScale(val decimals: Int) {
    /** Money, in cents. */
    Cents(2),

    /** A count of Transactions. */
    Whole(0),

    /** Percentages, points and changes, in tenths. */
    Tenths(1),
}

/** A Metric's value, or no value with the reason. */
sealed interface MetricResult {
    /**
     * An amount of money: a sum or an average, Spending shown positive. [uncategorisedCents] is the
     * part of a sum that comes from Uncategorised Transactions. An average per day, week or month
     * says what it was [dividedBy].
     */
    data class Money(
        val cents: Long,
        val uncategorisedCents: Long = 0,
        val dividedBy: DividedBy? = null,
    ) : MetricResult

    /** A whole number of Transactions; a difference of counts may be negative. */
    data class Count(val count: Int) : MetricResult

    /** A ratio as a percentage, in [tenths] of a percent: 250 is 25.0%. */
    data class Percentage(val tenths: Long) : MetricResult

    /**
     * Percentage points, in [tenths] of a point: −83 is −8.3 pp. A difference of percentages, or
     * the change of one against the previous period.
     */
    data class Points(val tenths: Long) : MetricResult

    /** The change of money or a count against the previous period, in [tenths] of a percent. */
    data class PercentChange(val tenths: Long) : MetricResult

    /** No value can be given, for [reason]. */
    data class NoValue(val reason: NoValueReason) : MetricResult
}

/** Why a Metric has no value. */
sealed interface NoValueReason {
    /** An average with no Transactions to divide by. */
    data object NoTransactions : NoValueReason

    /** A ratio whose [divisor] (a Metric's name) is [zero], money or a count, in the period. */
    data class DivideByZero(val divisor: String, val zero: MetricResult) : NoValueReason

    /** A change of [metric] (its name), which was zero in the previous period. */
    data class PreviousPeriodZero(val metric: String) : NoValueReason

    /** A combined Metric whose sides are not chosen yet. */
    data object ChooseMetrics : NoValueReason

    /** A combination the units or the sides don't allow; it has no value in any period. */
    sealed interface Refusal : NoValueReason

    /** A difference of two Metrics in different units. */
    data object DifferenceNeedsSameUnit : Refusal

    /** A ratio other than money ÷ money, count ÷ count or money ÷ count. */
    data object RatioUnits : Refusal

    /** A change used as a side of another combination. */
    data object ChangeCombinedAgain : Refusal

    /** A combined Metric that uses itself, directly or through another. */
    data object BuiltFromItself : Refusal
}

/** The kind of Category this source keeps. */
val MetricSource.kind: CategoryKind
    get() = when (this) {
        MetricSource.Spending -> CategoryKind.Spending
        MetricSource.Income -> CategoryKind.Income
    }

/** [signedCents] the way this source shows it: spending as a positive amount. */
private fun MetricSource.positive(signedCents: Long): Long =
    if (this == MetricSource.Spending) -signedCents else signedCents

/**
 * The kind of [transaction]: its Category's, or for an Uncategorised one, Spending when money goes
 * out and Income when it comes in. Null when it has neither (a zero amount, an unknown Category).
 */
private fun kindOf(transaction: Transaction, kinds: Map<Long, CategoryKind>): CategoryKind? {
    val categoryId = transaction.categoryId
    return when {
        categoryId != null -> kinds[categoryId]
        transaction.amountCents < 0 -> CategoryKind.Spending
        transaction.amountCents > 0 -> CategoryKind.Income
        else -> null
    }
}

/**
 * This Metric with the Category [from] replaced by [to] in its Category filter, as when [from] is
 * deleted and its Transactions move to [to] (ADR 0002); unchanged when the filter does not name [from].
 */
fun Metric.withCategoryMoved(from: Long, to: Long): Metric =
    if (from in categoryIds) copy(categoryIds = categoryIds - from + to) else this
