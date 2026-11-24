package com.pablojimenez.financetracker.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.domain.FinanceRepository
import com.pablojimenez.financetracker.ui.account.AccountDetailScreen
import com.pablojimenez.financetracker.ui.account.AccountDetailViewModel
import com.pablojimenez.financetracker.ui.account.AccountFormScreen
import com.pablojimenez.financetracker.ui.account.AccountFormViewModel
import com.pablojimenez.financetracker.ui.categories.CategoriesScreen
import com.pablojimenez.financetracker.ui.categories.CategoriesViewModel
import com.pablojimenez.financetracker.ui.components.LocalSharedTransitionScope
import com.pablojimenez.financetracker.ui.components.TransactionListRow
import com.pablojimenez.financetracker.ui.components.accountContainer
import com.pablojimenez.financetracker.ui.home.HomeScreen
import com.pablojimenez.financetracker.ui.home.HomeViewModel
import com.pablojimenez.financetracker.ui.home.suggestionNames
import com.pablojimenez.financetracker.ui.kpi.KpiDetailScreen
import com.pablojimenez.financetracker.ui.kpi.KpiDetailViewModel
import com.pablojimenez.financetracker.ui.merchants.MerchantsScreen
import com.pablojimenez.financetracker.ui.merchants.MerchantsViewModel
import com.pablojimenez.financetracker.ui.metric.MetricBuilderScreen
import com.pablojimenez.financetracker.ui.metric.MetricBuilderViewModel
import com.pablojimenez.financetracker.ui.metrics.MetricsScreen
import com.pablojimenez.financetracker.ui.metrics.MetricsViewModel
import com.pablojimenez.financetracker.ui.settings.SettingsScreen
import com.pablojimenez.financetracker.ui.theme.FinanceMotion
import com.pablojimenez.financetracker.ui.theme.LocalReduceMotion
import com.pablojimenez.financetracker.ui.transaction.TransactionFormScreen
import com.pablojimenez.financetracker.ui.transaction.TransactionFormViewModel
import com.pablojimenez.financetracker.ui.transaction.merchantSuggestions
import com.pablojimenez.financetracker.ui.transfer.TransferFormScreen
import com.pablojimenez.financetracker.ui.transfer.TransferFormViewModel
import java.text.DecimalFormatSymbols
import java.time.Clock

/** A screen of the app; the back stack is a list of these. */
private sealed interface Destination {
    /** What the back stack saves for this screen; [fromSaveKey] reads it back. */
    val saveKey: String

    data object Home : Destination {
        override val saveKey = "home"
    }

    /** The Account form; with [thenAddTransaction] the Transaction form follows a save. */
    data class AddAccount(val thenAddTransaction: Boolean) : Destination {
        override val saveKey = if (thenAddTransaction) "account-then-transaction" else "account"
    }

    data object AddTransaction : Destination {
        override val saveKey = "transaction"
    }

    data class EditTransaction(val transactionId: Long) : Destination {
        override val saveKey = "$EDIT_TRANSACTION$transactionId"
    }

    data object AddTransfer : Destination {
        override val saveKey = "transfer"
    }

    data class EditTransfer(val transferId: Long) : Destination {
        override val saveKey = "$EDIT_TRANSFER$transferId"
    }

    data class AccountDetail(val accountId: Long) : Destination {
        override val saveKey = "$ACCOUNT_DETAIL$accountId"
    }

    /** The second top-level screen, above Home in the back stack. */
    data object Metrics : Destination {
        override val saveKey = "metrics"
    }

    data object NewMetric : Destination {
        override val saveKey = "metric"
    }

    data class EditMetric(val metricId: Long) : Destination {
        override val saveKey = "$EDIT_METRIC$metricId"
    }

    /** A KPI's detail, which its tile on Home grows into. */
    data class KpiDetail(val metricId: Long) : Destination {
        override val saveKey = "$KPI_DETAIL$metricId"
    }

    /** Settings, opened from Home's gear. */
    data object Settings : Destination {
        override val saveKey = "settings"
    }

    /** The Category tree, opened from Settings. */
    data object Categories : Destination {
        override val saveKey = "categories"
    }

    /** Every Merchant, opened from Settings. */
    data object Merchants : Destination {
        override val saveKey = "merchants"
    }

