package com.pablojimenez.financetracker.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A read-only field that shows [value] and calls [onClick] when tapped, to open a picker (a
 * sheet or a dialog). It reads as a button to screen readers and never shows the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    // The anchor of an exposed dropdown is exactly this: a read-only field that opens something.
    ExposedDropdownMenuBox(expanded = false, onExpandedChange = { onClick() }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            label = { Text(label) },
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            isError = isError,
            singleLine = true,
        )
    }
}
