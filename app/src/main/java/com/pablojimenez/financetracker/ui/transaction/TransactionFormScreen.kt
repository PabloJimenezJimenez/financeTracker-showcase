package com.pablojimenez.financetracker.ui.transaction

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.CategoryKind
import com.pablojimenez.financetracker.domain.Direction
import com.pablojimenez.financetracker.domain.EUR
import com.pablojimenez.financetracker.ui.categories.CategoryDialog
import com.pablojimenez.financetracker.ui.categories.CategoryDialogActions
import com.pablojimenez.financetracker.ui.categories.CategoryDialogState
import com.pablojimenez.financetracker.ui.categories.PreviewDialogActions
import com.pablojimenez.financetracker.ui.components.AccountPickerField
import com.pablojimenez.financetracker.ui.components.ConfirmDialog
import com.pablojimenez.financetracker.ui.components.DateDialog
import com.pablojimenez.financetracker.ui.components.DeleteAction
import com.pablojimenez.financetracker.ui.components.FormScaffold
import com.pablojimenez.financetracker.ui.components.PickerField
import com.pablojimenez.financetracker.ui.components.SaveButton
import com.pablojimenez.financetracker.ui.components.fieldError
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the owner can do on the Transaction form; each call maps to [TransactionFormViewModel]. */
interface TransactionFormActions {
    fun onDirectionChange(direction: Direction)
    fun onAmountChange(text: String)
    fun onAccountChange(accountId: Long)
    fun onCategoryChange(categoryId: Long)

    /** Opens the Category dialog from the Category picker. */
    fun onNewCategory()
    fun onDateChange(date: LocalDate)
    fun onTimeChange(time: LocalTime?)
    fun onMerchantChange(merchant: String)
    fun onMerchantPick(merchantId: Long)
    fun onMerchantSuggestionsDismiss()
    fun onNoteChange(note: String)
    fun onSave()
    fun onDelete()
}

