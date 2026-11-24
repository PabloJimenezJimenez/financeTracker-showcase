package com.pablojimenez.financetracker.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.CategoryKind
import com.pablojimenez.financetracker.domain.branches
import com.pablojimenez.financetracker.domain.canMove
import com.pablojimenez.financetracker.domain.moveTargets
import com.pablojimenez.financetracker.domain.place
import com.pablojimenez.financetracker.ui.components.ConfirmDialog
import com.pablojimenez.financetracker.ui.components.FieldDialog
import com.pablojimenez.financetracker.ui.components.PickerField
import com.pablojimenez.financetracker.ui.theme.FinanceMotion
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.LocalReduceMotion
import com.pablojimenez.financetracker.ui.transaction.CategoryPickerSheet
import com.pablojimenez.financetracker.ui.theme.Spacing

/** What the owner can do on the Categories screen; each call maps to [CategoriesViewModel]. */
interface CategoriesActions {
    /** Opens the Category dialog for a new Category. */
    fun onAdd()

    /** Opens the Category dialog for [category]. */
    fun onOpen(category: Category)

    /** Moves the Category [id] before ([up]) or after its neighbouring sibling. */
    fun onMove(id: Long, up: Boolean)

    /** Starts deleting [category]: see [CategoryDeletion]. */
    fun onDelete(category: Category)

    /** Picks where the deleted Category's Transactions go. */
    fun onDeleteTargetChange(id: Long)

    fun onConfirmDelete()
    fun onDeleteDismiss()
}

/**
 * The Category tree, opened from Settings: the Spending and Income headings, each parent followed
 * by its children (indented), and "Add category" in the top bar. Tapping a row (tagged
 * `category-row-<id>`) opens the [dialog] for it, which also moves it among its siblings and
 * starts its [deletion]. Stateless apart from whether the move-to picker is open:
 * [CategoriesViewModel] holds the tree and the dialogs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    categories: List<Category>,
    dialog: CategoryDialogState?,
    dialogActions: CategoryDialogActions,
    deletion: CategoryDeletion?,
    actions: CategoriesActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories), style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = actions::onAdd) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add_category))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        val branches = categories.branches()
        val reduceMotion = LocalReduceMotion.current
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("categories"),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + Spacing.XXXLarge,
            ),
        ) {
            CategoryKind.entries.forEach { kind ->
                item(key = "heading-${kind.name}") {
                    Text(
                        text = stringResource(kind.headingRes),
                        modifier = Modifier
                            .padding(
                                start = Spacing.ScreenMargin,
                                end = Spacing.ScreenMargin,
                                top = Spacing.Large,
                                bottom = Spacing.Small,
                            )
                            .semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                branches.filter { it.parent.kind == kind }.forEach { branch ->
                    item(key = branch.parent.id) {
                        CategoryRow(branch.parent, isChild = false, actions::onOpen, Modifier.rowMotion(this, reduceMotion))
                    }
                    items(branch.children, key = { it.id }) { child ->
                        CategoryRow(child, isChild = true, actions::onOpen, Modifier.rowMotion(this, reduceMotion))
                    }
                }
            }
        }
    }
    if (dialog != null) {
        CategoryDialog(dialog, categories, dialogActions) {
            dialog.editing?.let { editing ->
                MoveButtons(editing.id, categories, actions::onMove)
                TextButton(
                    onClick = { actions.onDelete(editing) },
                    modifier = Modifier.testTag("category-delete"),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) { Text(stringResource(R.string.delete)) }
            }
        }
    }
    if (deletion != null) DeletionDialog(deletion, actions)
}

/** The dialog of a [deletion]: an explanation, a plain confirmation, or where its Transactions go. */
@Composable
private fun DeletionDialog(deletion: CategoryDeletion, actions: CategoriesActions) {
    when (deletion) {
        is CategoryDeletion.HasChildren -> AlertDialog(
            onDismissRequest = actions::onDeleteDismiss,
            title = { Text(stringResource(R.string.delete_subcategories_first)) },
            text = { Text(stringResource(R.string.delete_subcategories_first_text)) },
            confirmButton = { TextButton(onClick = actions::onDeleteDismiss) { Text(stringResource(R.string.ok)) } },
        )
        is CategoryDeletion.Confirm -> ConfirmDialog(
            title = stringResource(R.string.delete_category_title),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = actions::onConfirmDelete,
            onDismiss = actions::onDeleteDismiss,
        )
        is CategoryDeletion.MoveTransactions -> MoveTransactionsDialog(deletion, actions)
    }
}

