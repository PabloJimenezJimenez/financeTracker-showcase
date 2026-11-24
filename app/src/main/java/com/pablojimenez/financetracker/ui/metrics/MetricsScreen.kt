package com.pablojimenez.financetracker.ui.metrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.domain.AverageDivisor
import com.pablojimenez.financetracker.domain.DividedBy
import com.pablojimenez.financetracker.domain.MetricResult
import com.pablojimenez.financetracker.domain.NoValueReason
import com.pablojimenez.financetracker.domain.SubPeriod
import com.pablojimenez.financetracker.domain.Verdict
import com.pablojimenez.financetracker.ui.components.BentoTile
import com.pablojimenez.financetracker.ui.components.MetricResultText
import com.pablojimenez.financetracker.ui.components.TileSpan
import com.pablojimenez.financetracker.ui.components.verdictText
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.Spacing

/**
 * Metrics: every saved Metric as a grey tile (tagged `metric-<id>`) with its current value, or
 * "No Metrics yet". A pinned Metric's tile is marked "KPI" and shows its Verdict, when it has one.
 * Tapping a tile calls [onOpenMetric]; its "Pin as KPI" switch (tagged `pin-<id>`) calls
 * [onPinChange] instead. "New Metric" calls [onNewMetric]. Stateless; [MetricsViewModel] provides
 * [state].
 */
@Composable
fun MetricsScreen(
    state: MetricsState,
    onNewMetric: () -> Unit,
    onOpenMetric: (Long) -> Unit,
    onPinChange: (id: Long, pinned: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewMetric,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Spacer(Modifier.width(Spacing.Small))
                Text(stringResource(R.string.new_metric))
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.ScreenMargin,
                end = Spacing.ScreenMargin,
                top = innerPadding.calculateTopPadding() + Spacing.Large,
                bottom = innerPadding.calculateBottomPadding() + Spacing.XXXLarge + Spacing.XXXLarge,
            ),
        ) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.metrics),
                    modifier = Modifier
                        .padding(bottom = Spacing.XLarge)
                        .semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (state.metrics.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.empty_metrics),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.metrics, key = { "metric-${it.id}" }) { metric ->
                BentoTile(
                    caption = metric.name,
                    modifier = Modifier
                        .padding(bottom = Spacing.Gutter)
                        .testTag("metric-${metric.id}"),
                    span = TileSpan.Wide,
                    onClick = { onOpenMetric(metric.id) },
                ) {
                    MetricTileBody(metric, onPinChange = { pinned -> onPinChange(metric.id, pinned) })
                }
            }
        }
    }
}

/**
 * A tile's value and, for a KPI, the "KPI" mark and its Verdict line; the "Pin as KPI" switch sits
 * at the end.
 */
@Composable
private fun MetricTileBody(metric: MetricTile, onPinChange: (Boolean) -> Unit) {
    val pinLabel = stringResource(R.string.pin_as_kpi)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
            MetricResultText(
                result = metric.result,
                style = MaterialTheme.typography.headlineSmall,
                noteColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (metric.pinned) {
                Text(
                    text = stringResource(R.string.kpi),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                metric.verdict?.let { verdict ->
                    Text(
                        text = verdictText(verdict),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(Spacing.Small))
        Switch(
            checked = metric.pinned,
            onCheckedChange = onPinChange,
            modifier = Modifier
                .testTag("pin-${metric.id}")
                .semantics { contentDescription = pinLabel },
        )
    }
}

private val previewState = MetricsState(
    metrics = listOf(
        MetricTile(
            7,
            "Groceries this month",
            MetricResult.Money(cents = 10_000),
            pinned = true,
            verdict = Verdict.OnPace(
                soFar = MetricResult.Money(10_000),
                projection = MetricResult.Money(28_182),
                target = MetricResult.Money(25_000),
                onTrack = false,
            ),
        ),
        MetricTile(
            8,
            "Eating out",
            MetricResult.Money(cents = 4_250),
            pinned = true,
            verdict = Verdict.AgainstPrevious(
                value = MetricResult.Money(4_250),
                previous = MetricResult.Money(6_100),
                better = true,
            ),
        ),
        MetricTile(1, "Groceries", MetricResult.Money(cents = 36_420, uncategorisedCents = 1_250)),
        MetricTile(2, "Coffees this week", MetricResult.Count(6)),
        MetricTile(
            4,
            "Daily spending",
            MetricResult.Money(cents = 2_350, dividedBy = DividedBy(21, SubPeriod.Day, AverageDivisor.EverySubPeriod)),
        ),
        MetricTile(3, "Average taxi ride", MetricResult.NoValue(NoValueReason.NoTransactions)),
        MetricTile(5, "Savings rate", MetricResult.Percentage(tenths = 250)),
        MetricTile(6, "Eating out vs last month", MetricResult.PercentChange(tenths = -40)),
    ),
)

@Preview(name = "Light", showBackground = true)
@Composable
private fun MetricsScreenPreview() {
    FinanceTrackerTheme(darkTheme = false, dynamicColor = false) {
        MetricsScreen(state = previewState, onNewMetric = {}, onOpenMetric = {}, onPinChange = { _, _ -> })
    }
}

@Preview(name = "Dark, empty", showBackground = true)
@Composable
private fun MetricsScreenEmptyPreview() {
    FinanceTrackerTheme(darkTheme = true, dynamicColor = false) {
        MetricsScreen(state = MetricsState(), onNewMetric = {}, onOpenMetric = {}, onPinChange = { _, _ -> })
    }
}