/**
 * The form that adds a Transaction by hand, or edits one ("Edit transaction") with a Delete
 * action that asks first. Stateless apart from which picker or dialog is open;
 * [TransactionFormViewModel] holds [state]. The Merchant field offers [merchantSuggestions] in a
 * menu below it. The Category picker's "New category" opens the [categoryDialog]; once that saves,
 * the picker closes. [onDone] runs once it is saved or deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreen(
    state: TransactionFormState,
    accounts: List<Account>,
    categories: List<Category>,
    merchantSuggestions: List<MerchantSuggestion>,
    categoryDialog: CategoryDialogState?,
    categoryDialogActions: CategoryDialogActions,
    actions: TransactionFormActions,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(Picker.None) }
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(state.deleted) { if (state.deleted) currentOnDone() }
    FormScaffold(
        title = stringResource(if (state.editing) R.string.edit_transaction else R.string.new_transaction),
        onBack = onBack,
        modifier = modifier,
        actions = { if (state.editing) DeleteAction(onClick = { picking = Picker.ConfirmDelete }) },
        saveButton = { SaveButton(saved = state.saved, onClick = actions::onSave, onDone = onDone) },
    ) {
        DirectionToggle(selected = state.direction, onSelect = actions::onDirectionChange)
        OutlinedTextField(
            value = state.amount,
            onValueChange = actions::onAmountChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("amount"),
            label = { Text(stringResource(R.string.amount)) },
            suffix = { Text(EUR.getSymbol(LocalLocale.current.platformLocale)) },
            isError = state.amountError,
            supportingText = fieldError(state.amountError, R.string.error_amount),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        AccountPickerField(
            label = stringResource(R.string.account),
            accounts = accounts,
            selectedId = state.accountId ?: accounts.firstOrNull()?.id,
            onSelect = actions::onAccountChange,
            tag = "account-field",
        )
        PickerField(
            value = categories.firstOrNull { it.id == state.categoryId }?.name.orEmpty(),
            label = stringResource(R.string.category),
            onClick = { picking = Picker.Category },
            modifier = Modifier.testTag("category-field"),
            isError = state.categoryError,
            supportingText = fieldError(state.categoryError, R.string.error_category),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) },
        )
        DateAndTime(
            date = state.date,
            time = state.time,
            onPickDate = { picking = Picker.Date },
            onPickTime = { picking = Picker.Time },
            onClearTime = { actions.onTimeChange(null) },
        )
        MerchantField(
            text = state.merchant,
            suggestions = merchantSuggestions,
            onTextChange = actions::onMerchantChange,
            onPick = actions::onMerchantPick,
            onDismiss = actions::onMerchantSuggestionsDismiss,
        )
        OutlinedTextField(
            value = state.note,
            onValueChange = actions::onNoteChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("note"),
            label = { Text(stringResource(R.string.note_optional)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
    val close = { picking = Picker.None }
    when (picking) {
        Picker.None -> Unit
        Picker.Category -> CategoryPickerSheet(
            categories = categories,
            selectedId = state.categoryId,
            onPick = { actions.onCategoryChange(it.id); close() },
            onDismiss = close,
            onNewCategory = actions::onNewCategory,
        )
        Picker.Date -> DateDialog(state.date, onPick = { actions.onDateChange(it); close() }, onDismiss = close)
        Picker.Time -> TimeDialog(state.time, onPick = { actions.onTimeChange(it); close() }, onDismiss = close)
        Picker.ConfirmDelete -> ConfirmDialog(
            title = stringResource(R.string.delete_transaction_title),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { close(); actions.onDelete() },
            onDismiss = close,
        )
    }
    if (categoryDialog != null) CategoryDialog(categoryDialog, categories, categoryDialogActions, onSaved = close)
}

private enum class Picker { None, Category, Date, Time, ConfirmDelete }

/**
 * The Merchant field (tagged `merchant`). While there are [suggestions], a menu below it lists
 * each one's name over its default Category, tagged `merchant-suggestion-<id>`; picking one
 * calls [onPick].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MerchantField(
    text: String,
    suggestions: List<MerchantSuggestion>,
    onTextChange: (String) -> Unit,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val expanded = suggestions.isNotEmpty()
    // Typing opens the menu and picking or dismissing closes it, so a tap on the field changes nothing.
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = {}) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .testTag("merchant"),
            label = { Text(stringResource(R.string.merchant_optional)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
        )
        val scheme = MaterialTheme.colorScheme
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            containerColor = scheme.surfaceContainer,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(DividerDefaults.Thickness, scheme.outlineVariant),
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = suggestion.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurface,
                            )
                            if (suggestion.category != null) {
                                Text(
                                    text = suggestion.category,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = { onPick(suggestion.id) },
                    modifier = Modifier.testTag("merchant-suggestion-${suggestion.id}"),
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionToggle(selected: Direction, onSelect: (Direction) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        Direction.entries.forEachIndexed { index, direction ->
            SegmentedButton(
                selected = direction == selected,
                onClick = { onSelect(direction) },
                shape = SegmentedButtonDefaults.itemShape(index, Direction.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    activeContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                label = {
                    Text(
                        stringResource(
                            when (direction) {
                                Direction.Expense -> R.string.expense
                                Direction.Income -> R.string.income
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun DateAndTime(
    date: LocalDate,
    time: LocalTime?,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onClearTime: () -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PickerField(
            value = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date),
            label = stringResource(R.string.date),
            onClick = onPickDate,
            modifier = Modifier
                .weight(1f)
                .testTag("date-field"),
        )
        PickerField(
            value = time?.let { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(it) }
                .orEmpty(),
            label = stringResource(R.string.time_optional),
            onClick = onPickTime,
            modifier = Modifier
                .weight(1f)
                .testTag("time-field"),
        )
        if (time != null) {
            IconButton(onClick = onClearTime) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.clear_time))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(time: LocalTime?, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val initial = time ?: LocalTime.NOON
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = {
            TimePicker(
                state = pickerState,
                // Greys for the selected parts: primaryContainer is the hero tile's, tertiary unused.
                colors = TimePickerDefaults.colors(
                    timeSelectorSelectedContainerColor = scheme.surfaceContainerHighest,
                    timeSelectorSelectedContentColor = scheme.onSurface,
                    periodSelectorSelectedContainerColor = scheme.surfaceContainerHighest,
                    periodSelectorSelectedContentColor = scheme.onSurface,
                ),
            )
        },
    )
}

private object PreviewActions : TransactionFormActions {
    override fun onDirectionChange(direction: Direction) = Unit
    override fun onAmountChange(text: String) = Unit
    override fun onAccountChange(accountId: Long) = Unit
    override fun onCategoryChange(categoryId: Long) = Unit
    override fun onNewCategory() = Unit
    override fun onDateChange(date: LocalDate) = Unit
    override fun onTimeChange(time: LocalTime?) = Unit
    override fun onMerchantChange(merchant: String) = Unit
    override fun onMerchantPick(merchantId: Long) = Unit
    override fun onMerchantSuggestionsDismiss() = Unit
    override fun onNoteChange(note: String) = Unit
    override fun onSave() = Unit
    override fun onDelete() = Unit
}

private val previewAccounts = listOf(Account(1, "Current", AccountType.Current, 100_000))
private val previewCategories = listOf(Category(1, "Groceries", CategoryKind.Spending))

@Preview(showBackground = true)
@Composable
private fun TransactionFormPreview() {
    FinanceTrackerTheme(dynamicColor = false) {
        TransactionFormScreen(
            state = TransactionFormState(
                date = LocalDate.of(2026, 3, 10),
                amount = "12,50",
                categoryId = 1,
                time = LocalTime.of(18, 45),
                merchant = "Mercadona",
            ),
            accounts = previewAccounts,
            categories = previewCategories,
            merchantSuggestions = emptyList(),
            categoryDialog = null,
            categoryDialogActions = PreviewDialogActions,
            actions = PreviewActions,
            onDone = {},
            onBack = {},
        )
    }
}

@Preview(name = "Merchant suggestions", showBackground = true)
@Composable
private fun TransactionFormMerchantPreview() {
    FinanceTrackerTheme(dynamicColor = false) {
        TransactionFormScreen(
            state = TransactionFormState(
                date = LocalDate.of(2026, 3, 10),
                amount = "12,50",
                merchant = "merc",
                suggestingMerchants = true,
            ),
            accounts = previewAccounts,
            categories = previewCategories,
            merchantSuggestions = listOf(
                MerchantSuggestion(1, "Mercadona", "Food"),
                MerchantSuggestion(2, "Mercado de San Miguel", null),
            ),
            categoryDialog = null,
            categoryDialogActions = PreviewDialogActions,
            actions = PreviewActions,
            onDone = {},
            onBack = {},
        )
    }
}

@Preview(name = "Errors, dark", showBackground = true)
@Composable
private fun TransactionFormErrorPreview() {
    FinanceTrackerTheme(darkTheme = true, dynamicColor = false) {
        TransactionFormScreen(
            state = TransactionFormState(
                date = LocalDate.of(2026, 3, 10),
                amountError = true,
                categoryError = true,
            ),
            accounts = previewAccounts,
            categories = previewCategories,
            merchantSuggestions = emptyList(),
            categoryDialog = null,
            categoryDialogActions = PreviewDialogActions,
            actions = PreviewActions,
            onDone = {},
            onBack = {},
        )
    }
}