    companion object {
        private const val EDIT_TRANSACTION = "transaction-"
        private const val EDIT_TRANSFER = "transfer-"
        private const val ACCOUNT_DETAIL = "account-detail-"
        private const val EDIT_METRIC = "metric-"
        private const val KPI_DETAIL = "kpi-detail-"
        private val fixed =
            listOf(
                Home, AddAccount(false), AddAccount(true), AddTransaction, AddTransfer, Metrics, NewMetric,
                Settings, Categories, Merchants,
            )
        private val withId = mapOf<String, (Long) -> Destination>(
            EDIT_TRANSACTION to ::EditTransaction,
            EDIT_TRANSFER to ::EditTransfer,
            ACCOUNT_DETAIL to ::AccountDetail,
            EDIT_METRIC to ::EditMetric,
            KPI_DETAIL to ::KpiDetail,
        )

        fun fromSaveKey(key: String): Destination? =
            fixed.firstOrNull { it.saveKey == key }
                ?: withId.entries.firstNotNullOfOrNull { (prefix, destination) ->
                    key.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.toLongOrNull()?.let(destination)
                }
    }
}

private val BackStackSaver = listSaver<SnapshotStateList<Destination>, String>(
    save = { stack -> stack.map { it.saveKey } },
    restore = { keys ->
        val stack = keys.mapNotNull(Destination::fromSaveKey)
        val valid = stack.firstOrNull() == Destination.Home
        (if (valid) stack else listOf(Destination.Home)).toMutableStateList()
    },
)

/**
 * The whole app: Home and Metrics, the two top-level screens with the Home · Metrics bottom bar,
 * and the detail screens and forms they open (without the bar), kept in [repository]. [clock]
 * says what today is. Home and Metrics switch with a fade through; forms enter on the shared X
 * axis; an Account's or a KPI's tile grows into its detail screen (container transform). Back, including
 * predictive back, reverses each.
 */
