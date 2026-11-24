package com.pablojimenez.financetracker.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.FakeFinanceRepository
import com.pablojimenez.financetracker.domain.Merchant
import com.pablojimenez.financetracker.domain.PreloadedMerchants
import com.pablojimenez.financetracker.domain.Transaction
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
    it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
}

/** The Merchant field of the Transaction form: autocomplete, prefill, and create-or-reuse on save. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "es-rES-w411dp-h2000dp")
class FinanceAppMerchantTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val repository = FakeFinanceRepository()

    /** 2026-03-10 at noon, UTC. */
    private val clock = Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC)

    private val accountId = repository.givenAccount(
        Account(name = "Current", type = AccountType.Current, startingBalanceCents = 10_000),
    )

    private fun launch() {
        composeRule.setContent {
            FinanceTrackerTheme(dynamicColor = false) {
                FinanceApp(repository = repository, clock = clock)
            }
        }
    }

    private fun category(name: String): Category = repository.savedCategories.single { it.name == name }

    private fun merchant(name: String): Merchant = repository.savedMerchants.single { it.name == name }

    private fun openTransactionForm() {
        composeRule.onNodeWithContentDescription("Add").performClick()
        composeRule.onNodeWithTag("add-transaction").performClick()
    }

    private fun pickCategory(category: Category) {
        composeRule.onNodeWithTag("category-field").performClick()
        composeRule.onNodeWithTag("category-${category.id}").performScrollTo().performClick()
    }

    private fun save() {
        composeRule.onNodeWithText("Save").performClick()
    }

    private fun suggestion(merchant: Merchant) = composeRule.onNodeWithTag("merchant-suggestion-${merchant.id}")

    @Test
    fun `picking a suggested Merchant fills the field and its Category, which the owner can still change`() {
        val mercadona = merchant("Mercadona")
        val electronics = category("Electronics")
        val household = category("Household")
        launch()
        openTransactionForm()
        composeRule.onNodeWithTag("amount").performTextInput("42,35")
        pickCategory(electronics)

        composeRule.onNodeWithTag("merchant").performTextInput("merc")
        suggestion(mercadona).assertIsDisplayed().assert(hasText("Mercadona")).assert(hasText("Food"))
        suggestion(mercadona).performClick()

        suggestion(mercadona).assertDoesNotExist()
        composeRule.onNodeWithTag("merchant").assert(hasText("Mercadona"))
        composeRule.onNodeWithTag("category-field").assert(hasAnyDescendant(hasText("Food")))

        pickCategory(household)
        save()

        val saved = repository.savedTransactions.single()
        assertEquals(mercadona.id, saved.merchantId)
        assertEquals(household.id, saved.categoryId)
        assertEquals(mercadona, merchant("Mercadona"))
        composeRule.onNodeWithTag("transaction-${saved.id}").assert(hasText("Mercadona")).assert(hasText("Household"))
    }

    @Test
    fun `the suggestions close when the text matches no Merchant`() {
        launch()
        openTransactionForm()

        composeRule.onNodeWithTag("merchant").performTextInput("merc")
        suggestion(merchant("Mercadona")).assertIsDisplayed()
        composeRule.onNodeWithTag("merchant").performTextInput("xyz")

        suggestion(merchant("Mercadona")).assertDoesNotExist()
    }

    @Test
    fun `suggestions ignore case, accents and spacing, put names starting with the text first, and stop at five`() {
        launch()
        openTransactionForm()

        composeRule.onNodeWithTag("merchant").performTextInput("MA")

        val shown = listOf("Mango", "Mapfre", "MásMóvil", "Massimo Dutti", "Ahorramas")
        shown.forEach { name -> suggestion(merchant(name)).assertIsDisplayed() }
        val tops = shown.map { name ->
            suggestion(merchant(name)).fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals("in the order $shown", tops.sorted(), tops)
        assertEquals(5, composeRule.onAllNodes(hasTestTagPrefix("merchant-suggestion-")).fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("merchant").performTextClearance()
        composeRule.onNodeWithTag("merchant").performTextInput("corte INGLES")
        suggestion(merchant("El Corte Inglés")).assertIsDisplayed()
    }

    @Test
    fun `a new Merchant name creates one owner's Merchant with the picked Category, and typing it again reuses it`() {
        val restaurants = category("Restaurants")
        val coffee = category("Coffee")
        launch()

        openTransactionForm()
        composeRule.onNodeWithTag("amount").performTextInput("12,00")
        pickCategory(restaurants)
        composeRule.onNodeWithTag("merchant").performTextInput(" Bar Pepe ")
        save()

        val barPepe = merchant("Bar Pepe")
        assertEquals(Merchant(barPepe.id, "Bar Pepe", emptyList(), restaurants.id), barPepe)
        assertEquals(barPepe.id, repository.savedTransactions.single().merchantId)

        openTransactionForm()
        composeRule.onNodeWithTag("amount").performTextInput("2,00")
        pickCategory(coffee)
        composeRule.onNodeWithTag("merchant").performTextInput("bar pepe")
        save()

        val second = repository.savedTransactions.single { it.amountCents == -200L }
        assertEquals(barPepe.id, second.merchantId)
        assertEquals(coffee.id, second.categoryId)
        assertEquals(PreloadedMerchants.size + 1, repository.savedMerchants.size)
        assertEquals("the default Category stays", barPepe, merchant("Bar Pepe"))
    }

    @Test
    fun `saving with no Merchant and no Category shows the Category error and saves nothing`() {
        launch()
        openTransactionForm()

        composeRule.onNodeWithTag("amount").performTextInput("5,00")
        save()

        composeRule.onNodeWithText("New transaction").assertIsDisplayed()
        composeRule.onNodeWithText("Pick a category").assertIsDisplayed()
        assertEquals(emptyList<Transaction>(), repository.savedTransactions)
        assertEquals(PreloadedMerchants.size, repository.savedMerchants.size)
    }

    private fun givenExpense(merchantId: Long?): Transaction {
        val expense = Transaction(
            accountId = accountId,
            categoryId = category("Food").id,
            amountCents = -1_250,
            date = LocalDate.of(2026, 3, 10),
            merchantId = merchantId,
        )
        return expense.copy(id = repository.givenTransaction(expense))
    }

    @Test
    fun `editing shows the Merchant's name, and clearing it saves no Merchant but keeps the Merchant`() {
        val mercadona = merchant("Mercadona")
        val expense = givenExpense(mercadona.id)
        launch()

        composeRule.onNodeWithTag("transaction-${expense.id}").assert(hasText("Mercadona")).performClick()
        composeRule.onNodeWithTag("merchant").assert(hasText("Mercadona"))
        composeRule.onNodeWithTag("merchant").performTextClearance()
        save()

        assertEquals(listOf(expense.copy(merchantId = null)), repository.savedTransactions)
        assertEquals(mercadona, merchant("Mercadona"))
    }

    @Test
    fun `saving never changes an existing Merchant's default Category`() {
        val mercadona = merchant("Mercadona")
        val electronics = category("Electronics")
        val expense = givenExpense(mercadona.id)
        launch()

        composeRule.onNodeWithTag("transaction-${expense.id}").performClick()
        pickCategory(electronics)
        save()
        openTransactionForm()
        composeRule.onNodeWithTag("amount").performTextInput("3,00")
        pickCategory(electronics)
        composeRule.onNodeWithTag("merchant").performTextInput("MERCADONA")
        save()

        assertEquals(listOf(mercadona.id, mercadona.id), repository.savedTransactions.map { it.merchantId })
        assertEquals(listOf(electronics.id, electronics.id), repository.savedTransactions.map { it.categoryId })
        assertEquals(mercadona, merchant("Mercadona"))
        composeRule.onNodeWithTag("transaction-${expense.id}").assert(hasText("Mercadona"))
    }
}
