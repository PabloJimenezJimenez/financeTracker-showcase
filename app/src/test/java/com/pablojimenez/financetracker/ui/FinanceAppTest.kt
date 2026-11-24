package com.pablojimenez.financetracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.CategoryKind
import com.pablojimenez.financetracker.domain.FakeFinanceRepository
import com.pablojimenez.financetracker.domain.Transaction
import com.pablojimenez.financetracker.domain.TransactionSource
import com.pablojimenez.financetracker.domain.Transfer
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.LocalReduceMotion
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Money and dates as Spanish formats them: "50,00 €" has a no-break space before the sign. */
private fun euros(text: String) = text.replace(' ', ' ')

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "es-rES-w411dp-h2000dp")
class FinanceAppTest {
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

    private fun total(): SemanticsNodeInteraction = composeRule.onNodeWithTag("balance")

    @Test
    fun `adding the first account shows its tile and the total on Home`() {
        launch()
        total().assert(hasText("Net worth")).assert(hasContentDescription(euros("0,00 €")))
        composeRule.onNodeWithText("No transactions yet").assertIsDisplayed()

        composeRule.onNodeWithText("Add account").performClick()
        composeRule.onNodeWithTag("account-name").performTextInput("Cash")
        composeRule.onNodeWithTag("account-type-Cash").performClick()
        composeRule.onNodeWithTag("starting-balance").performTextInput("50,00")
        composeRule.onNodeWithText("EUR").assertIsDisplayed().assert(!hasSetTextAction())
        composeRule.onNodeWithText("Save").performClick()

        val id = repository.savedAccounts.single().id
        assertEquals(
            Account(id = id, name = "Cash", type = AccountType.Cash, startingBalanceCents = 5_000),
            repository.savedAccounts.single(),
        )
        composeRule.onNodeWithTag("account-$id")
            .assertIsDisplayed()
            .assert(hasText("Cash"))
            .assert(hasContentDescription(euros("50,00 €")))
        total().assert(hasContentDescription(euros("50,00 €")))
    }

    private val current = Account(name = "Current", type = AccountType.Current, startingBalanceCents = 10_000)

    private fun category(kind: CategoryKind, child: Boolean): Category =
        repository.savedCategories.first { it.kind == kind && (it.parentId != null) == child }

    private fun pickCategory(category: Category) {
        composeRule.onNodeWithTag("category-field").performClick()
        composeRule.onNodeWithTag("category-${category.id}").performScrollTo().performClick()
    }

    /** "+", then the Transaction or Transfer item of its menu. */
    private fun openFromPlus(item: String) {
        composeRule.onNodeWithContentDescription("Add").performClick()
        composeRule.onNodeWithTag(item).performClick()
    }

    private fun addTransaction(amount: String, category: Category, income: Boolean = false) {
        openFromPlus("add-transaction")
        if (income) composeRule.onNodeWithText("Income").performClick()
        composeRule.onNodeWithTag("amount").performTextInput(amount)
        pickCategory(category)
        composeRule.onNodeWithText("Save").performClick()
    }

    /** Asserts the node tagged [below] is laid out under the node tagged [above]. */
    private fun assertBelow(below: String, above: String) {
        val top = composeRule.onNodeWithTag(below).fetchSemanticsNode().boundsInRoot.top
        val bottom = composeRule.onNodeWithTag(above).fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("$below (top $top) is under $above (bottom $bottom)", top >= bottom)
    }

    @Test
    fun `an expense of 12,50 is stored as minus 1250 cents today and lowers the balances`() {
        val accountId = repository.givenAccount(current)
        val groceries = category(CategoryKind.Spending, child = true)
        launch()

        addTransaction("12,50", groceries)

        val saved = repository.savedTransactions.single()
        assertEquals(
            Transaction(
                id = saved.id,
                accountId = accountId,
                categoryId = groceries.id,
                amountCents = -1_250,
                date = LocalDate.of(2026, 3, 10),
                time = null,
                merchantId = null,
                note = null,
                source = TransactionSource.Manual,
            ),
            saved,
        )
        composeRule.onNodeWithTag("account-$accountId").assert(hasContentDescription(euros("87,50 €")))
        total().assert(hasContentDescription(euros("87,50 €")))
        composeRule.onNodeWithTag("day-2026-03-10").assert(hasText("Martes, 10 de marzo de 2026"))
        composeRule.onNodeWithTag("transaction-${saved.id}")
            .assertIsDisplayed()
            .assert(hasText(groceries.name))
            .assert(hasText(euros("−12,50 €")))
        assertBelow("transaction-${saved.id}", above = "day-2026-03-10")
    }

