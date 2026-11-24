package com.pablojimenez.financetracker.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How a combined Metric combines its sides. */
enum class CombinationKind {
    /** A ÷ B. */
    Ratio,

    /** A − B. */
    Difference,

    /** A against the previous period, like for like. */
    Change,
}

/**
 * A combined Metric's [kind] and its sides, by saved Metric id: [a], and [b] for a ratio or a
 * difference. An id that matches no saved Metric (0 until one is picked) is a side not chosen yet.
 */
data class Combination(val kind: CombinationKind, val a: Long, val b: Long? = null)

/** The Metrics in this list that use the Metric [id] as a side, in this list's order. */
fun List<Metric>.usersOf(id: Long): List<Metric> = filter { metric ->
    val combination = metric.combination
    combination != null &&
        (combination.a == id || (combination.kind != CombinationKind.Change && combination.b == id))
}

/**
 * The Metrics in this list a combined Metric [id] may use as a side: all but itself and those that
 * use it, directly or through others. For a new Metric (id 0), all of them.
 */
fun List<Metric>.sidesFor(id: Long): List<Metric> {
    if (id == 0L) return this
    val excluded = mutableSetOf(id)
    var users = usersOf(id)
    while (users.any { it.id !in excluded }) {
        val fresh = users.filter { it.id !in excluded }
        excluded += fresh.map { it.id }
        users = fresh.flatMap { usersOf(it.id) }
    }
    return filter { it.id !in excluded }
}

/**
 * The unit of a Metric's value. A simple Metric's is [Count] for a count and [Money] for every
 * other measure; a combined Metric's follows from its kind and its sides' units.
 */
private enum class MetricUnit {
    Money,
    Count,
    Percentage,
    Points,

    /** A change of money or a count, in percent. */
    PercentChange,

    /** A change of a percentage or of points, in points. */
    PointsChange,
}

/** A Metric's unit, or the reason its definition can have no value. */
private sealed interface Checked {
    data class Sound(val unit: MetricUnit) : Checked

    data class Refused(val reason: NoValueReason) : Checked
}

/** A side's value, or why it has none. */
private sealed interface Outcome {
    /** A value in its [unit], exact: [amount] is in cents for money. */
    data class Value(val unit: MetricUnit, val amount: BigDecimal) : Outcome

    data class None(val reason: NoValueReason) : Outcome
}

/**
 * Evaluates combined Metrics, resolving sides by id from [metrics]. A combined Metric is checked
 * first (sides chosen, units, no loop); only a sound one is computed.
 */
internal class Combiner(metrics: List<Metric>) {
    private val byId = metrics.associateBy { it.id }

    /** Why [metric]'s definition can have no value in any period, or null when it is sound. */
    fun problem(metric: Metric): NoValueReason? = (check(metric, path = emptySet()) as? Checked.Refused)?.reason

    /** The scale of [metric]'s values, or null when its definition can have no value. */
    fun scale(metric: Metric): ResultScale? = when (val checked = check(metric, path = emptySet())) {
        is Checked.Refused -> null
        is Checked.Sound -> when (checked.unit) {
            MetricUnit.Money -> ResultScale.Cents
            MetricUnit.Count -> ResultScale.Whole
            MetricUnit.Percentage, MetricUnit.Points, MetricUnit.PercentChange, MetricUnit.PointsChange ->
                ResultScale.Tenths
        }
    }

    /** [metric]'s value over [transactions] on [today], with [categories] giving their kinds. */
    fun evaluate(
        metric: Metric,
        transactions: List<Transaction>,
        categories: List<Category>,
        today: LocalDate,
    ): MetricResult {
        problem(metric)?.let { return MetricResult.NoValue(it) }
        return when (val outcome = Evaluation(transactions, categories, today).value(metric, metric.period)) {
            is Outcome.Value -> outcome.toResult()
            is Outcome.None -> MetricResult.NoValue(outcome.reason)
        }
    }

    /**
     * [metric]'s unit, or why its definition can't have a value. [path] holds the ids of the
     * combined Metrics being checked that lead to [metric], so a loop back to one is caught.
     */
    private fun check(metric: Metric, path: Set<Long>): Checked =
        if (metric.id == 0L) {
            checkUncached(metric, path)
        } else {
            checked.getOrPut(metric.id) { checkUncached(metric, path) }
        }

