package com.pablojimenez.financetracker.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * The `supportingText` of an `OutlinedTextField`: the error [message] while [shown], otherwise
 * nothing, so the field takes no room for an error it does not have.
 */
fun fieldError(shown: Boolean, @StringRes message: Int): (@Composable () -> Unit)? =
    if (shown) {
        { Text(stringResource(message)) }
    } else {
        null
    }
