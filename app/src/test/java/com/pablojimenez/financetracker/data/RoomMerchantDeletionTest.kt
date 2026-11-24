package com.pablojimenez.financetracker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.Merchant
import com.pablojimenez.financetracker.domain.Transaction
import com.pablojimenez.financetracker.domain.sameMerchantName
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Deleting a Merchant from a database file, which is then closed and opened again as the app does. */
@RunWith(AndroidJUnit4::class)
class RoomMerchantDeletionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "merchant-deletion-test.db"

    @After
    fun deleteFile() {
        context.deleteDatabase(name)
    }

    /** Opens the database file, runs [block] on its repository, and closes it. */
    private fun <T> withRepository(block: suspend (RoomFinanceRepository) -> T): T {
        val database = FinanceDatabase.create(context, name)
        try {
            return runBlocking { block(RoomFinanceRepository(database)) }
        } finally {
            database.close()
        }
    }

    @Test
    fun `deleting a Merchant clears it from its Transactions, keeps them, and it does not come back on reopening`() {
        val (mercadona, lidl, saved) = withRepository { repository ->
            val merchants = repository.merchants.first()
            val mercadona = merchants.single { it.name == "Mercadona" }
            val lidl = merchants.single { it.name == "Lidl" }
            val categories = repository.categories.first()
            fun category(name: String) = categories.single { it.name == name }.id
            val accountId = repository.addAccount(
                Account(name = "Current", type = AccountType.Current, startingBalanceCents = 0),
            )
            val drafts = listOf(
                Transaction(accountId = accountId, categoryId = category("Food"), amountCents = -4_235,
                    date = LocalDate.of(2026, 3, 9), merchantId = mercadona.id, note = "Weekly shop"),
                Transaction(accountId = accountId, categoryId = category("Household"), amountCents = -799,
                    date = LocalDate.of(2026, 3, 10), merchantId = mercadona.id),
                Transaction(accountId = accountId, categoryId = category("Food"), amountCents = -1_500,
                    date = LocalDate.of(2026, 3, 10), merchantId = lidl.id),
            )
            val saved = drafts.map { it.copy(id = repository.addTransaction(it)) }
            repository.deleteMerchant(mercadona.id)
            Triple(mercadona, lidl, saved)
        }

        val (merchants, transactions) = withRepository { repository ->
            repository.merchants.first() to repository.transactions.first()
        }

        assertNull(merchants.firstOrNull { it.id == mercadona.id })
        assertNull(merchants.firstOrNull { sameMerchantName(it.name, "Mercadona") })
        assertEquals(
            "Mercadona's two lose their Merchant only, Lidl's keeps it",
            setOf(saved[0].copy(merchantId = null), saved[1].copy(merchantId = null), saved[2]),
            transactions.toSet(),
        )
        assertEquals(lidl.id, transactions.single { it.id == saved[2].id }.merchantId)
    }

    @Test
    fun `a Merchant added later with a deleted Merchant's name is a new owner's Merchant with a new id`() {
        val mercadona = withRepository { repository ->
            val mercadona = repository.merchants.first().single { it.name == "Mercadona" }
            repository.deleteMerchant(mercadona.id)
            mercadona
        }

        val (id, merchants) = withRepository { repository ->
            repository.addMerchant(Merchant(name = "Mercadona")) to repository.merchants.first()
        }

        assertTrue("a new id", id != mercadona.id)
        assertEquals(Merchant(id = id, name = "Mercadona"), merchants.single { it.id == id })
        assertEquals(1, merchants.count { sameMerchantName(it.name, "Mercadona") })
    }
}
