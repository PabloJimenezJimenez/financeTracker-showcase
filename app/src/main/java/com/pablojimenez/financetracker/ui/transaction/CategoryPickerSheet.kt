package com.pablojimenez.financetracker.ui.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.CategoryKind
import com.pablojimenez.financetracker.domain.branches
import com.pablojimenez.financetracker.ui.categories.headingRes
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * The Category picker: [categories] under a Spending and an Income heading (a heading with none
 * is left out), each parent followed by its children. Parents and children can both be picked.
 * Picking one hides the sheet and then calls [onPick]. With [onNewCategory], a last row "New
 * category" (tagged `new-category`) calls it, leaving the sheet open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    categories: List<Category>,
    selectedId: Long?,
    onPick: (Category) -> Unit,
    onDismiss: () -> Unit,
    onNewCategory: (() -> Unit)? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CategoryList(
            categories = categories,
            selectedId = selectedId,
            onPick = { category ->
                scope.launch { sheetState.hide() }.invokeOnCompletion { onPick(category) }
            },
            onNewCategory = onNewCategory,
        )
    }
}

@Composable
private fun CategoryList(
    categories: List<Category>,
    selectedId: Long?,
    onPick: (Category) -> Unit,
    onNewCategory: (() -> Unit)? = null,
) {
    val branches = categories.branches()
    Column(
        modifier = Modifier
            .testTag("category-list")
            .verticalScroll(rememberScrollState())
            .padding(bottom = Spacing.XLarge),
    ) {
        CategoryKind.entries.forEach { kind ->
            val ofKind = branches.filter { it.parent.kind == kind }
            if (ofKind.isEmpty()) return@forEach
            Text(
                text = stringResource(kind.headingRes),
                modifier = Modifier
                    .padding(
                        start = Spacing.ScreenMargin,
                        end = Spacing.ScreenMargin,
                        top = Spacing.Large,
                        bottom = Spacing.Small,
                    )
                    .semantics { heading() }
                    .testTag("category-heading-${kind.name}"),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ofKind.forEach { branch ->
                CategoryRow(branch.parent, isChild = false, selected = branch.parent.id == selectedId, onPick)
                branch.children.forEach { child ->
                    CategoryRow(child, isChild = true, selected = child.id == selectedId, onPick)
                }
            }
        }
        if (onNewCategory != null) NewCategoryRow(onNewCategory)
    }
}

@Composable
private fun NewCategoryRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.MinTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.ScreenMargin)
            .testTag("new-category"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.Small))
        Text(
            text = stringResource(R.string.new_category),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    isChild: Boolean,
    selected: Boolean,
    onPick: (Category) -> Unit,
) {
    val highlight = if (selected) {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.MinTouchTarget)
            .then(highlight)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(category) })
            .padding(
                start = if (isChild) Spacing.ScreenMargin + Spacing.XLarge else Spacing.ScreenMargin,
                end = Spacing.ScreenMargin,
            )
            .testTag("category-${category.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = category.name,
            modifier = Modifier.weight(1f),
            style = if (isChild) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (selected) {
            Spacer(Modifier.width(Spacing.Small))
            Icon(painterResource(R.drawable.ic_check), contentDescription = null)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CategoryListPreview() {
    FinanceTrackerTheme(dynamicColor = false) {
        CategoryList(
            categories = listOf(
                Category(1, "Groceries", CategoryKind.Spending),
                Category(2, "Food", CategoryKind.Spending, parentId = 1),
                Category(3, "Household", CategoryKind.Spending, parentId = 1),
                Category(4, "Education", CategoryKind.Spending),
                Category(5, "Salary", CategoryKind.Income),
            ),
            selectedId = 2,
            onPick = {},
            onNewCategory = {},
        )
    }
}
