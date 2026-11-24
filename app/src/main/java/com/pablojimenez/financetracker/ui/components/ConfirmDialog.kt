package com.pablojimenez.financetracker.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme

/**
 * Asks before something that cannot be undone (docs/design.md §9): [title], an optional [text],
 * Cancel, and [confirmLabel] in `error`. Confirming calls [onConfirm]; Cancel, back or a tap
 * outside call [onDismiss].
 */
@Composable
fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Preview
@Composable
private fun ConfirmDialogPreview() {
    FinanceTrackerTheme(dynamicColor = false) {
        ConfirmDialog(
            title = stringResource(R.string.delete_transfer_title),
            text = stringResource(R.string.delete_transfer_text),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {},
            onDismiss = {},
        )
    }
}
