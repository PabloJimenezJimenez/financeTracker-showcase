package com.pablojimenez.financetracker.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.CategoryKind
import com.pablojimenez.financetracker.domain.FakeFinanceRepository
import com.pablojimenez.financetracker.domain.Metric
import com.pablojimenez.financetracker.domain.Transaction
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings and the Categories screen: the gear on Home, the tree, and its dialogs. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2000dp")
class FinanceAppCategoriesTest {
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

    private fun row(category: Category) = composeRule.onNodeWithTag("category-row-${category.id}")

    private fun top(category: Category): Float = row(category).fetchSemanticsNode().boundsInRoot.top

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private fun openCategories() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithTag("settings-categories").performClick()
    }

    private fun openDialog(category: Category) {
        composeRule.onNodeWithTag("categories").performScrollToNode(hasTestTag("category-row-${category.id}"))
        row(category).performClick()
    }

    private fun typeName(name: String) {
        composeRule.onNodeWithTag("category-name").performTextClearance()
        composeRule.onNodeWithTag("category-name").performTextInput(name)
    }

    private fun save() {
        composeRule.onNodeWithTag("category-save").performClick()
    }

    private fun back() {
        composeRule.onNodeWithContentDescription("Back").performClick()
    }

    @Test
    fun `the gear on Home opens Settings, whose Categories row shows the tree under Spending and Income`() {
        launch()

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-categories").assert(hasText("Categories")).performClick()

        val spending = composeRule.onNodeWithText("Spending").assert(isHeading)
        assertTrue("Groceries is under Spending", spending.fetchSemanticsNode().boundsInRoot.top < top(category("Groceries")))
        row(category("Groceries")).assert(hasText("Groceries"))
        row(category("Food")).assert(hasText("Food"))
        assertTrue("Food follows Groceries", top(category("Groceries")) < top(category("Food")))
        row(category("Salary")).performScrollTo().assert(hasText("Salary"))
        val income = composeRule.onNodeWithText("Income").assert(isHeading)
        assertTrue("Salary is under Income", income.fetchSemanticsNode().boundsInRoot.top < top(category("Salary")))
    }

    @Test
    fun `renaming a Category shows the new name in the tree and in the transaction form's picker`() {
        repository.givenAccount(Account(name = "Current", type = AccountType.Current, startingBalanceCents = 0))
        val food = category("Food")
        launch()
        openCategories()

        openDialog(food)
        typeName("Supermarket")
        save()

        row(food).assert(hasText("Supermarket"))
        assertEquals("Supermarket", repository.savedCategories.single { it.id == food.id }.name)
        back()
        back()
        composeRule.onNodeWithContentDescription("Add").performClick()
        composeRule.onNodeWithTag("add-transaction").performClick()
        composeRule.onNodeWithTag("category-field").performClick()
        composeRule.onNodeWithTag("category-${food.id}").performScrollTo().assert(hasText("Supermarket"))
    }

    @Test
    fun `a blank name, or one a sibling has ignoring case and spaces, shows an error and saves nothing`() {
        val before = repository.savedCategories
        launch()
        openCategories()
        openDialog(category("Food"))

        typeName("   ")
        save()
        composeRule.onNodeWithText("Enter a name").assertIsDisplayed()

        typeName(" household ")
        save()
        composeRule.onNodeWithText("There is already a category with this name here").assertIsDisplayed()

        assertEquals(before, repository.savedCategories)
    }

    private fun addCategory(name: String, placeTag: String) {
        composeRule.onNodeWithContentDescription("Add category").performClick()
        typeName(name)
        composeRule.onNodeWithTag("category-place").performClick()
        composeRule.onNodeWithTag(placeTag).performClick()
        save()
    }

    @Test
    fun `Add category puts a top-level one last among its kind's parents and a child under its parent`() {
        launch()
        openCategories()

        composeRule.onNodeWithContentDescription("Add category").performClick()
        composeRule.onNodeWithTag("category-place").assert(hasText("Spending (top level)"))
        composeRule.onNodeWithText("Cancel").performClick()
        addCategory("Pets", placeTag = "place-Spending")
        val pets = category("Pets")
        addCategory("Vet", placeTag = "place-${pets.id}")

        val vet = category("Vet")
        assertEquals(Category(pets.id, "Pets", CategoryKind.Spending), pets)
        assertEquals(Category(vet.id, "Vet", CategoryKind.Spending, parentId = pets.id), vet)
        val spendingParents = repository.savedCategories.filter { it.parentId == null && it.kind == CategoryKind.Spending }
        assertEquals(pets, spendingParents.last())
        row(vet).performScrollTo().assert(hasText("Vet"))
        assertTrue("Vet is under Pets", top(pets) < top(vet))
        assertTrue("Other comes before Pets", top(category("Other")) < top(pets))
    }

    @Test
    fun `Move up on Coffee puts it before Restaurants`() {
        launch()
        openCategories()

        openDialog(category("Coffee"))
        composeRule.onNodeWithText("Move up").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertTrue(top(category("Coffee")) < top(category("Restaurants")))
        val eatingOut = category("Eating out")
        assertEquals(
            listOf("Coffee", "Restaurants", "Delivery"),
            repository.savedCategories.filter { it.parentId == eatingOut.id }.map { it.name },
        )
    }

    @Test
    fun `Move up is disabled on the first sibling and Move down on the last`() {
        launch()
        openCategories()

        openDialog(category("Restaurants"))
        composeRule.onNodeWithText("Move up").assertIsNotEnabled()
        composeRule.onNodeWithText("Move down").assertIsEnabled()
        composeRule.onNodeWithText("Cancel").performClick()

        openDialog(category("Delivery"))
        composeRule.onNodeWithText("Move up").assertIsEnabled()
        composeRule.onNodeWithText("Move down").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").performClick()

        openDialog(category("Housing"))
        composeRule.onNodeWithText("Move up").assertIsNotEnabled()
    }

    private fun givenExpense(category: Category): Long {
        val accountId = repository.savedAccounts.firstOrNull()?.id
            ?: repository.givenAccount(Account(name = "Current", type = AccountType.Current, startingBalanceCents = 0))
        return repository.givenTransaction(
            Transaction(accountId = accountId, categoryId = category.id, amountCents = -1_000, date = LocalDate.of(2026, 3, 10)),
        )
    }

    private fun startDelete(category: Category) {
        openDialog(category)
        composeRule.onNodeWithTag("category-delete").performClick()
    }

    @Test
    fun `deleting Food moves its Transactions to Groceries, preselected among the other Spending Categories`() {
        val food = category("Food")
        val groceries = category("Groceries")
        val coffee = category("Coffee")
        val first = givenExpense(food)
        val second = givenExpense(food)
        val filter = repository.givenMetric(Metric(name = "Food and coffee", categoryIds = setOf(food.id, coffee.id)))
        launch()
        openCategories()

        startDelete(food)
        composeRule.onNodeWithText("Move its 2 transactions to").assertIsDisplayed()
        composeRule.onNodeWithTag("move-to-field").assert(hasAnyDescendant(hasText("Groceries")))
        composeRule.onNodeWithTag("move-to-field").performClick()
        composeRule.onNodeWithTag("category-${coffee.id}").assertExists()
        composeRule.onNodeWithTag("category-${food.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("category-${category("Salary").id}").assertDoesNotExist()
        composeRule.onNodeWithTag("new-category").assertDoesNotExist()
        composeRule.onNodeWithTag("category-${groceries.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("delete-category-confirm").performClick()

        assertTrue(repository.savedCategories.none { it.id == food.id })
        assertEquals(setOf(groceries.id, coffee.id), repository.savedMetrics.single { it.id == filter }.categoryIds)
        back()
        back()
        composeRule.onNodeWithTag("transaction-$first").performScrollTo().assert(hasText("Groceries"))
        composeRule.onNodeWithTag("transaction-$second").performScrollTo().assert(hasText("Groceries"))
    }

    @Test
    fun `deleting a Category with no Transactions asks a plain confirmation`() {
        val taxi = category("Taxi")
        launch()
        openCategories()

        startDelete(taxi)
        composeRule.onNodeWithText("Delete this category?").assertIsDisplayed()
        composeRule.onNodeWithTag("move-to-field").assertDoesNotExist()
        composeRule.onNodeWithText("Delete").performClick()

        assertTrue(repository.savedCategories.none { it.id == taxi.id })
        row(taxi).assertDoesNotExist()
    }

    @Test
    fun `deleting a parent with children asks to delete its subcategories first and deletes nothing`() {
        val before = repository.savedCategories
        launch()
        openCategories()

        startDelete(category("Groceries"))
        composeRule.onNodeWithText("Delete its subcategories first").assertIsDisplayed()
        composeRule.onNodeWithText("OK").performClick()

        assertEquals(before, repository.savedCategories)
    }

    @Test
    fun `a top-level Category with Transactions has no preselected target and Delete waits for one`() {
        val other = category("Other")
        val education = category("Education")
        val expense = givenExpense(other)
        launch()
        openCategories()

        startDelete(other)
        composeRule.onNodeWithText("Move its 1 transaction to").assertIsDisplayed()
        composeRule.onNodeWithTag("delete-category-confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("move-to-field").performClick()
        composeRule.onNodeWithTag("category-${education.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("move-to-field").assert(hasAnyDescendant(hasText("Education")))
        composeRule.onNodeWithTag("delete-category-confirm").assertIsEnabled().performClick()

        assertTrue(repository.savedCategories.none { it.id == other.id })
        assertEquals(education.id, repository.savedTransactions.single { it.id == expense }.categoryId)
    }

    @Test
    fun `New category in the transaction form's picker adds one, selects it, and the Transaction is saved in it`() {
        repository.givenAccount(Account(name = "Current", type = AccountType.Current, startingBalanceCents = 0))
        launch()
        composeRule.onNodeWithContentDescription("Add").performClick()
        composeRule.onNodeWithTag("add-transaction").performClick()
        composeRule.onNodeWithTag("amount").performTextInput("12")

        composeRule.onNodeWithTag("category-field").performClick()
        composeRule.onNodeWithTag("new-category").performScrollTo().assert(hasText("New category")).performClick()
        composeRule.onNodeWithTag("category-place").assert(hasText("Spending (top level)"))
        typeName("Pets")
        save()

        val pets = category("Pets")
        assertEquals(Category(pets.id, "Pets", CategoryKind.Spending), pets)
        composeRule.onNodeWithTag("category-list").assertDoesNotExist()
        composeRule.onNodeWithTag("category-field").assert(hasAnyDescendant(hasText("Pets")))
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(pets.id, repository.savedTransactions.single().categoryId)
    }

    @Test
    @Config(qualifiers = "w411dp-h480dp")
    fun `at 200 percent font scale on a short screen the edit dialog keeps Save and every action reachable`() {
        RuntimeEnvironment.setFontScale(2f)
        launch()
        openCategories()

        openDialog(category("Restaurants"))

        composeRule.onNodeWithTag("category-save").assertIsDisplayed()
        val field = composeRule.onNodeWithTag("category-name").fetchSemanticsNode().boundsInWindow
        listOf("Move up", "Move down", "Delete").forEach { label ->
            val action = composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            val bounds = action.fetchSemanticsNode().boundsInWindow
            assertTrue("$label fits the dialog's width", bounds.left >= field.left && bounds.right <= field.right)
        }
        composeRule.onNodeWithTag("category-save").assertIsDisplayed()
    }
}