    /**
     * What [check] gave per Metric id, so a side used many times is checked once. Safe whatever
     * the path: a side that loops back to anything on it is refused the first time it is checked.
     */
    private val checked = mutableMapOf<Long, Checked>()

    private fun checkUncached(metric: Metric, path: Set<Long>): Checked {
        val combination = metric.combination
            ?: return Checked.Sound(if (metric.measure == Measure.Count) MetricUnit.Count else MetricUnit.Money)
        val ids = listOfNotNull(combination.a, combination.b.takeIf { combination.kind != CombinationKind.Change })
        val sides = ids.map { byId[it] }
        if (sides.any { it == null } || (combination.kind != CombinationKind.Change && combination.b == null)) {
            return Checked.Refused(NoValueReason.ChooseMetrics)
        }
        val inside = if (metric.id == 0L) path else path + metric.id
        if (ids.any { it in inside }) return Checked.Refused(NoValueReason.BuiltFromItself)
        val units = sides.map { side ->
            when (val checked = check(checkNotNull(side), inside)) {
                is Checked.Refused -> return checked
                is Checked.Sound -> checked.unit
            }
        }
        if (units.any { it == MetricUnit.PercentChange || it == MetricUnit.PointsChange }) {
            return Checked.Refused(NoValueReason.ChangeCombinedAgain)
        }
        val a = units.first()
        val unit = when (combination.kind) {
            CombinationKind.Ratio -> ratioUnit(a, units[1]) ?: return Checked.Refused(NoValueReason.RatioUnits)
            CombinationKind.Difference -> differenceUnit(a, units[1])
                ?: return Checked.Refused(NoValueReason.DifferenceNeedsSameUnit)
            CombinationKind.Change -> when (a) {
                MetricUnit.Percentage, MetricUnit.Points -> MetricUnit.PointsChange
                else -> MetricUnit.PercentChange
            }
        }
        return Checked.Sound(unit)
    }

    /** Computes values of Metrics [check] has passed, over [transactions] on [today]. */
    private inner class Evaluation(
        private val transactions: List<Transaction>,
        private val categories: List<Category>,
        private val today: LocalDate,
    ) {
        /** What [value] gave per Metric id and period, so a side used many times is computed once. */
        private val values = mutableMapOf<Pair<Long, MetricPeriod>, Outcome>()

        /** [metric]'s exact value over [period]. */
        fun value(metric: Metric, period: MetricPeriod): Outcome =
            if (metric.id == 0L) {
                compute(metric, period)
            } else {
                values.getOrPut(metric.id to period) { compute(metric, period) }
            }

        private fun compute(metric: Metric, period: MetricPeriod): Outcome {
            val combination = metric.combination
                ?: return metric.evaluateSimple(transactions, categories, today, period).toOutcome()
            val sideA = checkNotNull(byId[combination.a])
            return when (combination.kind) {
                CombinationKind.Change -> change(sideA, period)
                CombinationKind.Ratio, CombinationKind.Difference ->
                    twoSided(combination.kind, sideA, checkNotNull(byId[combination.b]), period)
            }
        }

        /** A ratio or a difference of [sideA] and [sideB] over [period]. */
        private fun twoSided(kind: CombinationKind, sideA: Metric, sideB: Metric, period: MetricPeriod): Outcome {
            val a = value(sideA, period).let { it as? Outcome.Value ?: return it }
            val b = value(sideB, period).let { it as? Outcome.Value ?: return it }
            if (kind == CombinationKind.Difference) {
                return Outcome.Value(checkNotNull(differenceUnit(a.unit, b.unit)), a.amount - b.amount)
            }
            if (b.amount.signum() == 0) {
                val zero = Outcome.Value(b.unit, BigDecimal.ZERO).toResult()
                return Outcome.None(NoValueReason.DivideByZero(sideB.name, zero))
            }
            val unit = checkNotNull(ratioUnit(a.unit, b.unit))
            val quotient = a.amount.divide(b.amount, MathContext.DECIMAL128)
            return Outcome.Value(unit, if (unit == MetricUnit.Percentage) quotient * HUNDRED else quotient)
        }

        /**
         * [side]'s change over [period] against the previous period, like for like: money and counts
         * as a signed percentage of the previous value, percentages and points in points.
         */
        private fun change(side: Metric, period: MetricPeriod): Outcome {
            val (current, previous) = period.comparedWithPrevious(today)
            val now = value(side, current).let { it as? Outcome.Value ?: return it }
            val before = value(side, previous).let { it as? Outcome.Value ?: return it }
            val difference = now.amount - before.amount
            return when (now.unit) {
                MetricUnit.Percentage, MetricUnit.Points -> Outcome.Value(MetricUnit.PointsChange, difference)
                else -> if (before.amount.signum() == 0) {
                    Outcome.None(NoValueReason.PreviousPeriodZero(side.name))
                } else {
                    val percent = difference.divide(before.amount.abs(), MathContext.DECIMAL128) * HUNDRED
                    Outcome.Value(MetricUnit.PercentChange, percent)
                }
            }
        }
    }
}

