package com.pablojimenez.financetracker.data

import androidx.room.withTransaction
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.AverageDivisor
import com.pablojimenez.financetracker.domain.CategoryKind
import com.pablojimenez.financetracker.domain.Combination
import com.pablojimenez.financetracker.domain.CombinationKind
import com.pablojimenez.financetracker.domain.FinanceRepository
import com.pablojimenez.financetracker.domain.Kpi
import com.pablojimenez.financetracker.domain.KpiDirection
import com.pablojimenez.financetracker.domain.Measure
import com.pablojimenez.financetracker.domain.Merchant
import com.pablojimenez.financetracker.domain.Metric
import com.pablojimenez.financetracker.domain.MetricPeriod
import com.pablojimenez.financetracker.domain.MetricSource
import com.pablojimenez.financetracker.domain.Transaction
import com.pablojimenez.financetracker.domain.TransactionSource
import com.pablojimenez.financetracker.domain.Transfer
import com.pablojimenez.financetracker.domain.canDeleteCategory
import com.pablojimenez.financetracker.domain.merchantNameError
import com.pablojimenez.financetracker.domain.sameMerchantName
import com.pablojimenez.financetracker.domain.usersOf
import com.pablojimenez.financetracker.domain.withAdded
import com.pablojimenez.financetracker.domain.withCategoryMoved
import com.pablojimenez.financetracker.domain.withMoved
import java.time.LocalDate
import java.time.LocalTime
import java.util.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [FinanceRepository] kept in a Room [FinanceDatabase]. */
class RoomFinanceRepository(private val database: FinanceDatabase) : FinanceRepository {
    private val dao = database.dao()

    override val accounts: Flow<List<Account>> =
        dao.accounts().map { rows -> rows.map { it.toAccount() } }

    override val categories: Flow<List<Category>> =
        dao.categories().map { rows -> rows.map { it.toCategory() } }

    override val merchants: Flow<List<Merchant>> =
        dao.merchants().map { rows -> rows.map { it.toMerchant() } }

    override suspend fun saveCategory(category: Category): Long {
        val name = category.name.trim()
        require(name.isNotEmpty()) { "A Category needs a name" }
        if (category.id != 0L) {
            require(dao.renameCategory(category.id, name) == 1) { "No Category has id ${category.id}" }
            return category.id
        }
        return database.withTransaction {
            val all = dao.categoryList().map { it.toCategory() }
            val parent = category.parentId?.let { parentId -> all.single { it.id == parentId } }
            require(parent?.parentId == null) { "A Category's parent must be a top-level Category" }
            val kind = parent?.kind ?: category.kind
            val id = dao.insertCategory(CategoryEntity(0, parent?.id, name, kind.code, position = all.size))
            savePositions(all.withAdded(Category(id, name, kind, parent?.id)))
            id
        }
    }

    /** Gives each Category its place in [ordered], writing only the rows whose place changed. */
    private suspend fun savePositions(ordered: List<Category>) {
        val saved = dao.categoryList().associate { it.id to it.position }
        ordered.forEachIndexed { position, category ->
            if (saved[category.id] != position) dao.setCategoryPosition(category.id, position)
        }
    }

    override suspend fun moveCategory(id: Long, up: Boolean) {
        database.withTransaction {
            savePositions(dao.categoryList().map { it.toCategory() }.withMoved(id, up))
        }
    }

    override suspend fun deleteCategory(id: Long, moveTo: Long?): Boolean = database.withTransaction {
        val all = dao.categoryList().map { it.toCategory() }
        if (!all.canDeleteCategory(id, moveTo, dao.categoryHasTransactions(id))) return@withTransaction false
        if (moveTo != null) {
            dao.moveCategoryTransactions(from = id, to = moveTo)
            dao.metricList().map { it.toMetric() }.forEach { metric ->
                val moved = metric.withCategoryMoved(from = id, to = moveTo)
                if (moved != metric) dao.updateMetric(moved.toEntity())
            }
        }
        // Merchants whose default this was keep none: the foreign key sets it to null (ADR 0003).
        dao.deleteCategory(id)
        true
    }

    override suspend fun addMerchant(merchant: Merchant): Long {
        val name = merchant.name.trim()
        require(name.isNotEmpty()) { "A Merchant needs a name" }
        return database.withTransaction {
            dao.merchantList().firstOrNull { sameMerchantName(it.name, name) }?.id
                ?: dao.insertMerchant(merchant.toEntity().copy(id = 0, name = name))
        }
    }

    override suspend fun updateMerchant(merchant: Merchant): Boolean = database.withTransaction {
        val all = dao.merchantList().map { it.toMerchant() }
        if (all.merchantNameError(merchant.name, renaming = merchant.id) != null) return@withTransaction false
        dao.updateMerchant(merchant.copy(name = merchant.name.trim()).toEntity()) == 1
    }

    override suspend fun deleteMerchant(id: Long) {
        // Its Transactions keep no Merchant: the foreign key sets it to null (ADR 0003).
        dao.deleteMerchant(id)
    }

