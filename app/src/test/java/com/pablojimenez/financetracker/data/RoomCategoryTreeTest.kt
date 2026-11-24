package com.pablojimenez.financetracker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.CategoryKind
import com.pablojimenez.financetracker.domain.Metric
import com.pablojimenez.financetracker.domain.Transaction
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The editable Category tree in Room: add, rename, reorder and delete with a move (ADR 0002, 0003). */
@RunWith(AndroidJUnit4::class)
class RoomCategoryTreeTest {
    private val database = FinanceDatabase.create(
        ApplicationProvider.getApplicationContext<Context>(),
        name = null,
    )
    private val repository = RoomFinanceRepository(database)

    @After
    fun close() {
        database.close()
    }

    private fun categories(): List<Category> = runBlocking { repository.categories.first() }

    private fun category(name: String): Category = categories().single { it.name == name }

    private fun names(categories: List<Category>): List<String> = categories.map { it.name }

    private fun parentNames(kind: CategoryKind): List<String> =
        names(categories().filter { it.parentId == null && it.kind == kind })

    private fun childNames(parent: String): List<String> =
        names(categories().filter { it.parentId == category(parent).id })

    @Test
    fun `a new top-level Category is trimmed and goes last among the parents of its kind`() = runBlocking {
        val id = repository.saveCategory(Category(0, " Pets ", CategoryKind.Spending))

        assertEquals(Category(id, "Pets", CategoryKind.Spending), category("Pets"))
        assertEquals(
            listOf(
                "Housing", "Groceries", "Eating out", "Transport", "Shopping", "Health", "Leisure",
                "Bills & fees", "Gifts & donations", "Education", "Other", "Pets",
            ),
            parentNames(CategoryKind.Spending),
        )
        val all = names(categories())
        assertEquals("Pets comes before the Income parents", all.indexOf("Salary") - 1, all.indexOf("Pets"))
    }

    @Test
    fun `a new child goes last among its parent's children and takes the parent's kind`() = runBlocking {
        val groceries = category("Groceries")

        val id = repository.saveCategory(Category(0, "Drinks", CategoryKind.Income, parentId = groceries.id))

        assertEquals(Category(id, "Drinks", CategoryKind.Spending, groceries.id), category("Drinks"))
        assertEquals(listOf("Food", "Household", "Personal care", "Drinks"), childNames("Groceries"))
        val all = names(categories())
        assertEquals("Drinks comes right before the next parent", all.indexOf("Eating out") - 1, all.indexOf("Drinks"))
    }

    @Test
    fun `saving an existing id renames it, keeping its id, parent, kind and place`() = runBlocking {
        val food = category("Food")
        val before = categories()

        val id = repository.saveCategory(Category(food.id, " Supermarket ", CategoryKind.Income, parentId = null))

        assertEquals(food.id, id)
        assertEquals(before.map { if (it.id == food.id) it.copy(name = "Supermarket") else it }, categories())
    }

    @Test
    fun `moving a child up swaps it with the sibling before it`() = runBlocking {
        val before = names(categories())

        repository.moveCategory(category("Coffee").id, up = true)

        assertEquals(listOf("Coffee", "Restaurants", "Delivery"), childNames("Eating out"))
        assertEquals(
            before.map { if (it == "Coffee") "Restaurants" else if (it == "Restaurants") "Coffee" else it },
            names(categories()),
        )
    }

    @Test
    fun `the first sibling moving up and the last one moving down stay where they are`() = runBlocking {
        val before = categories()

        repository.moveCategory(category("Restaurants").id, up = true)
        repository.moveCategory(category("Delivery").id, up = false)
        repository.moveCategory(category("Housing").id, up = true)
        repository.moveCategory(category("Other").id, up = false)
        repository.moveCategory(category("Salary").id, up = true)
        repository.moveCategory(category("Other income").id, up = false)

        assertEquals(before, categories())
    }

    @Test
    fun `a parent moves among the parents of its kind and takes its children along`() = runBlocking {
        repository.moveCategory(category("Groceries").id, up = false)

        val all = names(categories())
        assertEquals(
            listOf(
                "Housing", "Rent", "Utilities", "Internet & phone",
                "Eating out", "Restaurants", "Coffee", "Delivery",
                "Groceries", "Food", "Household", "Personal care",
                "Transport",
            ),
            all.take(13),
        )

        repository.moveCategory(category("Salary").id, up = false)
        assertEquals(listOf("Interest & dividends", "Salary", "Other income"), parentNames(CategoryKind.Income))
        assertEquals("Spending parents stay first", "Other", parentNames(CategoryKind.Spending).last())
    }

