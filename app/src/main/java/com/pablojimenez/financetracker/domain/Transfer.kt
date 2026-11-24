package com.pablojimenez.financetracker.domain

import java.time.LocalDate

/**
 * Money moved from the Account [fromAccountId] to the Account [toAccountId]: [amountCents] (above
 * zero) leaves one and arrives in the other on [date], so it never counts as spending or income.
 *
 * It is stored as two Transactions, its [legs], that share [id] as their
 * [Transaction.transferId]. [id] is 0 until the repository saves the Transfer.
 */
data class Transfer(
    val id: Long = 0,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amountCents: Long,
    val date: LocalDate,
    val note: String? = null,
) {
    init {
        require(amountCents > 0) { "A Transfer moves more than nothing" }
        require(fromAccountId != toAccountId) { "A Transfer moves money between two Accounts" }
    }

    /** The two legs: out of the from-Account and into the to-Account. Both have id 0. */
    fun legs(): TransferLegs {
        val out = Transaction(
            accountId = fromAccountId,
            categoryId = null,
            amountCents = -amountCents,
            date = date,
            note = note,
            source = TransactionSource.Manual,
            transferId = id,
        )
        return TransferLegs(out = out, into = out.copy(accountId = toAccountId, amountCents = amountCents))
    }
}

/** A Transfer's two Transactions: [out] has the negative amount, [into] the positive one. */
data class TransferLegs(val out: Transaction, val into: Transaction)

/**
 * The Transfer whose legs are among [transactions] with the transfer id [transferId], or null when
 * there are not exactly two of them, one out and one in.
 */
fun transferOf(transferId: Long, transactions: List<Transaction>): Transfer? {
    val legs = transactions.filter { it.transferId == transferId }
    val out = legs.singleOrNull { it.amountCents < 0 } ?: return null
    val into = legs.singleOrNull { it.amountCents > 0 } ?: return null
    if (legs.size != 2) return null
    return Transfer(
        id = transferId,
        fromAccountId = out.accountId,
        toAccountId = into.accountId,
        amountCents = into.amountCents,
        date = out.date,
        note = out.note,
    )
}
