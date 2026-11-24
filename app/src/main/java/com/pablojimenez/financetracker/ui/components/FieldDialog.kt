package com.pablojimenez.financetracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import com.pablojimenez.financetracker.ui.theme.Spacing

/**
 * An `AlertDialog` that holds text fields: [title], [text], and the [confirmButton] and
 * [dismissButton]. It takes the screen's width less a margin, within `AlertDialog`'s own minimum
 * and maximum, instead of the platform's default dialog width: with that default, a text field in
 * a dialog keeps Compose from ever settling under Robolectric, so it could not be tested. The
 * [text] scrolls, so at large font scales or in landscape the buttons stay on screen.
 */
@Composable
fun FieldDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = Modifier.padding(horizontal = Spacing.XLarge),
        dismissButton = dismissButton,
        title = title,
        text = { Box(Modifier.verticalScroll(rememberScrollState())) { text() } },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    )
}
