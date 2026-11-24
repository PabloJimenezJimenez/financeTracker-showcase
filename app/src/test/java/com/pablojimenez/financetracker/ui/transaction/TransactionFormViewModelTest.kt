package com.pablojimenez.financetracker.ui.transaction

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.FakeFinanceRepository
import com.pablojimenez.financetracker.domain.Merchant
import com.pablojimenez.financetracker.domain.PreloadedMerchants
import com.pablojimenez.financetracker.domain.Transaction
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** An open Transaction form when a Merchant is deleted elsewhere; Robolectric only provides the main thread. */
@RunWith(AndroidJUnit4::class)
class TransactionFormViewModelTest {
    private val repository = FakeFinanceRepository()

    /** 2026-03-10 at noon, UTC. */
    private val clock = Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC)

    private val accountId = repository.givenAccount(
        Account(name = "Current", type = AccountType.Current, startingBalanceCents = 0),
    )

    private val mercadona: Merchant = repository.savedMerchants.single { it.name == "Mercadona" }
    private val lidl: Merchant = repository.savedMerchants.single { it.name == "Lidl" }
    private val household: Long = repository.savedCategories.single { it.name == "Household" }.id

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun deleteMerchant(id: Long) {
        runBlocking { repository.deleteMerchant(id) }
        idle()
    }

    private fun addForm(): TransactionFormViewModel =
        TransactionFormViewModel(repository, clock).also { idle() }

    @Test
    fun `deleting the Merchant picked in an add form empties the field and keeps the Category, so saving links none`() {
        val form = addForm()
        form.onAmountChange("12.50")
        form.onMerchantChange("merc")
        form.onMerchantPick(mercadona.id)
        form.onCategoryChange(household)

        deleteMerchant(mercadona.id)

        assertEquals("", form.state.merchant)
        assertNull(form.state.merchantId)
        assertEquals(household, form.state.categoryId)
        form.onSave()
        idle()
        assertNull(repository.savedTransactions.single().merchantId)
        assertEquals(PreloadedMerchants.size - 1, repository.savedMerchants.size)
        assertNull(repository.savedMerchants.firstOrNull { it.name == "Mercadona" })
    }

    @Test
    fun `deleting the Merchant of an edited Transaction empties the field and keeps the Category, so saving links none`() {
        val draft = Transaction(
            accountId = accountId,
            categoryId = household,
            amountCents = -1_250,
            date = LocalDate.of(2026, 3, 9),
            merchantId = mercadona.id,
        )
        val saved = draft.copy(id = repository.givenTransaction(draft))
        val form = TransactionFormViewModel(repository, clock, saved.id).also { idle() }
        assertEquals("Mercadona", form.state.merchant)

        deleteMerchant(mercadona.id)

        assertEquals("", form.state.merchant)
        assertNull(form.state.merchantId)
        assertEquals(household, form.state.categoryId)
        form.onSave()
        idle()
        assertEquals(listOf(saved.copy(merchantId = null)), repository.savedTransactions)
        assertNull(repository.savedMerchants.firstOrNull { it.name == "Mercadona" })
    }

    @Test
    fun `deleting a Merchant the form does not have leaves the form as it was`() {
        val picked = addForm()
        picked.onMerchantChange("merc")
        picked.onMerchantPick(mercadona.id)
        val typed = addForm()
        typed.onMerchantChange("Lid")
        val pickedBefore = picked.state
        val typedBefore = typed.state

        deleteMerchant(lidl.id)

        assertEquals(pickedBefore, picked.state)
        assertEquals(typedBefore, typed.state)
    }
}
