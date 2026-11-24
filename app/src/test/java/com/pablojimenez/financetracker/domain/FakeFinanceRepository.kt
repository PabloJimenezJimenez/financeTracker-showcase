package com.pablojimenez.financetracker.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking

/**
 * An in-memory [FinanceRepository] holding [startingCategories] (the starter set by default, in
 * tree order) and, like a new database, the [PreloadedMerchants] (ids from 1, with no default
 * Category when theirs is not among [startingCategories]), for tests. Its Categories are edited
 * like Room's, through the same domain rules.
 */
class FakeFinanceRepository(startingCategories: List<Category> = SeededStarterCategories) : FinanceRepository {
    private val accountList = MutableStateFlow<List<Account>>(emptyList())
    private val transactionList = MutableStateFlow<List<Transaction>>(emptyList())
    private val metricList = MutableStateFlow<List<Metric>>(emptyList())
    private val kpiOrderList = MutableStateFlow<List<Long>>(emptyList())
    private var nextId = 1L
    private val merchantList = MutableStateFlow(
        PreloadedMerchants.mapIndexed { index, preloaded ->
            Merchant(
                id = index + 1L,
                name = preloaded.name,
                keywords = preloaded.keywords,
                defaultCategoryId = preloaded.defaultCategorySeedId.takeIf { id -> startingCategories.any { it.id == id } },
            )
        },
    )
    private var nextMerchantId = merchantList.value.size + 1L

    override val accounts: Flow<List<Account>> = accountList
    private val categoryList = MutableStateFlow(startingCategories)
    private var nextCategoryId = (startingCategories.maxOfOrNull { it.id } ?: 0) + 1
    override val categories: Flow<List<Category>> = categoryList
    override val merchants: Flow<List<Merchant>> = merchantList
    override val transactions: Flow<List<Transaction>> = transactionList
    override val metrics: Flow<List<Metric>> = metricList
    override val kpiOrder: Flow<List<Long>> = kpiOrderList

    /** What the repository holds now, without collecting a flow. */
    val savedAccounts: List<Account> get() = accountList.value
    val savedTransactions: List<Transaction> get() = transactionList.value
    val savedMerchants: List<Merchant> get() = merchantList.value
    val savedCategories: List<Category> get() = categoryList.value
    val savedMetrics: List<Metric> get() = metricList.value
    val savedKpiOrder: List<Long> get() = kpiOrderList.value

    /** How many times [saveKpiOrder] has been called. */
    var kpiOrderSaves = 0
        private set

    override suspend fun saveCategory(category: Category): Long {
        val name = category.name.trim()
        require(name.isNotEmpty()) { "A Category needs a name" }
        if (category.id != 0L) {
            require(categoryList.value.any { it.id == category.id }) { "No Category has id ${category.id}" }
            categoryList.update { list -> list.map { if (it.id == category.id) it.copy(name = name) else it } }
            return category.id
        }
        val parent = category.parentId?.let { parentId -> categoryList.value.single { it.id == parentId } }
        require(parent?.parentId == null) { "A Category's parent must be a top-level Category" }
        val id = nextCategoryId++
        categoryList.update { it.withAdded(Category(id, name, parent?.kind ?: category.kind, parent?.id)) }
        return id
    }

    override suspend fun moveCategory(id: Long, up: Boolean) {
        categoryList.update { it.withMoved(id, up) }
    }

    override suspend fun deleteCategory(id: Long, moveTo: Long?): Boolean {
        val hasTransactions = transactionList.value.any { it.categoryId == id }
        if (!categoryList.value.canDeleteCategory(id, moveTo, hasTransactions)) return false
        if (moveTo != null) {
            transactionList.update { list -> list.map { if (it.categoryId == id) it.copy(categoryId = moveTo) else it } }
            metricList.update { list -> list.map { it.withCategoryMoved(from = id, to = moveTo) } }
        }
        merchantList.update { list ->
            list.map { if (it.defaultCategoryId == id) it.copy(defaultCategoryId = null) else it }
        }
        categoryList.update { list -> list.filterNot { it.id == id } }
        return true
    }