    @Test
    fun `ids are never reused after a delete`() = runBlocking {
        val highest = categories().maxOf { it.id }
        val pets = repository.saveCategory(Category(0, "Pets", CategoryKind.Spending))
        assertEquals(highest + 1, pets)

        assertEquals(true, repository.deleteCategory(pets, moveTo = null))
        val vet = repository.saveCategory(Category(0, "Vet", CategoryKind.Spending))

        assertEquals(highest + 2, vet)
    }

    private fun givenExpense(category: Category): Long = runBlocking {
        val accountId = repository.accounts.first().firstOrNull()?.id
            ?: repository.addAccount(Account(name = "Current", type = AccountType.Current, startingBalanceCents = 0))
        repository.addTransaction(
            Transaction(accountId = accountId, categoryId = category.id, amountCents = -1_000, date = LocalDate.of(2026, 3, 10)),
        )
    }

    private fun givenMetric(vararg categories: Category): Long = runBlocking {
        repository.saveMetric(Metric(name = "Filter", categoryIds = categories.map { it.id }.toSet()))
    }

    private fun metric(id: Long): Metric = runBlocking { repository.metrics.first().single { it.id == id } }

    private fun mercadonaDefault(): Long? = runBlocking {
        repository.merchants.first().single { it.name == "Mercadona" }.defaultCategoryId
    }

    @Test
    fun `deleting a Category moves its Transactions and Metric filters to the target and clears Merchant defaults`() = runBlocking {
        val food = category("Food")
        val groceries = category("Groceries")
        val coffee = category("Coffee")
        val household = category("Household")
        assertEquals("Mercadona starts in Food", food.id, mercadonaDefault())
        val first = givenExpense(food)
        val second = givenExpense(food)
        val other = givenExpense(household)
        val foodAndCoffee = givenMetric(food, coffee)
        val groceriesAndFood = givenMetric(groceries, food)
        val untouched = givenMetric(household)

        assertTrue(repository.deleteCategory(food.id, moveTo = groceries.id))

        val categoryOf = repository.transactions.first().associate { it.id to it.categoryId }
        assertEquals(mapOf(first to groceries.id, second to groceries.id, other to household.id), categoryOf)
        assertEquals(setOf(groceries.id, coffee.id), metric(foodAndCoffee).categoryIds)
        assertEquals(setOf(groceries.id), metric(groceriesAndFood).categoryIds)
        assertEquals(setOf(household.id), metric(untouched).categoryIds)
        assertNull(mercadonaDefault())
        assertTrue(categories().none { it.id == food.id })
    }

    @Test
    fun `a Category with children, or with Transactions and no valid target, is not deleted`() = runBlocking {
        val food = category("Food")
        val groceries = category("Groceries")
        val salary = category("Salary")
        givenExpense(food)
        givenMetric(food)
        val before = listOf(categories(), repository.transactions.first(), repository.metrics.first(), repository.merchants.first())

        assertFalse("a parent with children", repository.deleteCategory(groceries.id, moveTo = category("Housing").id))
        assertFalse("Transactions and no target", repository.deleteCategory(food.id, moveTo = null))
        assertFalse("Transactions moving to itself", repository.deleteCategory(food.id, moveTo = food.id))
        assertFalse("Transactions moving to the other kind", repository.deleteCategory(food.id, moveTo = salary.id))

        val after = listOf(categories(), repository.transactions.first(), repository.metrics.first(), repository.merchants.first())
        assertEquals(before, after)
    }

    @Test
    fun `a Category without Transactions is deleted with no target and Metric filters keep its id`() = runBlocking {
        val taxi = category("Taxi")
        val filter = givenMetric(taxi, category("Fuel"))

        assertTrue(repository.deleteCategory(taxi.id, moveTo = null))

        assertTrue(categories().none { it.id == taxi.id })
        assertEquals(setOf(taxi.id, category("Fuel").id), metric(filter).categoryIds)
    }

    @Test
    fun `renaming an id no Category has fails and changes nothing`() {
        val before = categories()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.saveCategory(Category(999, "Ghost", CategoryKind.Spending)) }
        }

        assertEquals(before, categories())
    }

    @Test
    fun `a Category without Transactions deleted with a target hands its Metric filters to the target`() = runBlocking {
        val taxi = category("Taxi")
        val fuel = category("Fuel")
        val filter = givenMetric(taxi)

        assertTrue(repository.deleteCategory(taxi.id, moveTo = fuel.id))

        assertEquals(setOf(fuel.id), metric(filter).categoryIds)
        assertFalse("a target of the other kind is refused", repository.deleteCategory(fuel.id, moveTo = category("Salary").id))
    }
}
