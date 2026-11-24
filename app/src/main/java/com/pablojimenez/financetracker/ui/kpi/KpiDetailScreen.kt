package com.pablojimenez.financetracker.ui.kpi

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.domain.MetricPeriod
import com.pablojimenez.financetracker.domain.MetricResult
import com.pablojimenez.financetracker.domain.NoValueReason
import com.pablojimenez.financetracker.domain.PeriodValue
import com.pablojimenez.financetracker.domain.Verdict
import com.pablojimenez.financetracker.domain.amount
import com.pablojimenez.financetracker.ui.components.BentoTile
import com.pablojimenez.financetracker.ui.components.MetricResultText
import com.pablojimenez.financetracker.ui.components.TileSpan
import com.pablojimenez.financetracker.ui.components.VerdictRow
import com.pablojimenez.financetracker.ui.components.kpiContainer
import com.pablojimenez.financetracker.ui.components.label
import com.pablojimenez.financetracker.ui.components.metricValueText
import com.pablojimenez.financetracker.ui.components.verdictMark
import com.pablojimenez.financetracker.ui.components.verdictText
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The chart's bars as test semantics: each period's amount in its result scale, oldest first, null for no value. */
internal val KpiChartBarsKey = SemanticsPropertyKey<List<Long?>>("KpiChartBars")
private var SemanticsPropertyReceiver.chartBars by KpiChartBarsKey

/** The chart's target line as test semantics, in the Metric's result scale; null with none. */
internal val KpiChartTargetKey = SemanticsPropertyKey<Long?>("KpiChartTarget")
private var SemanticsPropertyReceiver.chartTarget by KpiChartTargetKey

/** The current bar's projection as test semantics, in the Metric's result scale; null with none. */
internal val KpiChartProjectionKey = SemanticsPropertyKey<Long?>("KpiChartProjection")
private var SemanticsPropertyReceiver.chartProjection by KpiChartProjectionKey

/**
 * One KPI (tagged `kpi-detail`), what its tile on Home grows into: its name in the top bar, its
 * value with the Verdict and its full note, a chart of its last periods with the list of their
 * values (not for custom dates), the "Full-width tile" switch ([onWideChange]), then Edit Metric
 * ([onEdit]) and Remove from KPIs ([onRemove]).
 * Until the Metric loads only the top bar shows. [KpiDetailViewModel] provides [state]; [onBack]
 * also runs once the KPI is closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KpiDetailScreen(
    metricId: Long,
    state: KpiDetailState,
    onWideChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(state.closed) { if (state.closed) currentOnBack() }
    val kpi = state.kpi
    Scaffold(
        modifier = modifier
            .kpiContainer(metricId)
            .fillMaxSize()
            .testTag("kpi-detail"),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    if (kpi != null) {
                        Text(
                            text = kpi.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        if (kpi != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(start = Spacing.ScreenMargin, end = Spacing.ScreenMargin, bottom = Spacing.XXXLarge),
            ) {
                ValueTile(kpi)
                if (kpi.history.isNotEmpty()) {
                    // The two tiles sit a gutter apart; the list and the actions are sections.
                    ChartTile(kpi, Modifier.padding(top = Spacing.Gutter))
                    PeriodList(kpi.period, kpi.history, Modifier.padding(top = Spacing.SectionGap))
                }
                WideTileSwitch(kpi.wide, onWideChange, Modifier.padding(top = Spacing.SectionGap))
                // The second button drops to its own line when both don't fit (large font sizes).
                FlowRow(
                    modifier = Modifier.padding(top = Spacing.Large),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.testTag("kpi-edit")) {
                        Text(stringResource(R.string.edit_metric))
                    }
                    TextButton(onClick = onRemove, modifier = Modifier.testTag("kpi-remove")) {
                        Text(stringResource(R.string.kpi_remove))
                    }
                }
            }
        }
    }
}

/**
 * "Full-width tile" with its supporting line and a Switch (tagged `kpi-wide`): the whole row is
 * one toggle, and [onChange] saves the new size at once.
 */
@Composable
private fun WideTileSwitch(wide: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.MinTouchTarget)
            .toggleable(value = wide, role = Role.Switch, onValueChange = onChange)
            .testTag("kpi-wide"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.kpi_wide_tile),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.kpi_wide_tile_supporting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = wide, onCheckedChange = null)
    }
}

/**
 * The value in a grey 2×1 tile (tagged `kpi-value`) captioned with the period, the Verdict icon
 * and word as on the KPI's tile, and the Verdict's full note.
 */