    override suspend fun addAccount(account: Account): Long {
        val id = nextId++
        accountList.update { it + account.copy(id = id) }
        return id
    }

    override suspend fun addMerchant(merchant: Merchant): Long {
        val name = merchant.name.trim()
        require(name.isNotEmpty()) { "A Merchant needs a name" }
        merchantList.value.firstOrNull { sameMerchantName(it.name, name) }?.let { return it.id }
        val id = nextMerchantId++
        merchantList.update { it + merchant.copy(id = id, name = name) }
        return id
    }

    override suspend fun updateMerchant(merchant: Merchant): Boolean {
        val all = merchantList.value
        if (all.none { it.id == merchant.id }) return false
        if (all.merchantNameError(merchant.name, renaming = merchant.id) != null) return false
        val saved = merchant.copy(name = merchant.name.trim())
        merchantList.update { list -> list.map { if (it.id == saved.id) saved else it } }
        return true
    }

    /** Like Room's foreign key, its Transactions keep no Merchant. */
    override suspend fun deleteMerchant(id: Long) {
        transactionList.update { list -> list.map { if (it.merchantId == id) it.copy(merchantId = null) else it } }
        merchantList.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun addTransaction(transaction: Transaction): Long {
        val id = nextId++
        transactionList.update { it + transaction.copy(id = id) }
        return id
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        transactionList.update { list -> list.map { if (it.id == transaction.id) transaction else it } }
    }

    /** Like Room: a new Transfer's id is the id of its out leg. */
    override suspend fun saveTransfer(transfer: Transfer): Long {
        val (out, into) = transfer.legs()
        if (transfer.id == 0L) {
            val outId = nextId++
            val inId = nextId++
            transactionList.update {
                it + out.copy(id = outId, transferId = outId) + into.copy(id = inId, transferId = outId)
            }
            return outId
        }
        transactionList.update { list ->
            list.map {
                when {
                    it.transferId != transfer.id -> it
                    it.amountCents < 0 -> out.copy(id = it.id)
                    else -> into.copy(id = it.id)
                }
            }
        }
        return transfer.id
    }

    override suspend fun deleteTransaction(id: Long) {
        transactionList.update { list ->
            val transferId = list.firstOrNull { it.id == id }?.transferId
            list.filterNot { it.id == id || (transferId != null && it.transferId == transferId) }
        }
    }

    override suspend fun archiveAccount(id: Long) {
        accountList.update { list -> list.map { if (it.id == id) it.copy(archived = true) else it } }
    }

    override suspend fun deleteAccount(id: Long): Boolean {
        if (transactionList.value.any { it.accountId == id }) return false
        val before = accountList.value
        accountList.update { list -> list.filterNot { it.id == id } }
        return accountList.value.size < before.size
    }

    override suspend fun saveMetric(metric: Metric): Long {
        if (metric.id == 0L) {
            val id = nextId++
            metricList.update { it + metric.copy(id = id) }
            return id
        }
        metricList.update { list -> list.map { if (it.id == metric.id) metric else it } }
        return metric.id
    }

    override suspend fun saveKpiOrder(ids: List<Long>) {
        kpiOrderSaves++
        kpiOrderList.value = ids
    }

    override suspend fun deleteMetric(id: Long): List<Metric> {
        val users = metricList.value.usersOf(id)
        if (users.isEmpty()) metricList.update { list -> list.filterNot { it.id == id } }
        return users
    }

    fun givenMetric(metric: Metric): Long = runBlocking { saveMetric(metric) }

    fun givenKpiOrder(vararg ids: Long) = runBlocking { saveKpiOrder(ids.toList()) }

    fun givenAccount(account: Account): Long = runBlocking { addAccount(account) }

    fun givenTransaction(transaction: Transaction): Long = runBlocking { addTransaction(transaction) }

    fun givenMerchant(merchant: Merchant): Long = runBlocking { addMerchant(merchant) }
}

