package com.pablojimenez.financetracker.ui.components

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.domain.AverageDivisor
import com.pablojimenez.financetracker.domain.DividedBy
import com.pablojimenez.financetracker.domain.Measure
import com.pablojimenez.financetracker.domain.MetricPeriod
import com.pablojimenez.financetracker.domain.MetricResult
import com.pablojimenez.financetracker.domain.MetricSource
import com.pablojimenez.financetracker.domain.NoValueReason
import com.pablojimenez.financetracker.domain.MINUS_SIGN
import com.pablojimenez.financetracker.domain.SubPeriod
import com.pablojimenez.financetracker.domain.Verdict
import com.pablojimenez.financetracker.domain.formatCents
import com.pablojimenez.financetracker.ui.theme.Spacing
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/**
 * A Metric's [result]: money via [formatCents] (rolling digits), a count as a whole number, a
 * percentage with 1 decimal ("25.0%"), a change or points signed ("+12.3%", "−2.1 pp"), or "—"
 * with the reason underneath. Under a sum with an Uncategorised part, "Includes €X
 * Uncategorised"; under an average per day, week or month, what it divided by ("÷ 21 days").
 * The value uses [style]; the line under it uses [noteColor].
 */
@Composable
fun MetricResultText(
    result: MetricResult,
    style: TextStyle,
    noteColor: Color,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
        val note = when (result) {
            is MetricResult.Money -> {
                AnimatedMoneyText(cents = result.cents, style = style)
                val dividedBy = result.dividedBy
                when {
                    dividedBy != null -> pluralStringResource(dividedBy.label, dividedBy.count, dividedBy.count)
                    result.uncategorisedCents != 0L -> stringResource(
                        R.string.metric_includes_uncategorised,
                        formatCents(result.uncategorisedCents, locale),
                    )
                    else -> null
                }
            }
            is MetricResult.Points -> {
                // Read as "+2.1 percentage points", not "p p".
                val spoken = stringResource(
                    R.string.metric_points_spoken,
                    formatTenths(result.tenths, locale, signed = true),
                )
                Text(metricValueText(result), modifier = Modifier.semantics { contentDescription = spoken }, style = style)
                null
            }
            is MetricResult.Count, is MetricResult.Percentage, is MetricResult.PercentChange -> {
                Text(metricValueText(result), style = style)
                null
            }
            is MetricResult.NoValue -> {
                Text(stringResource(R.string.metric_no_value), style = style)
                result.reason.text()
            }
        }
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodyMedium, color = noteColor)
        }
    }
}

/** A whole number in [locale], negative ones with [MINUS_SIGN]. */
private fun formatCount(count: Int, locale: Locale): String {
    val digits = NumberFormat.getIntegerInstance(locale).format(abs(count.toLong()))
    return if (count < 0) "$MINUS_SIGN$digits" else digits
}

/**
 * [tenths] as a number with one decimal, the separator [locale]'s: 250 is "25.0". A negative one
 * starts with [MINUS_SIGN]; with [signed], a positive one with "+".
 */
private fun formatTenths(tenths: Long, locale: Locale, signed: Boolean = false): String {
    val format = NumberFormat.getNumberInstance(locale)
    format.minimumFractionDigits = 1
    format.maximumFractionDigits = 1
    val digits = format.format(BigDecimal.valueOf(abs(tenths), 1))
    return when {
        tenths < 0 -> "$MINUS_SIGN$digits"
        tenths > 0 && signed -> "+$digits"
        else -> digits
    }
}

private val DividedBy.label: Int
    @PluralsRes get() = when (divisor) {
        AverageDivisor.EverySubPeriod -> when (subPeriod) {
            SubPeriod.Day -> R.plurals.divided_by_days
            SubPeriod.Week -> R.plurals.divided_by_weeks
            SubPeriod.Month -> R.plurals.divided_by_months
        }
        AverageDivisor.SubPeriodsWithTransactions -> when (subPeriod) {
            SubPeriod.Day -> R.plurals.divided_by_days_with_transactions
            SubPeriod.Week -> R.plurals.divided_by_weeks_with_transactions
            SubPeriod.Month -> R.plurals.divided_by_months_with_transactions
        }
    }

/** The divisor chip's label for averages per [subPeriod]: "Every day", "Only days with Transactions". */
@StringRes
fun AverageDivisor.label(subPeriod: SubPeriod): Int = when (this) {
    AverageDivisor.EverySubPeriod -> when (subPeriod) {
        SubPeriod.Day -> R.string.divisor_every_day
        SubPeriod.Week -> R.string.divisor_every_week
        SubPeriod.Month -> R.string.divisor_every_month
    }
    AverageDivisor.SubPeriodsWithTransactions -> when (subPeriod) {
        SubPeriod.Day -> R.string.divisor_days_with_transactions
        SubPeriod.Week -> R.string.divisor_weeks_with_transactions
        SubPeriod.Month -> R.string.divisor_months_with_transactions
    }
}