    @Test
    fun `an income of 12,50 is stored as plus 1250 cents`() {
        val accountId = repository.givenAccount(current)
        val salary = category(CategoryKind.Income, child = false)
        launch()

        addTransaction("12,50", salary, income = true)

        val saved = repository.savedTransactions.single()
        assertEquals(1_250L, saved.amountCents)
        assertEquals(salary.id, saved.categoryId)
        assertEquals(TransactionSource.Manual, saved.source)
        composeRule.onNodeWithTag("account-$accountId").assert(hasContentDescription(euros("112,50 €")))
        total().assert(hasContentDescription(euros("112,50 €")))
        composeRule.onNodeWithTag("transaction-${saved.id}").assert(hasText(euros("+12,50 €")))
    }

    private fun openTransactionForm() {
        openFromPlus("add-transaction")
    }

    private fun assertFormStaysOpenWithError(error: String) {
        composeRule.onNodeWithText("New transaction").assertIsDisplayed()
        composeRule.onNodeWithText(error).assertIsDisplayed()
        assertTrue("nothing was saved", repository.savedTransactions.isEmpty())
    }

    @Test
    fun `saving without a category keeps the form open with Pick a category`() {
        repository.givenAccount(current)
        launch()
        openTransactionForm()

        composeRule.onNodeWithTag("amount").performTextInput("12,50")
        composeRule.onNodeWithText("Save").performClick()

        assertFormStaysOpenWithError("Pick a category")
        composeRule.onNodeWithText("Enter an amount").assertDoesNotExist()
    }

    @Test
    fun `saving with an empty amount keeps the form open with Enter an amount`() {
        repository.givenAccount(current)
        launch()
        openTransactionForm()

        pickCategory(category(CategoryKind.Spending, child = false))
        composeRule.onNodeWithText("Save").performClick()

        assertFormStaysOpenWithError("Enter an amount")
        composeRule.onNodeWithText("Pick a category").assertDoesNotExist()
    }

    @Test
    fun `saving a zero amount keeps the form open with Enter an amount`() {
        repository.givenAccount(current)
        launch()
        openTransactionForm()

        composeRule.onNodeWithTag("amount").performTextInput("0,00")
        pickCategory(category(CategoryKind.Spending, child = false))
        composeRule.onNodeWithText("Save").performClick()

        assertFormStaysOpenWithError("Enter an amount")
    }

    @Test
    fun `saving an account without a name keeps the form open with Enter a name`() {
        launch()
        composeRule.onNodeWithText("Add account").performClick()

        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("New account").assertIsDisplayed()
        composeRule.onNodeWithText("Enter a name").assertIsDisplayed()
        assertTrue("nothing was saved", repository.savedAccounts.isEmpty())
    }

    /** Asserts the nodes tagged [tags] are laid out in this order, top to bottom. */
    private fun assertTopToBottom(vararg tags: String) {
        val tops = tags.map { composeRule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.top }
        val order = tags.indices.sortedBy { tops[it] }.map { tags[it] }
        assertEquals(tags.toList(), order)
    }

