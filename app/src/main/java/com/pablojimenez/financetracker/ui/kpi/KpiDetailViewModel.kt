package com.pablojimenez.financetracker.ui.kpi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pablojimenez.financetracker.domain.FinanceRepository
import com.pablojimenez.financetracker.domain.MetricPeriod
import com.pablojimenez.financetracker.domain.MetricResult
import com.pablojimenez.financetracker.domain.PeriodValue
import com.pablojimenez.financetracker.domain.Verdict
import com.pablojimenez.financetracker.domain.history
import com.pablojimenez.financetracker.domain.verdict
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How many periods a KPI's chart and list show. */
internal const val KPI_PERIODS = 12

/** One KPI as its detail screen shows it. */
data class KpiDetail(
    val name: String,
    /** The Metric's period: which kind of periods [history] steps through. */
    val period: MetricPeriod,
    /** The value now: the current period's in [history], or over the custom dates. */
    val value: MetricResult,
    val verdict: Verdict? = null,
    /** The last [KPI_PERIODS] periods, oldest first; empty for custom dates. */
    val history: List<PeriodValue> = emptyList(),
    /** The KPI's target in the Metric's result scale, or null with none. */
    val target: Long? = null,
    /** Whether the KPI's Home tile is 2×1 (full width, with a sparkline) rather than 1×1. */
    val wide: Boolean = false,
)

/** What a KPI's detail screen shows. */
data class KpiDetailState(
    /** Null until the Metric has been read. */
    val kpi: KpiDetail? = null,
    /** True once the Metric is no longer pinned or no longer exists, so the screen can close. */
    val closed: Boolean = false,
)

/**
 * The detail of the KPI [metricId], computed again whenever Categories, Transactions or Metrics
 * change; [clock] says what today is. It can unpin the Metric and change its tile's size.
 */
class KpiDetailViewModel(
    private val repository: FinanceRepository,
    clock: Clock,
    private val metricId: Long,
) : ViewModel() {
    val state: StateFlow<KpiDetailState> = combine(
        repository.categories,
        repository.transactions,
        repository.metrics,
    ) { categories, transactions, metrics ->
        val metric = metrics.firstOrNull { it.id == metricId }
        val kpi = metric?.kpi ?: return@combine KpiDetailState(closed = true)
        val today = LocalDate.now(clock)
        val history = metric.history(transactions, categories, today, metrics, periods = KPI_PERIODS)
        KpiDetailState(
            kpi = KpiDetail(
                name = metric.name,
                period = metric.period,
                value = history.lastOrNull()?.value ?: metric.evaluate(transactions, categories, today, metrics),
                verdict = metric.verdict(transactions, categories, today, metrics),
                history = history,
                target = kpi.target,
                wide = kpi.wide,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), KpiDetailState())

    private var removing = false

    /** Unpins the Metric, keeping it; the screen then closes. */
    fun remove() {
        if (removing) return
        removing = true
        viewModelScope.launch {
            try {
                val metric = repository.metrics.first().firstOrNull { it.id == metricId }
                if (metric != null) repository.saveMetric(metric.copy(kpi = null))
            } finally {
                removing = false
            }
        }
    }

    /** Makes the KPI's Home tile 2×1 when [wide], otherwise 1×1, saving it at once. */
    fun setWide(wide: Boolean) {
        viewModelScope.launch {
            val metric = repository.metrics.first().firstOrNull { it.id == metricId } ?: return@launch
            val kpi = metric.kpi ?: return@launch
            if (kpi.wide != wide) repository.saveMetric(metric.copy(kpi = kpi.copy(wide = wide)))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