@Composable
fun FinanceApp(repository: FinanceRepository, clock: Clock, modifier: Modifier = Modifier) {
    val backStack = rememberSaveable(saver = BackStackSaver) {
        mutableStateListOf<Destination>(Destination.Home)
    }

    /** Leaves [current] when it is still on top, then opens [next] if given. */
    fun finish(current: Destination, next: Destination? = null) {
        if (backStack.lastOrNull() != current || backStack.size == 1) return
        backStack.removeAt(backStack.lastIndex)
        next?.let(backStack::add)
    }

    /** Goes to a top-level screen: Metrics sits on top of Home, so Home pops everything else. */
    fun select(topLevel: Destination) {
        if (backStack.lastOrNull() == topLevel) return
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        if (topLevel != Destination.Home) backStack.add(topLevel)
    }

    val decimalSeparator = DecimalFormatSymbols.getInstance(LocalLocale.current.platformLocale).decimalSeparator
    val transitions = currentSharedAxisX()
    val top = backStack.lastOrNull()
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        // Each screen handles the system bars itself; this only makes room for the bottom bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (top == Destination.Home || top == Destination.Metrics) {
                TopLevelBar(selected = top, onSelect = ::select)
            }
        },
    ) { barPadding ->
        SharedTransitionLayout(
            modifier = Modifier
                .padding(barPadding)
                .consumeWindowInsets(barPadding),
        ) {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    sharedTransitionScope = this,
                    transitionSpec = { transitions.forward() },
                    popTransitionSpec = { transitions.back() },
                    predictivePopTransitionSpec = { transitions.predictiveBack() },
                    entryProvider = entryProvider {
                        entry<Destination.Home> {
                            val viewModel = viewModel { HomeViewModel(repository, clock) }
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            val suggestionNames = suggestionNames()
                            HomeScreen(
                                state = state,
                                onAddAccount = { backStack.add(Destination.AddAccount(thenAddTransaction = false)) },
                                // A Transaction needs an Account, so without one "+" asks for it first.
                                onAddTransaction = {
                                    backStack.add(
                                        if (state.accounts.isEmpty()) {
                                            Destination.AddAccount(thenAddTransaction = true)
                                        } else {
                                            Destination.AddTransaction
                                        },
                                    )
                                },
                                onAddTransfer = { backStack.add(Destination.AddTransfer) },
                                onOpenTransaction = { row -> backStack.add(row.destination()) },
                                onOpenAccount = { id -> backStack.add(Destination.AccountDetail(id)) },
                                onOpenKpi = { id -> backStack.add(Destination.KpiDetail(id)) },
                                onReorderKpis = viewModel::saveKpiOrder,
                                onSuggestion = { viewModel.addSuggestion(it, suggestionNames) },
                                onBuildMetric = { backStack.add(Destination.NewMetric) },
                                onOpenSettings = { backStack.add(Destination.Settings) },
                            )
                        }
                        entry<Destination.Settings> { key ->
                            SettingsScreen(
                                onOpenCategories = { backStack.add(Destination.Categories) },
                                onOpenMerchants = { backStack.add(Destination.Merchants) },
                                onBack = { finish(key) },
                            )
                        }
                        entry<Destination.Categories> { key ->
                            val viewModel = viewModel { CategoriesViewModel(repository) }
                            val categories by viewModel.categories.collectAsStateWithLifecycle()
                            CategoriesScreen(
                                categories = categories,
                                dialog = viewModel.editor.state,
                                dialogActions = viewModel.editor,
                                deletion = viewModel.deletion,
                                actions = viewModel,
                                onBack = { finish(key) },
                            )
                        }
                        entry<Destination.Merchants> { key ->
                            val viewModel = viewModel { MerchantsViewModel(repository) }
                            val merchants by viewModel.merchants.collectAsStateWithLifecycle()
                            val categories by viewModel.categories.collectAsStateWithLifecycle()
                            MerchantsScreen(
                                merchants = merchants,
                                categories = categories,
                                dialog = viewModel.dialog,
                                deletion = viewModel.deletion,
                                categoryDialog = viewModel.categoryEditor.state,
                                categoryDialogActions = viewModel.categoryEditor,
                                actions = viewModel,
                                onBack = { finish(key) },
                            )
                        }
                        entry<Destination.Metrics>(metadata = transitions.fadeThrough()) {
                            val viewModel = viewModel { MetricsViewModel(repository, clock) }
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            MetricsScreen(
                                state = state,
                                onNewMetric = { backStack.add(Destination.NewMetric) },
                                onOpenMetric = { id -> backStack.add(Destination.EditMetric(id)) },
                                onPinChange = viewModel::onPinChange,
                            )
                        }
                        entry<Destination.NewMetric> { key ->
                            MetricBuilder(
                                viewModel = viewModel {
                                    MetricBuilderViewModel(repository, clock, decimalSeparator = decimalSeparator)
                                },
                                onDone = { finish(key) },
                            )
                        }
                        entry<Destination.EditMetric> { key ->
                            MetricBuilder(
                                viewModel = viewModel {
                                    MetricBuilderViewModel(repository, clock, key.metricId, decimalSeparator)
                                },
                                onDone = { finish(key) },
                            )
                        }
                        entry<Destination.AccountDetail>(metadata = transitions.containerTransform()) { key ->
                            val viewModel = viewModel { AccountDetailViewModel(repository, key.accountId) }
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            AccountDetailScreen(
                                accountId = key.accountId,
                                state = state,
                                onOpenTransaction = { row -> backStack.add(row.destination()) },
                                onDelete = viewModel::delete,
                                onArchive = viewModel::archive,
                                onBack = { finish(key) },
                            )
                        }
                        entry<Destination.KpiDetail>(metadata = transitions.containerTransform()) { key ->
                            val viewModel = viewModel { KpiDetailViewModel(repository, clock, key.metricId) }
                            val state by viewModel.state.collectAsStateWithLifecycle()
                            KpiDetailScreen(
                                metricId = key.metricId,
                                state = state,
                                onWideChange = viewModel::setWide,
                                onEdit = { backStack.add(Destination.EditMetric(key.metricId)) },
                                onRemove = viewModel::remove,
                                onBack = { finish(key) },
                            )
                        }
                        entry<Destination.AddAccount> { key ->
                            val viewModel = viewModel { AccountFormViewModel(repository) }
                            AccountFormScreen(
                                state = viewModel.state,
                                onNameChange = viewModel::onNameChange,
                                onTypeChange = viewModel::onTypeChange,
                                onStartingBalanceChange = viewModel::onStartingBalanceChange,
                                onSave = viewModel::save,
                                onDone = {
                                    finish(key, next = Destination.AddTransaction.takeIf { key.thenAddTransaction })
                                },
                                onBack = { finish(key) },
                            )
                        }
                        entry<Destination.AddTransaction> { key ->
                            TransactionForm(
                                viewModel = viewModel { TransactionFormViewModel(repository, clock) },
                                onDone = { finish(key) },
                            )
                        }
                        entry<Destination.EditTransaction> { key ->
                            TransactionForm(
                                viewModel = viewModel {
                                    TransactionFormViewModel(repository, clock, key.transactionId, decimalSeparator)
                                },
                                onDone = { finish(key) },
                            )
                        }
                        entry<Destination.AddTransfer> { key ->
                            TransferForm(
                                viewModel = viewModel { TransferFormViewModel(repository, clock) },
                                onDone = { finish(key) },
                            )
                        }
                        entry<Destination.EditTransfer> { key ->
                            TransferForm(
                                viewModel = viewModel {
                                    TransferFormViewModel(repository, clock, key.transferId, decimalSeparator)
                                },
                                onDone = { finish(key) },
                            )
                        }
                    },
                )
            }
        }
    }
}

