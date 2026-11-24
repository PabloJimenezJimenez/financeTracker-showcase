package com.pablojimenez.financetracker.ui.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.CategoryKind
import com.pablojimenez.financetracker.domain.CategoryNameError
import com.pablojimenez.financetracker.domain.CategoryPlace
import com.pablojimenez.financetracker.ui.components.FieldDialog
import com.pablojimenez.financetracker.ui.components.fieldError
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.Spacing

/**
 * The Category dialog: the Name field (tagged `category-name`), and for a new Category its Place,
 * the top level of Spending or Income or under a parent of [categories]. Save (tagged
 * `category-save`), or the keyboard's Done, calls
 * [CategoryDialogActions.onSave], then [onSaved] when that closed the dialog. While editing, the
 * [editActions] follow the name.
 */
@Composable
fun CategoryDialog(
    state: CategoryDialogState,
    categories: List<Category>,
    actions: CategoryDialogActions,
    onSaved: () -> Unit = {},
    editActions: @Composable ColumnScope.() -> Unit = {},
) {
    FieldDialog(
        onDismissRequest = actions::onDismiss,
        title = { Text(stringResource(if (state.editing == null) R.string.new_category else R.string.edit_category)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Large)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = actions::onNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category-name"),
                    label = { Text(stringResource(R.string.category_name)) },
                    isError = state.error != null,
                    supportingText = state.error?.let { fieldError(shown = true, it.message) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (actions.onSave()) onSaved() }),
                )
                if (state.editing == null) {
                    PlaceField(state.place, categories, actions::onPlaceChange)
                } else {
                    editActions()
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (actions.onSave()) onSaved() },
                modifier = Modifier.testTag("category-save"),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = actions::onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val CategoryNameError.message: Int
    get() = when (this) {
        CategoryNameError.Blank -> R.string.error_name
        CategoryNameError.Taken -> R.string.error_category_name_taken
    }

/**
 * The Place field (tagged `category-place`): a menu of the top levels of Spending (tagged
 * `place-Spending`) and Income (`place-Income`), then each parent by name (`place-<id>`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceField(place: CategoryPlace, categories: List<Category>, onPick: (CategoryPlace) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val parents = categories.filter { it.parentId == null }
    val topLevels = CategoryKind.entries.map { CategoryPlace(it) }
    val options = topLevels.map { it to "place-${it.kind.name}" } +
        parents.map { CategoryPlace(it.kind, it.id) to "place-${it.id}" }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = place.label(parents),
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .testTag("category-place"),
            readOnly = true,
            label = { Text(stringResource(R.string.category_place)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
        )
        val scheme = MaterialTheme.colorScheme
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = scheme.surfaceContainer,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(DividerDefaults.Thickness, scheme.outlineVariant),
        ) {
            options.forEach { (option, tag) ->
                DropdownMenuItem(
                    text = { Text(option.label(parents), color = scheme.onSurface) },
                    onClick = {
                        expanded = false
                        onPick(option)
                    },
                    modifier = Modifier.testTag(tag),
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** "Spending (top level)", "Income (top level)", or the name of the parent among [parents]. */
@Composable
private fun CategoryPlace.label(parents: List<Category>): String = when {
    parentId != null -> parents.firstOrNull { it.id == parentId }?.name.orEmpty()
    kind == CategoryKind.Spending -> stringResource(R.string.category_place_spending)
    else -> stringResource(R.string.category_place_income)
}

/** Actions that do nothing, for previews. */
internal object PreviewDialogActions : CategoryDialogActions {
    override fun onNameChange(name: String) = Unit
    override fun onPlaceChange(place: CategoryPlace) = Unit
    override fun onSave() = false
    override fun onDismiss() = Unit
}

@Preview
@Composable
private fun NewCategoryDialogPreview() {
    FinanceTrackerTheme(dynamicColor = false) {
        CategoryDialog(
            state = CategoryDialogState(name = "Groceries", error = CategoryNameError.Taken),
            categories = listOf(Category(1, "Groceries", CategoryKind.Spending)),
            actions = PreviewDialogActions,
        )
    }
}
