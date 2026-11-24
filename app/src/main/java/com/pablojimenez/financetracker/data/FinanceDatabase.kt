package com.pablojimenez.financetracker.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pablojimenez.financetracker.domain.SeededStarterCategories

/**
 * The app's Room database: a plain SQLite file in app-private storage, with no encryption of
 * its own and no backup (docs/adr/0001-no-database-encryption-no-backup.md).
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        MetricEntity::class,
        KpiOrderEntity::class,
        MerchantEntity::class,
        PreloadedMerchantSeedEntity::class,
    ],
    version = 9,
    autoMigrations = [
        // Adds accounts.archived (false) and transactions.transferId (null).
        AutoMigration(from = 1, to = 2),
        // Adds the empty metrics table.
        AutoMigration(from = 2, to = 3),
        // Adds metrics.combination, combinationA and combinationB (null: every Metric stays simple).
        AutoMigration(from = 3, to = 4),
        // Adds metrics.kpiPinned (false), kpiTarget and kpiDirection (null): every Metric stays unpinned.
        AutoMigration(from = 4, to = 5),
        // Adds metrics.kpiWide (false: every KPI is 1×1) and the empty kpi_order table (KPIs oldest first).
        AutoMigration(from = 5, to = 6),
        // 6 to 7 is MIGRATION_6_7: it adds the Merchants and turns free-text merchants into them.
        // 7 to 8 is MIGRATION_7_8: categories.id becomes AUTOINCREMENT, keeping every row.
        // Adds metrics.merchantIds (empty: every Metric keeps every Merchant).
        AutoMigration(from = 8, to = 9),
    ],
)
abstract class FinanceDatabase : RoomDatabase() {
    internal abstract fun dao(): FinanceDao

    companion object {
        const val FILE_NAME = "finance.db"

        /**
         * Opens the database file [name], or an in-memory database when [name] is null. A new
         * database is seeded with the starter Categories as it is created, and every open adds
         * the [com.pablojimenez.financetracker.domain.PreloadedMerchants] it was never seeded with.
         */
        fun create(context: Context, name: String? = FILE_NAME): FinanceDatabase {
            val builder = if (name == null) {
                Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java)
            } else {
                Room.databaseBuilder(context, FinanceDatabase::class.java, name)
            }
            return builder
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                .addCallback(SeedStarterCategories)
                .addCallback(SeedPreloadedMerchants)
                .build()
        }
    }
}

/** Inserts [SeededStarterCategories] once, when the database file is first created. */
private object SeedStarterCategories : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        starterCategoryRows().forEach { row ->
            db.execSQL(
                "INSERT INTO categories (id, parentId, name, kind, position) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>(row.id, row.parentId, row.name, row.kind, row.position),
            )
        }
    }
}

/** [SeededStarterCategories] as rows, each at its place in the picker's order. */
internal fun starterCategoryRows(): List<CategoryEntity> =
    SeededStarterCategories.mapIndexed { position, category ->
        CategoryEntity(category.id, category.parentId, category.name, category.kind.code, position)
    }
