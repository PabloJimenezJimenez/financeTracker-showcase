package com.pablojimenez.financetracker.ui.metric

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AverageDivisor
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.Combination
import com.pablojimenez.financetracker.domain.CombinationKind
import com.pablojimenez.financetracker.domain.FinanceRepository
import com.pablojimenez.financetracker.domain.Kpi
import com.pablojimenez.financetracker.domain.KpiDirection
import com.pablojimenez.financetracker.domain.Measure
import com.pablojimenez.financetracker.domain.Merchant
import com.pablojimenez.financetracker.domain.Metric
import com.pablojimenez.financetracker.domain.MetricPeriod
import com.pablojimenez.financetracker.domain.MetricResult
import com.pablojimenez.financetracker.domain.MetricSource
import com.pablojimenez.financetracker.domain.ResultScale
import com.pablojimenez.financetracker.domain.TargetInput
import com.pablojimenez.financetracker.domain.foldMerchantText
import com.pablojimenez.financetracker.domain.kind
import com.pablojimenez.financetracker.domain.pickable
import com.pablojimenez.financetracker.domain.sidesFor
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the Metric builder shows: the [metric] being built (its name may still be blank). The name
 * error appears only after a Save without a name. While [editing] it shows a saved Metric. After a
 * refused Delete, [usedBy] names the Metrics that use it until the next change.
 *
 * While [pinned] as a KPI, [target] is the target as typed (blank for none) and [direction] the
 * one picked; [metric]'s own KPI is replaced by them on Save. [targetError] and [directionError]
 * appear after a Save with a target that is not a number in the Metric's unit, or with a target
 * and no direction.
 */
data class MetricBuilderState(
    val metric: Metric = Metric(name = ""),
    val editing: Boolean = false,
    val nameError: Boolean = false,
    val saved: Boolean = false,
    val usedBy: List<String> = emptyList(),
    val deleted: Boolean = false,
    val pinned: Boolean = false,
    val target: String = "",
    val direction: KpiDirection? = null,
    val targetError: Boolean = false,
    val directionError: Boolean = false,
)

/**
 * Builds a new Metric, or with [metricId] edits that saved one. A new Metric starts as Spending ·
 * sum · this month with no filters. [result] is its value, computed again on every change. A
 * combined Metric starts as a ratio with no sides chosen. [decimalSeparator] is the one the owner
 * types, used to show a saved KPI target.
 */
