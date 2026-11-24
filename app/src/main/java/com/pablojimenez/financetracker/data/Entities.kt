package com.pablojimenez.financetracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Enum columns hold these stable codes, so renaming a Kotlin constant never breaks old rows. */
internal object Codes {
    const val CURRENT = "current"
    const val SAVINGS = "savings"
    const val CREDIT_CARD = "credit_card"
    const val CASH = "cash"
    const val SPENDING = "spending"
    const val INCOME = "income"
    const val MANUAL = "manual"
    const val SUM = "sum"
    const val COUNT = "count"
    const val AVERAGE_PER_TRANSACTION = "average_per_transaction"
    const val AVERAGE_PER_DAY = "average_per_day"
    const val AVERAGE_PER_WEEK = "average_per_week"
    const val AVERAGE_PER_MONTH = "average_per_month"
    const val THIS_WEEK = "this_week"
    const val THIS_MONTH = "this_month"
    const val THIS_YEAR = "this_year"
    const val CUSTOM = "custom"
    const val EVERY_SUB_PERIOD = "every"
    const val SUB_PERIODS_WITH_TRANSACTIONS = "with_transactions"
    const val RATIO = "ratio"
    const val DIFFERENCE = "difference"
    const val CHANGE = "change"
    const val LOWER_IS_BETTER = "lower"
    const val HIGHER_IS_BETTER = "higher"
}

@Entity(tableName = "accounts")
internal data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val type: String,
    val startingBalanceCents: Long,
    /** ISO 4217 code, e.g. "EUR". */
    val currency: String,
    @ColumnInfo(defaultValue = "0") val archived: Boolean,
)

@Entity(
    tableName = "categories",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["parentId"])],
    indices = [Index("parentId")],
)
internal data class CategoryEntity(
    /** AUTOINCREMENT, so a deleted Category's id is never given again. */
    @PrimaryKey(autoGenerate = true) val id: Long,
    val parentId: Long?,
    val name: String,
    val kind: String,
    /** Display order across the whole tree: each parent, then its children. */
    val position: Int,
)

/**
 * A Merchant. [keywords] are separated by [KEYWORD_SEPARATOR]. [defaultCategoryId] is null when it
 * has no default Category, and becomes null when that Category is deleted.
 */
@Entity(
    tableName = "merchants",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["defaultCategoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("defaultCategoryId")],
)
internal data class MerchantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val keywords: String,
    val defaultCategoryId: Long?,
) {
    companion object {
        const val KEYWORD_SEPARATOR = "\n"
    }
}

/**
 * The key of a [com.pablojimenez.financetracker.domain.PreloadedMerchant] this database was
 * seeded with once. A key here is never seeded again, so a preloaded Merchant the owner renamed
 * or deleted stays that way across app updates.
 */
@Entity(tableName = "preloaded_merchant_seeds")
internal data class PreloadedMerchantSeedEntity(@PrimaryKey val key: String)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"]),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"]),
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchantId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("date"), Index("transferId"), Index("merchantId")],
)
internal data class TransactionEntity(
    /** AUTOINCREMENT, so ids only grow and order Transactions by creation. */
    @PrimaryKey(autoGenerate = true) val id: Long,
    val accountId: Long,
    /** Null when the Transaction has no Category (Uncategorised, or a Transfer leg). */
    val categoryId: Long?,
    val amountCents: Long,
    /** [java.time.LocalDate.toEpochDay]. */
    val date: Long,
    /** [java.time.LocalTime.toNanoOfDay], or null when the Transaction has no time. */
    val time: Long?,
    /** Null when the Transaction has no Merchant, and once its Merchant is deleted. */
    val merchantId: Long?,
    val note: String?,
    val source: String,
    /** Shared by the two legs of a Transfer; null for every other Transaction. */
    val transferId: Long?,
)

/**
 * A Metric's definition; its value is computed from Transactions, never stored. The id lists hold
 * no foreign keys: an id that no longer exists simply matches nothing. A combined Metric has a
 * [combination] and its sides; the simple pieces of its row are then ignored. A pinned Metric is
 * a KPI: deleting the row unpins it.
 */
@Entity(tableName = "metrics")
internal data class MetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    /** [Codes.SPENDING] or [Codes.INCOME]. */
    val source: String,
    /** Category ids separated by commas; empty, with [uncategorised] false, means every Category. */
    val categoryIds: String,
    val uncategorised: Boolean,
    /** Account ids separated by commas; empty means every Account. */
    val accountIds: String,
    val measure: String,
    val period: String,
    /** [java.time.LocalDate.toEpochDay] of a custom period's first day; null for other periods. */
    val customStart: Long?,
    /** [java.time.LocalDate.toEpochDay] of a custom period's last day; null for other periods. */
    val customEnd: Long?,
    /** What an average per day, week or month divides by; every sub-period unless chosen. */
    @ColumnInfo(defaultValue = Codes.EVERY_SUB_PERIOD) val divisor: String,
    /** [Codes.RATIO], [Codes.DIFFERENCE] or [Codes.CHANGE]; null for a simple Metric. */
    val combination: String? = null,
    /** The id of a combined Metric's side A; null for a simple Metric. */
    val combinationA: Long? = null,
    /** The id of a ratio's or a difference's side B; null otherwise. */
    val combinationB: Long? = null,
    /** Whether the Metric is pinned as a KPI; a row written before KPIs existed is not. */
    @ColumnInfo(defaultValue = "0") val kpiPinned: Boolean = false,
    /** A KPI's target in the Metric's result scale; null without one or when not pinned. */
    val kpiTarget: Long? = null,
    /** [Codes.LOWER_IS_BETTER] or [Codes.HIGHER_IS_BETTER]; null without one or when not pinned. */
    val kpiDirection: String? = null,
    /** Whether a KPI's Home tile is 2×1 rather than 1×1; false when not pinned or written before tile sizes. */
    @ColumnInfo(defaultValue = "0") val kpiWide: Boolean = false,
    /**
     * Merchant ids separated by commas; empty means every Merchant. No foreign key: a deleted
     * Merchant's id stays and matches nothing (ADR 0002, ADR 0003).
     */
    @ColumnInfo(defaultValue = "") val merchantIds: String = "",
)

/**
 * One place in the owner's order of the KPI tiles on Home: the Metric [metricId] at [position],
 * counting from 0. The rows are replaced as a whole on every reorder. There is no foreign key: a
 * row whose Metric is unpinned or deleted is simply ignored, and a Metric with no row goes last.
 */
@Entity(tableName = "kpi_order")
internal data class KpiOrderEntity(
    @PrimaryKey val metricId: Long,
    val position: Int,
)
