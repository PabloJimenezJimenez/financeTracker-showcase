package com.pablojimenez.financetracker.domain

import java.util.Currency

/** The only currency for now: every Account holds euros and the field cannot be changed. */
val EUR: Currency = Currency.getInstance("EUR")

/** What kind of place an [Account] is. */
enum class AccountType { Current, Savings, CreditCard, Cash }

/**
 * A place money is held and moves through. Its balance is [startingBalanceCents] plus the
 * amounts of its Transactions (see [accountBalances]).
 *
 * An [archived] Account keeps its Transactions and still counts in the total, but it is no longer
 * shown on Home or offered for anything new.
 *
 * [id] is 0 until the repository saves the Account and assigns one.
 */
data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val startingBalanceCents: Long,
    val currency: Currency = EUR,
    val archived: Boolean = false,
)

/**
 * The Accounts that can be picked for something new: every one that is not archived, plus [keep]
 * (an archived Account an existing Transaction already uses), in the same order.
 */
fun List<Account>.pickable(keep: Collection<Long> = emptyList()): List<Account> =
    filter { !it.archived || it.id in keep }
