package com.pablojimenez.financetracker.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One period of a Metric's history: its [range] of days, the Metric's [value] over it and, for
 * the current period of a Metric that projects, its [projection] to the end of the period.
 */
data class PeriodValue(
    val range: ClosedRange<LocalDate>,
    val value: MetricResult,
    val projection: MetricResult? = null,
)

/**
 * This Metric's last [periods] periods of its own kind, oldest first, ending with the one that
 * holds [today]: calendar weeks (Monday to Sunday), months or years. A past period's value is
 * [Metric.evaluate] as of its last day; the current one's is as of [today], with its projection
 * when this Metric projects (a simple sum or count). A past period that ends before the app's
 * first Transaction (of any Account and Category, the same date the [AverageDivisor.EverySubPeriod]
 * divisor starts from) is a gap with no value, rather than a zero, since the app has no data for
 * it. A Metric over custom dates has a single period, so its history is empty.
 */
fun Metric.history(
    transactions: List<Transaction>,
    categories: List<Category>,
    today: LocalDate,
    metrics: List<Metric> = emptyList(),
    periods: Int,
): List<PeriodValue> {
    val period = period
    if (period is MetricPeriod.Custom) return emptyList()
    val start = period.range(today).start
    val first = transactions.minOfOrNull { it.date }
    return (periods - 1 downTo 0).map { back ->
        val range = period.range(period.step(start, back))
        if (back == 0) {
            val value = evaluate(transactions, categories, today, metrics)
            PeriodValue(range, value, projection(value, today))
        } else if (first == null || range.endInclusive < first) {
            PeriodValue(range, MetricResult.NoValue(NoValueReason.NoTransactions))
        } else {
            PeriodValue(range, evaluate(transactions, categories, range.endInclusive, metrics))
        }
    }
}

/**
 * Where [current], this Metric's value so far on [today], is heading by the end of its period:
 * so far × the period's days ÷ the days gone by, today included, rounded half-up. Null unless
 * this Metric [projects] and has a value.
 */
internal fun Metric.projection(current: MetricResult, today: LocalDate): MetricResult? {
    if (!projects()) return null
    val soFar = current.amount() ?: return null
    val days = period.range(today)
    val length = ChronoUnit.DAYS.between(days.start, days.endInclusive) + 1
    val elapsed = ChronoUnit.DAYS.between(days.start, today) + 1
    val projected = BigDecimal.valueOf(soFar)
        .multiply(BigDecimal.valueOf(length))
        .divide(BigDecimal.valueOf(elapsed), 0, RoundingMode.HALF_UP)
        .longValueExact()
    return current.withAmount(projected)
}

/**
 * Whether this Metric is judged on its pace: a simple sum or count in this calendar week, month
 * or year. Those periods always hold today, so they are never over; a custom range is not a
 * calendar period and is judged as it stands.
 */
internal fun Metric.projects(): Boolean =
    combination == null &&
        (measure == Measure.Sum || measure == Measure.Count) &&
        period !is MetricPeriod.Custom

/** The start of the calendar period [back] periods before the one starting on [start]. */
private fun MetricPeriod.step(start: LocalDate, back: Int): LocalDate {
    val count = back.toLong()
    return when (this) {
        MetricPeriod.ThisWeek -> start.minusWeeks(count)
        MetricPeriod.ThisMonth -> start.minusMonths(count)
        MetricPeriod.ThisYear -> start.minusYears(count)
        is MetricPeriod.Custom -> error("A custom period has no calendar steps")
    }
}
