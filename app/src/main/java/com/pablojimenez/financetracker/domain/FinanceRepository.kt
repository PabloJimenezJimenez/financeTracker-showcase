package com.pablojimenez.financetracker.domain

import kotlinx.coroutines.flow.Flow

/**
 * Where Accounts, Categories, Merchants, Transactions and Metric definitions are kept. Reads are flows that emit again after
 * every write; writes are suspend functions that return the new id.
 */
interface FinanceRepository {
    /** Every Account, oldest first, archived ones included: callers leave those out where needed. */
    val accounts: Flow<List<Account>>

    /**
     * Every Category, parents and children, in the owner's order: the Spending parents, then the
     * Income ones, each followed by its children.
     */
    val categories: Flow<List<Category>>

    /**
     * Every Merchant, oldest first: a new database starts with [PreloadedMerchants], and later
     * updates add the entries it was never seeded with.
     */
    val merchants: Flow<List<Merchant>>

    /** Every Transaction, in no particular order. */
    val transactions: Flow<List<Transaction>>

    /** Every saved Metric definition, oldest first. Their values are computed, never stored. */
    val metrics: Flow<List<Metric>>

    /**
     * The owner's order of the KPI tiles on Home, as Metric ids, first to last: the list last given
     * to [saveKpiOrder], empty until then. It may name Metrics that are no longer pinned or no
     * longer exist, and miss ones pinned since; see [kpisInOrder].
     */
    val kpiOrder: Flow<List<Long>>

    /**
     * Saves [category] with its name trimmed, which must not be blank, and returns its id.
     *
     * With [Category.id] 0 it adds a Category with an id no Category ever had, deleted ones
     * included. A top-level one ([Category.parentId] null) goes last among the parents of its
     * [Category.kind]. A child goes last among the children of its parent, which must be a
     * top-level Category, and takes the parent's kind, whatever [Category.kind] says.
     *
     * Otherwise it renames the saved Category with that id, keeping its parent, kind and place;
     * there must be one.
     */
    suspend fun saveCategory(category: Category): Long

    /**
     * Swaps the Category [id] with the sibling before it ([up]) or after it: a child among its
     * parent's children, a parent among the parents of its kind, taking its children along. The
     * first sibling moving up, or the last one moving down, stays where it is.
     */
    suspend fun moveCategory(id: Long, up: Boolean)

    /**
     * Deletes the Category [id] and returns true, all in one transaction: its Transactions move
     * to [moveTo], every Metric filter naming [id] names [moveTo] instead (ADR 0002), and
     * Merchants whose default it was keep no default (ADR 0003). With [moveTo] null only the
     * Category goes, and Metric filters keep its id, which then matches nothing. A [moveTo] given
     * for a Category without Transactions still takes over its Metric filters. When
     * [canDeleteCategory] refuses (it has children, or has Transactions and [moveTo] is not
     * another Category of its kind), it changes nothing and returns false.
     */
    suspend fun deleteCategory(id: Long, moveTo: Long?): Boolean

    /** Saves a new Account (its [Account.id] is ignored) and returns the id it got. */
    suspend fun addAccount(account: Account): Long

    /**
     * Returns the id of the Merchant with the same name as [merchant] (see [sameMerchantName]),
     * changing nothing; when there is none, saves [merchant] (its [Merchant.id] is ignored) with
     * its name trimmed and returns the id it got. The name must not be blank.
     */
    suspend fun addMerchant(merchant: Merchant): Long

    /**
     * Replaces the saved Merchant with the same [Merchant.id] by [merchant], its name trimmed, and
     * returns true. Its Transactions keep pointing at it and keep their own Categories. When the
     * name is blank or another Merchant has it (see [merchantNameError]), or there is no Merchant
     * with that id, it changes nothing and returns false. The keywords are stored as given.
     */
    suspend fun updateMerchant(merchant: Merchant): Boolean

    /**
     * Deletes the Merchant [id]. Its Transactions stay, with every other field as it was, and no
     * Merchant (ADR 0003). A preloaded Merchant never comes back.
     */
    suspend fun deleteMerchant(id: Long)

    /**
     * Saves a new Transaction (its [Transaction.id] is ignored) and returns the id it got, which
     * is higher than every id given before.
     */
    suspend fun addTransaction(transaction: Transaction): Long

    /** Replaces the saved Transaction with the same [Transaction.id] by [transaction]. */
    suspend fun updateTransaction(transaction: Transaction)

    /**
     * Saves [transfer] as its two legs, all at once, and returns its id. With [Transfer.id] 0 it
     * creates both legs with a new transfer id; otherwise it rewrites the two legs of that
     * Transfer, keeping their Transaction ids.
     */
    suspend fun saveTransfer(transfer: Transfer): Long

    /** Deletes the Transaction [id]; when it is a Transfer leg, the other leg goes with it. */
    suspend fun deleteTransaction(id: Long)

    /** Marks the Account [id] archived. Its Transactions stay. */
    suspend fun archiveAccount(id: Long)

    /**
     * Deletes the Account [id] and returns true, but only when it has no Transactions; otherwise
     * it deletes nothing and returns false.
     */
    suspend fun deleteAccount(id: Long): Boolean

    /**
     * Saves [metric]'s definition and returns its id: with [Metric.id] 0 as a new Metric with a
     * new id, otherwise in place of the saved Metric with that id.
     */
    suspend fun saveMetric(metric: Metric): Long

    /** Replaces the saved KPI order by [ids], Metric ids first to last. */
    suspend fun saveKpiOrder(ids: List<Long>)

    /**
     * Deletes the Metric [id] unless a combined Metric uses it as a side. Returns the Metrics that
     * use it, oldest first: empty when it was deleted, otherwise nothing was deleted.
     */
    suspend fun deleteMetric(id: Long): List<Metric>
}