class MetricBuilderViewModel(
    private val repository: FinanceRepository,
    private val clock: Clock,
    private val metricId: Long? = null,
    private val decimalSeparator: Char = '.',
) : ViewModel(), MetricBuilderActions {
    var state by mutableStateOf(MetricBuilderState(editing = metricId != null))
        private set

    /** [state]'s Metric as a flow, so [result] follows every change. */
    private val draft = MutableStateFlow(state.metric)

    private var loaded = metricId == null
    private var saving = false

    val categories: StateFlow<List<Category>> =
        repository.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The Accounts the filter offers: no archived one, unless the Metric already uses it. */
    val accounts: StateFlow<List<Account>> =
        combine(repository.accounts, draft) { accounts, metric -> accounts.pickable(keep = metric.accountIds) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The Merchants the filter offers, by name ignoring case and accents. */
    val merchants: StateFlow<List<Merchant>> =
        repository.merchants
            .map { list -> list.sortedWith(compareBy<Merchant> { foldMerchantText(it.name) }.thenBy { it.id }) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val metrics: StateFlow<List<Metric>> =
        repository.metrics.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The saved Metrics a side can be: all but this one and those that use it. */
    val sides: StateFlow<List<Metric>> =
        metrics.map { it.sidesFor(metricId ?: 0) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The live result; null until the Transactions have been read. */
    val result: StateFlow<MetricResult?> =
        combine(draft, repository.transactions, repository.categories, metrics) { metric, transactions, categories, saved ->
            metric.evaluate(transactions, categories, LocalDate.now(clock), saved)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        if (metricId != null) {
            viewModelScope.launch {
                val metrics = repository.metrics.first()
                val saved = metrics.firstOrNull { it.id == metricId } ?: return@launch
                loaded = true
                update { saved }
                val kpi = saved.kpi ?: return@launch
                val scale = saved.resultScale(metrics)
                state = state.copy(
                    pinned = true,
                    target = kpi.target?.let { target ->
                        scale?.let { TargetInput.text(target, it, decimalSeparator) }
                    }.orEmpty(),
                    direction = kpi.direction,
                )
            }
        }
    }

    private fun update(change: (Metric) -> Metric) {
        state = state.copy(metric = change(state.metric), usedBy = emptyList())
        draft.value = state.metric
    }

    override fun onNameChange(name: String) {
        update { it.copy(name = name) }
        state = state.copy(nameError = false)
    }

    /**
     * Makes it a simple Metric starting from [source]; Categories of the other kind no longer
     * apply and are dropped. Merchants don't depend on the source and are kept.
     */
    override fun onSourceChange(source: MetricSource) {
        val ofKind = categories.value.filter { it.kind == source.kind }.map { it.id }.toSet()
        update { it.copy(source = source, categoryIds = it.categoryIds intersect ofKind, combination = null) }
    }

    /** Makes it a combined Metric: a ratio with no sides chosen, unless it already is one. */
    override fun onCombine() {
        update { it.copy(combination = it.combination ?: Combination(CombinationKind.Ratio, a = 0)) }
    }

    override fun onCombinationKindChange(kind: CombinationKind) {
        update { metric -> metric.copy(combination = metric.combination?.copy(kind = kind)) }
    }

    override fun onSideAChange(metricId: Long) {
        update { metric -> metric.copy(combination = metric.combination?.copy(a = metricId)) }
    }

    override fun onSideBChange(metricId: Long) {
        update { metric -> metric.copy(combination = metric.combination?.copy(b = metricId)) }
    }

    override fun onToggleCategory(categoryId: Long) {
        update { it.copy(categoryIds = it.categoryIds.toggled(categoryId)) }
    }

    override fun onToggleUncategorised() {
        update { it.copy(uncategorised = !it.uncategorised) }
    }

    override fun onAllCategories() {
        update { it.copy(categoryIds = emptySet(), uncategorised = false) }
    }

    override fun onToggleAccount(accountId: Long) {
        update { it.copy(accountIds = it.accountIds.toggled(accountId)) }
    }

    override fun onAllAccounts() {
        update { it.copy(accountIds = emptySet()) }
    }

    override fun onToggleMerchant(merchantId: Long) {
        update { it.copy(merchantIds = it.merchantIds.toggled(merchantId)) }
    }

    override fun onAllMerchants() {
        update { it.copy(merchantIds = emptySet()) }
    }

    override fun onMeasureChange(measure: Measure) {
        update { if (measure in it.period.measures) it.copy(measure = measure) else it }
    }

    override fun onDivisorChange(divisor: AverageDivisor) {
        update { it.copy(divisor = divisor) }
    }

    /** Switches the period; a measure the new period does not offer falls back to sum. */
    override fun onPeriodChange(period: MetricPeriod) {
        update { it.copy(period = period, measure = it.measure.takeIf { m -> m in period.measures } ?: Measure.Sum) }
    }

    /** Custom dates from the first of this month to today, as a start the owner then adjusts. */
    override fun onCustomPeriod() {
        if (state.metric.period is MetricPeriod.Custom) return
        val today = LocalDate.now(clock)
        onPeriodChange(MetricPeriod.Custom(today.withDayOfMonth(1), today))
    }

    /** Moves the custom period's start; an end before it moves along. */
    override fun onCustomStartChange(start: LocalDate) {
        val custom = state.metric.period as? MetricPeriod.Custom ?: return
        onPeriodChange(MetricPeriod.Custom(start, maxOf(start, custom.end)))
    }

    /** Moves the custom period's end; a start after it moves along. */
    override fun onCustomEndChange(end: LocalDate) {
        val custom = state.metric.period as? MetricPeriod.Custom ?: return
        onPeriodChange(MetricPeriod.Custom(minOf(custom.start, end), end))
    }

    /**
     * Pins the Metric as a KPI with no target and the direction its source suggests (Lower is
     * better for Spending, Higher is better for Income, none for a combined Metric), or unpins it,
     * dropping its target and direction.
     */
    override fun onPinChange(pinned: Boolean) {
        val metric = state.metric
        val suggested = when {
            metric.combination != null -> null
            metric.source == MetricSource.Spending -> KpiDirection.LowerIsBetter
            else -> KpiDirection.HigherIsBetter
        }
        state = state.copy(
            pinned = pinned,
            target = "",
            direction = if (pinned) suggested else null,
            targetError = false,
            directionError = false,
        )
    }

    /** Takes [text] only while it can still become a target in the Metric's unit. */
    override fun onTargetChange(text: String) {
        val scale = state.metric.resultScale(metrics.value) ?: ResultScale.Cents
        if (TargetInput.accepts(text, scale)) state = state.copy(target = text, targetError = false)
    }

    /** Picks [direction], or clears it when it is the one picked. */
    override fun onToggleDirection(direction: KpiDirection) {
        state = state.copy(direction = direction.takeIf { it != state.direction }, directionError = false)
    }

    /**
     * Saves the definition when it has a name; otherwise shows the error and saves nothing. A
     * combined Metric whose sides are missing or refused is not saved either: the live result
     * says why. A combined Metric is saved without the simple pieces it ignores. A pinned
     * Metric's target must be a number in its unit and come with a direction; otherwise the
     * errors show and nothing is saved.
     */
    override fun onSave() {
        if (saving || !loaded) return
        val name = state.metric.name.trim()
        if (name.isEmpty()) {
            state = state.copy(nameError = true)
        }
        if (state.metric.definitionProblem(metrics.value) != null) return
        val typed = state.target.trim().takeIf { state.pinned && it.isNotEmpty() }
        val target = typed?.let { text ->
            state.metric.resultScale(metrics.value)?.let { TargetInput.parse(text, it) }
        }
        val targetError = typed != null && target == null
        val directionError = target != null && state.direction == null
        state = state.copy(targetError = targetError, directionError = directionError)
        if (name.isEmpty() || targetError || directionError) return
        // The tile size is chosen on the KPI's detail; the builder keeps it.
        val kpi = if (state.pinned) Kpi(target, state.direction, wide = state.metric.kpi?.wide == true) else null
        saving = true
        val metric = state.metric.copy(name = name, kpi = kpi).saved()
        viewModelScope.launch {
            repository.saveMetric(metric)
            state = state.copy(saved = true)
        }
    }

    /**
     * Deletes the edited Metric, unless combined Metrics use it: then nothing is deleted and
     * [MetricBuilderState.usedBy] names them.
     */
    override fun onDelete() {
        val id = metricId ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            val users = repository.deleteMetric(id)
            saving = false
            state = if (users.isEmpty()) state.copy(deleted = true) else state.copy(usedBy = users.map { it.name })
        }
    }
}

/** What is saved of this Metric: a combined one keeps only its name, period, combination and KPI. */
private fun Metric.saved(): Metric {
    val combination = combination ?: return this
    return Metric(
        id = id,
        name = name,
        period = period,
        combination = if (combination.kind == CombinationKind.Change) combination.copy(b = null) else combination,
        kpi = kpi,
    )
}

private fun Set<Long>.toggled(id: Long): Set<Long> = if (id in this) this - id else this + id
