package com.pablojimenez.financetracker.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.FakeFinanceRepository
import com.pablojimenez.financetracker.domain.Merchant
import com.pablojimenez.financetracker.domain.Transaction
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Merchants screen under Settings: the list, the edit dialog and deleting a Merchant. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2000dp")
class FinanceAppMerchantsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val repository = FakeFinanceRepository()

    /** 2026-03-10 at noon, UTC. */
    private val clock = Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC)

    private fun launch() {
        composeRule.setContent {
            FinanceTrackerTheme(dynamicColor = false) {
                FinanceApp(repository = repository, clock = clock)
            }
        }
    }

    private fun category(name: String): Category = repository.savedCategories.single { it.name == name }

    private fun merchant(name: String): Merchant = repository.savedMerchants.single { it.name == name }

    private fun givenMerchant(name: String, defaultCategoryId: Long? = null): Merchant = runBlocking {
        val id = repository.addMerchant(Merchant(name = name, defaultCategoryId = defaultCategoryId))
        repository.savedMerchants.single { it.id == id }
    }

    private fun openMerchants() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithTag("settings-merchants").performClick()
    }

    private fun row(merchant: Merchant) = composeRule.onNodeWithTag("merchant-row-${merchant.id}")

    private fun scrollTo(merchant: Merchant) {
        composeRule.onNodeWithTag("merchants").performScrollToNode(hasTestTag("merchant-row-${merchant.id}"))
    }

    private fun top(merchant: Merchant): Float = row(merchant).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun `Settings opens the Merchants screen, which lists every Merchant by name ignoring case and accents`() {
        val barPepe = givenMerchant("Bar Pepe")
        val abaco = givenMerchant("ábaco", defaultCategoryId = category("Coffee").id)
        launch()

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithTag("settings-merchants").assert(hasText("Merchants")).performClick()

        composeRule.onNodeWithText("Merchants").assertIsDisplayed()
        val mercadona = merchant("Mercadona")
        scrollTo(mercadona)
        row(mercadona).assert(hasText("Mercadona")).assert(hasText("Food"))
        composeRule.onNodeWithTag("merchants").performScrollToIndex(0)
        row(abaco).assert(hasText("ábaco")).assert(hasText("Coffee"))
        row(barPepe).assert(hasText("Bar Pepe")).assert(hasText("No default category"))
        val inOrder = listOf("100 Montaditos", "ábaco", "Ahorramas", "Air Europa", "Ballenoil", "Bar Pepe", "Basic-Fit")
            .map(::merchant)
        val tops = inOrder.map(::top)
        assertEquals("in the order ${inOrder.map { it.name }}", tops.sorted(), tops)
        repository.savedMerchants.forEach { merchant ->
            scrollTo(merchant)
            row(merchant).assert(hasText(merchant.name))
        }
    }

    private val accountId by lazy {
        repository.givenAccount(Account(name = "Current", type = AccountType.Current, startingBalanceCents = 0))
    }

    private fun givenExpense(merchant: Merchant, category: Category, cents: Long = 1_250): Transaction {
        val expense = Transaction(
            accountId = accountId,
            categoryId = category.id,
            amountCents = -cents,
            date = LocalDate.of(2026, 3, 10),
            merchantId = merchant.id,
        )
        return expense.copy(id = repository.givenTransaction(expense))
    }

    private fun openDialog(merchant: Merchant) {
        scrollTo(merchant)
        row(merchant).performClick()
    }

    private fun type(tag: String, text: String) {
        composeRule.onNodeWithTag(tag).performTextClearance()
        composeRule.onNodeWithTag(tag).performTextInput(text)
    }

    private fun save() {
        composeRule.onNodeWithTag("merchant-save").performClick()
    }

    private fun back() {
        composeRule.onNodeWithContentDescription("Back").performClick()
    }

    @Test
    fun `editing a Merchant saves its name, folded keywords and default Category, and Home shows the new name`() {
        val mercadona = merchant("Mercadona")
        val groceries = category("Groceries")
        val expense = givenExpense(mercadona, category("Food"))
        launch()
        openMerchants()

        openDialog(mercadona)
        composeRule.onNodeWithTag("merchant-name").assert(hasText("Mercadona"))
        composeRule.onNodeWithTag("merchant-keywords").assert(hasText("mercadona"))
        composeRule.onNodeWithTag("merchant-category").assert(hasAnyDescendant(hasText("Food")))
        type("merchant-name", "Mercadona Online")
        type("merchant-keywords", " Mercadona, MERCADONA online ,, Mércadona")
        composeRule.onNodeWithTag("merchant-category").performClick()
        composeRule.onNodeWithTag("category-${groceries.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("merchant-category").assert(hasAnyDescendant(hasText("Groceries")))
        save()

        composeRule.onNodeWithTag("merchant-name").assertDoesNotExist()
        assertEquals(
            Merchant(mercadona.id, "Mercadona Online", listOf("mercadona", "mercadona online"), groceries.id),
            repository.savedMerchants.single { it.id == mercadona.id },
        )
        scrollTo(mercadona)
        row(mercadona).assert(hasText("Mercadona Online")).assert(hasText("Groceries"))
        assertEquals(listOf(expense), repository.savedTransactions)
        back()
        back()
        composeRule.onNodeWithTag("transaction-${expense.id}")
            .assert(hasText("Mercadona Online"))
            .assert(hasText("Food"))
    }

    @Test
    fun `the default Category picker's New category saves a Category and makes it the default`() {
        val mercadona = merchant("Mercadona")
        launch()
        openMerchants()

        openDialog(mercadona)
        composeRule.onNodeWithTag("merchant-category").performClick()
        composeRule.onNodeWithTag("new-category").performScrollTo().performClick()
        type("category-name", "Pets")
        composeRule.onNodeWithTag("category-save").performClick()

        val pets = category("Pets")
        composeRule.onNodeWithTag("merchant-category").assert(hasAnyDescendant(hasText("Pets")))
        save()

        assertEquals(pets.id, repository.savedMerchants.single { it.id == mercadona.id }.defaultCategoryId)
        row(mercadona).assert(hasText("Pets"))
    }

    @Test
    fun `a blank name or another Merchant's name shows an error, saves nothing and keeps the dialog open`() {
        val mercadona = merchant("Mercadona")
        val before = repository.savedMerchants
        launch()
        openMerchants()
        openDialog(mercadona)

        type("merchant-name", "   ")
        save()
        composeRule.onNodeWithText("Enter a name").assertIsDisplayed()
        composeRule.onNodeWithTag("merchant-name").assertIsDisplayed()
        assertEquals(before, repository.savedMerchants)

        type("merchant-name", "lidl")
        composeRule.onNodeWithText("Enter a name").assertDoesNotExist()
        save()
        composeRule.onNodeWithText("There is already a merchant with this name").assertIsDisplayed()
        composeRule.onNodeWithTag("merchant-name").assertIsDisplayed()
        assertEquals(before, repository.savedMerchants)
    }

    @Test
    fun `Delete asks first, Cancel keeps the Merchant, and confirming removes it from the list, Home and the form`() {
        val mercadona = merchant("Mercadona")
        val food = category("Food")
        val expenses = listOf(givenExpense(mercadona, food, cents = 4_235), givenExpense(mercadona, food, cents = 799))
        launch()
        openMerchants()

        openDialog(mercadona)
        composeRule.onNodeWithTag("merchant-delete").performClick()
        composeRule.onNodeWithText("Delete Mercadona?").assertIsDisplayed()
        composeRule.onNodeWithText("Its 2 transactions keep their amounts and categories but will have no merchant.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Delete Mercadona?").assertDoesNotExist()
        row(mercadona).assertIsDisplayed()
        assertEquals(mercadona, merchant("Mercadona"))

        openDialog(mercadona)
        composeRule.onNodeWithTag("merchant-delete").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        row(mercadona).assertDoesNotExist()
        assertEquals(null, repository.savedMerchants.firstOrNull { it.id == mercadona.id })
        assertEquals(expenses.map { it.copy(merchantId = null) }, repository.savedTransactions)
        back()
        back()
        expenses.forEach { expense ->
            composeRule.onNodeWithTag("transaction-${expense.id}").assert(!hasText("Mercadona")).assert(hasText("Food"))
        }
        composeRule.onNodeWithContentDescription("Add").performClick()
        composeRule.onNodeWithTag("add-transaction").performClick()
        composeRule.onNodeWithTag("merchant").performTextInput("merc")
        composeRule.onNodeWithTag("merchant-suggestion-${mercadona.id}").assertDoesNotExist()
    }

    @Test
    fun `deleting a Merchant without Transactions says it is not used`() {
        val lidl = merchant("Lidl")
        launch()
        openMerchants()

        openDialog(lidl)
        composeRule.onNodeWithTag("merchant-delete").performClick()

        composeRule.onNodeWithText("Delete Lidl?").assertIsDisplayed()
        composeRule.onNodeWithText("It is not used by any transaction.").assertIsDisplayed()
    }

    @Test
    fun `with no Merchants the screen says they are added when entering a transaction`() {
        runBlocking { repository.savedMerchants.forEach { repository.deleteMerchant(it.id) } }
        launch()

        openMerchants()

        composeRule.onNodeWithText("No merchants yet. They are added when you enter a transaction.").assertIsDisplayed()
    }

    @Test
    fun `Done on the Keywords keyboard saves like Save`() {
        val mercadona = merchant("Mercadona")
        launch()
        openMerchants()
        openDialog(mercadona)

        type("merchant-keywords", "mercadona, merc")
        composeRule.onNodeWithTag("merchant-keywords").performImeAction()

        composeRule.onNodeWithTag("merchant-name").assertDoesNotExist()
        assertEquals(listOf("mercadona", "merc"), merchant("Mercadona").keywords)
    }
}