    override val transactions: Flow<List<Transaction>> =
        dao.transactions().map { rows -> rows.map { it.toTransaction() } }

    override val metrics: Flow<List<Metric>> =
        dao.metrics().map { rows -> rows.map { it.toMetric() } }

    override val kpiOrder: Flow<List<Long>> = dao.kpiOrder()

    override suspend fun saveKpiOrder(ids: List<Long>) {
        dao.replaceKpiOrder(ids.distinct().mapIndexed { position, id -> KpiOrderEntity(id, position) })
    }

    override suspend fun saveMetric(metric: Metric): Long =
        if (metric.id == 0L) {
            dao.insertMetric(metric.toEntity())
        } else {
            dao.updateMetric(metric.toEntity())
            metric.id
        }

    override suspend fun deleteMetric(id: Long): List<Metric> = database.withTransaction {
        val users = dao.metricList().map { it.toMetric() }.usersOf(id)
        if (users.isEmpty()) dao.deleteMetric(id)
        users
    }

    override suspend fun addAccount(account: Account): Long =
        dao.insertAccount(account.toEntity().copy(id = 0))

    override suspend fun addTransaction(transaction: Transaction): Long =
        dao.insertTransaction(transaction.toEntity().copy(id = 0))

    override suspend fun updateTransaction(transaction: Transaction) {
        dao.updateTransaction(transaction.toEntity())
    }

    /** A new Transfer's id is the id of its out leg. */
    override suspend fun saveTransfer(transfer: Transfer): Long = database.withTransaction {
        val (out, into) = transfer.legs()
        if (transfer.id == 0L) {
            val outId = dao.insertTransaction(out.toEntity().copy(transferId = null))
            dao.updateTransaction(out.copy(id = outId, transferId = outId).toEntity())
            dao.insertTransaction(into.copy(transferId = outId).toEntity())
            outId
        } else {
            val legs = dao.transferLegs(transfer.id)
            legs.firstOrNull { it.amountCents < 0 }?.let { dao.updateTransaction(out.copy(id = it.id).toEntity()) }
            legs.firstOrNull { it.amountCents > 0 }?.let { dao.updateTransaction(into.copy(id = it.id).toEntity()) }
            transfer.id
        }
    }

    override suspend fun deleteTransaction(id: Long) {
        dao.deleteTransaction(id)
    }

    override suspend fun archiveAccount(id: Long) {
        dao.archiveAccount(id)
    }

    override suspend fun deleteAccount(id: Long): Boolean = dao.deleteAccountWithoutTransactions(id) > 0
}

private fun AccountEntity.toAccount() = Account(
    id = id,
    name = name,
    type = accountType(type),
    startingBalanceCents = startingBalanceCents,
    currency = Currency.getInstance(currency),
    archived = archived,
)

private fun Account.toEntity() = AccountEntity(
    id = id,
    name = name,
    type = type.code,
    startingBalanceCents = startingBalanceCents,
    currency = currency.currencyCode,
    archived = archived,
)

private fun CategoryEntity.toCategory() = Category(
    id = id,
    name = name,
    kind = categoryKind(kind),
    parentId = parentId,
)

private fun MerchantEntity.toMerchant() = Merchant(
    id = id,
    name = name,
    keywords = if (keywords.isEmpty()) emptyList() else keywords.split(MerchantEntity.KEYWORD_SEPARATOR),
    defaultCategoryId = defaultCategoryId,
)

private fun Merchant.toEntity() = MerchantEntity(
    id = id,
    name = name,
    keywords = keywords.joinToString(MerchantEntity.KEYWORD_SEPARATOR),
    defaultCategoryId = defaultCategoryId,
)

private fun TransactionEntity.toTransaction() = Transaction(
    id = id,
    accountId = accountId,
    categoryId = categoryId,
    amountCents = amountCents,
    date = LocalDate.ofEpochDay(date),
    time = time?.let(LocalTime::ofNanoOfDay),
    merchantId = merchantId,
    note = note,
    source = transactionSource(source),
    transferId = transferId,
)

private fun Transaction.toEntity() = TransactionEntity(
    id = id,
    accountId = accountId,
    categoryId = categoryId,
    amountCents = amountCents,
    date = date.toEpochDay(),
    time = time?.toNanoOfDay(),
    merchantId = merchantId,
    note = note,
    source = source.code,
    transferId = transferId,
)

private fun MetricEntity.toMetric() = Metric(
    id = id,
    name = name,
    source = metricSource(source),
    categoryIds = idSet(categoryIds),
    uncategorised = uncategorised,
    accountIds = idSet(accountIds),
    merchantIds = idSet(merchantIds),
    measure = measure(measure),
    period = when (period) {
        Codes.THIS_WEEK -> MetricPeriod.ThisWeek
        Codes.THIS_MONTH -> MetricPeriod.ThisMonth
        Codes.THIS_YEAR -> MetricPeriod.ThisYear
        Codes.CUSTOM -> MetricPeriod.Custom(
            LocalDate.ofEpochDay(checkNotNull(customStart) { "A custom period without a start" }),
            LocalDate.ofEpochDay(checkNotNull(customEnd) { "A custom period without an end" }),
        )
        else -> error("Unknown period '$period'")
    },
    divisor = averageDivisor(divisor),
    combination = combination?.let { code ->
        Combination(
            kind = combinationKind(code),
            a = checkNotNull(combinationA) { "A combined Metric without side A" },
            b = combinationB,
        )
    },
    kpi = if (kpiPinned) Kpi(kpiTarget, kpiDirection?.let(::kpiDirection), kpiWide) else null,
)

