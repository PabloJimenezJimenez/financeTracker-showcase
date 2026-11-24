package com.pablojimenez.financetracker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.AverageDivisor
import com.pablojimenez.financetracker.domain.CategoryKind
import com.pablojimenez.financetracker.domain.Combination
import com.pablojimenez.financetracker.domain.CombinationKind
import com.pablojimenez.financetracker.domain.Kpi
import com.pablojimenez.financetracker.domain.KpiDirection
import com.pablojimenez.financetracker.domain.Measure
import com.pablojimenez.financetracker.domain.Merchant
import com.pablojimenez.financetracker.domain.Metric
import com.pablojimenez.financetracker.domain.MetricPeriod
import com.pablojimenez.financetracker.domain.MetricSource
import com.pablojimenez.financetracker.domain.PreloadedMerchants
import com.pablojimenez.financetracker.domain.Transaction
import com.pablojimenez.financetracker.domain.TransactionSource
import com.pablojimenez.financetracker.domain.Transfer
import java.time.LocalDate
import java.time.LocalTime
import java.util.Currency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomFinanceRepositoryTest {
    private val database = FinanceDatabase.create(
        ApplicationProvider.getApplicationContext<Context>(),
        name = null,
    )
    private val repository = RoomFinanceRepository(database)

    @After
    fun close() {
        database.close()
    }

    private fun categories(): List<Category> = runBlocking { repository.categories.first() }

    @Test
    fun `a new database holds 11 spending and 3 income parents`() {
        val parents = categories().filter { it.parentId == null }

        assertEquals(11, parents.count { it.kind == CategoryKind.Spending })
        assertEquals(3, parents.count { it.kind == CategoryKind.Income })
    }

    @Test
    fun `every child has its parent's kind and no category is deeper than two levels`() {
        val categories = categories()
        val byId = categories.associateBy { it.id }
        val children = categories.filter { it.parentId != null }

        assertTrue("the starter set has children", children.isNotEmpty())
        children.forEach { child ->
            val parent = byId.getValue(child.parentId!!)
            assertEquals("${child.name} has the kind of ${parent.name}", parent.kind, child.kind)
            assertNull("${parent.name} is a top-level Category", parent.parentId)
        }
    }

    @Test
    fun `a saved account comes back with every field`() = runBlocking {
        val id = repository.addAccount(
            Account(name = "Cash", type = AccountType.Cash, startingBalanceCents = -5_000),
        )

        val expected = Account(
            id = id,
            name = "Cash",
            type = AccountType.Cash,
            startingBalanceCents = -5_000,
            currency = Currency.getInstance("EUR"),
        )
        assertEquals(listOf(expected), repository.accounts.first())
    }

    @Test
    fun `a saved transaction comes back with every field`() = runBlocking {
        val accountId = repository.addAccount(
            Account(name = "Current", type = AccountType.Current, startingBalanceCents = 10_000),
        )
        val categoryId = categories().first().id
        val expense = Transaction(
            accountId = accountId,
            categoryId = categoryId,
            amountCents = -1_250,
            date = LocalDate.of(2026, 3, 10),
            time = LocalTime.of(18, 45),
            merchantId = repository.addMerchant(Merchant(name = "Mercadona")),
            note = "Weekly shop",
            source = TransactionSource.Manual,
        )
        val income = Transaction(
            accountId = accountId,
            categoryId = categoryId,
            amountCents = 1_250,
            date = LocalDate.of(2026, 3, 11),
        )

        val expenseId = repository.addTransaction(expense)
        val incomeId = repository.addTransaction(income)

        assertTrue("ids grow", incomeId > expenseId)
        assertEquals(
            setOf(expense.copy(id = expenseId), income.copy(id = incomeId)),
            repository.transactions.first().toSet(),
        )
    }

    @Test
    fun `a transaction without a category comes back without one`() = runBlocking {
        val accountId = repository.addAccount(
            Account(name = "Current", type = AccountType.Current, startingBalanceCents = 0),
        )
        val uncategorised = Transaction(
            accountId = accountId,
            categoryId = null,
            amountCents = -999,
            date = LocalDate.of(2026, 3, 10),
        )

        val id = repository.addTransaction(uncategorised)

        assertEquals(listOf(uncategorised.copy(id = id)), repository.transactions.first())
    }

    private fun givenAccount(name: String = "Current", startingBalanceCents: Long = 0): Long = runBlocking {
        repository.addAccount(Account(name = name, type = AccountType.Current, startingBalanceCents = startingBalanceCents))
    }

    private fun transactions(): List<Transaction> = runBlocking { repository.transactions.first() }

    @Test
    fun `an updated transaction comes back with every field the same`() = runBlocking {
        val current = givenAccount("Current")
        val cash = givenAccount("Cash")
        val categories = categories()
        val id = repository.addTransaction(
            Transaction(
                accountId = current,
                categoryId = categories.first().id,
                amountCents = -1_250,
                date = LocalDate.of(2026, 3, 10),
                time = LocalTime.of(18, 45),
                merchantId = repository.addMerchant(Merchant(name = "Mercadona")),
                note = "Weekly shop",
            ),
        )
        val updated = Transaction(
            id = id,
            accountId = cash,
            categoryId = categories.last().id,
            amountCents = 2_000,
            date = LocalDate.of(2026, 3, 11),
            time = null,
            merchantId = repository.addMerchant(Merchant(name = "Lidl")),
            note = null,
            source = TransactionSource.Manual,
        )

        repository.updateTransaction(updated)

        assertEquals(listOf(updated), transactions())
    }

    @Test
    fun `a saved transfer comes back as two legs with opposite signs and one transfer id`() = runBlocking {
        val current = givenAccount("Current")
        val savings = givenAccount("Savings")
        val date = LocalDate.of(2026, 3, 10)

        val transferId = repository.saveTransfer(
            Transfer(fromAccountId = current, toAccountId = savings, amountCents = 3_000, date = date, note = "Rainy day"),
        )

        val legs = transactions()
        assertEquals(2, legs.size)
        val out = legs.single { it.accountId == current }
        val into = legs.single { it.accountId == savings }
        assertEquals(
            Transaction(
                id = out.id,
                accountId = current,
                categoryId = null,
                amountCents = -3_000,
                date = date,
                note = "Rainy day",
                source = TransactionSource.Manual,
                transferId = transferId,
            ),
            out,
        )
        assertEquals(out.copy(id = into.id, accountId = savings, amountCents = 3_000), into)
    }

    @Test
    fun `re-saving a transfer rewrites both legs and keeps their ids`() = runBlocking {
        val current = givenAccount("Current")
        val savings = givenAccount("Savings")
        val cash = givenAccount("Cash")
        val transferId = repository.saveTransfer(
            Transfer(fromAccountId = current, toAccountId = savings, amountCents = 3_000, date = LocalDate.of(2026, 3, 10)),
        )
        val before = transactions()
        val outId = before.single { it.amountCents < 0 }.id
        val inId = before.single { it.amountCents > 0 }.id

        val savedId = repository.saveTransfer(
            Transfer(
                id = transferId,
                fromAccountId = cash,
                toAccountId = current,
                amountCents = 4_500,
                date = LocalDate.of(2026, 3, 11),
                note = "Back",
            ),
        )

        assertEquals(transferId, savedId)
        val leg = Transaction(
            accountId = cash,
            categoryId = null,
            amountCents = -4_500,
            date = LocalDate.of(2026, 3, 11),
            note = "Back",
            transferId = transferId,
        )
        assertEquals(
            setOf(leg.copy(id = outId), leg.copy(id = inId, accountId = current, amountCents = 4_500)),
            transactions().toSet(),
        )
    }

    @Test
    fun `deleting a transaction deletes only it`() = runBlocking {
        val current = givenAccount("Current")
        fun expense(cents: Long) =
            Transaction(accountId = current, categoryId = null, amountCents = cents, date = LocalDate.of(2026, 3, 10))
        val kept = repository.addTransaction(expense(-100))
        val deleted = repository.addTransaction(expense(-200))

        repository.deleteTransaction(deleted)

        assertEquals(listOf(kept), transactions().map { it.id })
    }

    @Test
    fun `deleting either leg of a transfer deletes both`() = runBlocking {
        val current = givenAccount("Current")
        val savings = givenAccount("Savings")
        val kept = repository.addTransaction(
            Transaction(accountId = current, categoryId = null, amountCents = -100, date = LocalDate.of(2026, 3, 10)),
        )
        fun transfer() = Transfer(
            fromAccountId = current,
            toAccountId = savings,
            amountCents = 3_000,
            date = LocalDate.of(2026, 3, 10),
        )
        val first = repository.saveTransfer(transfer())
        val second = repository.saveTransfer(transfer())
        val legs = transactions()

        repository.deleteTransaction(legs.single { it.transferId == first && it.amountCents < 0 }.id)
        repository.deleteTransaction(legs.single { it.transferId == second && it.amountCents > 0 }.id)

        assertEquals(listOf(kept), transactions().map { it.id })
    }

    private fun accounts(): List<Account> = runBlocking { repository.accounts.first() }

    @Test
    fun `deleteAccount refuses an account with transactions and deletes nothing`() = runBlocking {
        val current = givenAccount("Current")
        val transaction = repository.addTransaction(
            Transaction(accountId = current, categoryId = null, amountCents = -100, date = LocalDate.of(2026, 3, 10)),
        )

        val deleted = repository.deleteAccount(current)

        assertFalse(deleted)
        assertEquals(listOf(current), accounts().map { it.id })
        assertEquals(listOf(transaction), transactions().map { it.id })
    }

    @Test
    fun `deleteAccount deletes an account without transactions`() = runBlocking {
        val kept = givenAccount("Current")
        val empty = givenAccount("Cash")

        val deleted = repository.deleteAccount(empty)

        assertTrue(deleted)
        assertEquals(listOf(kept), accounts().map { it.id })
    }

    @Test
    fun `an archived account comes back archived`() = runBlocking {
        val current = givenAccount("Current", startingBalanceCents = 10_000)
        val cash = givenAccount("Cash")

        repository.archiveAccount(current)

        assertEquals(
            listOf(
                Account(id = current, name = "Current", type = AccountType.Current, startingBalanceCents = 10_000, archived = true),
                Account(id = cash, name = "Cash", type = AccountType.Current, startingBalanceCents = 0, archived = false),
            ),
            accounts(),
        )
    }

    private fun metrics(): List<Metric> = runBlocking { repository.metrics.first() }

    @Test
    fun `a saved metric comes back from metrics with every piece, custom dates included`() = runBlocking {
        val current = givenAccount("Current")
        val cash = givenAccount("Cash")
        val metric = Metric(
            name = "Grocery income",
            source = MetricSource.Income,
            categoryIds = setOf(2L, 5L),
            uncategorised = true,
            accountIds = setOf(current, cash),
            measure = Measure.AveragePerWeek,
            period = MetricPeriod.Custom(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 20)),
            divisor = AverageDivisor.SubPeriodsWithTransactions,
        )

        val id = repository.saveMetric(metric)

        assertEquals(listOf(metric.copy(id = id)), metrics())
    }

    @Test
    fun `a metric saved with a Merchant filter comes back with the same Merchants`() = runBlocking {
        val metric = Metric(name = "Supermarkets", merchantIds = setOf(3L, 7L))

        val id = repository.saveMetric(metric)

        assertEquals(listOf(metric.copy(id = id)), metrics())
    }

    @Test
    fun `each calendar period and an empty filter come back as saved`() = runBlocking {
        val periods = listOf(MetricPeriod.ThisWeek, MetricPeriod.ThisMonth, MetricPeriod.ThisYear)
        val saved = periods.map { period ->
            val metric = Metric(name = "Spending", period = period, measure = Measure.Count)
            metric.copy(id = repository.saveMetric(metric))
        }

        assertEquals(saved, metrics())
    }

    @Test
    fun `a metric row written without a divisor reads back as every sub-period`() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO metrics (name, source, categoryIds, uncategorised, accountIds, measure, period) " +
                "VALUES ('Per day', 'spending', '', 0, '', 'average_per_day', 'this_month')",
        )

        assertEquals(AverageDivisor.EverySubPeriod, metrics().single().divisor)
    }

    @Test
    fun `saving a metric with its id replaces it`() = runBlocking {
        val id = repository.saveMetric(Metric(name = "Groceries", categoryIds = setOf(2L)))
        val other = repository.saveMetric(Metric(name = "Eating out"))
        val edited = Metric(id = id, name = "Food", categoryIds = setOf(3L), measure = Measure.Count)

        val savedId = repository.saveMetric(edited)

        assertEquals(id, savedId)
        assertEquals(listOf(edited, Metric(id = other, name = "Eating out")), metrics())
    }

    @Test
    fun `a combined metric comes back with its kind, both sides and its period, custom dates included`() = runBlocking {
        val income = repository.saveMetric(Metric(name = "Income", source = MetricSource.Income))
        val spending = repository.saveMetric(Metric(name = "Spending"))
        val saved = Metric(
            name = "Saved",
            period = MetricPeriod.Custom(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 20)),
            combination = Combination(CombinationKind.Difference, a = income, b = spending),
        )
        val savedId = repository.saveMetric(saved)
        val change = Metric(
            name = "Income change",
            period = MetricPeriod.ThisYear,
            combination = Combination(CombinationKind.Change, a = income),
        )
        val ratio = Metric(name = "Savings rate", combination = Combination(CombinationKind.Ratio, a = savedId, b = income))

        val changeId = repository.saveMetric(change)
        val ratioId = repository.saveMetric(ratio)

        assertEquals(
            listOf(saved.copy(id = savedId), change.copy(id = changeId), ratio.copy(id = ratioId)),
            metrics().drop(2),
        )
    }

    @Test
    fun `saving a combined metric as a simple one drops its combination`() = runBlocking {
        val income = repository.saveMetric(Metric(name = "Income", source = MetricSource.Income))
        val id = repository.saveMetric(Metric(name = "Change", combination = Combination(CombinationKind.Change, income)))

        repository.saveMetric(Metric(id = id, name = "Change", measure = Measure.Count))

        assertNull(metrics().last().combination)
    }

    @Test
    fun `deleteMetric deletes an unused metric and returns no users`() = runBlocking {
        val income = repository.saveMetric(Metric(name = "Income", source = MetricSource.Income))
        val spending = repository.saveMetric(Metric(name = "Spending"))

        val users = repository.deleteMetric(spending)

        assertEquals(emptyList<Metric>(), users)
        assertEquals(listOf(income), metrics().map { it.id })
    }

    @Test
    fun `deleteMetric refuses a metric that combined metrics use, deletes nothing and returns them`() = runBlocking {
        val income = repository.saveMetric(Metric(name = "Income", source = MetricSource.Income))
        val spending = repository.saveMetric(Metric(name = "Spending"))
        repository.saveMetric(Metric(name = "Saved", combination = Combination(CombinationKind.Difference, income, spending)))
        repository.saveMetric(Metric(name = "Income change", combination = Combination(CombinationKind.Change, income)))
        val before = metrics()

        val users = repository.deleteMetric(income)

        assertEquals(listOf("Saved", "Income change"), users.map { it.name })
        assertEquals(before, metrics())
    }

    @Test
    fun `a pinned metric comes back with its target and direction, and one pinned without them too`() = runBlocking {
        val groceries = Metric(name = "Groceries", kpi = Kpi(target = 25_000, direction = KpiDirection.LowerIsBetter))
        val pay = Metric(name = "Pay", source = MetricSource.Income, kpi = Kpi(direction = KpiDirection.HigherIsBetter))
        val coffees = Metric(name = "Coffees", measure = Measure.Count, kpi = Kpi())
        val rent = Metric(name = "Rent")

        val ids = listOf(groceries, pay, coffees, rent).map { repository.saveMetric(it) }

        assertEquals(
            listOf(groceries.copy(id = ids[0]), pay.copy(id = ids[1]), coffees.copy(id = ids[2]), rent.copy(id = ids[3])),
            metrics(),
        )
    }

    @Test
    fun `saving a pinned metric unpinned drops its target and direction`() = runBlocking {
        val id = repository.saveMetric(
            Metric(name = "Groceries", kpi = Kpi(target = 25_000, direction = KpiDirection.LowerIsBetter)),
        )

        repository.saveMetric(Metric(id = id, name = "Groceries"))

        assertNull(metrics().single().kpi)
    }

    @Test
    fun `deleting a pinned metric unpins it, and a refused delete keeps its pin, target and direction`() = runBlocking {
        val kpi = Kpi(target = -1_000, direction = KpiDirection.HigherIsBetter)
        val income = repository.saveMetric(Metric(name = "Income", source = MetricSource.Income, kpi = kpi))
        val spending = repository.saveMetric(Metric(name = "Spending", kpi = kpi))
        repository.saveMetric(Metric(name = "Income change", combination = Combination(CombinationKind.Change, income)))

        repository.deleteMetric(spending)
        repository.deleteMetric(income)

        assertEquals(listOf("Income", "Income change"), metrics().map { it.name })
        assertEquals(kpi, metrics().first().kpi)
    }

    @Test
    fun `a 2x1 KPI comes back wide, and saving it 1x1 or unpinned makes it 1x1 again`() = runBlocking {
        val wide = Kpi(target = 25_000, direction = KpiDirection.LowerIsBetter, wide = true)
        val id = repository.saveMetric(Metric(name = "Groceries", kpi = wide))

        assertEquals(wide, metrics().single().kpi)

        repository.saveMetric(Metric(id = id, name = "Groceries", kpi = wide.copy(wide = false)))
        assertEquals(false, metrics().single().kpi?.wide)

        repository.saveMetric(Metric(id = id, name = "Groceries", kpi = wide))
        repository.saveMetric(Metric(id = id, name = "Groceries"))
        repository.saveMetric(Metric(id = id, name = "Groceries", kpi = Kpi()))
        assertEquals(Kpi(), metrics().single().kpi)
    }

    @Test
    fun `the KPI order is empty in a new database and comes back as last saved`() = runBlocking {
        val a = repository.saveMetric(Metric(name = "A", kpi = Kpi()))
        val b = repository.saveMetric(Metric(name = "B", kpi = Kpi()))
        val c = repository.saveMetric(Metric(name = "C", kpi = Kpi()))
        assertEquals(emptyList<Long>(), repository.kpiOrder.first())

        repository.saveKpiOrder(listOf(b, a, c))
        assertEquals(listOf(b, a, c), repository.kpiOrder.first())

        repository.saveKpiOrder(listOf(c, b))
        assertEquals(listOf(c, b), repository.kpiOrder.first())
    }

    @Test
    fun `saving a Metric or deleting another one leaves the KPI order as it was`() = runBlocking {
        val a = repository.saveMetric(Metric(name = "A", kpi = Kpi()))
        val b = repository.saveMetric(Metric(name = "B", kpi = Kpi()))
        val c = repository.saveMetric(Metric(name = "C"))
        repository.saveKpiOrder(listOf(b, a))

        repository.saveMetric(Metric(id = a, name = "A renamed", kpi = Kpi(wide = true)))
        repository.saveMetric(Metric(name = "D", kpi = Kpi()))
        repository.deleteMetric(c)

        assertEquals(listOf(b, a), repository.kpiOrder.first())
    }

    private fun merchants(): List<Merchant> = runBlocking { repository.merchants.first() }

    @Test
    fun `adding a Merchant named like a preloaded one returns the preloaded id and adds no row`() = runBlocking {
        val before = merchants()
        val mercadona = before.single { it.name == "Mercadona" }

        val id = repository.addMerchant(Merchant(name = " mercadona"))

        assertEquals(mercadona.id, id)
        assertEquals(before, merchants())
    }

    @Test
    fun `adding a new name adds one Merchant with its trimmed name and returns its id`() = runBlocking {
        val restaurants = categories().single { it.name == "Restaurants" }
        val before = merchants()

        val id = repository.addMerchant(Merchant(name = "  Bar Pepe ", defaultCategoryId = restaurants.id))

        assertEquals(before + Merchant(id = id, name = "Bar Pepe", defaultCategoryId = restaurants.id), merchants())
        assertEquals(id, repository.addMerchant(Merchant(name = "BAR PEPE")))
        assertEquals(before.size + 1, merchants().size)
    }

    @Test
    fun `a new database lists every preloaded Merchant with its starter default Category`() {
        val merchants = merchants()
        val categoryNames = categories().associate { it.id to it.name }

        assertEquals(PreloadedMerchants.size, merchants.size)
        PreloadedMerchants.forEach { preloaded ->
            val merchant = merchants.single { it.name == preloaded.name }
            assertEquals(preloaded.keywords, merchant.keywords)
            assertEquals(preloaded.name, preloaded.defaultCategorySeedId, merchant.defaultCategoryId)
        }
        fun defaultOf(name: String) = categoryNames[merchants.single { it.name == name }.defaultCategoryId]
        assertEquals("Food", defaultOf("Mercadona"))
        assertEquals("Shopping", defaultOf("Amazon"))
        assertEquals("Subscriptions", defaultOf("Netflix"))
        assertEquals("Insurance", defaultOf("Mutua Madrileña"))
        assertEquals(listOf("moeve", "cepsa"), merchants.single { it.name == "Moeve" }.keywords)
        assertEquals(
            listOf("el corte ingles", "corte ingles"),
            merchants.single { it.name == "El Corte Inglés" }.keywords,
        )
    }

    /** Mercadona with two Transactions, in Food and Household, and one Lidl Transaction. */
    private class MercadonaFixture(val mercadona: Merchant, val lidl: Merchant, val transactions: List<Transaction>)

    private fun givenMercadonaTransactions(): MercadonaFixture = runBlocking {
        val merchants = merchants()
        val mercadona = merchants.single { it.name == "Mercadona" }
        val lidl = merchants.single { it.name == "Lidl" }
        val accountId = repository.addAccount(
            Account(name = "Current", type = AccountType.Current, startingBalanceCents = 0),
        )
        fun category(name: String) = categories().single { it.name == name }.id
        val drafts = listOf(
            Transaction(accountId = accountId, categoryId = category("Food"), amountCents = -4_235,
                date = LocalDate.of(2026, 3, 9), merchantId = mercadona.id, note = "Weekly shop"),
            Transaction(accountId = accountId, categoryId = category("Household"), amountCents = -799,
                date = LocalDate.of(2026, 3, 10), merchantId = mercadona.id),
            Transaction(accountId = accountId, categoryId = category("Food"), amountCents = -1_500,
                date = LocalDate.of(2026, 3, 10), merchantId = lidl.id),
        )
        MercadonaFixture(mercadona, lidl, drafts.map { it.copy(id = repository.addTransaction(it)) })
    }

    @Test
    fun `updating a Merchant saves its trimmed name, keywords and default under the same id, leaving its Transactions`() =
        runBlocking {
            val fixture = givenMercadonaTransactions()
            val groceries = categories().single { it.name == "Groceries" }
            val before = merchants()

            val updated = repository.updateMerchant(
                fixture.mercadona.copy(
                    name = " Mercadona Online ",
                    keywords = listOf("mercadona", "merc"),
                    defaultCategoryId = groceries.id,
                ),
            )

            assertTrue(updated)
            val expected = Merchant(fixture.mercadona.id, "Mercadona Online", listOf("mercadona", "merc"), groceries.id)
            assertEquals(before.map { if (it.id == expected.id) expected else it }, merchants())
            assertEquals(fixture.transactions.toSet(), repository.transactions.first().toSet())
        }

    @Test
    fun `a Merchant can be renamed to its own name in other case`() = runBlocking {
        val mercadona = merchants().single { it.name == "Mercadona" }

        assertTrue(repository.updateMerchant(mercadona.copy(name = "MERCADONA")))

        assertEquals("MERCADONA", merchants().single { it.id == mercadona.id }.name)
    }

    @Test
    fun `renaming a Merchant to another Merchant's name or a blank one is refused and changes nothing`() =
        runBlocking {
            val mercadona = merchants().single { it.name == "Mercadona" }
            val before = merchants()

            assertFalse(repository.updateMerchant(mercadona.copy(name = " lidl", keywords = listOf("x"))))
            assertFalse(repository.updateMerchant(mercadona.copy(name = "   ", keywords = listOf("x"))))

            assertEquals(before, merchants())
        }
}
