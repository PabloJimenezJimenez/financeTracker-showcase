package com.pablojimenez.financetracker.domain

/** Whether a Category holds spending or income. Children share their parent's kind. */
enum class CategoryKind { Spending, Income }

/**
 * What money was spent on or came from. Categories form a tree two levels deep: a parent has
 * [parentId] null; a child points at its parent and has the same [kind].
 */
data class Category(
    val id: Long,
    val name: String,
    val kind: CategoryKind,
    val parentId: Long? = null,
)

/** A top-level Category of the starter set, with the names of its children. */
data class StarterCategory(
    val name: String,
    val kind: CategoryKind,
    val children: List<String> = emptyList(),
)

/**
 * The Categories a new database starts with, in the order the picker shows them (issue #12).
 * They are the owner's data from then on, so the names are not translated.
 */
val StarterCategories: List<StarterCategory> = listOf(
    StarterCategory("Housing", CategoryKind.Spending, listOf("Rent", "Utilities", "Internet & phone")),
    StarterCategory("Groceries", CategoryKind.Spending, listOf("Food", "Household", "Personal care")),
    StarterCategory("Eating out", CategoryKind.Spending, listOf("Restaurants", "Coffee", "Delivery")),
    StarterCategory("Transport", CategoryKind.Spending, listOf("Public transport", "Fuel", "Taxi")),
    StarterCategory("Shopping", CategoryKind.Spending, listOf("Clothes", "Electronics", "Home")),
    StarterCategory("Health", CategoryKind.Spending, listOf("Pharmacy", "Doctor", "Sport")),
    StarterCategory("Leisure", CategoryKind.Spending, listOf("Subscriptions", "Travel", "Events")),
    StarterCategory("Bills & fees", CategoryKind.Spending, listOf("Bank fees", "Insurance", "Taxes")),
    StarterCategory("Gifts & donations", CategoryKind.Spending),
    StarterCategory("Education", CategoryKind.Spending),
    StarterCategory("Other", CategoryKind.Spending),
    StarterCategory("Salary", CategoryKind.Income),
    StarterCategory("Interest & dividends", CategoryKind.Income),
    StarterCategory("Other income", CategoryKind.Income),
)

/**
 * [StarterCategories] as a new database is seeded with them: ids from 1 in the picker's order,
 * each parent right before its children. A seeded Category keeps its id for good, whatever the
 * owner renames it to, so the id is how the app recognises a starter Category.
 */
val SeededStarterCategories: List<Category> = buildList {
    StarterCategories.forEach { parent ->
        val parentId = size + 1L
        add(Category(parentId, parent.name, parent.kind))
        parent.children.forEach { child -> add(Category(size + 1L, child, parent.kind, parentId)) }
    }
}

/** The seed id of the top-level starter Category "Groceries". */
val GroceriesSeedId: Long = SeededStarterCategories.first { it.parentId == null && it.name == "Groceries" }.id

/** A top-level Category with its children. */
data class CategoryBranch(val parent: Category, val children: List<Category>)

/** These Categories as their two-level tree, keeping their order. */
fun List<Category>.branches(): List<CategoryBranch> =
    filter { it.parentId == null }.map { parent ->
        CategoryBranch(parent, filter { it.parentId == parent.id })
    }

/**
 * These Categories with [category] added, in tree order (see [inTreeOrder]): a top-level one last
 * among the parents of its kind, a child last among its parent's children.
 */
fun List<Category>.withAdded(category: Category): List<Category> {
    val branches = branches()
    val added = if (category.parentId == null) {
        branches + CategoryBranch(category, emptyList())
    } else {
        branches.map { if (it.parent.id == category.parentId) it.copy(children = it.children + category) else it }
    }
    return added.inTreeOrder()
}

/**
 * These Categories, in tree order (see [inTreeOrder]), with the Category [id] swapped with the
 * sibling before it ([up]) or after it: a child among its parent's children, a parent among the
 * parents of its kind, with its children. The first sibling moving up, or the last moving down,
 * stays put.
 */
fun List<Category>.withMoved(id: Long, up: Boolean): List<Category> {
    val moving = firstOrNull { it.id == id } ?: return this
    val branches = branches()
    val moved = if (moving.parentId == null) {
        val ofKind = branches.filter { it.parent.kind == moving.kind }
        val swapped = ofKind.swapped(ofKind.indexOfFirst { it.parent.id == id }, up)
        swapped + branches.filter { it.parent.kind != moving.kind }
    } else {
        branches.map { branch ->
            if (branch.parent.id != moving.parentId) return@map branch
            branch.copy(children = branch.children.swapped(branch.children.indexOf(moving), up))
        }
    }
    return moved.inTreeOrder()
}

/** Whether [withMoved] would change anything: the Category [id] has a sibling on that side. */
fun List<Category>.canMove(id: Long, up: Boolean): Boolean = withMoved(id, up) != branches().inTreeOrder()

/** This list with the item at [index] swapped with its neighbour before ([up]) or after it, if any. */
private fun <T> List<T>.swapped(index: Int, up: Boolean): List<T> {
    val other = if (up) index - 1 else index + 1
    if (index !in indices || other !in indices) return this
    return toMutableList().apply {
        this[index] = this@swapped[other]
        this[other] = this@swapped[index]
    }
}

/** The tree as one list: the Spending branches, then the Income ones, each parent before its children. */
private fun List<CategoryBranch>.inTreeOrder(): List<Category> =
    CategoryKind.entries.flatMap { kind ->
        filter { it.parent.kind == kind }.flatMap { listOf(it.parent) + it.children }
    }

/** Where the Transactions of the Category [id] can go when it is deleted: every other Category of its kind. */
fun List<Category>.moveTargets(id: Long): List<Category> {
    val deleting = firstOrNull { it.id == id } ?: return emptyList()
    return filter { it.id != id && it.kind == deleting.kind }
}

/**
 * Whether the Category [id] can be deleted, its Transactions (when it [hasTransactions]) moving
 * to [moveTo]: it must have no children, and [moveTo] must be one of its [moveTargets] when it
 * has Transactions, and whenever it is given.
 */
fun List<Category>.canDeleteCategory(id: Long, moveTo: Long?, hasTransactions: Boolean): Boolean {
    if (none { it.id == id } || any { it.parentId == id }) return false
    if (moveTo == null) return !hasTransactions
    return moveTargets(id).any { it.id == moveTo }
}

/** Where a Category sits: at the top level of [kind], or under the parent [parentId], whose kind it shares. */
data class CategoryPlace(val kind: CategoryKind, val parentId: Long? = null)

/** Where this Category sits. */
val Category.place: CategoryPlace get() = CategoryPlace(kind, parentId)

/** Why a name can't be saved for a Category. */
enum class CategoryNameError {
    /** The name is empty or only spaces. */
    Blank,

    /** Another Category at the same place already has it. */
    Taken,
}

/**
 * What stops [name] from naming a Category at [place] among these, or null when nothing does: it
 * is blank, or a sibling there has it, ignoring case and surrounding spaces. The Category
 * [renaming] (0 when adding one) may keep its own name.
 */
fun List<Category>.nameError(name: String, place: CategoryPlace, renaming: Long = 0): CategoryNameError? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return CategoryNameError.Blank
    val taken = any {
        it.id != renaming && it.place == place && it.name.trim().equals(trimmed, ignoreCase = true)
    }
    return if (taken) CategoryNameError.Taken else null
}