private fun Metric.toEntity(): MetricEntity {
    val custom = period as? MetricPeriod.Custom
    return MetricEntity(
        id = id,
        name = name,
        source = source.code,
        categoryIds = categoryIds.sorted().joinToString(","),
        uncategorised = uncategorised,
        accountIds = accountIds.sorted().joinToString(","),
        measure = measure.code,
        period = when (period) {
            MetricPeriod.ThisWeek -> Codes.THIS_WEEK
            MetricPeriod.ThisMonth -> Codes.THIS_MONTH
            MetricPeriod.ThisYear -> Codes.THIS_YEAR
            is MetricPeriod.Custom -> Codes.CUSTOM
        },
        customStart = custom?.start?.toEpochDay(),
        customEnd = custom?.end?.toEpochDay(),
        divisor = divisor.code,
        combination = combination?.kind?.code,
        combinationA = combination?.a,
        combinationB = combination?.b,
        kpiPinned = kpi != null,
        kpiTarget = kpi?.target,
        kpiDirection = kpi?.direction?.code,
        kpiWide = kpi?.wide == true,
        merchantIds = merchantIds.sorted().joinToString(","),
    )
}

private val KpiDirection.code: String
    get() = when (this) {
        KpiDirection.LowerIsBetter -> Codes.LOWER_IS_BETTER
        KpiDirection.HigherIsBetter -> Codes.HIGHER_IS_BETTER
    }

private fun kpiDirection(code: String): KpiDirection =
    KpiDirection.entries.firstOrNull { it.code == code } ?: error("Unknown KPI direction '$code'")

private fun idSet(text: String): Set<Long> =
    if (text.isEmpty()) emptySet() else text.split(",").map { it.toLong() }.toSet()

private val MetricSource.code: String
    get() = when (this) {
        MetricSource.Spending -> Codes.SPENDING
        MetricSource.Income -> Codes.INCOME
    }

private fun metricSource(code: String): MetricSource =
    MetricSource.entries.firstOrNull { it.code == code } ?: error("Unknown metric source '$code'")

private val Measure.code: String
    get() = when (this) {
        Measure.Sum -> Codes.SUM
        Measure.Count -> Codes.COUNT
        Measure.AveragePerTransaction -> Codes.AVERAGE_PER_TRANSACTION
        Measure.AveragePerDay -> Codes.AVERAGE_PER_DAY
        Measure.AveragePerWeek -> Codes.AVERAGE_PER_WEEK
        Measure.AveragePerMonth -> Codes.AVERAGE_PER_MONTH
    }

private fun measure(code: String): Measure =
    Measure.entries.firstOrNull { it.code == code } ?: error("Unknown measure '$code'")

private val AverageDivisor.code: String
    get() = when (this) {
        AverageDivisor.EverySubPeriod -> Codes.EVERY_SUB_PERIOD
        AverageDivisor.SubPeriodsWithTransactions -> Codes.SUB_PERIODS_WITH_TRANSACTIONS
    }

private fun averageDivisor(code: String): AverageDivisor =
    AverageDivisor.entries.firstOrNull { it.code == code } ?: error("Unknown average divisor '$code'")

private val CombinationKind.code: String
    get() = when (this) {
        CombinationKind.Ratio -> Codes.RATIO
        CombinationKind.Difference -> Codes.DIFFERENCE
        CombinationKind.Change -> Codes.CHANGE
    }

private fun combinationKind(code: String): CombinationKind =
    CombinationKind.entries.firstOrNull { it.code == code } ?: error("Unknown combination '$code'")

private val AccountType.code: String
    get() = when (this) {
        AccountType.Current -> Codes.CURRENT
        AccountType.Savings -> Codes.SAVINGS
        AccountType.CreditCard -> Codes.CREDIT_CARD
        AccountType.Cash -> Codes.CASH
    }

private fun accountType(code: String): AccountType =
    AccountType.entries.firstOrNull { it.code == code } ?: error("Unknown account type '$code'")

internal val CategoryKind.code: String
    get() = when (this) {
        CategoryKind.Spending -> Codes.SPENDING
        CategoryKind.Income -> Codes.INCOME
    }

private fun categoryKind(code: String): CategoryKind =
    CategoryKind.entries.firstOrNull { it.code == code } ?: error("Unknown category kind '$code'")

private val TransactionSource.code: String
    get() = when (this) {
        TransactionSource.Manual -> Codes.MANUAL
    }

private fun transactionSource(code: String): TransactionSource =
    TransactionSource.entries.firstOrNull { it.code == code } ?: error("Unknown source '$code'")
