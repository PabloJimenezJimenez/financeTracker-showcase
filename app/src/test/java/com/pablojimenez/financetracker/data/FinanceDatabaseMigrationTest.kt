package com.pablojimenez.financetracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.AverageDivisor
import com.pablojimenez.financetracker.domain.Category
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
import com.pablojimenez.financetracker.domain.SeededStarterCategories
import com.pablojimenez.financetracker.domain.Transaction
import com.pablojimenez.financetracker.domain.TransactionSource
import com.pablojimenez.financetracker.domain.kpisInOrder
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens databases written by older app versions. Each old version is built from the schema Room
 * exported for it in `app/schemas/` (a test resource), so the file is exactly what that version
 * created; opening it through [FinanceDatabase.create] runs Room's migrations and its check that
 * the result matches the current entities.
 */
@RunWith(AndroidJUnit4::class)
class FinanceDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    /** What Room writes in an exported `createSql` where the table's name goes. */
    private val tableNamePlaceholder = "\${TABLE_NAME}"

    @After
    fun deleteFile() {
        context.deleteDatabase(name)
    }

    /** Creates the database file [name] as version [version] of the app created it. */
    private fun createDatabase(version: Int): SQLiteDatabase {
        val path = "${FinanceDatabase::class.java.name}/$version.json"
        val json = checkNotNull(javaClass.classLoader?.getResource(path)) { "no schema $path" }.readText()
        val schema = JSONObject(json).getJSONObject("database")
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
        val entities = schema.getJSONArray("entities")
        for (e in 0 until entities.length()) {
            val entity = entities.getJSONObject(e)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace(tableNamePlaceholder, table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (i in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(i).getString("createSql").replace(tableNamePlaceholder, table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (q in 0 until setup.length()) db.execSQL(setup.getString(q))
        db.version = version
        return db
    }

    @Test
    fun `a version 1 database migrates to version 2 with its rows intact`() {
        createDatabase(version = 1).use { db ->
            db.execSQL(
                "INSERT INTO categories (id, parentId, name, kind, position) VALUES (1, NULL, 'Groceries', 'spending', 0)",
            )
            db.execSQL(
                "INSERT INTO accounts (id, name, type, startingBalanceCents, currency) " +
                    "VALUES (1, 'Current', 'current', 10000, 'EUR')",
            )
            db.execSQL(
                "INSERT INTO transactions (id, accountId, categoryId, amountCents, date, time, merchant, note, source) " +
                    "VALUES (1, 1, 1, -1250, ${LocalDate.of(2026, 3, 10).toEpochDay()}, " +
                    "${LocalTime.of(18, 45).toNanoOfDay()}, 'Mercadona', 'Weekly shop', 'manual')",
            )
        }

        val database = FinanceDatabase.create(context, name)
        try {
            val repository = RoomFinanceRepository(database)
            runBlocking {
                val mercadona = repository.merchants.first().single { it.name == "Mercadona" }.id
                assertEquals(
                    listOf(Account(id = 1, name = "Current", type = AccountType.Current, startingBalanceCents = 10_000)),
                    repository.accounts.first(),
                )
                assertEquals(
                    listOf(
                        Transaction(
                            id = 1,
                            accountId = 1,
                            categoryId = 1,
                            amountCents = -1_250,
                            date = LocalDate.of(2026, 3, 10),
                            time = LocalTime.of(18, 45),
                            merchantId = mercadona,
                            note = "Weekly shop",
                            source = TransactionSource.Manual,
                        ),
                    ),
                    repository.transactions.first(),
                )
                assertEquals(false, repository.accounts.first().single().archived)
                assertEquals(null, repository.transactions.first().single().transferId)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `a version 2 database migrates to version 3 with its rows intact and no metrics`() {
        createDatabase(version = 2).use { db ->
            db.execSQL(
                "INSERT INTO categories (id, parentId, name, kind, position) VALUES (1, NULL, 'Groceries', 'spending', 0)",
            )
            db.execSQL(
                "INSERT INTO accounts (id, name, type, startingBalanceCents, currency, archived) " +
                    "VALUES (1, 'Current', 'current', 10000, 'EUR', 1), (2, 'Savings', 'savings', 0, 'EUR', 0)",
            )
            db.execSQL(
                "INSERT INTO transactions " +
                    "(id, accountId, categoryId, amountCents, date, time, merchant, note, source, transferId) " +
                    "VALUES (1, 1, 1, -1250, ${LocalDate.of(2026, 3, 10).toEpochDay()}, NULL, 'Mercadona', NULL, " +
                    "'manual', NULL), (2, 1, NULL, -3000, ${LocalDate.of(2026, 3, 11).toEpochDay()}, NULL, NULL, " +
                    "NULL, 'manual', 2), (3, 2, NULL, 3000, ${LocalDate.of(2026, 3, 11).toEpochDay()}, NULL, NULL, " +
                    "NULL, 'manual', 2)",
            )
        }

        val database = FinanceDatabase.create(context, name)
        try {
            val repository = RoomFinanceRepository(database)
            runBlocking {
                val mercadona = repository.merchants.first().single { it.name == "Mercadona" }.id
                assertEquals(
                    listOf(Category(id = 1, name = "Groceries", kind = CategoryKind.Spending)),
                    repository.categories.first(),
                )
                assertEquals(
                    listOf(
                        Account(id = 1, name = "Current", type = AccountType.Current, startingBalanceCents = 10_000, archived = true),
                        Account(id = 2, name = "Savings", type = AccountType.Savings, startingBalanceCents = 0),
                    ),
                    repository.accounts.first(),
                )
                val leg = Transaction(
                    id = 2,
                    accountId = 1,
                    categoryId = null,
                    amountCents = -3_000,
                    date = LocalDate.of(2026, 3, 11),
                    transferId = 2,
                )
                assertEquals(
                    listOf(
                        Transaction(
                            id = 1,
                            accountId = 1,
                            categoryId = 1,
                            amountCents = -1_250,
                            date = LocalDate.of(2026, 3, 10),
                            merchantId = mercadona,
                        ),
                        leg,
                        leg.copy(id = 3, accountId = 2, amountCents = 3_000),
                    ),
                    repository.transactions.first().sortedBy { it.id },
                )
                assertEquals(emptyList<Metric>(), repository.metrics.first())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `a version 3 database migrates to version 4 with every Metric kept as a simple one`() {
        val start = LocalDate.of(2026, 1, 10)
        val end = LocalDate.of(2026, 2, 20)
        createDatabase(version = 3).use { db ->
            db.execSQL(
                "INSERT INTO metrics (id, name, source, categoryIds, uncategorised, accountIds, measure, period, " +
                    "customStart, customEnd, divisor) VALUES " +
                    "(1, 'Groceries', 'spending', '2,5', 1, '1,3', 'average_per_week', 'custom', " +
                    "${start.toEpochDay()}, ${end.toEpochDay()}, 'with_transactions'), " +
                    "(2, 'Income', 'income', '', 0, '', 'count', 'this_year', NULL, NULL, 'every')",
            )
        }

        val database = FinanceDatabase.create(context, name)
        try {
            val repository = RoomFinanceRepository(database)
            runBlocking {
                assertEquals(
                    listOf(
                        Metric(
                            id = 1,
                            name = "Groceries",
                            source = MetricSource.Spending,
                            categoryIds = setOf(2, 5),
                            uncategorised = true,
                            accountIds = setOf(1, 3),
                            measure = Measure.AveragePerWeek,
                            period = MetricPeriod.Custom(start, end),
                            divisor = AverageDivisor.SubPeriodsWithTransactions,
                            combination = null,
                        ),
                        Metric(
                            id = 2,
                            name = "Income",
                            source = MetricSource.Income,
                            measure = Measure.Count,
                            period = MetricPeriod.ThisYear,
                            divisor = AverageDivisor.EverySubPeriod,
                            combination = null,
                        ),
                    ),
                    repository.metrics.first(),
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `a version 4 database migrates to version 5 with every Metric unpinned`() {
        createDatabase(version = 4).use { db ->
            db.execSQL(
                "INSERT INTO metrics (id, name, source, categoryIds, uncategorised, accountIds, measure, period, " +
                    "customStart, customEnd, divisor, combination, combinationA, combinationB) VALUES " +
                    "(1, 'Spending', 'spending', '', 0, '', 'sum', 'this_month', NULL, NULL, 'every', NULL, NULL, NULL), " +
                    "(2, 'Spending change', 'spending', '', 0, '', 'sum', 'this_year', NULL, NULL, 'every', " +
                    "'change', 1, NULL)",
            )
        }

        val database = FinanceDatabase.create(context, name)
        try {
            val repository = RoomFinanceRepository(database)
            runBlocking {
                assertEquals(
                    listOf(
                        Metric(id = 1, name = "Spending", kpi = null),
                        Metric(
                            id = 2,
                            name = "Spending change",
                            period = MetricPeriod.ThisYear,
                            combination = Combination(CombinationKind.Change, a = 1),
                            kpi = null,
                        ),
                    ),
                    repository.metrics.first(),
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `a version 5 database migrates to version 6 with every KPI 1x1 and no saved order`() {
        createDatabase(version = 5).use { db ->
            db.execSQL(
                "INSERT INTO metrics (id, name, source, categoryIds, uncategorised, accountIds, measure, period, " +
                    "customStart, customEnd, divisor, combination, combinationA, combinationB, kpiPinned, kpiTarget, " +
                    "kpiDirection) VALUES " +
                    "(1, 'Spending', 'spending', '', 0, '', 'sum', 'this_month', NULL, NULL, 'every', NULL, NULL, NULL, " +
                    "1, 25000, 'lower'), " +
                    "(2, 'Rent', 'spending', '', 0, '', 'sum', 'this_month', NULL, NULL, 'every', NULL, NULL, NULL, " +
                    "0, NULL, NULL), " +
                    "(3, 'Pay', 'income', '', 0, '', 'sum', 'this_month', NULL, NULL, 'every', NULL, NULL, NULL, " +
                    "1, NULL, 'higher')",
            )
        }

        val database = FinanceDatabase.create(context, name)
        try {
            val repository = RoomFinanceRepository(database)
            runBlocking {
                assertEquals(
                    listOf(
                        Metric(id = 1, name = "Spending", kpi = Kpi(target = 25_000, direction = KpiDirection.LowerIsBetter)),
                        Metric(id = 2, name = "Rent"),
                        Metric(
                            id = 3,
                            name = "Pay",
                            source = MetricSource.Income,
                            kpi = Kpi(direction = KpiDirection.HigherIsBetter),
                        ),
                    ),
                    repository.metrics.first(),
                )
                assertEquals(emptyList<Long>(), repository.kpiOrder.first())
                assertEquals(
                    "with no saved order the KPIs come oldest first",
                    listOf(1L, 3L),
                    repository.metrics.first().kpisInOrder(repository.kpiOrder.first()).map { it.id },
                )
            }
        } finally {
            database.close()
        }
    }

    private val march = LocalDate.of(2026, 3, 1)

    /** Writes a version 6 database with the starter Categories, one Account and these Transactions. */
    private fun givenVersion6(vararg transactions: String) {
        createDatabase(version = 6).use { db ->
            starterCategoryRows().forEach { row ->
                db.execSQL(
                    "INSERT INTO categories (id, parentId, name, kind, position) VALUES (?, ?, ?, ?, ?)",
                    arrayOf<Any?>(row.id, row.parentId, row.name, row.kind, row.position),
                )
            }
            db.execSQL(
                "INSERT INTO accounts (id, name, type, startingBalanceCents, currency, archived) " +
                    "VALUES (1, 'Current', 'current', 0, 'EUR', 0)",
            )
            transactions.forEach { values ->
                db.execSQL(
                    "INSERT INTO transactions " +
                        "(id, accountId, categoryId, amountCents, date, time, merchant, note, source, transferId) " +
                        "VALUES $values",
                )
            }
        }
    }

    /** A version 6 Transaction row of Account 1 on [day] of March 2026. */
    private fun v6Row(id: Long, categoryId: Long, cents: Long, day: Int, merchant: String?, note: String? = null) =
        "($id, 1, $categoryId, $cents, ${march.withDayOfMonth(day).toEpochDay()}, NULL, " +
            "${merchant?.let { "'$it'" } ?: "NULL"}, ${note?.let { "'$it'" } ?: "NULL"}, 'manual', NULL)"

    private fun <T> migrated(read: suspend (RoomFinanceRepository) -> T): T {
        val database = FinanceDatabase.create(context, name)
        try {
            return runBlocking { read(RoomFinanceRepository(database)) }
        } finally {
            database.close()
        }
    }

    private fun seedId(category: String): Long = starterCategoryRows().single { it.name == category }.id

    @Test
    fun `a version 6 database turns its free-text merchants into Merchants`() {
        val food = seedId("Food")
        val restaurants = seedId("Restaurants")
        val coffee = seedId("Coffee")
        givenVersion6(
            v6Row(1, food, -4_235, day = 1, merchant = "Mercadona", note = "Weekly shop"),
            v6Row(2, food, -1_000, day = 2, merchant = "mercadona "),
            v6Row(3, food, -500, day = 3, merchant = "MERCADONA"),
            v6Row(4, restaurants, -2_000, day = 4, merchant = "bar pepe"),
            v6Row(5, coffee, -150, day = 5, merchant = "BAR PEPE"),
            v6Row(6, restaurants, -2_500, day = 6, merchant = " Bar Pepe "),
            v6Row(7, food, -300, day = 7, merchant = null, note = "Market"),
            v6Row(8, food, -200, day = 7, merchant = "  "),
        )

        val (merchants, transactions) = migrated { it.merchants.first() to it.transactions.first().sortedBy { t -> t.id } }

        val mercadona = merchants.single { it.name == "Mercadona" }
        val barPepe = merchants.single { it.name == "Bar Pepe" }
        assertEquals(Merchant(barPepe.id, "Bar Pepe", emptyList(), restaurants), barPepe)
        assertEquals(
            "one owner's Merchant beside the preloaded ones",
            PreloadedMerchants.size + 1,
            merchants.size,
        )
        fun expense(id: Long, categoryId: Long, cents: Long, day: Int, merchantId: Long?, note: String? = null) =
            Transaction(
                id = id,
                accountId = 1,
                categoryId = categoryId,
                amountCents = cents,
                date = march.withDayOfMonth(day),
                merchantId = merchantId,
                note = note,
            )
        assertEquals(
            listOf(
                expense(1, food, -4_235, day = 1, merchantId = mercadona.id, note = "Weekly shop"),
                expense(2, food, -1_000, day = 2, merchantId = mercadona.id),
                expense(3, food, -500, day = 3, merchantId = mercadona.id),
                expense(4, restaurants, -2_000, day = 4, merchantId = barPepe.id),
                expense(5, coffee, -150, day = 5, merchantId = barPepe.id),
                expense(6, restaurants, -2_500, day = 6, merchantId = barPepe.id),
                expense(7, food, -300, day = 7, merchantId = null, note = "Market"),
                expense(8, food, -200, day = 7, merchantId = null),
            ),
            transactions,
        )
    }

    @Test
    fun `a migrated Merchant takes the spelling and the Category of its most recent Transaction on a tie`() {
        val food = seedId("Food")
        val coffee = seedId("Coffee")
        givenVersion6(
            // Created last but dated first, so not the most recent.
            v6Row(2, food, -100, day = 1, merchant = "KIOSKO luis"),
            v6Row(1, coffee, -100, day = 3, merchant = "Kiosko Luis"),
        )

        val merchants = migrated { it.merchants.first() }

        val kiosko = merchants.single { it.name.equals("kiosko luis", ignoreCase = true) }
        assertEquals("Kiosko Luis", kiosko.name)
        assertEquals(coffee, kiosko.defaultCategoryId)
    }

    @Test
    fun `transaction ids keep growing after the version 7 migration`() {
        val food = seedId("Food")
        givenVersion6(v6Row(1, food, -100, day = 1, merchant = null), v6Row(2, food, -100, day = 1, merchant = null))
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
            .use { db -> db.execSQL("DELETE FROM transactions WHERE id = 2") }

        val id = migrated { repository ->
            repository.addTransaction(
                Transaction(accountId = 1, categoryId = null, amountCents = -100, date = march),
            )
        }

        assertEquals(3L, id)
    }

    @Test
    fun `a version 7 database keeps every Category and its links, and new Category ids never reuse a deleted one`() {
        val food = seedId("Food")
        val household = seedId("Household")
        val highest = starterCategoryRows().maxOf { it.id }
        createDatabase(version = 7).use { db ->
            starterCategoryRows().forEach { row ->
                db.execSQL(
                    "INSERT INTO categories (id, parentId, name, kind, position) VALUES (?, ?, ?, ?, ?)",
                    arrayOf<Any?>(row.id, row.parentId, row.name, row.kind, row.position),
                )
            }
            db.execSQL(
                "INSERT INTO accounts (id, name, type, startingBalanceCents, currency, archived) " +
                    "VALUES (1, 'Current', 'current', 0, 'EUR', 0)",
            )
            db.execSQL("INSERT INTO merchants (id, name, keywords, defaultCategoryId) VALUES (1, 'Bar Pepe', '', $household)")
            db.execSQL(
                "INSERT INTO transactions (id, accountId, categoryId, amountCents, date, time, merchantId, note, source, transferId) " +
                    "VALUES (1, 1, $food, -100, ${march.toEpochDay()}, NULL, 1, NULL, 'manual', NULL)",
            )
        }

        migrated { repository ->
            assertEquals(SeededStarterCategories, repository.categories.first())
            assertEquals(food, repository.transactions.first().single().categoryId)
            assertEquals(household, repository.merchants.first().single { it.id == 1L }.defaultCategoryId)

            val pets = repository.saveCategory(Category(0, "Pets", CategoryKind.Spending))
            assertEquals(highest + 1, pets)
            assertEquals(true, repository.deleteCategory(pets, moveTo = null))
            assertEquals(highest + 2, repository.saveCategory(Category(0, "Vet", CategoryKind.Spending)))

            assertEquals(true, repository.deleteCategory(household, moveTo = null))
            assertEquals(
                "deleting a Category still clears Merchant defaults",
                null,
                repository.merchants.first().single { it.id == 1L }.defaultCategoryId,
            )
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT sql FROM sqlite_master WHERE name = 'categories'", null).use { cursor ->
                cursor.moveToFirst()
                assertTrue("categories.id is AUTOINCREMENT", "AUTOINCREMENT" in cursor.getString(0))
            }
        }
    }

    @Test
    fun `a version 8 Metric migrates to version 9 with no Merchant filter and its other fields unchanged`() {
        val start = LocalDate.of(2026, 1, 10)
        val end = LocalDate.of(2026, 2, 20)
        createDatabase(version = 8).use { db ->
            db.execSQL(
                "INSERT INTO metrics (id, name, source, categoryIds, uncategorised, accountIds, measure, period, " +
                    "customStart, customEnd, divisor, combination, combinationA, combinationB, kpiPinned, kpiTarget, " +
                    "kpiDirection, kpiWide) VALUES " +
                    "(1, 'Groceries', 'spending', '2,5', 1, '1,3', 'average_per_week', 'custom', " +
                    "${start.toEpochDay()}, ${end.toEpochDay()}, 'with_transactions', NULL, NULL, NULL, " +
                    "1, 25000, 'lower', 1)",
            )
        }

        val metrics = migrated { it.metrics.first() }

        assertEquals(
            listOf(
                Metric(
                    id = 1,
                    name = "Groceries",
                    source = MetricSource.Spending,
                    categoryIds = setOf(2, 5),
                    uncategorised = true,
                    accountIds = setOf(1, 3),
                    merchantIds = emptySet(),
                    measure = Measure.AveragePerWeek,
                    period = MetricPeriod.Custom(start, end),
                    divisor = AverageDivisor.SubPeriodsWithTransactions,
                    kpi = Kpi(target = 25_000, direction = KpiDirection.LowerIsBetter, wide = true),
                ),
            ),
            metrics,
        )
    }
}
