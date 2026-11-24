package com.pablojimenez.financetracker.domain

/** An Account with its balance: the starting balance plus every one of its Transactions. */
data class AccountBalance(val account: Account, val balanceCents: Long)

/** The balance of each of [accounts], in the same order. */
fun accountBalances(accounts: List<Account>, transactions: List<Transaction>): List<AccountBalance> {
    val movedByAccount = transactions.groupBy { it.accountId }
        .mapValues { (_, own) -> own.sumOf { it.amountCents } }
    return accounts.map { account ->
        AccountBalance(account, account.startingBalanceCents + (movedByAccount[account.id] ?: 0L))
    }
}

/** The total across every Account. */
fun List<AccountBalance>.totalCents(): Long = sumOf { it.balanceCents }
