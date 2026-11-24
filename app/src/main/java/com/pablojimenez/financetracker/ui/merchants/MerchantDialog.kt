package com.pablojimenez.financetracker.ui.merchants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.CategoryKind
import com.pablojimenez.financetracker.domain.Merchant
import com.pablojimenez.financetracker.domain.MerchantNameError
import com.pablojimenez.financetracker.ui.categories.CategoryDialog
import com.pablojimenez.financetracker.ui.categories.CategoryDialogActions
import com.pablojimenez.financetracker.ui.categories.CategoryDialogState
import com.pablojimenez.financetracker.ui.categories.PreviewDialogActions
import com.pablojimenez.financetracker.ui.components.FieldDialog
import com.pablojimenez.financetracker.ui.components.PickerField
import com.pablojimenez.financetracker.ui.components.fieldError
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.Spacing
import com.pablojimenez.financetracker.ui.transaction.CategoryPickerSheet

/**
 * The Merchant dialog: Name (tagged `merchant-name`), Keywords (`merchant-keywords`, separated
 * by commas) and Default category (`merchant-category`), a field that opens the Category picker
 * with "New category", whose [categoryDialog] then opens. Save (tagged `merchant-save`), or the
 * keyboard's Done on Keywords, saves; Delete (tagged `merchant-delete`) starts deleting the Merchant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantDialog(
    state: MerchantDialogState,
    categories: List<Category>,
    categoryDialog: CategoryDialogState?,
    categoryDialogActions: CategoryDialogActions,
    actions: MerchantsActions,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    FieldDialog(
        onDismissRequest = actions::onDismiss,
        title = { Text(stringResource(R.string.edit_merchant)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Large)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = actions::onNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("merchant-name"),
                    label = { Text(stringResource(R.string.merchant_name)) },
                    isError = state.error != null,
                    supportingText = state.error?.let { fieldError(shown = true, it.message) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = state.keywords,
                    onValueChange = actions::onKeywordsChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("merchant-keywords"),
                    label = { Text(stringResource(R.string.merchant_keywords)) },
                    supportingText = { Text(stringResource(R.string.merchant_keywords_hint)) },
                    // One line: a pasted newline would end up inside a keyword, which only commas separate.
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { actions.onSave() }),
                )
                PickerField(
                    value = categories.firstOrNull { it.id == state.defaultCategoryId }?.name
                        ?: stringResource(R.string.no_default_category),
                    label = stringResource(R.string.default_category),
                    onClick = { picking = true },
                    modifier = Modifier.testTag("merchant-category"),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) },
                )
                TextButton(
                    onClick = actions::onDelete,
                    modifier = Modifier.testTag("merchant-delete"),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) { Text(stringResource(R.string.delete)) }
            }
        },
        confirmButton = {
            TextButton(onClick = actions::onSave, modifier = Modifier.testTag("merchant-save")) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = actions::onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    val closePicker = { picking = false }
    if (picking) {
        CategoryPickerSheet(
            categories = categories,
            selectedId = state.defaultCategoryId,
            onPick = {
                actions.onDefaultCategoryChange(it.id)
                closePicker()
            },
            onDismiss = closePicker,
            onNewCategory = actions::onNewCategory,
        )
    }
    if (categoryDialog != null) CategoryDialog(categoryDialog, categories, categoryDialogActions, onSaved = closePicker)
}

private val MerchantNameError.message: Int
    get() = when (this) {
        MerchantNameError.Blank -> R.string.error_name
        MerchantNameError.Taken -> R.string.error_merchant_name_taken
    }

/** Actions that do nothing, for previews. */
internal object PreviewMerchantsActions : MerchantsActions {
    override fun onOpen(merchant: Merchant) = Unit
    override fun onNameChange(name: String) = Unit
    override fun onKeywordsChange(keywords: String) = Unit
    override fun onDefaultCategoryChange(categoryId: Long) = Unit
    override fun onNewCategory() = Unit
    override fun onSave() = Unit
    override fun onDismiss() = Unit
    override fun onDelete() = Unit
    override fun onConfirmDelete() = Unit
    override fun onDeleteDismiss() = Unit
}

private val previewMercadona = Merchant(1, "Mercadona", listOf("mercadona", "mercadona online"), defaultCategoryId = 2)

@Composable
private fun MerchantDialogPreview(state: MerchantDialogState) {
    FinanceTrackerTheme(dynamicColor = false) {
        MerchantDialog(
            state = state,
            categories = listOf(Category(2, "Food", CategoryKind.Spending)),
            categoryDialog = null,
            categoryDialogActions = PreviewDialogActions,
            actions = PreviewMerchantsActions,
        )
    }
}

@Preview(name = "Edit")
@Composable
private fun MerchantDialogEditPreview() {
    MerchantDialogPreview(MerchantDialogState(previewMercadona))
}

@Preview(name = "No default category")
@Composable
private fun MerchantDialogNoDefaultPreview() {
    MerchantDialogPreview(MerchantDialogState(Merchant(3, "Bar Pepe")))
}

@Preview(name = "Name taken")
@Composable
private fun MerchantDialogErrorPreview() {
    MerchantDialogPreview(MerchantDialogState(previewMercadona, name = "Lidl", error = MerchantNameError.Taken))
}
