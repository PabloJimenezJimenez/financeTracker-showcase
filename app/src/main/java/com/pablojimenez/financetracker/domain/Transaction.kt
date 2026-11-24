package com.pablojimenez.financetracker.domain

import java.time.LocalDate
import java.time.LocalTime

/** Where a Transaction came from. Every Transaction entered in a form is [Manual]. */
enum class TransactionSource { Manual }

/**
 * One movement of money into or out of the Account [accountId], in the Category [categoryId].
 * [categoryId] is null for a Transaction with no Category (an Uncategorised one, or a Transfer
 * leg); a Transaction entered in the form always has one.
 *
 * [amountCents] carries the direction: negative is money out (an expense), positive is money in
 * (income, or a Refund when the Category is a spending one). [time] is optional.
 *
 * [merchantId] is the Merchant where the money was spent or came from, or null when none was given.
 *
 * A Transfer leg has a [transferId], the same on both legs of one Transfer; every other
 * Transaction has none.
 *
 * [id] is 0 until the repository saves the Transaction. Saved ids only grow, so a higher id
 * means a more recently created Transaction.
 */
data class Transaction(
    val id: Long = 0,
    val accountId: Long,
    val categoryId: Long?,
    val amountCents: Long,
    val date: LocalDate,
    val time: LocalTime? = null,
    val merchantId: Long? = null,
    val note: String? = null,
    val source: TransactionSource = TransactionSource.Manual,
    val transferId: Long? = null,
)

/** Which way money moves in a Transaction; the amount is typed positive and this gives the sign. */
enum class Direction {
    Expense,
    Income,
    ;

    /** [cents] (positive) as a Transaction amount: negative for an expense. */
    fun signed(cents: Long): Long = if (this == Expense) -cents else cents
}

/** The Transactions of one [date]. */
data class TransactionDay(val date: LocalDate, val transactions: List<Transaction>)

/**
 * These Transactions grouped by date, newest date first. Within a day, Transactions with a time
 * come first, latest time first, then those without one; ties go to the most recently created.
 */
fun List<Transaction>.groupedByDay(): List<TransactionDay> =
    sortedWith(newestFirst)
        .groupBy { it.date }
        .map { (date, transactions) -> TransactionDay(date, transactions) }

private val newestFirst: Comparator<Transaction> =
    compareByDescending<Transaction> { it.date }
        .thenBy { it.time == null }
        .thenByDescending { it.time }
        .thenByDescending { it.id }