/** The Home · Metrics bottom bar, in greys: the selected item has a grey pill and a filled icon. */
@Composable
private fun TopLevelBar(selected: Destination, onSelect: (Destination) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = scheme.onSurface,
        selectedTextColor = scheme.onSurface,
        indicatorColor = scheme.surfaceContainerHighest,
        unselectedIconColor = scheme.onSurfaceVariant,
        unselectedTextColor = scheme.onSurfaceVariant,
    )
    NavigationBar(containerColor = scheme.surfaceContainer, tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = selected == Destination.Home,
            onClick = { onSelect(Destination.Home) },
            icon = {
                val icon = if (selected == Destination.Home) R.drawable.ic_home_filled else R.drawable.ic_home
                Icon(painterResource(icon), contentDescription = null)
            },
            label = { Text(stringResource(R.string.nav_home)) },
            modifier = Modifier.testTag("nav-home"),
            colors = colors,
        )
        NavigationBarItem(
            selected = selected == Destination.Metrics,
            onClick = { onSelect(Destination.Metrics) },
            icon = { Icon(painterResource(R.drawable.ic_bar_chart), contentDescription = null) },
            label = { Text(stringResource(R.string.nav_metrics)) },
            modifier = Modifier.testTag("nav-metrics"),
            colors = colors,
        )
    }
}

@Composable
private fun MetricBuilder(viewModel: MetricBuilderViewModel, onDone: () -> Unit) {
    val result by viewModel.result.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val merchants by viewModel.merchants.collectAsStateWithLifecycle()
    val sides by viewModel.sides.collectAsStateWithLifecycle()
    MetricBuilderScreen(
        state = viewModel.state,
        result = result,
        categories = categories,
        accounts = accounts,
        merchants = merchants,
        sides = sides,
        actions = viewModel,
        onDone = onDone,
        onBack = onDone,
    )
}

/** A Transfer leg opens its Transfer; any other row its Transaction. */
private fun TransactionListRow.destination(): Destination =
    transfer?.let { Destination.EditTransfer(it.transferId) } ?: Destination.EditTransaction(id)

@Composable
private fun TransactionForm(viewModel: TransactionFormViewModel, onDone: () -> Unit) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val merchants by viewModel.merchants.collectAsStateWithLifecycle()
    val state = viewModel.state
    TransactionFormScreen(
        state = state,
        accounts = accounts,
        categories = categories,
        merchantSuggestions = merchantSuggestions(state, merchants, categories),
        categoryDialog = viewModel.categoryEditor.state,
        categoryDialogActions = viewModel.categoryEditor,
        actions = viewModel,
        onDone = onDone,
        onBack = onDone,
    )
}

@Composable
private fun TransferForm(viewModel: TransferFormViewModel, onDone: () -> Unit) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    TransferFormScreen(
        state = viewModel.state,
        accounts = accounts,
        actions = viewModel,
        onDone = onDone,
        onBack = onDone,
    )
}

/**
 * Shared axis X (docs/design.md §9): the new screen slides 30 dp in from the end and fades in
 * over 400 ms while the old one slides out and fades out first; back runs the other way in
 * 300 ms. With [LocalReduceMotion] both are plain fades.
 */
