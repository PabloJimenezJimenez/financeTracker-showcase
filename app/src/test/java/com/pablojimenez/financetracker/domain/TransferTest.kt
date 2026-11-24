package com.pablojimenez.financetracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferTest {
    private val date = LocalDate.of(2026, 3, 10)
    private val transfer = Transfer(id = 7, fromAccountId = 1, toAccountId = 2, amountCents = 3_000, date = date, note = "Rent")

    @Test
    fun `a transfer's legs move its amount out of one account and into the other`() {
        val leg = Transaction(
            accountId = 1,
            categoryId = null,
            amountCents = -3_000,
            date = date,
            note = "Rent",
            source = TransactionSource.Manual,
            transferId = 7,
        )

        assertEquals(TransferLegs(out = leg, into = leg.copy(accountId = 2, amountCents = 3_000)), transfer.legs())
    }

    @Test
    fun `a transfer is found again from its two legs in any order`() {
        val (out, into) = transfer.legs()
        val other = Transaction(accountId = 1, categoryId = 3, amountCents = -50, date = date)

        assertEquals(transfer, transferOf(7, listOf(into.copy(id = 2), other, out.copy(id = 1))))
    }

    @Test
    fun `there is no transfer without both legs`() {
        val (out, _) = transfer.legs()

        assertNull(transferOf(7, listOf(out)))
        assertNull(transferOf(8, transfer.legs().let { listOf(it.out, it.into) }))
    }
}
