package com.pablojimenez.financetracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.Spacing

/**
 * Settings, opened from Home's gear: a "Categories" row, which calls [onOpenCategories], and a
 * "Merchants" row, which calls [onOpenMerchants].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenCategories: () -> Unit,
    onOpenMerchants: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            SettingsRow(stringResource(R.string.categories), onOpenCategories, Modifier.testTag("settings-categories"))
            SettingsRow(stringResource(R.string.merchants), onOpenMerchants, Modifier.testTag("settings-merchants"))
        }
    }
}

/** One row of Settings: [text] that calls [onClick], at least a touch target tall. */
@Composable
private fun SettingsRow(text: String, onClick: () -> Unit, modifier: Modifier) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.MinTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.ScreenMargin)
            .wrapContentHeight()
            .then(modifier),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    FinanceTrackerTheme(dynamicColor = false) {
        SettingsScreen(onOpenCategories = {}, onOpenMerchants = {}, onBack = {})
    }
}