/** Why there is no value, in words: "No Transactions in this period". */
@Composable
private fun NoValueReason.text(): String = when (this) {
    NoValueReason.NoTransactions -> stringResource(R.string.metric_no_transactions)
    is NoValueReason.DivideByZero -> stringResource(R.string.metric_divide_by_zero, divisor, metricValueText(zero))
    is NoValueReason.PreviousPeriodZero -> stringResource(R.string.metric_previous_period_zero, metric)
    NoValueReason.ChooseMetrics -> stringResource(R.string.metric_choose_metrics)
    NoValueReason.DifferenceNeedsSameUnit -> stringResource(R.string.metric_refused_difference)
    NoValueReason.RatioUnits -> stringResource(R.string.metric_refused_ratio)
    NoValueReason.ChangeCombinedAgain -> stringResource(R.string.metric_refused_change)
    NoValueReason.BuiltFromItself -> stringResource(R.string.metric_refused_itself)
}

/**
 * [verdict] as one line, its values written as [MetricResultText] writes them: "€100.00 so far,
 * on pace for €281.82 of €250.00: off track", "Target 25.0%: on track", "Better than last period
 * (€150.00)".
 */
@Composable
fun verdictText(verdict: Verdict): String = when (verdict) {
    is Verdict.OnPace -> stringResource(
        R.string.verdict_on_pace,
        metricValueText(verdict.soFar),
        metricValueText(verdict.projection),
        metricValueText(verdict.target),
        stringResource(if (verdict.onTrack) R.string.verdict_on_track else R.string.verdict_off_track),
    )
    is Verdict.AgainstTarget -> stringResource(
        R.string.verdict_against_target,
        metricValueText(verdict.target),
        stringResource(if (verdict.onTrack) R.string.verdict_on_track else R.string.verdict_off_track),
    )
    is Verdict.AgainstPrevious ->
        stringResource(if (verdict.better) R.string.verdict_better else R.string.verdict_worse, metricValueText(verdict.previous))
}

/** [result]'s value as plain text, as [MetricResultText] writes it, for a value quoted inside a sentence or a tile. */
@Composable
fun metricValueText(result: MetricResult): String {
    val locale = LocalLocale.current.platformLocale
    return when (result) {
        is MetricResult.Money -> formatCents(result.cents, locale)
        is MetricResult.Count -> formatCount(result.count, locale)
        is MetricResult.Percentage -> stringResource(R.string.metric_percentage, formatTenths(result.tenths, locale))
        is MetricResult.Points ->
            stringResource(R.string.metric_points, formatTenths(result.tenths, locale, signed = true))
        is MetricResult.PercentChange ->
            stringResource(R.string.metric_percentage, formatTenths(result.tenths, locale, signed = true))
        is MetricResult.NoValue -> stringResource(R.string.metric_no_value)
    }
}

/**
 * [result]'s value as a screen reader should say it: as [metricValueText] writes it, except points
 * in words ("+2.1 percentage points") and no value as "No value" rather than a dash.
 */
@Composable
fun metricSpokenValueText(result: MetricResult): String = when (result) {
    is MetricResult.Points -> stringResource(
        R.string.metric_points_spoken,
        formatTenths(result.tenths, LocalLocale.current.platformLocale, signed = true),
    )
    is MetricResult.NoValue -> stringResource(R.string.kpi_no_value)
    else -> metricValueText(result)
}

val MetricSource.label: Int
    @StringRes get() = when (this) {
        MetricSource.Spending -> R.string.spending
        MetricSource.Income -> R.string.income
    }

val Measure.label: Int
    @StringRes get() = when (this) {
        Measure.Sum -> R.string.measure_sum
        Measure.Count -> R.string.measure_count
        Measure.AveragePerTransaction -> R.string.measure_average_per_transaction
        Measure.AveragePerDay -> R.string.measure_average_per_day
        Measure.AveragePerWeek -> R.string.measure_average_per_week
        Measure.AveragePerMonth -> R.string.measure_average_per_month
    }

val MetricPeriod.label: Int
    @StringRes get() = when (this) {
        MetricPeriod.ThisWeek -> R.string.period_this_week
        MetricPeriod.ThisMonth -> R.string.period_this_month
        MetricPeriod.ThisYear -> R.string.period_this_year
        is MetricPeriod.Custom -> R.string.period_custom
    }
