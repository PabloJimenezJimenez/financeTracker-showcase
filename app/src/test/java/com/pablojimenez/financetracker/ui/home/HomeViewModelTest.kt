package com.pablojimenez.financetracker.ui.home

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.Combination
import com.pablojimenez.financetracker.domain.CombinationKind
import com.pablojimenez.financetracker.domain.FakeFinanceRepository
import com.pablojimenez.financetracker.domain.Kpi
import com.pablojimenez.financetracker.domain.Metric
import com.pablojimenez.financetracker.domain.MetricPeriod
import com.pablojimenez.financetracker.domain.MetricResult
import com.pablojimenez.financetracker.domain.MetricSource
import com.pablojimenez.financetracker.domain.NoValueReason
import com.pablojimenez.financetracker.domain.Transaction
import com.pablojimenez.financetracker.domain.history
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** What [HomeViewModel.state] computes for the KPI tiles; Robolectric only provides the main thread. */
@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {
    private val repository = FakeFinanceRepository()

    /** Wednesday 2026-03-11 at noon, UTC. */
    private val clock = Clock.fixed(Instant.parse("2026-03-11T12:00:00Z"), ZoneOffset.UTC)

    private val current by lazy {
        repository.givenAccount(Account(name = "Current", type = AccountType.Current, startingBalanceCents = 0))
    }

    private val food by lazy { repository.savedCategories.first { it.name == "Food" }.id }
    private val salary by lazy { repository.savedCategories.first { it.name == "Salary" }.id }

    private fun spent(cents: Long, date: LocalDate, category: Long = food) {
        repository.givenTransaction(Transaction(accountId = current, categoryId = category, amountCents = -cents, date = date))
    }

    private fun earned(cents: Long, date: LocalDate) {
        repository.givenTransaction(Transaction(accountId = current, categoryId = salary, amountCents = cents, date = date))
    }

    /** Home as the view model computes it now, while something collects it. */
    private fun home(): HomeState {
        val viewModel = HomeViewModel(repository, clock)
        val scope = MainScope()
        scope.launch { viewModel.state.collect {} }
        shadowOf(Looper.getMainLooper()).idle()
        return viewModel.state.value.also { scope.cancel() }
    }

    private val gap = MetricResult.NoValue(NoValueReason.NoTransactions)

    @Test
    fun `KPI tiles follow the saved order, then pins it does not list oldest first, ignoring unpinned and deleted ids`() {
        val a = repository.givenMetric(Metric(name = "A", kpi = Kpi()))
        val b = repository.givenMetric(Metric(name = "B", kpi = Kpi()))
        val unpinned = repository.givenMetric(Metric(name = "Unpinned"))
        val c = repository.givenMetric(Metric(name = "C", kpi = Kpi()))
        val d = repository.givenMetric(Metric(name = "D", kpi = Kpi()))
        repository.givenKpiOrder(c, unpinned, 999, a)

        assertEquals(listOf(c, a, b, d), home().kpis.map { it.id })
    }

    @Test
    fun `with no saved order the KPI tiles come oldest first`() {
        val a = repository.givenMetric(Metric(name = "A", kpi = Kpi()))
        val b = repository.givenMetric(Metric(name = "B", kpi = Kpi()))

        assertEquals(listOf(a, b), home().kpis.map { it.id })
    }

    @Test
    fun `a 2x1 monthly KPI has a sparkline of its last 6 months, oldest first, with gaps where there is no value`() {
        // The first Transaction is in November, so October is a gap; December and February are real zeros.
        spent(2_000, LocalDate.of(2025, 11, 5))
        spent(3_000, LocalDate.of(2026, 1, 10))
        spent(1_000, LocalDate.of(2026, 3, 2))
        val id = repository.givenMetric(Metric(name = "Spending", kpi = Kpi(wide = true)))

        val tile = home().kpis.single()

        assertEquals(true, tile.wide)
        val cents = { value: Long -> MetricResult.Money(value) }
        assertEquals(listOf(gap, cents(2_000), cents(0), cents(3_000), cents(0), cents(1_000)), tile.sparkline)
        val metric = repository.savedMetrics.single { it.id == id }
        val history = runBlocking {
            metric.history(repository.savedTransactions, repository.categories.first(), LocalDate.of(2026, 3, 11), periods = 6)
        }
        assertEquals(history.map { it.value }, tile.sparkline)
    }

    @Test
    fun `a ratio's sparkline keeps a month it cannot divide as a gap, not a zero`() {
        earned(100_000, LocalDate.of(2026, 1, 1))
        spent(25_000, LocalDate.of(2026, 1, 15))
        earned(200_000, LocalDate.of(2026, 3, 1))
        spent(50_000, LocalDate.of(2026, 3, 2))
        val spending = repository.givenMetric(Metric(name = "Spending"))
        val income = repository.givenMetric(Metric(name = "Income", source = MetricSource.Income))
        repository.givenMetric(
            Metric(name = "Share spent", combination = Combination(CombinationKind.Ratio, spending, income), kpi = Kpi(wide = true)),
        )

        val sparkline = home().kpis.single().sparkline

        assertEquals(6, sparkline.size)
        assertEquals(listOf(gap, gap, gap), sparkline.take(3))
        assertEquals(MetricResult.Percentage(250), sparkline[3])
        assertTrue("February has no income to divide by", (sparkline[4] as MetricResult.NoValue).reason is NoValueReason.DivideByZero)
        assertEquals(MetricResult.Percentage(250), sparkline[5])
    }

    @Test
    fun `a 1x1 KPI and a 2x1 KPI over custom dates have no sparkline`() {
        spent(2_000, LocalDate.of(2026, 3, 2))
        repository.givenMetric(Metric(name = "Small", kpi = Kpi()))
        repository.givenMetric(
            Metric(
                name = "March",
                period = MetricPeriod.Custom(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)),
                kpi = Kpi(wide = true),
            ),
        )

        val (small, custom) = home().kpis

        assertEquals(false, small.wide)
        assertEquals(emptyList<MetricResult>(), small.sparkline)
        assertEquals(true, custom.wide)
        assertEquals(emptyList<MetricResult>(), custom.sparkline)
    }
}