private class SharedAxisX(
    private val slidePx: Int,
    private val reduceMotion: Boolean,
    private val rtl: Boolean,
) {
    fun forward(): ContentTransform =
        transform(towardsEnd = true, FinanceMotion.DURATION_LONG_MS)

    fun back(): ContentTransform =
        transform(towardsEnd = false, FinanceMotion.DURATION_MEDIUM_MS).apply { targetContentZIndex = -1f }

    /**
     * The metadata of a screen that a tile grows into: the screens themselves do not move, so the
     * shared bounds (see [accountContainer]) carry the transition, forwards, back and with the
     * back gesture, while the screen underneath fades away over the hero duration. With
     * [LocalReduceMotion] there are no shared bounds and screens simply fade.
     */
    fun containerTransform(): Map<String, Any> {
        val transform = {
            if (reduceMotion) {
                fadeIn(FinanceMotion.effects()) togetherWith fadeOut(FinanceMotion.effects())
            } else {
                EnterTransition.None togetherWith
                    fadeOut(tween(FinanceMotion.DURATION_HERO_MS, easing = FinanceMotion.EasingStandard))
            }
        }
        val back = { transform().apply { targetContentZIndex = -1f } }
        return NavDisplay.transitionSpec { transform() } +
            NavDisplay.popTransitionSpec { back() } +
            NavDisplay.predictivePopTransitionSpec { back() }
    }

    /**
     * The metadata of a top-level screen (docs/design.md §9, fade through): the old screen fades
     * out over the first 35 % of the medium duration, then the new one fades in from 0.92 scale.
     * With [LocalReduceMotion] it is a plain fade.
     */
    fun fadeThrough(): Map<String, Any> {
        val transform = {
            val duration = FinanceMotion.DURATION_MEDIUM_MS
            val fadeOutMs = (duration * FADE_OUT_FRACTION).toInt()
            val fadeIn = fadeIn(tween(duration - fadeOutMs, delayMillis = fadeOutMs, easing = FinanceMotion.EasingEnter))
            val enter = if (reduceMotion) {
                fadeIn
            } else {
                val scale = tween<Float>(duration - fadeOutMs, delayMillis = fadeOutMs, easing = FinanceMotion.EasingEnter)
                fadeIn + scaleIn(scale, initialScale = FADE_THROUGH_SCALE)
            }
            enter togetherWith fadeOut(tween(fadeOutMs, easing = FinanceMotion.EasingExit))
        }
        return NavDisplay.transitionSpec { transform() } +
            NavDisplay.popTransitionSpec { transform().apply { targetContentZIndex = -1f } }
    }

    /** The outgoing screen shrinks towards 0.9 and fades as the back gesture progresses. */
    fun predictiveBack(): ContentTransform {
        val enter = fadeIn(FinanceMotion.effects())
        val exit = if (reduceMotion) {
            fadeOut(FinanceMotion.effects())
        } else {
            scaleOut(FinanceMotion.effects(), targetScale = PREDICTIVE_BACK_SCALE) + fadeOut(FinanceMotion.effects())
        }
        return (enter togetherWith exit).apply { targetContentZIndex = -1f }
    }

    private fun transform(towardsEnd: Boolean, duration: Int): ContentTransform {
        val fadeOutMs = (duration * FADE_OUT_FRACTION).toInt()
        val fadeIn = fadeIn(tween(duration - fadeOutMs, delayMillis = fadeOutMs, easing = FinanceMotion.EasingEnter))
        val fadeOut = fadeOut(tween(fadeOutMs, easing = FinanceMotion.EasingExit))
        if (reduceMotion) return fadeIn togetherWith fadeOut
        // Forward, the new screen comes from the end side: the right, or the left in RTL.
        val sign = (if (towardsEnd) 1 else -1) * (if (rtl) -1 else 1)
        val slide = tween<IntOffset>(duration, easing = FinanceMotion.EasingStandard)
        return (slideInHorizontally(slide) { sign * slidePx } + fadeIn) togetherWith
            (slideOutHorizontally(slide) { -sign * slidePx } + fadeOut)
    }

    private companion object {
        /** The outgoing screen is gone after the first 35 % (Material shared axis). */
        const val FADE_OUT_FRACTION = 0.35f
        const val PREDICTIVE_BACK_SCALE = 0.9f
        const val FADE_THROUGH_SCALE = 0.92f
    }
}

@Composable
private fun currentSharedAxisX(): SharedAxisX {
    val slidePx = with(LocalDensity.current) { FinanceMotion.SharedAxisSlideDistance.roundToPx() }
    return SharedAxisX(
        slidePx = slidePx,
        reduceMotion = LocalReduceMotion.current,
        rtl = LocalLayoutDirection.current == LayoutDirection.Rtl,
    )
}
