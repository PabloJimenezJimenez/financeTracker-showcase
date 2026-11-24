package com.pablojimenez.financetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pablojimenez.financetracker.domain.AccountBalance
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.Combination
import com.pablojimenez.financetracker.domain.CombinationKind
import com.pablojimenez.financetracker.domain.FinanceRepository
import com.pablojimenez.financetracker.domain.GroceriesSeedId
import com.pablojimenez.financetracker.domain.Kpi
import com.pablojimenez.financetracker.domain.KpiDirection
import com.pablojimenez.financetracker.domain.Measure
import com.pablojimenez.financetracker.domain.Metric
import com.pablojimenez.financetracker.domain.MetricPeriod
import com.pablojimenez.financetracker.domain.MetricResult
import com.pablojimenez.financetracker.domain.MetricSource
import com.pablojimenez.financetracker.domain.Verdict
import com.pablojimenez.financetracker.domain.accountBalances
import com.pablojimenez.financetracker.domain.history
import com.pablojimenez.financetracker.domain.kpisInOrder
import com.pablojimenez.financetracker.domain.totalCents
import com.pablojimenez.financetracker.domain.verdict
import com.pablojimenez.financetracker.ui.components.TransactionListDay
import com.pablojimenez.financetracker.ui.components.transactionListDays
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How many periods a 2×1 KPI tile's sparkline shows. */
internal const val SPARKLINE_PERIODS = 6

/**
 * One KPI as its Home tile shows it: its name, its current [value] and its [verdict], if it has
 * one. [periodElapsed] is how much of its period has gone by today, from 0 to 1, where an
 * [Verdict.OnPace] KPI's pace bar puts its mark. A [wide] (2×1) tile also has a [sparkline]: the
 * values of the Metric's last [SPARKLINE_PERIODS] periods, oldest first, as [history] gives them
 * (a [MetricResult.NoValue] is a gap); it is empty for a 1×1 tile and for custom dates.
 */
data class KpiTile(
    val id: Long,
    val name: String,
    val value: MetricResult,
    val verdict: Verdict? = null,
    val periodElapsed: Float = 0f,
    val wide: Boolean = false,
    /** The kind of periods [sparkline] steps through. */
    val period: MetricPeriod = MetricPeriod.ThisMonth,
    val sparkline: List<MetricResult> = emptyList(),
)

/** A KPI Home offers to set up in one tap while there is none. */
enum class KpiSuggestion {
    /** Spending this month, lower is better. */
    Spending,

    /** (Income − Spending) ÷ Income this month, higher is better. */
    SavingsRate,

    /** Spending in the starter top-level Category Groceries this month, lower is better. */
    Groceries,
}

/**
 * The names the suggested KPIs save their Metrics under, in the owner's language: the three
 * suggestions' own, and the unpinned sides Savings rate is built from.
 */
data class SuggestionNames(
    val spending: String,
    val savingsRate: String,
    val groceries: String,
    val income: String,
    val savings: String,
)

/** Everything Home shows. */
data class HomeState(
    /** Net worth: the total across every Account, archived ones included. */
    val netWorthCents: Long = 0,
    /** The pinned Metrics, in the owner's order: [FinanceRepository.kpiOrder], then newer pins oldest first. */
    val kpis: List<KpiTile> = emptyList(),
    /**
     * The suggested KPIs, offered only while no Metric is pinned; Groceries only when a top-level
     * Category of that name exists. Empty until Home has loaded.
     */
    val suggestions: List<KpiSuggestion> = emptyList(),
    /** The Accounts that are not archived. */
    val accounts: List<AccountBalance> = emptyList(),
    /** Every Transaction, by day, newest first. */
    val days: List<TransactionListDay> = emptyList(),
)

/**
 * Computes Home again whenever Accounts, Categories, Transactions, Merchants, Metrics or the KPI order change;
 * [clock] says what today is.
 */