/**
 * The days a change compares when today is [today]: this period so far, and the same days of the
 * previous one. A calendar period's previous one is the calendar week, month or year before,
 * clipped to its end; a custom range's is as many days right before it. A period not started yet
 * compares all of it.
 */
internal fun MetricPeriod.comparedWithPrevious(today: LocalDate): Pair<MetricPeriod.Custom, MetricPeriod.Custom> {
    val days = range(today)
    val start = days.start
    val end = if (today < start) days.endInclusive else minOf(today, days.endInclusive)
    val previous = when (this) {
        MetricPeriod.ThisWeek -> start.minusWeeks(1)..end.minusWeeks(1)
        MetricPeriod.ThisMonth -> start.minusMonths(1)..end.minusMonths(1)
        MetricPeriod.ThisYear -> start.minusYears(1)..end.minusYears(1)
        is MetricPeriod.Custom -> {
            val length = ChronoUnit.DAYS.between(start, end) + 1
            start.minusDays(length)..start.minusDays(1)
        }
    }
    return MetricPeriod.Custom(start, end) to MetricPeriod.Custom(previous.start, previous.endInclusive)
}

/** money ÷ money and count ÷ count give a percentage, money ÷ count money per Transaction. */
private fun ratioUnit(a: MetricUnit, b: MetricUnit): MetricUnit? = when {
    a == MetricUnit.Money && b == MetricUnit.Count -> MetricUnit.Money
    a == b && (a == MetricUnit.Money || a == MetricUnit.Count) -> MetricUnit.Percentage
    else -> null
}

/** A difference keeps its sides' unit, except that percentages give percentage points. */
private fun differenceUnit(a: MetricUnit, b: MetricUnit): MetricUnit? = when {
    a != b -> null
    a == MetricUnit.Percentage -> MetricUnit.Points
    else -> a
}

private val HUNDRED = BigDecimal.valueOf(100)

private fun MetricResult.toOutcome(): Outcome = when (this) {
    is MetricResult.Money -> Outcome.Value(MetricUnit.Money, BigDecimal.valueOf(cents))
    is MetricResult.Count -> Outcome.Value(MetricUnit.Count, BigDecimal.valueOf(count.toLong()))
    is MetricResult.NoValue -> Outcome.None(reason)
    is MetricResult.Percentage, is MetricResult.Points, is MetricResult.PercentChange ->
        error("A simple Metric is money or a count")
}

/** Rounds half-up: money to the cent, percentages and points to 1 decimal. */
private fun Outcome.Value.toResult(): MetricResult = when (unit) {
    MetricUnit.Money -> MetricResult.Money(amount.setScale(0, RoundingMode.HALF_UP).longValueExact())
    MetricUnit.Count -> MetricResult.Count(amount.intValueExact())
    MetricUnit.Percentage -> MetricResult.Percentage(amount.tenths())
    MetricUnit.Points, MetricUnit.PointsChange -> MetricResult.Points(amount.tenths())
    MetricUnit.PercentChange -> MetricResult.PercentChange(amount.tenths())
}

private fun BigDecimal.tenths(): Long = movePointRight(1).setScale(0, RoundingMode.HALF_UP).longValueExact()
