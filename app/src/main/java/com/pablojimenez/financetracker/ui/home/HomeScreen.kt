package com.pablojimenez.financetracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountBalance
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.MetricResult
import com.pablojimenez.financetracker.domain.NoValueReason
import com.pablojimenez.financetracker.domain.Verdict
import com.pablojimenez.financetracker.ui.components.AnimatedMoneyText
import com.pablojimenez.financetracker.ui.components.BentoTile
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
 * Home: Net worth in the hero tile, the KPIs (or, with none, the suggested KPIs), one tile per
 * active Account, and every Transaction. Tapping a KPI calls [onOpenKpi], and dragging or moving one
 * calls [onReorderKpis] with the KPIs' new order; a suggestion calls
 * [onSuggestion], "Build a Metric" [onBuildMetric]. "+" offers a Transaction or a Transfer; with
 * no Account it goes straight to [onAddTransaction], which has to ask for an Account first.
 * The gear in the top bar calls [onOpenSettings].
 * Stateless apart from whether that menu is open; [HomeViewModel] provides [state].
 */
@Composable
fun HomeScreen(
    state: HomeState,
    onAddAccount: () -> Unit,
    onAddTransaction: () -> Unit,
    onAddTransfer: () -> Unit,
    onOpenTransaction: (TransactionListRow) -> Unit,
    onOpenAccount: (Long) -> Unit,
    onOpenKpi: (Long) -> Unit,
    onReorderKpis: (List<Long>) -> Unit,
    onSuggestion: (KpiSuggestion) -> Unit,
    onBuildMetric: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { HomeTopBar(onOpenSettings) },
        floatingActionButton = {
            AddButton(
                hasAccounts = state.accounts.isNotEmpty(),
                canTransfer = state.accounts.size >= 2,
                onAddTransaction = onAddTransaction,
                onAddTransfer = onAddTransfer,
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
                bottom = innerPadding.calculateBottomPadding() + Spacing.XXXLarge + Spacing.XXXLarge,
            ),
        ) {
            item(key = "total") {
                BentoTile(
                    caption = stringResource(R.string.net_worth),
                    modifier = Modifier.testTag("balance"),
                    span = TileSpan.Hero,
                ) {
                    AnimatedMoneyText(
                        cents = state.netWorthCents,
                        modifier = Modifier.testTag("balance-amount"),
                        style = MaterialTheme.typography.displaySmall,
                    )
                }
            }
            if (state.kpis.isNotEmpty()) {
                item(key = "kpis") {
                    KpiTiles(state.kpis, onOpenKpi, onReorderKpis, Modifier.padding(top = Spacing.HeroGap))
                }
            }
            if (state.suggestions.isNotEmpty()) {
                item(key = "kpi-suggestions") {
                    KpiSuggestions(
                        suggestions = state.suggestions,
                        onSuggestion = onSuggestion,
                        onBuildMetric = onBuildMetric,
                        modifier = Modifier.padding(top = Spacing.HeroGap),
                    )
                }
            }
            item(key = "accounts") {
                // The KPIs are their own section; without one the Accounts follow the hero closely.
                val kpiSection = state.kpis.isNotEmpty() || state.suggestions.isNotEmpty()
                val gap = if (kpiSection) Spacing.SectionGap else Spacing.HeroGap
                AccountTiles(state.accounts, onOpenAccount, Modifier.padding(top = gap))
            }
            item(key = "add-account") {
                TextButton(onClick = onAddAccount, modifier = Modifier.padding(top = Spacing.Small)) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    Spacer(Modifier.width(Spacing.Small))
                    Text(stringResource(R.string.add_account))
                }
            }
            item(key = "all-transactions") {
                Text(
                    text = stringResource(R.string.all_transactions),
                    // Less than a section gap: the Add account button brings its own padding.
                    modifier = Modifier.padding(top = Spacing.XLarge),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            transactionList(state.days, reduceMotion, onOpenTransaction)
        }
    }
}

/** The bar above the hero tile, on `surface` with no tonal elevation: the gear that opens Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(onOpenSettings: () -> Unit) {
    TopAppBar(
        title = {},
        actions = {
            IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("settings")) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/** "+": a menu of Transaction and Transfer, or the Transaction path alone without Accounts. */
@Composable
private fun AddButton(
    hasAccounts: Boolean,
    canTransfer: Boolean,
    onAddTransaction: () -> Unit,
    onAddTransfer: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Box {
        FloatingActionButton(
            onClick = { if (hasAccounts) menuOpen = true else onAddTransaction() },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_menu_transaction)) },
                onClick = {
                    menuOpen = false
                    onAddTransaction()
                },
                modifier = Modifier.testTag("add-transaction"),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_menu_transfer)) },
                onClick = {
                    menuOpen = false
                    onAddTransfer()
                },
                modifier = Modifier.testTag("add-transfer"),
                enabled = canTransfer,
            )
        }
    }
}