class HomeViewModel(private val repository: FinanceRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<HomeState> = combine(
        repository.accounts,
        repository.categories,
        combine(repository.transactions, repository.merchants, ::Pair),
        repository.metrics,
        repository.kpiOrder,
    ) { accounts, categories, (transactions, merchants), metrics, order ->
        val balances = accountBalances(accounts, transactions)
        val today = LocalDate.now(clock)
        val pinned = metrics.kpisInOrder(order)
        HomeState(
            netWorthCents = balances.totalCents(),
            kpis = pinned.map { metric ->
                val wide = metric.kpi?.wide == true
                KpiTile(
                    id = metric.id,
                    name = metric.name,
                    value = metric.evaluate(transactions, categories, today, metrics),
                    verdict = metric.verdict(transactions, categories, today, metrics),
                    periodElapsed = metric.period.elapsed(today),
                    wide = wide,
                    period = metric.period,
                    sparkline = if (wide) {
                        metric.history(transactions, categories, today, metrics, SPARKLINE_PERIODS).map { it.value }
                    } else {
                        emptyList()
                    },
                )
            },
            suggestions = if (pinned.isEmpty()) suggestions(categories) else emptyList(),
            accounts = balances.filterNot { it.account.archived },
            days = transactionListDays(transactions, accounts, categories, merchants),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeState())

    /** The suggestion being saved; another tap waits for it rather than saving a second set. */
    private var adding: Job? = null

    /**
     * Saves the Metrics [suggestion] stands for, named from [names], with only the suggested KPI
     * pinned. Each side of Savings rate is a new Metric, never one already saved. Nothing is saved
     * for Groceries when the starter Groceries Category is gone.
     */
    fun addSuggestion(suggestion: KpiSuggestion, names: SuggestionNames) {
        if (adding?.isActive == true) return
        adding = viewModelScope.launch {
            when (suggestion) {
                KpiSuggestion.Spending -> repository.saveMetric(thisMonth(names.spending, kpi = lowerIsBetter))
                KpiSuggestion.Groceries -> {
                    val groceries = repository.categories.first().groceries() ?: return@launch
                    val metric = thisMonth(names.groceries, kpi = lowerIsBetter)
                    repository.saveMetric(metric.copy(categoryIds = setOf(groceries.id)))
                }
                KpiSuggestion.SavingsRate -> {
                    val income = repository.saveMetric(thisMonth(names.income, MetricSource.Income))
                    val spending = repository.saveMetric(thisMonth(names.spending))
                    val difference = Combination(CombinationKind.Difference, income, spending)
                    val savings = repository.saveMetric(thisMonth(names.savings).copy(combination = difference))
                    repository.saveMetric(
                        thisMonth(names.savingsRate, kpi = Kpi(direction = KpiDirection.HigherIsBetter))
                            .copy(combination = Combination(CombinationKind.Ratio, savings, income)),
                    )
                }
            }
        }
    }

    /** Saves [ids], the pinned Metrics' ids, as the owner's order of the KPI tiles. */
    fun saveKpiOrder(ids: List<Long>) {
        viewModelScope.launch { repository.saveKpiOrder(ids) }
    }

    private fun suggestions(categories: List<Category>): List<KpiSuggestion> =
        listOfNotNull(
            KpiSuggestion.Spending,
            KpiSuggestion.SavingsRate,
            KpiSuggestion.Groceries.takeIf { categories.groceries() != null },
        )

    /** How much of this period has gone by on [today], today included, from 0 to 1. */
    private fun MetricPeriod.elapsed(today: LocalDate): Float {
        val days = range(today)
        val length = ChronoUnit.DAYS.between(days.start, days.endInclusive) + 1
        val gone = ChronoUnit.DAYS.between(days.start, today) + 1
        return (gone.toFloat() / length).coerceIn(0f, 1f)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private val lowerIsBetter = Kpi(direction = KpiDirection.LowerIsBetter)

/** A sum of [source] over this month called [name], pinned with [kpi] when given. */
private fun thisMonth(name: String, source: MetricSource = MetricSource.Spending, kpi: Kpi? = null) =
    Metric(name = name, source = source, measure = Measure.Sum, period = MetricPeriod.ThisMonth, kpi = kpi)

/**
 * The top-level Category the Groceries suggestion filters on: the starter Groceries one, found by
 * its seed id so a rename keeps it.
 */
private fun List<Category>.groceries(): Category? = firstOrNull { it.id == GroceriesSeedId && it.parentId == null }

