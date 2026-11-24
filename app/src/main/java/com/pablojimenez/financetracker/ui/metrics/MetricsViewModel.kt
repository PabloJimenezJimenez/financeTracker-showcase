package com.pablojimenez.financetracker.ui.metrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pablojimenez.financetracker.domain.FinanceRepository
import com.pablojimenez.financetracker.domain.Kpi
import com.pablojimenez.financetracker.domain.MetricResult
import com.pablojimenez.financetracker.domain.Verdict
import com.pablojimenez.financetracker.domain.verdict
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One saved Metric as the Metrics screen shows it: its name, its current [result], whether it is
 * [pinned] as a KPI and, when it has one, its [verdict].
 */
data class MetricTile(
    val id: Long,
    val name: String,
    val result: MetricResult,
    val pinned: Boolean = false,
    val verdict: Verdict? = null,
)

/** Everything the Metrics screen shows: every saved Metric, oldest first. */
data class MetricsState(val metrics: List<MetricTile> = emptyList())

/**
 * Evaluates every saved Metric and its Verdict again whenever Metrics, Transactions or Categories
 * change, and pins or unpins Metrics from the list.
 */
class MetricsViewModel(private val repository: FinanceRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<MetricsState> = combine(
        repository.metrics,
        repository.transactions,
        repository.categories,
    ) { metrics, transactions, categories ->
        val today = LocalDate.now(clock)
        MetricsState(
            metrics.map {
                MetricTile(
                    id = it.id,
                    name = it.name,
                    result = it.evaluate(transactions, categories, today, metrics),
                    pinned = it.kpi != null,
                    verdict = it.verdict(transactions, categories, today, metrics),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MetricsState())

    /**
     * Pins the Metric [id] as a KPI with no target or direction, or unpins it, dropping its target
     * and direction. Pinning a Metric already pinned keeps its KPI as it is.
     */
    fun onPinChange(id: Long, pinned: Boolean) {
        viewModelScope.launch {
            val metric = repository.metrics.first().firstOrNull { it.id == id } ?: return@launch
            if ((metric.kpi != null) == pinned) return@launch
            repository.saveMetric(metric.copy(kpi = if (pinned) Kpi() else null))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
