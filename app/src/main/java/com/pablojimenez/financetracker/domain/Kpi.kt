package com.pablojimenez.financetracker.domain

import java.time.LocalDate

/** Which way a KPI should move. */
enum class KpiDirection { LowerIsBetter, HigherIsBetter }

/**
 * A pinned Metric's KPI settings: an optional [target], in the Metric's own result scale (cents
 * for money, a whole number for a count, tenths for percentages, points and changes), and an
 * optional [direction]. A target always comes with a direction, so it can be judged. [wide] makes
 * its Home tile 2×1 (full width, with a trend of its last periods) instead of 1×1; it travels with
 * the pin, so a KPI pinned again starts 1×1.
 */
data class Kpi(val target: Long? = null, val direction: KpiDirection? = null, val wide: Boolean = false) {
    init {
        require(target == null || direction != null) { "A KPI target needs a direction" }
    }
}

/**
 * The pinned Metrics among these, in the owner's [order] of Metric ids: those it names first, as it
 * names them, then any it does not (pinned since), in this list's order, which is oldest first for
 * [FinanceRepository.metrics]. Ids of Metrics that are not pinned or do not exist are ignored.
 */
fun List<Metric>.kpisInOrder(order: List<Long>): List<Metric> {
    val pinned = filter { it.kpi != null }
    val place = order.withIndex().associate { (index, id) -> id to index }
    return pinned.sortedBy { place[it.id] ?: Int.MAX_VALUE }
}

/** How a KPI is doing right now. */
sealed interface Verdict {
    /**
     * A sum or a count in an unfinished calendar period, judged on its [projection] to the end of
     * the period from the value [soFar], against [target].
     */
    data class OnPace(
        val soFar: MetricResult,
        val projection: MetricResult,
        val target: MetricResult,
        val onTrack: Boolean,
    ) : Verdict {
        /**
         * How much of [target] the value [soFar] has reached, from 0 to 1 (clamped both ways). A
         * target of 0 or less is always reached.
         */
        val reached: Float
            get() {
                val goal = checkNotNull(target.amount())
                if (goal <= 0) return 1f
                return (checkNotNull(soFar.amount()).toDouble() / goal).coerceIn(0.0, 1.0).toFloat()
            }
    }

    /** The current [value] against [target], as it stands: no projection. */
    data class AgainstTarget(val value: MetricResult, val target: MetricResult, val onTrack: Boolean) : Verdict

    /**
     * No target: the [value] so far against the [previous] period's over the same days, [better]
     * or worse; a tie counts as better.
     */
    data class AgainstPrevious(val value: MetricResult, val previous: MetricResult, val better: Boolean) : Verdict {
        /** Whether [value] is at or above [previous], whichever way is better. */
        val rose: Boolean
            get() = checkNotNull(value.amount()) >= checkNotNull(previous.amount())
    }
}

/**
 * This Metric's Verdict as a KPI over [transactions] on [today], with [categories] giving their
 * kinds and [metrics], the saved Metrics, giving a combined Metric's sides by id.
 */
fun Metric.verdict(
    transactions: List<Transaction>,
    categories: List<Category>,
    today: LocalDate,
    metrics: List<Metric> = emptyList(),
): Verdict? {
    val kpi = kpi ?: return null
    val direction = kpi.direction ?: return null
    val target = kpi.target ?: return againstPrevious(direction, transactions, categories, today, metrics)
    val current = evaluate(transactions, categories, today, metrics)
    val soFar = current.amount() ?: return null
    val projection = projection(current, today)?.let { checkNotNull(it.amount()) }
        ?: return Verdict.AgainstTarget(
            value = current.withAmount(soFar),
            target = current.withAmount(target),
            onTrack = direction.judges(soFar, target),
        )
    return Verdict.OnPace(
        soFar = current.withAmount(soFar),
        projection = current.withAmount(projection),
        target = current.withAmount(target),
        onTrack = direction.judges(projection, target),
    )
}

/**
 * This Metric's value so far against the previous period's over the same days, like the Change
 * combination compares them. A calendar period's previous one is its value as it stood on the same
 * day a week, month or year before (so a change is compared with the change before it); a custom
 * range's is as many days right before it.
 */
private fun Metric.againstPrevious(
    direction: KpiDirection,
    transactions: List<Transaction>,
    categories: List<Category>,
    today: LocalDate,
    metrics: List<Metric>,
): Verdict? {
    fun asOf(day: LocalDate) = evaluate(transactions.filter { it.date <= day }, categories, day, metrics)
    val (current, previous) = when (val period = period) {
        MetricPeriod.ThisWeek -> asOf(today) to asOf(today.minusWeeks(1))
        MetricPeriod.ThisMonth -> asOf(today) to asOf(today.minusMonths(1))
        MetricPeriod.ThisYear -> asOf(today) to asOf(today.minusYears(1))
        is MetricPeriod.Custom -> {
            val (now, before) = period.comparedWithPrevious(today)
            copy(period = now).evaluate(transactions, categories, today, metrics) to
                copy(period = before).evaluate(transactions, categories, today, metrics)
        }
    }
    val value = current.amount() ?: return null
    val was = previous.amount() ?: return null
    return Verdict.AgainstPrevious(
        value = current.withAmount(value),
        previous = previous.withAmount(was),
        better = direction.judges(value, was),
    )
}

/** Whether [value] is as good as [reference] or better, going this way. */
private fun KpiDirection.judges(value: Long, reference: Long): Boolean = when (this) {
    KpiDirection.LowerIsBetter -> value <= reference
    KpiDirection.HigherIsBetter -> value >= reference
}

/** The value in its result scale (cents, a whole number or tenths); null for no value. */
internal fun MetricResult.amount(): Long? = when (this) {
    is MetricResult.Money -> cents
    is MetricResult.Count -> count.toLong()
    is MetricResult.Percentage -> tenths
    is MetricResult.Points -> tenths
    is MetricResult.PercentChange -> tenths
    is MetricResult.NoValue -> null
}

/** [amount] in this result's unit, with nothing else (no Uncategorised part, no divisor). */
internal fun MetricResult.withAmount(amount: Long): MetricResult = when (this) {
    is MetricResult.Money -> MetricResult.Money(amount)
    is MetricResult.Count -> MetricResult.Count(Math.toIntExact(amount))
    is MetricResult.Percentage -> MetricResult.Percentage(amount)
    is MetricResult.Points -> MetricResult.Points(amount)
    is MetricResult.PercentChange -> MetricResult.PercentChange(amount)
    is MetricResult.NoValue -> error("No value to give an amount")
}
