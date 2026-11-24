package com.pablojimenez.financetracker.ui.transfer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.EUR
import com.pablojimenez.financetracker.ui.components.AccountPickerField
import com.pablojimenez.financetracker.ui.components.ConfirmDialog
import com.pablojimenez.financetracker.ui.components.DateDialog
import com.pablojimenez.financetracker.ui.components.DeleteAction
import com.pablojimenez.financetracker.ui.components.FormScaffold
import com.pablojimenez.financetracker.ui.components.PickerField
import com.pablojimenez.financetracker.ui.components.SaveButton
import com.pablojimenez.financetracker.ui.components.fieldError
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The form that moves money between two of the owner's Accounts, or edits a Transfer ("Edit
 * transfer") with a Delete action that asks first. It has no Category, time or Merchant.
 * Stateless apart from which dialog is open; [TransferFormViewModel] holds [state]. [onDone]
 * runs once it is saved or deleted.
 */
@Composable
fun TransferFormScreen(
    state: TransferFormState,
    accounts: List<Account>,
    actions: TransferFormActions,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by rememberSaveable { mutableStateOf(Dialog.None) }
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(state.deleted) { if (state.deleted) currentOnDone() }
    FormScaffold(
        title = stringResource(if (state.editing) R.string.edit_transfer else R.string.new_transfer),
        onBack = onBack,
        modifier = modifier,
        actions = { if (state.editing) DeleteAction(onClick = { dialog = Dialog.ConfirmDelete }) },
        saveButton = { SaveButton(saved = state.saved, onClick = actions::onSave, onDone = onDone) },
    ) {
        AccountPickerField(
            label = stringResource(R.string.transfer_from_account),
            accounts = accounts,
            selectedId = state.from(accounts),
            onSelect = actions::onFromChange,
            tag = "from-account-field",
        )
        AccountPickerField(
            label = stringResource(R.string.transfer_to_account),
            accounts = accounts,
            selectedId = state.to(accounts),
            onSelect = actions::onToChange,
            tag = "to-account-field",
            isError = state.sameAccountError,
            supportingText = fieldError(state.sameAccountError, R.string.error_same_account),
        )
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
        PickerField(
            value = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(LocalLocale.current.platformLocale)
                .format(state.date),
            label = stringResource(R.string.date),
            onClick = { dialog = Dialog.Date },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("date-field"),
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
    val close = { dialog = Dialog.None }
    when (dialog) {
        Dialog.None -> Unit
        Dialog.Date -> DateDialog(state.date, onPick = { actions.onDateChange(it); close() }, onDismiss = close)
        Dialog.ConfirmDelete -> ConfirmDialog(
            title = stringResource(R.string.delete_transfer_title),
            text = stringResource(R.string.delete_transfer_text),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { close(); actions.onDelete() },
            onDismiss = close,
        )
    }
}

private enum class Dialog { None, Date, ConfirmDelete }

private object PreviewActions : TransferFormActions {
    override fun onFromChange(accountId: Long) = Unit
    override fun onToChange(accountId: Long) = Unit
    override fun onAmountChange(text: String) = Unit
    override fun onDateChange(date: LocalDate) = Unit
    override fun onNoteChange(note: String) = Unit
    override fun onSave() = Unit
    override fun onDelete() = Unit
}

private val previewAccounts = listOf(
    Account(1, "Current", AccountType.Current, 100_000),
    Account(2, "Savings", AccountType.Savings, 0),
)

@Preview(showBackground = true)
@Composable
private fun TransferFormPreview() {
    FinanceTrackerTheme(dynamicColor = false) {
        TransferFormScreen(
            state = TransferFormState(date = LocalDate.of(2026, 3, 10), amount = "30,00", note = "Rainy day"),
            accounts = previewAccounts,
            actions = PreviewActions,
            onDone = {},
            onBack = {},
        )
    }
}

@Preview(name = "Editing with errors, dark", showBackground = true)
@Composable
private fun TransferFormErrorPreview() {
    FinanceTrackerTheme(darkTheme = true, dynamicColor = false) {
        TransferFormScreen(
            state = TransferFormState(
                date = LocalDate.of(2026, 3, 10),
                editing = true,
                toAccountId = 1,
                amountError = true,
                sameAccountError = true,
            ),
            accounts = previewAccounts,
            actions = PreviewActions,
            onDone = {},
            onBack = {},
        )
    }
}