/** The Accounts as 1×1 tiles, two per row; each grows into its detail when tapped. */
@Composable
private fun AccountTiles(accounts: List<AccountBalance>, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
        accounts.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter),
            ) {
                pair.forEach { (account, balanceCents) ->
                    BentoTile(
                        caption = account.name,
                        modifier = Modifier
                            .accountContainer(account.id)
                            .weight(1f)
                            .testTag("account-${account.id}"),
                        onClick = { onOpen(account.id) },
                    ) {
                        AnimatedMoneyText(
                            cents = balanceCents,
                            modifier = Modifier.accountBalance(account.id),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private val previewAccounts = listOf(
    AccountBalance(Account(1, "Current", AccountType.Current, 100_000), 170_000),
    AccountBalance(Account(2, "Cash", AccountType.Cash, 5_000), -10_000),
    AccountBalance(Account(3, "Savings", AccountType.Savings, 0), 0),
)

private val previewDays = listOf(
    TransactionListDay(
        LocalDate.of(2026, 3, 10),
        listOf(
            TransactionListRow(3, "Food", "Mercadona", -4_235),
            TransactionListRow(2, "Salary", null, 250_000),
            TransactionListRow(4, null, null, -20_000, TransferSide(4, "Savings", outgoing = true)),
        ),
    ),
    TransactionListDay(LocalDate.of(2026, 3, 9), listOf(TransactionListRow(1, "Coffee", null, -180))),
)

/** One KPI of each kind: a pace bar, a target as text, Better on a 2×1 tile with a sparkline, and no value. */
private val previewKpis = listOf(
    KpiTile(
        id = 1,
        name = "Groceries this month",
        value = MetricResult.Money(10_000),
        verdict = Verdict.OnPace(
            soFar = MetricResult.Money(10_000),
            projection = MetricResult.Money(28_182),
            target = MetricResult.Money(25_000),
            onTrack = false,
        ),
        periodElapsed = 11f / 31,
    ),
    KpiTile(
        id = 2,
        name = "Share spent",
        value = MetricResult.Percentage(324),
        verdict = Verdict.AgainstTarget(MetricResult.Percentage(324), MetricResult.Percentage(200), onTrack = false),
    ),
    KpiTile(
        id = 3,
        name = "Eating out",
        value = MetricResult.Money(4_250),
        verdict = Verdict.AgainstPrevious(MetricResult.Money(4_250), MetricResult.Money(6_100), better = true),
        wide = true,
        sparkline = listOf(
            MetricResult.NoValue(NoValueReason.NoTransactions),
            MetricResult.Money(5_200),
            MetricResult.Money(7_400),
            MetricResult.Money(6_100),
            MetricResult.Money(6_100),
            MetricResult.Money(4_250),
        ),
    ),
    KpiTile(
        id = 4,
        name = "Savings rate",
        value = MetricResult.NoValue(NoValueReason.DivideByZero("Income this month", MetricResult.Money(0))),
    ),
)

private val previewState = HomeState(
    netWorthCents = 160_000,
    kpis = previewKpis,
    accounts = previewAccounts,
    days = previewDays,
)

private val previewSuggestionsState = HomeState(
    netWorthCents = 160_000,
    suggestions = KpiSuggestion.entries,
    accounts = previewAccounts,
    days = previewDays,
)

@Composable
private fun HomeScreenPreview(state: HomeState, darkTheme: Boolean, dynamicColor: Boolean = false) {
    FinanceTrackerTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        HomeScreen(
            state = state,
            onAddAccount = {},
            onAddTransaction = {},
            onAddTransfer = {},
            onOpenTransaction = {},
            onOpenAccount = {},
            onOpenKpi = {},
            onReorderKpis = {},
            onSuggestion = {},
            onBuildMetric = {},
            onOpenSettings = {},
        )
    }
}

@Preview(name = "Light, empty", showBackground = true)
@Composable
private fun HomeScreenLightEmptyPreview() {
    HomeScreenPreview(HomeState(), darkTheme = false)
}

@Preview(name = "Light, KPIs", showBackground = true, heightDp = 1200)
@Composable
private fun HomeScreenLightPreview() {
    HomeScreenPreview(previewState, darkTheme = false)
}

@Preview(name = "Dark, KPIs", showBackground = true, heightDp = 1200)
@Composable
private fun HomeScreenDarkPreview() {
    HomeScreenPreview(previewState, darkTheme = true)
}

@Preview(name = "Light, suggested KPIs", showBackground = true, heightDp = 1200)
@Composable
private fun HomeScreenLightSuggestionsPreview() {
    HomeScreenPreview(previewSuggestionsState, darkTheme = false)
}

@Preview(name = "Dark, suggested KPIs", showBackground = true, heightDp = 1200)
@Composable
private fun HomeScreenDarkSuggestionsPreview() {
    HomeScreenPreview(previewSuggestionsState, darkTheme = true)
}

/** Material You, as on Android 12+; colours depend on the preview renderer's wallpaper. */
@Preview(name = "Material You", showBackground = true, apiLevel = 36)
@Composable
private fun HomeScreenDynamicPreview() {
    HomeScreenPreview(previewState, darkTheme = false, dynamicColor = true)
}
