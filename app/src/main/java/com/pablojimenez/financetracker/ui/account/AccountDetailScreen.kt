package com.pablojimenez.financetracker.ui.account

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.ui.components.AnimatedMoneyText
import com.pablojimenez.financetracker.ui.components.BentoTile
import com.pablojimenez.financetracker.ui.components.ConfirmDialog
import com.pablojimenez.financetracker.ui.components.TileSpan
import com.pablojimenez.financetracker.ui.components.TransactionListDay
import com.pablojimenez.financetracker.ui.components.TransactionListRow
import com.pablojimenez.financetracker.ui.components.TransferSide
import com.pablojimenez.financetracker.ui.components.accountBalance
import com.pablojimenez.financetracker.ui.components.accountContainer
import com.pablojimenez.financetracker.ui.components.transactionList
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.LocalReduceMotion
import com.pablojimenez.financetracker.ui.theme.Spacing
import java.time.LocalDate

/**
 * One Account: its name and balance in the hero tile, Delete account (no Transactions) or Archive
 * account (some) after a dialog, then only its Transactions by day. The screen is what the
 * Account's tile on Home grows into. Stateless apart from which dialog is open;
 * [AccountDetailViewModel] provides [state]. [onBack] also runs once the Account is closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    accountId: Long,
    state: AccountDetailState,
    onOpenTransaction: (TransactionListRow) -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(state.closed) { if (state.closed) currentOnBack() }
    Scaffold(
        modifier = modifier
            .accountContainer(accountId)
            .fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {},
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.ScreenMargin,
                end = Spacing.ScreenMargin,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + Spacing.XXXLarge,
            ),
        ) {
            item(key = "balance") {
                BentoTile(caption = state.name, modifier = Modifier.testTag("account-balance"), span = TileSpan.Hero) {
                    AnimatedMoneyText(
                        cents = state.balanceCents,
                        modifier = Modifier.accountBalance(accountId),
                        style = MaterialTheme.typography.displaySmall,
                    )
                }
            }
            if (state.loaded) {
                item(key = "action") {
                    TextButton(
                        onClick = { confirming = true },
                        modifier = Modifier.padding(top = Spacing.Small),
                    ) {
                        Text(stringResource(if (state.canDelete) R.string.delete_account else R.string.archive_account))
                    }
                }
            }
            item(key = "transactions") {
                Text(
                    text = stringResource(R.string.transactions),
                    // Less than a section gap: the action button above brings its own padding.
                    modifier = Modifier.padding(top = Spacing.XLarge),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            transactionList(state.days, reduceMotion, onOpenTransaction)
        }
    }
    if (confirming) {
        val close = { confirming = false }
        if (state.canDelete) {
            ConfirmDialog(
                title = stringResource(R.string.delete_account_title),
                confirmLabel = stringResource(R.string.delete),
                onConfirm = { close(); onDelete() },
                onDismiss = close,
            )
        } else {
            ConfirmDialog(
                title = stringResource(R.string.archive_account_title),
                text = stringResource(R.string.archive_account_text),
                confirmLabel = stringResource(R.string.archive),
                onConfirm = { close(); onArchive() },
                onDismiss = close,
            )
        }
    }
}

private val previewState = AccountDetailState(
    loaded = true,
    name = "Current",
    balanceCents = 167_820,
    days = listOf(
        TransactionListDay(
            LocalDate.of(2026, 3, 10),
            listOf(
                TransactionListRow(3, "Food", "Mercadona", -4_235),
                TransactionListRow(4, null, null, -20_000, TransferSide(4, "Savings", outgoing = true)),
            ),
        ),
        TransactionListDay(LocalDate.of(2026, 3, 9), listOf(TransactionListRow(1, "Salary", null, 250_000))),
    ),
)

@Preview(name = "Light", showBackground = true)
@Composable
private fun AccountDetailLightPreview() {
    FinanceTrackerTheme(darkTheme = false, dynamicColor = false) {
        AccountDetailScreen(
            accountId = 1,
            state = previewState,
            onOpenTransaction = {},
            onDelete = {},
            onArchive = {},
            onBack = {},
        )
    }
}

@Preview(name = "Dark, empty", showBackground = true)
@Composable
private fun AccountDetailDarkEmptyPreview() {
    FinanceTrackerTheme(darkTheme = true, dynamicColor = false) {
        AccountDetailScreen(
            accountId = 1,
            state = AccountDetailState(loaded = true, name = "Savings", balanceCents = 0),
            onOpenTransaction = {},
            onDelete = {},
            onArchive = {},
            onBack = {},
        )
    }
}
