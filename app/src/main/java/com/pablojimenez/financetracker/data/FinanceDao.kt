package com.pablojimenez.financetracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
internal interface FinanceDao {
    @Query("SELECT * FROM accounts ORDER BY id")
    fun accounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM categories ORDER BY position")
    fun categories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY position")
    suspend fun categoryList(): List<CategoryEntity>

    /** Inserts [category] with a new id (its own id must be 0) and returns that id. */
    @Insert
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("SELECT EXISTS (SELECT 1 FROM transactions WHERE categoryId = :id)")
    suspend fun categoryHasTransactions(id: Long): Boolean

    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveCategoryTransactions(from: Long, to: Long)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun renameCategory(id: Long, name: String): Int

    @Query("UPDATE categories SET position = :position WHERE id = :id")
    suspend fun setCategoryPosition(id: Long, position: Int)

    @Query("SELECT * FROM merchants ORDER BY id")
    fun merchants(): Flow<List<MerchantEntity>>

    @Query("SELECT * FROM merchants ORDER BY id")
    suspend fun merchantList(): List<MerchantEntity>

    @Insert
    suspend fun insertMerchant(merchant: MerchantEntity): Long

    /** Replaces the Merchant with [merchant]'s id; returns the rows changed, 0 when there is none. */
    @Update
    suspend fun updateMerchant(merchant: MerchantEntity): Int

    @Query("DELETE FROM merchants WHERE id = :id")
    suspend fun deleteMerchant(id: Long)

    @Query("SELECT * FROM transactions")
    fun transactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM metrics ORDER BY id")
    fun metrics(): Flow<List<MetricEntity>>

    @Query("SELECT * FROM metrics ORDER BY id")
    suspend fun metricList(): List<MetricEntity>

    @Query("DELETE FROM metrics WHERE id = :id")
    suspend fun deleteMetric(id: Long)

    @Insert
    suspend fun insertMetric(metric: MetricEntity): Long

    @Update
    suspend fun updateMetric(metric: MetricEntity)

    @Query("SELECT metricId FROM kpi_order ORDER BY position")
    fun kpiOrder(): Flow<List<Long>>

    @Query("DELETE FROM kpi_order")
    suspend fun clearKpiOrder()

    @Insert
    suspend fun insertKpiOrder(rows: List<KpiOrderEntity>)

    /** One transaction, so the order is never seen half written. */
    @Transaction
    suspend fun replaceKpiOrder(rows: List<KpiOrderEntity>) {
        clearKpiOrder()
        insertKpiOrder(rows)
    }

    @Insert
    suspend fun insertAccount(account: AccountEntity): Long

    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE transferId = :transferId")
    suspend fun transferLegs(transferId: Long): List<TransactionEntity>

    /** One statement, so a Transfer never loses only one leg. */
    @Query(
        "DELETE FROM transactions WHERE id = :id " +
            "OR transferId = (SELECT transferId FROM transactions WHERE id = :id)",
    )
    suspend fun deleteTransaction(id: Long)

    @Query("UPDATE accounts SET archived = 1 WHERE id = :id")
    suspend fun archiveAccount(id: Long)

    /** Deletes the Account only when no Transaction points at it; returns the rows deleted. */
    @Query(
        "DELETE FROM accounts WHERE id = :id " +
            "AND NOT EXISTS (SELECT 1 FROM transactions WHERE accountId = :id)",
    )
    suspend fun deleteAccountWithoutTransactions(id: Long): Int
}