    @Test
    fun `Home lists days newest first, timed rows by time, then untimed, ties newest created first`() {
        val current = repository.givenAccount(current)
        val cash = repository.givenAccount(
            Account(name = "Cash", type = AccountType.Cash, startingBalanceCents = 0),
        )
        val categoryId = category(CategoryKind.Spending, child = false).id
        fun given(accountId: Long, date: LocalDate, time: LocalTime? = null) = repository.givenTransaction(
            Transaction(accountId = accountId, categoryId = categoryId, amountCents = -100, date = date, time = time),
        )
        val march8 = LocalDate.of(2026, 3, 8)
        val march9 = LocalDate.of(2026, 3, 9)
        val march10 = LocalDate.of(2026, 3, 10)
        val oldest = given(current, march8)
        val morning = given(current, march10, LocalTime.of(9, 0))
        val untimedFirst = given(cash, march10)
        val eveningFirst = given(cash, march10, LocalTime.of(18, 45))
        val untimedSecond = given(current, march10)
        val eveningSecond = given(current, march10, LocalTime.of(18, 45))
        val noon = given(cash, march9, LocalTime.NOON)

        launch()

        assertTopToBottom(
            "day-2026-03-10",
            "transaction-$eveningSecond",
            "transaction-$eveningFirst",
            "transaction-$morning",
            "transaction-$untimedSecond",
            "transaction-$untimedFirst",
            "day-2026-03-09",
            "transaction-$noon",
            "day-2026-03-08",
            "transaction-$oldest",
        )
    }

    @Test
    fun `the picker lists each kind's parents under its heading with their children beneath`() {
        repository.givenAccount(current)
        launch()
        openTransactionForm()

        composeRule.onNodeWithTag("category-field").performClick()

        val categories = repository.savedCategories
        val parents = categories.filter { it.parentId == null }
        fun treeOf(kind: CategoryKind): List<String> = parents.filter { it.kind == kind }.flatMap { parent ->
            listOf("category-${parent.id}") + categories.filter { it.parentId == parent.id }.map { "category-${it.id}" }
        }
        assertEquals(11, parents.count { it.kind == CategoryKind.Spending })
        assertEquals(3, parents.count { it.kind == CategoryKind.Income })
        composeRule.onNodeWithTag("category-heading-Spending").assert(hasText("Spending"))
        composeRule.onNodeWithTag("category-heading-Income").assert(hasText("Income"))
        assertTopToBottom(
            "category-heading-Spending",
            *treeOf(CategoryKind.Spending).toTypedArray(),
            "category-heading-Income",
            *treeOf(CategoryKind.Income).toTypedArray(),
        )
        val child = categories.first { it.parentId != null }
        val parentLeft = composeRule.onNodeWithTag("category-${child.parentId}", useUnmergedTree = true)
            .onChildren().onFirst().fetchSemanticsNode().boundsInRoot.left
        val childLeft = composeRule.onNodeWithTag("category-${child.id}", useUnmergedTree = true)
            .onChildren().onFirst().fetchSemanticsNode().boundsInRoot.left
        assertTrue("a child's name is indented under its parent's", childLeft > parentLeft)
    }

    @Test
    fun `a parent and a child can both be picked and the last pick is shown`() {
        repository.givenAccount(current)
        val child = category(CategoryKind.Spending, child = true)
        val parent = repository.savedCategories.first { it.id == child.parentId }
        launch()
        openTransactionForm()

        pickCategory(child)
        composeRule.onNodeWithTag("category-field").assert(hasAnyDescendant(hasText(child.name)))
        pickCategory(parent)
        composeRule.onNodeWithTag("category-field").assert(hasAnyDescendant(hasText(parent.name)))
        composeRule.onNodeWithTag("amount").performTextInput("3")
        composeRule.onNodeWithText("Save").performClick()

        assertEquals(parent.id, repository.savedTransactions.single().categoryId)
    }

    @Test
    fun `an income in a spending category is a refund stored as a positive amount there`() {
        repository.givenAccount(current)
        val clothes = category(CategoryKind.Spending, child = true)
        launch()

        addTransaction("20", clothes, income = true)

        val refund = repository.savedTransactions.single()
        assertEquals(2_000L, refund.amountCents)
        assertEquals(clothes.id, refund.categoryId)
    }

    @Test
    fun `plus without an account opens the account form first, then the transaction form`() {
        launch()

        composeRule.onNodeWithContentDescription("Add").performClick()
        composeRule.onNodeWithText("New account").assertIsDisplayed()
        composeRule.onNodeWithTag("account-name").performTextInput("Cash")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("New transaction").assertIsDisplayed()
        composeRule.onNodeWithTag("account-field").assert(hasAnyDescendant(hasText("Cash")))
    }

