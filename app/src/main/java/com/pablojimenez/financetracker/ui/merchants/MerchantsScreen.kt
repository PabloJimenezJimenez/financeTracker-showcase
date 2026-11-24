package com.pablojimenez.financetracker.ui.merchants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.CategoryKind
import com.pablojimenez.financetracker.domain.Merchant
import com.pablojimenez.financetracker.ui.categories.CategoryDialogActions
import com.pablojimenez.financetracker.ui.categories.CategoryDialogState
import com.pablojimenez.financetracker.ui.categories.PreviewDialogActions
import com.pablojimenez.financetracker.ui.components.ConfirmDialog
import com.pablojimenez.financetracker.ui.theme.FinanceMotion
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.LocalReduceMotion
import com.pablojimenez.financetracker.ui.theme.Spacing

/**
 * Every Merchant, opened from Settings: a row per Merchant (tagged `merchant-row-<id>`) with its
 * name over its default Category, or "No default category"; [merchants] is null until the list
 * has been read. Tapping a row opens its [dialog] (see [MerchantDialog]), whose Delete asks the
 * [deletion] question. Stateless: [MerchantsViewModel] holds the list and the dialogs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantsScreen(
    merchants: List<MerchantListItem>?,
    categories: List<Category>,
    dialog: MerchantDialogState?,
    deletion: MerchantDeletion?,
    categoryDialog: CategoryDialogState?,
    categoryDialogActions: CategoryDialogActions,
    actions: MerchantsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.merchants), style = MaterialTheme.typography.headlineSmall) },
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
        val reduceMotion = LocalReduceMotion.current
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("merchants"),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + Spacing.XXXLarge,
            ),
        ) {
            // The empty text is an item of the same list, so the last row still fades out when deleted.
            if (merchants?.isEmpty() == true) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.no_merchants),
                        modifier = Modifier
                            .rowMotion(this, reduceMotion)
                            .padding(horizontal = Spacing.ScreenMargin, vertical = Spacing.Large),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(merchants.orEmpty(), key = { it.merchant.id }) { item ->
                MerchantRow(item, actions::onOpen, Modifier.rowMotion(this, reduceMotion))
            }
        }
    }
    if (dialog != null) MerchantDialog(dialog, categories, categoryDialog, categoryDialogActions, actions)
    if (deletion != null) {
        val count = deletion.transactionCount
        ConfirmDialog(
            title = stringResource(R.string.delete_merchant_title, deletion.merchant.name),
            text = if (count == 0) {
                stringResource(R.string.delete_merchant_unused)
            } else {
                pluralStringResource(R.plurals.delete_merchant_text, count, count)
            },
            confirmLabel = stringResource(R.string.delete),
            onConfirm = actions::onConfirmDelete,
            onDismiss = actions::onDeleteDismiss,
        )
    }
}

/**
 * A row's motion as Merchants are renamed and deleted: it fades in or out and the others slide
 * (docs/design.md §9); with reduce motion it only fades.
 */
private fun Modifier.rowMotion(scope: LazyItemScope, reduceMotion: Boolean): Modifier = with(scope) {
    animateItem(
        fadeInSpec = FinanceMotion.effects(),
        placementSpec = if (reduceMotion) null else FinanceMotion.spatialDefault(),
        fadeOutSpec = FinanceMotion.effects(),
    )
}

@Composable
private fun MerchantRow(item: MerchantListItem, onOpen: (Merchant) -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("merchant-row-${item.merchant.id}")
            .heightIn(min = Spacing.MinTouchTarget)
            .clickable(role = Role.Button, onClick = { onOpen(item.merchant) })
            .padding(horizontal = Spacing.ScreenMargin, vertical = Spacing.Small),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = item.merchant.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = item.category ?: stringResource(R.string.no_default_category),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val previewCategories = listOf(
    Category(1, "Groceries", CategoryKind.Spending),
    Category(2, "Food", CategoryKind.Spending, parentId = 1),
    Category(3, "Restaurants", CategoryKind.Spending),
)

private val previewMerchants = merchantList(
    listOf(
        Merchant(1, "Mercadona", listOf("mercadona"), defaultCategoryId = 2),
        Merchant(2, "Lidl", listOf("lidl"), defaultCategoryId = 2),
        Merchant(3, "Bar Pepe", defaultCategoryId = 3),
        Merchant(4, "Ana García"),
    ),
    previewCategories,
)

@Composable
private fun MerchantsPreview(
    darkTheme: Boolean = false,
    merchants: List<MerchantListItem> = previewMerchants,
    deletion: MerchantDeletion? = null,
) {
    FinanceTrackerTheme(darkTheme = darkTheme, dynamicColor = false) {
        MerchantsScreen(
            merchants = merchants,
            categories = previewCategories,
            dialog = null,
            deletion = deletion,
            categoryDialog = null,
            categoryDialogActions = PreviewDialogActions,
            actions = PreviewMerchantsActions,
            onBack = {},
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun MerchantsScreenPreview() {
    MerchantsPreview()
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun MerchantsScreenDarkPreview() {
    MerchantsPreview(darkTheme = true)
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun MerchantsScreenEmptyPreview() {
    MerchantsPreview(merchants = emptyList())
}

@Preview(name = "Delete", showBackground = true)
@Composable
private fun MerchantsDeletePreview() {
    MerchantsPreview(deletion = MerchantDeletion(previewMerchants.first().merchant, transactionCount = 2))
}
