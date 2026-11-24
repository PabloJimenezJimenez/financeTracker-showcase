package com.pablojimenez.financetracker.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.EUR
import com.pablojimenez.financetracker.ui.components.FormScaffold
import com.pablojimenez.financetracker.ui.components.SaveButton
import com.pablojimenez.financetracker.ui.components.fieldError
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.Spacing

/** The form that creates an Account. Stateless; [AccountFormViewModel] holds [state]. */
@Composable
fun AccountFormScreen(
    state: AccountFormState,
    onNameChange: (String) -> Unit,
    onTypeChange: (AccountType) -> Unit,
    onStartingBalanceChange: (String) -> Unit,
    onSave: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FormScaffold(
        title = stringResource(R.string.new_account),
        onBack = onBack,
        modifier = modifier,
        saveButton = { SaveButton(saved = state.saved, onClick = onSave, onDone = onDone) },
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("account-name"),
            label = { Text(stringResource(R.string.account_name)) },
            isError = state.nameError,
            supportingText = fieldError(state.nameError, R.string.error_name),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
        )
        AccountTypeChooser(selected = state.type, onSelect = onTypeChange)
        OutlinedTextField(
            value = state.startingBalance,
            onValueChange = onStartingBalanceChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("starting-balance"),
            label = { Text(stringResource(R.string.starting_balance)) },
            isError = state.startingBalanceError,
            supportingText = fieldError(state.startingBalanceError, R.string.error_amount),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                text = stringResource(R.string.currency),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = EUR.currencyCode,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AccountTypeChooser(selected: AccountType, onSelect: (AccountType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
        Text(
            text = stringResource(R.string.account_type),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            AccountType.entries.forEach { type ->
                val isSelected = type == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(type) },
                    label = { Text(stringResource(type.label)) },
                    modifier = Modifier.testTag("account-type-${type.name}"),
                    leadingIcon = if (isSelected) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                    } else {
                        null
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        }
    }
}

private val AccountType.label: Int
    get() = when (this) {
        AccountType.Current -> R.string.account_type_current
        AccountType.Savings -> R.string.account_type_savings
        AccountType.CreditCard -> R.string.account_type_credit_card
        AccountType.Cash -> R.string.account_type_cash
    }

@Preview(showBackground = true)
@Composable
private fun AccountFormPreview() {
    FinanceTrackerTheme(dynamicColor = false) {
        AccountFormScreen(
            state = AccountFormState(name = "Cash", type = AccountType.Cash, startingBalance = "50,00"),
            onNameChange = {},
            onTypeChange = {},
            onStartingBalanceChange = {},
            onSave = {},
            onDone = {},
            onBack = {},
        )
    }
}

@Preview(name = "Errors, dark", showBackground = true)
@Composable
private fun AccountFormErrorPreview() {
    FinanceTrackerTheme(darkTheme = true, dynamicColor = false) {
        AccountFormScreen(
            state = AccountFormState(nameError = true),
            onNameChange = {},
            onTypeChange = {},
            onStartingBalanceChange = {},
            onSave = {},
            onDone = {},
            onBack = {},
        )
    }
}