@Composable
private fun ValueTile(kpi: KpiDetail) {
    BentoTile(
        caption = stringResource(kpi.period.label),
        modifier = Modifier.testTag("kpi-value"),
        span = TileSpan.Wide,
    ) {
        MetricResultText(
            result = kpi.value,
            style = MaterialTheme.typography.displaySmall,
            noteColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        verdictMark(kpi.value, kpi.verdict)?.let { VerdictRow(it.icon, stringResource(it.word)) }
        val verdict = kpi.verdict
        if (verdict != null && kpi.value !is MetricResult.NoValue) {
            Text(
                text = verdictText(verdict),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The chart of [KpiDetail.history] in a 2×1 tile captioned with how many periods it covers. */
@Composable
private fun ChartTile(kpi: KpiDetail, modifier: Modifier = Modifier) {
    BentoTile(caption = stringResource(kpi.period.lastPeriodsCaption), modifier = modifier, span = TileSpan.Wide) {
        val description = stringResource(R.string.kpi_chart_description)
        KpiChart(
            bars = kpi.history.map { it.value.amount() },
            projection = kpi.history.last().projection?.amount(),
            target = kpi.target,
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .testTag("kpi-chart")
                .semantics { contentDescription = description },
        )
    }
}

private val MetricPeriod.lastPeriodsCaption: Int
    @StringRes get() = when (this) {
        MetricPeriod.ThisWeek -> R.string.kpi_last_weeks
        MetricPeriod.ThisMonth -> R.string.kpi_last_months
        MetricPeriod.ThisYear -> R.string.kpi_last_years
        is MetricPeriod.Custom -> error("Custom dates have no chart")
    }

/**
 * A monochrome bar chart (docs/design.md §6.7) of [bars], oldest first from the start side:
 * `onSurface` bars from a hairline `outlineVariant` zero baseline, negative ones below it, a thin
 * `onSurface` mark on the baseline for a real zero, and a gap where a period has no value. The last bar has a 1 dp `onSurface` outline from its value up to
 * its [projection], and [target] is a dashed 1 dp `onSurface` line across the chart.
 */
@Composable
private fun KpiChart(bars: List<Long?>, projection: Long?, target: Long?, modifier: Modifier = Modifier) {
    val mark = MaterialTheme.colorScheme.onSurface
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = modifier.semantics {
            chartBars = bars
            chartTarget = target
            chartProjection = projection
        },
    ) {
        val line = 1.dp.toPx()
        val zeroMark = ZeroMarkHeight.toPx()
        val values = bars.filterNotNull() + listOfNotNull(projection, target) + 0L
        val high = values.max()
        val low = values.min()
        val span = (high - low).coerceAtLeast(1).toFloat()
        // Inset by a line's width, so the target line and the outline stay inside the chart.
        val usable = size.height - 2 * line
        fun y(amount: Long): Float = line + (high - amount) / span * usable
        val baseline = y(0)
        val slot = size.width / bars.size
        val barWidth = slot * BAR_FRACTION
        fun left(index: Int): Float {
            val fromStart = index * slot + (slot - barWidth) / 2
            return if (layoutDirection == LayoutDirection.Rtl) size.width - fromStart - barWidth else fromStart
        }
        drawLine(baselineColor, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = Stroke.HairlineWidth)
        bars.forEachIndexed { index, amount ->
            when (amount) {
                null -> Unit
                // A real zero is a mark on the baseline, so it differs from a gap.
                0L -> drawRect(
                    mark,
                    topLeft = Offset(left(index), baseline - zeroMark / 2),
                    size = Size(barWidth, zeroMark),
                )
                else -> {
                    val top = min(y(amount), baseline)
                    drawRect(mark, topLeft = Offset(left(index), top), size = Size(barWidth, abs(y(amount) - baseline)))
                }
            }
        }
        val current = bars.lastOrNull()
        if (projection != null && current != null && projection != current) {
            val top = min(y(projection), y(current))
            val bottom = max(y(projection), y(current))
            drawRect(
                mark,
                topLeft = Offset(left(bars.lastIndex) + line / 2, top),
                size = Size(barWidth - line, bottom - top),
                style = Stroke(width = line),
            )
        }
        if (target != null) {
            val dash = DashLength.toPx()
            drawLine(
                mark,
                Offset(0f, y(target)),
                Offset(size.width, y(target)),
                strokeWidth = line,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
            )
        }
    }
}

/** A bar takes this share of its period's slot; the rest is the gap between bars. */
private const val BAR_FRACTION = 0.6f
private val ChartHeight = 128.dp
private val DashLength = 4.dp

/** A real zero's mark: thin, but thicker than the hairline baseline it sits on. */
private val ZeroMarkHeight = 2.dp

/**
 * The periods newest first (tagged `kpi-periods`, each row `kpi-period`): the period's label, the
 * current one marked "so far", and its value as the rest of the app writes it.
 */
@Composable
private fun PeriodList(period: MetricPeriod, history: List<PeriodValue>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.testTag("kpi-periods"), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        history.asReversed().forEachIndexed { index, entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("kpi-period")
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(Spacing.Large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = periodLabel(period, entry.range.start, current = index == 0),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = metricValueText(entry.value),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/**
 * The period of [period]'s kind starting on [start], in the owner's language: "Week of 9 Mar",
 * "March 2026" or "2026", with "· so far" when it is the [current] one.
 */
@Composable
private fun periodLabel(period: MetricPeriod, start: LocalDate, current: Boolean): String {
    val locale = LocalLocale.current.platformLocale
    val label = when (period) {
        MetricPeriod.ThisWeek -> stringResource(R.string.kpi_week_of, start.format(DAY_AND_MONTH, locale))
        MetricPeriod.ThisMonth -> start.format(MONTH_AND_YEAR, locale)
        MetricPeriod.ThisYear -> start.format(YEAR, locale)
        is MetricPeriod.Custom -> error("Custom dates have no periods")
    }
    return if (current) stringResource(R.string.kpi_period_so_far, label) else label
}

/** Date skeletons: the locale picks the order and the separators. */
private const val DAY_AND_MONTH = "dMMM"
private const val MONTH_AND_YEAR = "yMMMM"
private const val YEAR = "y"

private fun LocalDate.format(skeleton: String, locale: Locale): String {
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
    return DateTimeFormatter.ofPattern(pattern, locale).format(this)
}

/** A history of months ending in March 2026 for the previews, from [values] oldest first, null for no value. */
private fun monthly(values: List<MetricResult?>, projection: MetricResult? = null): List<PeriodValue> {
    val march = YearMonth.of(2026, 3)
    return values.mapIndexed { index, value ->
        val month = march.minusMonths((values.lastIndex - index).toLong())
        PeriodValue(
            range = month.atDay(1)..month.atEndOfMonth(),
            value = value ?: MetricResult.NoValue(NoValueReason.NoTransactions),
            projection = projection.takeIf { index == values.lastIndex },
        )
    }
}

private val paceKpi = KpiDetail(
    name = "Spending",
    period = MetricPeriod.ThisMonth,
    value = MetricResult.Money(10_000),
    verdict = Verdict.OnPace(
        soFar = MetricResult.Money(10_000),
        projection = MetricResult.Money(28_182),
        target = MetricResult.Money(25_000),
        onTrack = false,
    ),
    history = monthly(
        listOf(21_000L, 24_500, 19_800, 26_300, 23_000, 22_100, 30_400, 27_700, 24_900, 23_300, 25_100, 10_000)
            .map { MetricResult.Money(it) },
        projection = MetricResult.Money(28_182),
    ),
    target = 25_000,
    wide = true,
)

private val ratioKpi = KpiDetail(
    name = "Share spent",
    period = MetricPeriod.ThisMonth,
    value = MetricResult.Percentage(324),
    verdict = Verdict.AgainstTarget(MetricResult.Percentage(324), MetricResult.Percentage(200), onTrack = false),
    history = monthly(
        listOf(180L, 210, null, 250, 190, 170, 230, 260, 200, 150, 220, 324).map { tenths ->
            tenths?.let { MetricResult.Percentage(it) }
        },
    ),
    target = 200,
)

private val customKpi = KpiDetail(
    name = "March",
    period = MetricPeriod.Custom(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)),
    value = MetricResult.Money(10_000),
    verdict = Verdict.AgainstTarget(MetricResult.Money(10_000), MetricResult.Money(25_000), onTrack = true),
    target = 25_000,
)

@Composable
private fun KpiDetailPreview(kpi: KpiDetail, darkTheme: Boolean) {
    FinanceTrackerTheme(darkTheme = darkTheme, dynamicColor = false) {
        KpiDetailScreen(
            metricId = 1,
            state = KpiDetailState(kpi),
            onWideChange = {},
            onEdit = {},
            onRemove = {},
            onBack = {},
        )
    }
}

@Preview(name = "Pace, light", showBackground = true, heightDp = 1200)
@Composable
private fun KpiDetailPaceLightPreview() = KpiDetailPreview(paceKpi, darkTheme = false)

@Preview(name = "Pace, dark", showBackground = true, heightDp = 1200)
@Composable
private fun KpiDetailPaceDarkPreview() = KpiDetailPreview(paceKpi, darkTheme = true)

@Preview(name = "Ratio, light", showBackground = true, heightDp = 1200)
@Composable
private fun KpiDetailRatioLightPreview() = KpiDetailPreview(ratioKpi, darkTheme = false)

@Preview(name = "Ratio, dark", showBackground = true, heightDp = 1200)
@Composable
private fun KpiDetailRatioDarkPreview() = KpiDetailPreview(ratioKpi, darkTheme = true)

@Preview(name = "Custom dates, light", showBackground = true)
@Composable
private fun KpiDetailCustomLightPreview() = KpiDetailPreview(customKpi, darkTheme = false)

@Preview(name = "Custom dates, dark", showBackground = true)
@Composable
private fun KpiDetailCustomDarkPreview() = KpiDetailPreview(customKpi, darkTheme = true)
