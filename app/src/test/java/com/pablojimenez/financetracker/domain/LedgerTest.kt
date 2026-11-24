package com.pablojimenez.financetracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerTest {
    private val today = LocalDate.of(2026, 1, 1)
    private val current = Account(id = 1, name = "Current", type = AccountType.Current, startingBalanceCents = 10_000)
    private val cash = Account(id = 2, name = "Cash", type = AccountType.Cash, startingBalanceCents = -500)

    private fun transaction(accountId: Long, cents: Long) =
        Transaction(accountId = accountId, categoryId = 1, amountCents = cents, date = today)

    @Test
    fun `an account with no transactions has its starting balance`() {
        assertEquals(
            listOf(AccountBalance(current, 10_000), AccountBalance(cash, -500)),
            accountBalances(listOf(current, cash), emptyList()),
        )
    }

    @Test
    fun `an account's balance adds only its own income and expenses`() {
        val balances = accountBalances(
            listOf(current, cash),
            listOf(transaction(1, 250_000), transaction(1, -90_000), transaction(2, -1_250)),
        )

        assertEquals(listOf(170_000L, -1_750L), balances.map { it.balanceCents })
    }

    @Test
    fun `the total adds every account's balance`() {
        val balances = accountBalances(listOf(current, cash), listOf(transaction(2, -1_250)))

        assertEquals(8_250L, balances.totalCents())
    }

    @Test
    fun `no accounts make a total of zero`() {
        assertEquals(0L, accountBalances(emptyList(), emptyList()).totalCents())
    }
}