    @Test
    fun `the note is stored trimmed, a typed Merchant name links that Merchant and the row shows it`() {
        repository.givenAccount(current)
        val food = category(CategoryKind.Spending, child = true)
        launch()
        openTransactionForm()

        composeRule.onNodeWithTag("amount").performTextInput("42,35")
        pickCategory(food)
        composeRule.onNodeWithTag("merchant").performTextInput(" Mercadona ")
        composeRule.onNodeWithTag("note").performTextInput("Weekly shop")
        composeRule.onNodeWithText("Save").performClick()

        val saved = repository.savedTransactions.single()
        assertEquals(mercadona().id, saved.merchantId)
        assertEquals("Weekly shop", saved.note)
        composeRule.onNodeWithTag("transaction-${saved.id}")
            .assert(hasText(food.name))
            .assert(hasText("Mercadona"))
    }

    @Test
    fun `with reduce motion saving still goes back to Home`() {
        composeRule.setContent {
            FinanceTrackerTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    FinanceApp(repository = repository, clock = clock)
                }
            }
        }

        composeRule.onNodeWithText("Add account").performClick()
        composeRule.onNodeWithTag("account-name").performTextInput("Cash")
        composeRule.onNodeWithText("Save").performClick()

        val id = repository.savedAccounts.single().id
        composeRule.onNodeWithTag("account-$id").assertIsDisplayed()
    }

    private fun food(): Category = repository.savedCategories.first { it.name == "Food" }

    private fun mercadona() = repository.savedMerchants.single { it.name == "Mercadona" }

    private fun givenMercadonaExpense(accountId: Long): Transaction {
        val expense = Transaction(
            accountId = accountId,
            categoryId = food().id,
            amountCents = -1_250,
            date = LocalDate.of(2026, 3, 10),
            merchantId = mercadona().id,
            note = "Weekly shop",
        )
        return expense.copy(id = repository.givenTransaction(expense))
    }

    @Test
    fun `tapping a transaction opens Edit transaction with every field prefilled`() {
        val accountId = repository.givenAccount(current)
        val expense = givenMercadonaExpense(accountId)
        launch()

        composeRule.onNodeWithTag("transaction-${expense.id}").performClick()

        composeRule.onNodeWithText("Edit transaction").assertIsDisplayed()
        composeRule.onNodeWithText("Expense").assertIsSelected()
        composeRule.onNodeWithText("Income").assertIsNotSelected()
        composeRule.onNodeWithTag("amount").assert(hasText("12,50"))
        composeRule.onNodeWithTag("account-field").assert(hasAnyDescendant(hasText("Current")))
        composeRule.onNodeWithTag("category-field").assert(hasAnyDescendant(hasText("Food")))
        composeRule.onNodeWithTag("date-field").assert(hasAnyDescendant(hasText("10 mar 2026")))
        composeRule.onNodeWithTag("merchant").assert(hasText("Mercadona"))
        composeRule.onNodeWithTag("note").assert(hasText("Weekly shop"))
    }

    @Test
    fun `saving an edited transaction rewrites it under the same id and updates Home`() {
        val accountId = repository.givenAccount(current)
        val expense = givenMercadonaExpense(accountId)
        launch()
        composeRule.onNodeWithTag("transaction-${expense.id}").performClick()

        composeRule.onNodeWithText("Income").performClick()
        composeRule.onNodeWithTag("amount").performTextReplacement("20,00")
        composeRule.onNodeWithText("Save").performClick()

        assertEquals(
            listOf(expense.copy(amountCents = 2_000, source = TransactionSource.Manual)),
            repository.savedTransactions,
        )
        composeRule.onNodeWithTag("transaction-${expense.id}").assert(hasText(euros("+20,00 €")))
        composeRule.onNodeWithTag("account-$accountId").assert(hasContentDescription(euros("120,00 €")))
        total().assert(hasContentDescription(euros("120,00 €")))
    }

    @Test
    fun `saving an edited transaction with an empty amount fails with Enter an amount`() {
        val accountId = repository.givenAccount(current)
        val expense = givenMercadonaExpense(accountId)
        launch()
        composeRule.onNodeWithTag("transaction-${expense.id}").performClick()

        composeRule.onNodeWithTag("amount").performTextClearance()
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Edit transaction").assertIsDisplayed()
        composeRule.onNodeWithText("Enter an amount").assertIsDisplayed()
        assertEquals(listOf(expense), repository.savedTransactions)
    }

    @Test
    fun `Delete in the edit form asks first, and Cancel changes nothing`() {
        val accountId = repository.givenAccount(current)
        val expense = givenMercadonaExpense(accountId)
        launch()
        composeRule.onNodeWithTag("transaction-${expense.id}").performClick()

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete this transaction?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Delete this transaction?").assertDoesNotExist()
        composeRule.onNodeWithText("Edit transaction").assertIsDisplayed()
        assertEquals(listOf(expense), repository.savedTransactions)
    }

    @Test
    fun `confirming Delete removes the transaction and Home drops its row and updates the balances`() {
        val accountId = repository.givenAccount(current)
        val expense = givenMercadonaExpense(accountId)
        launch()
        composeRule.onNodeWithTag("transaction-${expense.id}").performClick()

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        assertTrue("the transaction is gone", repository.savedTransactions.isEmpty())
        composeRule.onNodeWithTag("transaction-${expense.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("account-$accountId").assert(hasContentDescription(euros("100,00 €")))
        total().assert(hasContentDescription(euros("100,00 €")))
    }

    private val savings = Account(name = "Savings", type = AccountType.Savings, startingBalanceCents = 0)

    /** Picks [account] in the Account picker tagged [field]. */
    private fun pickAccount(field: String, account: Long) {
        composeRule.onNodeWithTag(field).performClick()
        composeRule.onNodeWithTag("$field-$account").performClick()
    }

    @Test
    fun `a transfer from Current to Savings writes two linked legs and keeps the total`() {
        val currentId = repository.givenAccount(current)
        val savingsId = repository.givenAccount(savings)
        launch()

        openFromPlus("add-transfer")
        composeRule.onNodeWithText("New transfer").assertIsDisplayed()
        pickAccount("from-account-field", currentId)
        pickAccount("to-account-field", savingsId)
        composeRule.onNodeWithTag("amount").performTextInput("30,00")
        composeRule.onNodeWithTag("note").performTextInput("Rainy day")
        composeRule.onNodeWithText("Save").performClick()

        val out = repository.savedTransactions.single { it.accountId == currentId }
        val into = repository.savedTransactions.single { it.accountId == savingsId }
        assertEquals(2, repository.savedTransactions.size)
        val transferId = out.transferId
        assertTrue("the legs carry a transfer id", transferId != null)
        val leg = Transaction(
            accountId = currentId,
            categoryId = null,
            amountCents = -3_000,
            date = LocalDate.of(2026, 3, 10),
            time = null,
            merchantId = null,
            note = "Rainy day",
            source = TransactionSource.Manual,
            transferId = transferId,
        )
        assertEquals(leg.copy(id = out.id), out)
        assertEquals(leg.copy(id = into.id, accountId = savingsId, amountCents = 3_000), into)
        composeRule.onNodeWithTag("account-$currentId").assert(hasContentDescription(euros("70,00 €")))
        composeRule.onNodeWithTag("account-$savingsId").assert(hasContentDescription(euros("30,00 €")))
        total().assert(hasContentDescription(euros("100,00 €")))
        composeRule.onNodeWithTag("transaction-${out.id}")
            .assert(hasText("Transfer"))
            .assert(hasText("To Savings"))
            .assert(hasText(euros("−30,00 €")))
        composeRule.onNodeWithTag("transaction-${into.id}")
            .assert(hasText("Transfer"))
            .assert(hasText("From Current"))
            .assert(hasText(euros("+30,00 €")))
        composeRule.onNodeWithText("Uncategorised").assertDoesNotExist()
    }

    private fun assertTransferFormStaysOpenWithError(error: String) {
        composeRule.onNodeWithText("New transfer").assertIsDisplayed()
        composeRule.onNodeWithText(error).assertIsDisplayed()
        assertTrue("nothing was saved", repository.savedTransactions.isEmpty())
    }

    @Test
    fun `a transfer to the account it comes from shows Pick a different account and saves nothing`() {
        val currentId = repository.givenAccount(current)
        repository.givenAccount(savings)
        launch()
        openFromPlus("add-transfer")

        pickAccount("to-account-field", currentId)
        composeRule.onNodeWithTag("amount").performTextInput("30,00")
        composeRule.onNodeWithText("Save").performClick()

        assertTransferFormStaysOpenWithError("Pick a different account")
    }

    @Test
    fun `a transfer with an empty amount shows Enter an amount and saves nothing`() {
        repository.givenAccount(current)
        repository.givenAccount(savings)
        launch()
        openFromPlus("add-transfer")

        composeRule.onNodeWithText("Save").performClick()

        assertTransferFormStaysOpenWithError("Enter an amount")
        composeRule.onNodeWithText("Pick a different account").assertDoesNotExist()
    }

    @Test
    fun `a transfer of zero shows Enter an amount and saves nothing`() {
        repository.givenAccount(current)
        repository.givenAccount(savings)
        launch()
        openFromPlus("add-transfer")

        composeRule.onNodeWithTag("amount").performTextInput("0,00")
        composeRule.onNodeWithText("Save").performClick()

        assertTransferFormStaysOpenWithError("Enter an amount")
    }

    @Test
    fun `plus offers Transfer only with two accounts`() {
        repository.givenAccount(current)
        launch()

        composeRule.onNodeWithContentDescription("Add").performClick()

        composeRule.onNodeWithTag("add-transaction").assertIsEnabled()
        composeRule.onNodeWithTag("add-transfer").assertIsNotEnabled()
    }

    /** A Transfer of 30,00 from [from] to [to] on 2026-03-08 with a note; returns its legs, out first. */
    private fun givenTransfer(from: Long, to: Long): Pair<Transaction, Transaction> {
        val transferId = runBlocking {
            repository.saveTransfer(
                Transfer(
                    fromAccountId = from,
                    toAccountId = to,
                    amountCents = 3_000,
                    date = LocalDate.of(2026, 3, 8),
                    note = "Rainy day",
                ),
            )
        }
        val legs = repository.savedTransactions.filter { it.transferId == transferId }
        return legs.single { it.amountCents < 0 } to legs.single { it.amountCents > 0 }
    }

    @Test
    fun `tapping either leg of a transfer opens Edit transfer prefilled`() {
        val currentId = repository.givenAccount(current)
        val savingsId = repository.givenAccount(savings)
        val (out, into) = givenTransfer(currentId, savingsId)
        launch()

        listOf(out, into).forEach { leg ->
            composeRule.onNodeWithTag("transaction-${leg.id}").performClick()

            composeRule.onNodeWithText("Edit transfer").assertIsDisplayed()
            composeRule.onNodeWithTag("from-account-field").assert(hasAnyDescendant(hasText("Current")))
            composeRule.onNodeWithTag("to-account-field").assert(hasAnyDescendant(hasText("Savings")))
            composeRule.onNodeWithTag("amount").assert(hasText("30,00"))
            composeRule.onNodeWithTag("date-field").assert(hasAnyDescendant(hasText("8 mar 2026")))
            composeRule.onNodeWithTag("note").assert(hasText("Rainy day"))
            composeRule.onNodeWithContentDescription("Back").performClick()
            composeRule.onNodeWithTag("balance").assertIsDisplayed()
        }
    }

    @Test
    fun `saving an edited transfer rewrites both legs under the same ids`() {
        val currentId = repository.givenAccount(current)
        val savingsId = repository.givenAccount(savings)
        val (out, into) = givenTransfer(currentId, savingsId)
        launch()
        composeRule.onNodeWithTag("transaction-${into.id}").performClick()

        composeRule.onNodeWithTag("amount").performTextReplacement("45,00")
        composeRule.onNodeWithText("Save").performClick()

        assertEquals(
            setOf(out.copy(amountCents = -4_500), into.copy(amountCents = 4_500)),
            repository.savedTransactions.toSet(),
        )
        composeRule.onNodeWithTag("account-$currentId").assert(hasContentDescription(euros("55,00 €")))
        composeRule.onNodeWithTag("account-$savingsId").assert(hasContentDescription(euros("45,00 €")))
    }

    @Test
    fun `deleting a transfer asks once and removes both legs`() {
        val currentId = repository.givenAccount(current)
        val savingsId = repository.givenAccount(savings)
        val (out, into) = givenTransfer(currentId, savingsId)
        launch()
        composeRule.onNodeWithTag("transaction-${out.id}").performClick()

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete this transfer?").assertIsDisplayed()
        composeRule.onNodeWithText("Both sides of the transfer will be deleted.").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        assertTrue("both legs are gone", repository.savedTransactions.isEmpty())
        composeRule.onNodeWithTag("transaction-${out.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("transaction-${into.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("account-$currentId").assert(hasContentDescription(euros("100,00 €")))
        composeRule.onNodeWithTag("account-$savingsId").assert(hasContentDescription(euros("0,00 €")))
    }

    @Test
    fun `tapping an account tile opens its detail with its balance and only its transactions by day`() {
        val currentId = repository.givenAccount(current)
        val cashId = repository.givenAccount(Account(name = "Cash", type = AccountType.Cash, startingBalanceCents = 0))
        val foodId = food().id
        fun given(accountId: Long, cents: Long, day: Int) = repository.givenTransaction(
            Transaction(accountId = accountId, categoryId = foodId, amountCents = cents, date = LocalDate.of(2026, 3, day)),
        )
        val older = given(currentId, -500, day = 9)
        val newer = given(currentId, -250, day = 10)
        val cashOnly = given(cashId, -1_000, day = 10)
        val (out, into) = givenTransfer(currentId, cashId)
        launch()

        composeRule.onNodeWithTag("account-$currentId").performClick()

        composeRule.onNodeWithTag("account-balance")
            .assertIsDisplayed()
            .assert(hasText("Current"))
            .assert(hasContentDescription(euros("62,50 €")))
        composeRule.onNodeWithTag("transaction-$cashOnly").assertDoesNotExist()
        composeRule.onNodeWithTag("transaction-${into.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("transaction-${out.id}").assert(hasText("To Cash"))
        assertTopToBottom(
            "account-balance",
            "day-2026-03-10",
            "transaction-$newer",
            "day-2026-03-09",
            "transaction-$older",
            "day-2026-03-08",
            "transaction-${out.id}",
        )
    }

    @Test
    fun `a row on the account detail opens the edit form, and back returns to Home`() {
        val currentId = repository.givenAccount(current)
        val expense = givenMercadonaExpense(currentId)
        launch()
        composeRule.onNodeWithTag("account-$currentId").performClick()

        composeRule.onNodeWithTag("transaction-${expense.id}").performClick()
        composeRule.onNodeWithText("Edit transaction").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithTag("account-balance").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        total().assertIsDisplayed()
        composeRule.onNodeWithTag("account-balance").assertDoesNotExist()
    }

    @Test
    fun `an account without transactions shows No transactions yet on its detail`() {
        val currentId = repository.givenAccount(current)
        launch()

        composeRule.onNodeWithTag("account-$currentId").performClick()

        composeRule.onNodeWithTag("account-balance").assert(hasContentDescription(euros("100,00 €")))
        composeRule.onNodeWithText("No transactions yet").assertIsDisplayed()
    }

    @Test
    fun `with reduce motion an account tile still opens its detail and back returns`() {
        val currentId = repository.givenAccount(current)
        composeRule.setContent {
            FinanceTrackerTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    FinanceApp(repository = repository, clock = clock)
                }
            }
        }

        composeRule.onNodeWithTag("account-$currentId").performClick()
        composeRule.onNodeWithTag("account-balance").assert(hasText("Current"))
        composeRule.onNodeWithContentDescription("Back").performClick()

        total().assertIsDisplayed()
    }

    @Test
    fun `an account without transactions can be deleted from its detail after a dialog`() {
        val currentId = repository.givenAccount(current)
        val savingsId = repository.givenAccount(savings)
        launch()
        composeRule.onNodeWithTag("account-$savingsId").performClick()

        composeRule.onNodeWithText("Archive account").assertDoesNotExist()
        composeRule.onNodeWithText("Delete account").performClick()
        composeRule.onNodeWithText("Delete this account?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(listOf(currentId), repository.savedAccounts.map { it.id })
        total().assertIsDisplayed()
        composeRule.onNodeWithTag("account-$savingsId").assertDoesNotExist()
        composeRule.onNodeWithTag("account-$currentId").assertIsDisplayed()
    }

    @Test
    fun `cancelling the delete account dialog keeps the account`() {
        val savingsId = repository.givenAccount(savings)
        launch()
        composeRule.onNodeWithTag("account-$savingsId").performClick()

        composeRule.onNodeWithText("Delete account").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Delete this account?").assertDoesNotExist()
        assertEquals(listOf(savingsId), repository.savedAccounts.map { it.id })
        composeRule.onNodeWithTag("account-balance").assertIsDisplayed()
    }

    @Test
    fun `an account with transactions is archived from its detail, leaves Home and still counts`() {
        val currentId = repository.givenAccount(current)
        val savingsId = repository.givenAccount(savings)
        val expense = givenMercadonaExpense(currentId)
        launch()
        composeRule.onNodeWithTag("account-$currentId").performClick()

        composeRule.onNodeWithText("Delete account").assertDoesNotExist()
        composeRule.onNodeWithText("Archive account").performClick()
        composeRule.onNodeWithText("Archive this account?").assertIsDisplayed()
        composeRule.onNodeWithText("It leaves Home and the pickers. Its transactions stay.").assertIsDisplayed()
        composeRule.onNodeWithText("Archive").performClick()

        assertTrue("stored as archived", repository.savedAccounts.single { it.id == currentId }.archived)
        assertEquals(listOf(expense), repository.savedTransactions)
        total().assert(hasContentDescription(euros("87,50 €")))
        composeRule.onNodeWithTag("account-$currentId").assertDoesNotExist()
        composeRule.onNodeWithTag("account-$savingsId").assertIsDisplayed()
        composeRule.onNodeWithTag("transaction-${expense.id}").assertIsDisplayed()
    }

    @Test
    fun `an archived account is missing from the transaction and transfer pickers`() {
        val archivedId = repository.givenAccount(current.copy(archived = true))
        val savingsId = repository.givenAccount(savings)
        val cashId = repository.givenAccount(Account(name = "Cash", type = AccountType.Cash, startingBalanceCents = 0))
        launch()

        openFromPlus("add-transaction")
        composeRule.onNodeWithTag("account-field").assert(hasAnyDescendant(hasText("Savings")))
        composeRule.onNodeWithTag("account-field").performClick()
        composeRule.onNodeWithTag("account-field-$savingsId").assertIsDisplayed()
        composeRule.onNodeWithTag("account-field-$archivedId").assertDoesNotExist()
        composeRule.onNodeWithTag("account-field-$savingsId").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        openFromPlus("add-transfer")
        composeRule.onNodeWithTag("from-account-field").assert(hasAnyDescendant(hasText("Savings")))
        composeRule.onNodeWithTag("to-account-field").assert(hasAnyDescendant(hasText("Cash")))
        composeRule.onNodeWithTag("from-account-field").performClick()
        composeRule.onNodeWithTag("from-account-field-$cashId").assertIsDisplayed()
        composeRule.onNodeWithTag("from-account-field-$archivedId").assertDoesNotExist()
    }

    @Test
    fun `Transfer is disabled when only one account is not archived`() {
        repository.givenAccount(current.copy(archived = true))
        repository.givenAccount(savings)
        launch()

        composeRule.onNodeWithContentDescription("Add").performClick()

        composeRule.onNodeWithTag("add-transfer").assertIsNotEnabled()
    }

    @Test
    fun `a transaction of an archived account still shows that account in its edit form`() {
        val archivedId = repository.givenAccount(current)
        repository.givenAccount(savings)
        val expense = givenMercadonaExpense(archivedId)
        runBlocking { repository.archiveAccount(archivedId) }
        launch()

        composeRule.onNodeWithTag("transaction-${expense.id}").performClick()

        composeRule.onNodeWithTag("account-field").assert(hasAnyDescendant(hasText("Current")))
    }
}