/**
 * Deleting a Category with Transactions: "Move its N transactions to" a Category picked from the
 * deletion's targets (field tagged `move-to-field`). Delete (tagged `delete-category-confirm`)
 * stays disabled until there is one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveTransactionsDialog(deletion: CategoryDeletion.MoveTransactions, actions: CategoriesActions) {
    var picking by rememberSaveable { mutableStateOf(false) }
    FieldDialog(
        onDismissRequest = actions::onDeleteDismiss,
        title = { Text(stringResource(R.string.delete_category_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Large)) {
                Text(pluralStringResource(R.plurals.move_transactions_to, deletion.count, deletion.count))
                PickerField(
                    value = deletion.targets.firstOrNull { it.id == deletion.moveTo }?.name.orEmpty(),
                    label = stringResource(R.string.category),
                    onClick = { picking = true },
                    modifier = Modifier.testTag("move-to-field"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = actions::onConfirmDelete,
                modifier = Modifier.testTag("delete-category-confirm"),
                enabled = deletion.moveTo != null,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = actions::onDeleteDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
    if (picking) {
        CategoryPickerSheet(
            categories = deletion.targets,
            selectedId = deletion.moveTo,
            onPick = {
                actions.onDeleteTargetChange(it.id)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * "Move up" and "Move down" for the Category [id], each disabled where it has no sibling to swap
 * with. They wrap onto two lines when large text leaves no room for both.
 */
@Composable
private fun MoveButtons(id: Long, categories: List<Category>, onMove: (Long, Boolean) -> Unit) {
    val colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        TextButton(onClick = { onMove(id, true) }, enabled = categories.canMove(id, up = true), colors = colors) {
            Text(stringResource(R.string.move_up))
        }
        TextButton(onClick = { onMove(id, false) }, enabled = categories.canMove(id, up = false), colors = colors) {
            Text(stringResource(R.string.move_down))
        }
    }
}

/**
 * A row's motion as Categories are added, deleted and moved: it fades in or out and the others
 * slide (docs/design.md §9); with reduce motion it only fades.
 */
private fun Modifier.rowMotion(scope: LazyItemScope, reduceMotion: Boolean): Modifier = with(scope) {
    animateItem(
        fadeInSpec = FinanceMotion.effects(),
        placementSpec = if (reduceMotion) null else FinanceMotion.spatialDefault(),
        fadeOutSpec = FinanceMotion.effects(),
    )
}

@Composable
private fun CategoryRow(category: Category, isChild: Boolean, onOpen: (Category) -> Unit, modifier: Modifier) {
    Text(
        text = category.name,
        modifier = modifier
            .fillMaxWidth()
            .testTag("category-row-${category.id}")
            .heightIn(min = Spacing.MinTouchTarget)
            .clickable(role = Role.Button, onClick = { onOpen(category) })
            .padding(
                start = if (isChild) Spacing.ScreenMargin + Spacing.XLarge else Spacing.ScreenMargin,
                end = Spacing.ScreenMargin,
            )
            .wrapContentHeight(),
        style = if (isChild) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** The heading of a [CategoryKind]'s part of the tree. */
val CategoryKind.headingRes: Int
    get() = when (this) {
        CategoryKind.Spending -> R.string.spending
        CategoryKind.Income -> R.string.income
    }

private val previewCategories = listOf(
    Category(1, "Groceries", CategoryKind.Spending),
    Category(2, "Food", CategoryKind.Spending, parentId = 1),
    Category(3, "Household", CategoryKind.Spending, parentId = 1),
    Category(4, "Education", CategoryKind.Spending),
    Category(5, "Salary", CategoryKind.Income),
)

private object PreviewActions : CategoriesActions {
    override fun onAdd() = Unit
    override fun onOpen(category: Category) = Unit
    override fun onMove(id: Long, up: Boolean) = Unit
    override fun onDelete(category: Category) = Unit
    override fun onDeleteTargetChange(id: Long) = Unit
    override fun onConfirmDelete() = Unit
    override fun onDeleteDismiss() = Unit
}

@Composable
private fun CategoriesPreview(
    darkTheme: Boolean = false,
    dialog: CategoryDialogState? = null,
    deletion: CategoryDeletion? = null,
) {
    FinanceTrackerTheme(darkTheme = darkTheme, dynamicColor = false) {
        CategoriesScreen(
            categories = previewCategories,
            dialog = dialog,
            dialogActions = PreviewDialogActions,
            deletion = deletion,
            actions = PreviewActions,
            onBack = {},
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun CategoriesScreenPreview() {
    CategoriesPreview()
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun CategoriesScreenDarkPreview() {
    CategoriesPreview(darkTheme = true)
}

@Preview(name = "Edit dialog", showBackground = true)
@Composable
private fun CategoriesEditDialogPreview() {
    val food = previewCategories[1]
    CategoriesPreview(dialog = CategoryDialogState(editing = food, name = food.name, place = food.place))
}

@Preview(name = "Move its transactions", showBackground = true)
@Composable
private fun CategoriesMoveTransactionsPreview() {
    val food = previewCategories[1]
    CategoriesPreview(
        deletion = CategoryDeletion.MoveTransactions(
            category = food,
            count = 2,
            targets = previewCategories.moveTargets(food.id),
            moveTo = food.parentId,
        ),
    )
}

@Preview(name = "Delete its subcategories first", showBackground = true)
@Composable
private fun CategoriesHasChildrenPreview() {
    CategoriesPreview(deletion = CategoryDeletion.HasChildren(previewCategories[0]))
}
