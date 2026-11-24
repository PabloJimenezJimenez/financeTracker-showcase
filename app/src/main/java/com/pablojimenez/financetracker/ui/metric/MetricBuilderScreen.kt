package com.pablojimenez.financetracker.ui.metric

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.AverageDivisor
import com.pablojimenez.financetracker.domain.Category
import com.pablojimenez.financetracker.domain.CategoryKind
import com.pablojimenez.financetracker.domain.Combination
import com.pablojimenez.financetracker.domain.CombinationKind
import com.pablojimenez.financetracker.domain.KpiDirection
import com.pablojimenez.financetracker.domain.Measure
import com.pablojimenez.financetracker.domain.Merchant
import com.pablojimenez.financetracker.domain.Metric
import com.pablojimenez.financetracker.domain.MetricPeriod
import com.pablojimenez.financetracker.domain.MetricResult
import com.pablojimenez.financetracker.domain.MetricSource
import com.pablojimenez.financetracker.domain.NoValueReason
import com.pablojimenez.financetracker.domain.branches
import com.pablojimenez.financetracker.domain.kind
import com.pablojimenez.financetracker.domain.matching
import com.pablojimenez.financetracker.ui.components.BentoTile
import com.pablojimenez.financetracker.ui.components.ConfirmDialog
import com.pablojimenez.financetracker.ui.components.DateDialog
import com.pablojimenez.financetracker.ui.components.DeleteAction
import com.pablojimenez.financetracker.ui.components.FormScaffold
import com.pablojimenez.financetracker.ui.components.MetricResultText
import com.pablojimenez.financetracker.ui.components.PickerField
import com.pablojimenez.financetracker.ui.components.SaveButton
import com.pablojimenez.financetracker.ui.components.TileSpan
import com.pablojimenez.financetracker.ui.components.fieldError
import com.pablojimenez.financetracker.ui.components.label
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the owner can do in the Metric builder; each call maps to [MetricBuilderViewModel]. */
interface MetricBuilderActions {
    fun onNameChange(name: String)
    fun onSourceChange(source: MetricSource)
    fun onCombine()
    fun onCombinationKindChange(kind: CombinationKind)
    fun onSideAChange(metricId: Long)
    fun onSideBChange(metricId: Long)
    fun onToggleCategory(categoryId: Long)
    fun onToggleUncategorised()
    fun onAllCategories()
    fun onToggleAccount(accountId: Long)
    fun onAllAccounts()
    fun onToggleMerchant(merchantId: Long)
    fun onAllMerchants()
    fun onMeasureChange(measure: Measure)
    fun onDivisorChange(divisor: AverageDivisor)
    fun onPeriodChange(period: MetricPeriod)
    fun onCustomPeriod()
    fun onCustomStartChange(start: LocalDate)
    fun onCustomEndChange(end: LocalDate)
    fun onPinChange(pinned: Boolean)
    fun onTargetChange(text: String)
    fun onToggleDirection(direction: KpiDirection)
    fun onSave()
    fun onDelete()
}

/**
 * The one-screen Metric builder ("New Metric" or "Edit Metric"): the live [result] on top in the
 * hero tile (tagged `metric-result`), then the name and the pieces; an average per day, week or
 * month also offers what it divides by. [categories], [accounts] and [merchants] (by name) are what
 * the filters offer. A
 * combined Metric shows its kind and its sides instead, picked from [sides]. Last, "Pin as KPI"
 * (tagged `metric-pin`) and, while on, the KPI's target and direction. While editing, the
 * top bar offers Delete; a refused one says under the result which Metrics use it. Stateless
 * apart from which picker is open; [MetricBuilderViewModel] holds [state]. [onDone] runs once it
 * is saved or deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricBuilderScreen(
    state: MetricBuilderState,
    result: MetricResult?,
    categories: List<Category>,
    accounts: List<Account>,
    merchants: List<Merchant>,
    sides: List<Metric>,
    actions: MetricBuilderActions,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(Picker.None) }
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(state.deleted) { if (state.deleted) currentOnDone() }
    val metric = state.metric
    val combination = metric.combination
    FormScaffold(
        title = stringResource(if (state.editing) R.string.edit_metric else R.string.new_metric),
        onBack = onBack,
        modifier = modifier,
        actions = { if (state.editing) DeleteAction(onClick = { picking = Picker.ConfirmDelete }) },
        saveButton = { SaveButton(saved = state.saved, onClick = actions::onSave, onDone = onDone) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            LiveResult(metric, result)
            if (state.usedBy.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.metric_used_by, state.usedBy.joinToString(", ")),
                    // Announced when it appears: the dialog has closed and the form may be scrolled.
                    modifier = Modifier
                        .testTag("metric-used-by")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        OutlinedTextField(
            value = metric.name,
            onValueChange = actions::onNameChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("metric-name"),
            label = { Text(stringResource(R.string.metric_name)) },
            isError = state.nameError,
            supportingText = fieldError(state.nameError, R.string.error_name),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        SourceToggle(
            selected = metric.source.takeIf { combination == null },
            onSelect = actions::onSourceChange,
            onCombine = actions::onCombine,
        )
        if (combination == null) {
            SimplePieces(
                metric = metric,
                categories = categories,
                accounts = accounts,
                merchants = merchants,
                actions = actions,
                onPickCategories = { picking = Picker.Categories },
                onPickMerchants = { picking = Picker.Merchants },
            )
        } else {
            CombinedPieces(
                combination = combination,
                sides = sides,
                actions = actions,
                onPickA = { picking = Picker.SideA },
                onPickB = { picking = Picker.SideB },
            )
        }
        Section(stringResource(R.string.metric_period)) {
            listOf(MetricPeriod.ThisWeek, MetricPeriod.ThisMonth, MetricPeriod.ThisYear).forEach { period ->
                Chip(
                    label = stringResource(period.label),
                    selected = period == metric.period,
                    onClick = { actions.onPeriodChange(period) },
                    tag = "metric-period-$period",
                )
            }
            Chip(
                label = stringResource(R.string.period_custom),
                selected = metric.period is MetricPeriod.Custom,
                onClick = {
                    actions.onCustomPeriod()
                    picking = Picker.CustomStart
                },
                tag = "metric-period-Custom",
            )
        }
        if (combination != null) {
            Text(
                text = stringResource(R.string.metric_period_both_sides),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        (metric.period as? MetricPeriod.Custom)?.let { custom ->
            CustomDates(
                custom = custom,
                onPickStart = { picking = Picker.CustomStart },
                onPickEnd = { picking = Picker.CustomEnd },
            )
        }
        KpiPieces(state, actions)
    }
    val close = { picking = Picker.None }
    val custom = metric.period as? MetricPeriod.Custom
    when (picking) {
        Picker.None -> Unit
        Picker.Categories -> CategoryFilterSheet(
            categories = categories.filter { it.kind == metric.source.kind },
            metric = metric,
            actions = actions,
            onDismiss = close,
        )
        Picker.Merchants -> MerchantFilterSheet(
            merchants = merchants,
            picked = metric.merchantIds,
            actions = actions,
            onDismiss = close,
        )
        Picker.CustomStart -> custom?.let {
            DateDialog(it.start, onPick = { date -> actions.onCustomStartChange(date); close() }, onDismiss = close)
        }
        Picker.CustomEnd -> custom?.let {
            DateDialog(it.end, onPick = { date -> actions.onCustomEndChange(date); close() }, onDismiss = close)
        }
        Picker.SideA -> SideSheet(
            title = stringResource(R.string.metric_side_a),
            sides = sides,
            selectedId = combination?.a,
            onPick = { actions.onSideAChange(it); close() },
            onDismiss = close,
        )
        Picker.SideB -> SideSheet(
            title = stringResource(R.string.metric_side_b),
            sides = sides,
            selectedId = combination?.b,
            onPick = { actions.onSideBChange(it); close() },
            onDismiss = close,
        )
        Picker.ConfirmDelete -> ConfirmDialog(
            title = stringResource(R.string.delete_metric_title),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { close(); actions.onDelete() },
            onDismiss = close,
        )
    }
}

/**
 * The "Pin as KPI" row and, while it is on, the target field (tagged `metric-target`) and the
 * single choice of direction (chips tagged `metric-direction-<Direction>` in a group tagged
 * `metric-direction`), which says when a target needs one.
 */
@Composable
private fun KpiPieces(state: MetricBuilderState, actions: MetricBuilderActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.MinTouchTarget)
            .toggleable(value = state.pinned, role = Role.Switch, onValueChange = actions::onPinChange)
            .testTag("metric-pin"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.pin_as_kpi),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(Spacing.Small))
        Switch(checked = state.pinned, onCheckedChange = null)
    }
    if (!state.pinned) return
    OutlinedTextField(
        value = state.target,
        onValueChange = actions::onTargetChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("metric-target"),
        label = { Text(stringResource(R.string.kpi_target)) },
        isError = state.targetError,
        supportingText = fieldError(state.targetError, R.string.kpi_error_target),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
    val directionError = stringResource(R.string.kpi_error_direction)
    Column(
        modifier = Modifier
            .testTag("metric-direction")
            .semantics { if (state.directionError) error(directionError) },
        verticalArrangement = Arrangement.spacedBy(Spacing.XSmall),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            KpiDirection.entries.forEach { direction ->
                Chip(
                    label = stringResource(direction.label),
                    selected = direction == state.direction,
                    onClick = { actions.onToggleDirection(direction) },
                    tag = "metric-direction-${direction.name}",
                )
            }
        }
        if (state.directionError) {
            Text(
                text = directionError,
                // Announced when it appears, like a field's error.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private val KpiDirection.label: Int
    @StringRes get() = when (this) {
        KpiDirection.LowerIsBetter -> R.string.kpi_lower_is_better
        KpiDirection.HigherIsBetter -> R.string.kpi_higher_is_better
    }

/**
 * A simple Metric's pieces: its Categories, Merchants, Accounts, measure and, for an average, its
 * divisor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimplePieces(
    metric: Metric,
    categories: List<Category>,
    accounts: List<Account>,
    merchants: List<Merchant>,
    actions: MetricBuilderActions,
    onPickCategories: () -> Unit,
    onPickMerchants: () -> Unit,
) {
    PickerField(
        value = categorySummary(metric, categories),
        label = stringResource(R.string.metric_categories),
        onClick = onPickCategories,
        modifier = Modifier.testTag("metric-categories"),
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) },
    )
    PickerField(
        value = merchantSummary(metric.merchantIds, merchants),
        label = stringResource(R.string.metric_merchants),
        onClick = onPickMerchants,
        modifier = Modifier.testTag("metric-merchants"),
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) },
    )
    Section(stringResource(R.string.metric_accounts)) {
        Chip(
            label = stringResource(R.string.metric_all_accounts),
            selected = metric.accountIds.isEmpty(),
            onClick = actions::onAllAccounts,
            tag = "metric-account-all",
        )
        accounts.forEach { account ->
            Chip(
                label = account.name,
                selected = account.id in metric.accountIds,
                onClick = { actions.onToggleAccount(account.id) },
                tag = "metric-account-${account.id}",
            )
        }
    }
    Section(stringResource(R.string.metric_measure)) {
        metric.period.measures.forEach { measure ->
            Chip(
                label = stringResource(measure.label),
                selected = measure == metric.measure,
                onClick = { actions.onMeasureChange(measure) },
                tag = "metric-measure-${measure.name}",
            )
        }
    }
    metric.measure.subPeriod?.let { subPeriod ->
        Section(stringResource(R.string.metric_divide_by)) {
            AverageDivisor.entries.forEach { divisor ->
                Chip(
                    label = stringResource(divisor.label(subPeriod)),
                    selected = divisor == metric.divisor,
                    onClick = { actions.onDivisorChange(divisor) },
                    tag = "metric-divisor-${divisor.name}",
                )
            }
        }
    }
}

/**
 * A combined Metric's pieces: its kind (chips tagged `metric-combination-<Kind>`), then the
 * "Metric A" and, unless it is a change, "Metric B" pickers (tagged `metric-operand-a` and `-b`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CombinedPieces(
    combination: Combination,
    sides: List<Metric>,
    actions: MetricBuilderActions,
    onPickA: () -> Unit,
    onPickB: () -> Unit,
) {
    Section(stringResource(R.string.metric_combine)) {
        CombinationKind.entries.forEach { kind ->
            Chip(
                label = stringResource(kind.label),
                selected = kind == combination.kind,
                onClick = { actions.onCombinationKindChange(kind) },
                tag = "metric-combination-${kind.name}",
            )
        }
    }
    PickerField(
        value = sides.firstOrNull { it.id == combination.a }?.name.orEmpty(),
        label = stringResource(R.string.metric_side_a),
        onClick = onPickA,
        modifier = Modifier.testTag("metric-operand-a"),
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) },
    )
    if (combination.kind != CombinationKind.Change) {
        PickerField(
            value = sides.firstOrNull { it.id == combination.b }?.name.orEmpty(),
            label = stringResource(R.string.metric_side_b),
            onClick = onPickB,
            modifier = Modifier.testTag("metric-operand-b"),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) },
        )
    }
}

/**
 * A sheet listing [sides] by name (rows tagged `metric-operand-option-<id>`); a tap picks one.
 * With none, it says to save a simple Metric first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SideSheet(
    title: String,
    sides: List<Metric>,
    selectedId: Long?,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.XLarge),
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = Spacing.ScreenMargin, vertical = Spacing.Small),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (sides.isEmpty()) {
                Text(
                    text = stringResource(R.string.metric_no_sides),
                    modifier = Modifier.padding(horizontal = Spacing.ScreenMargin, vertical = Spacing.Small),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            sides.forEach { side ->
                val selected = side.id == selectedId
                val highlight = if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                } else {
                    Modifier
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.MinTouchTarget)
                        .then(highlight)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(side.id) })
                        .padding(horizontal = Spacing.ScreenMargin)
                        .testTag("metric-operand-option-${side.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = side.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (selected) {
                        Spacer(Modifier.width(Spacing.Small))
                        Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                    }
                }
            }
        }
    }
}

private enum class Picker { None, Categories, Merchants, CustomStart, CustomEnd, SideA, SideB, ConfirmDelete }

/** The hero tile: what the Metric measures as its caption, and its value as it is built. */
@Composable
private fun LiveResult(metric: Metric, result: MetricResult?) {
    val combination = metric.combination
    BentoTile(
        caption = if (combination == null) {
            stringResource(
                R.string.metric_summary,
                stringResource(metric.source.label),
                stringResource(metric.measure.label),
                stringResource(metric.period.label),
            )
        } else {
            stringResource(
                R.string.metric_combined_summary,
                stringResource(combination.kind.label),
                stringResource(metric.period.label),
            )
        },
        // A new value, or the reason there is none (e.g. after a refused Save), is announced.
        modifier = Modifier
            .testTag("metric-result")
            .semantics { liveRegion = LiveRegionMode.Polite },
        span = TileSpan.Hero,
    ) {
        if (result != null) {
            MetricResultText(
                result = result,
                style = MaterialTheme.typography.displaySmall,
                noteColor = LocalContentColor.current,
            )
        }
    }
}

/** Spending · Income · Combined; [selected] is null for a combined Metric. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceToggle(selected: MetricSource?, onSelect: (MetricSource) -> Unit, onCombine: () -> Unit) {
    val count = MetricSource.entries.size + 1
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        activeContentColor = MaterialTheme.colorScheme.onSurface,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        MetricSource.entries.forEachIndexed { index, source ->
            SegmentedButton(
                selected = source == selected,
                onClick = { onSelect(source) },
                shape = SegmentedButtonDefaults.itemShape(index, count),
                modifier = Modifier.testTag("metric-source-${source.name}"),
                colors = colors,
                label = { Text(stringResource(source.label)) },
            )
        }
        SegmentedButton(
            selected = selected == null,
            onClick = onCombine,
            shape = SegmentedButtonDefaults.itemShape(count - 1, count),
            modifier = Modifier.testTag("metric-source-Combined"),
            colors = colors,
            label = { Text(stringResource(R.string.metric_combined)) },
        )
    }
}

/** A caption over a wrapping row of chips. */
@Composable
private fun Section(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) { chips() }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit, tag: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.testTag(tag),
        leadingIcon = if (selected) {
            { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
        } else {
            null
        },
        colors = greyChipColors(),
    )
}

/** Selected chips in grey, not the secondary colour docs/design.md §2 leaves unused. */
@Composable
private fun greyChipColors(): SelectableChipColors {
    val scheme = MaterialTheme.colorScheme
    return FilterChipDefaults.filterChipColors(
        selectedContainerColor = scheme.surfaceContainerHighest,
        selectedLabelColor = scheme.onSurface,
        selectedLeadingIconColor = scheme.onSurface,
    )
}

@Composable
private fun CustomDates(custom: MetricPeriod.Custom, onPickStart: () -> Unit, onPickEnd: () -> Unit) {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalLocale.current.platformLocale)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        PickerField(
            value = format.format(custom.start),
            label = stringResource(R.string.period_from),
            onClick = onPickStart,
            modifier = Modifier
                .weight(1f)
                .testTag("metric-from"),
        )
        PickerField(
            value = format.format(custom.end),
            label = stringResource(R.string.period_to),
            onClick = onPickEnd,
            modifier = Modifier
                .weight(1f)
                .testTag("metric-to"),
        )
    }
}

/** The kind chip's label: "Ratio (A ÷ B)". */
private val CombinationKind.label: Int
    @StringRes get() = when (this) {
        CombinationKind.Ratio -> R.string.combination_ratio
        CombinationKind.Difference -> R.string.combination_difference
        CombinationKind.Change -> R.string.combination_change
    }

/** "All categories", or the picked Categories in tree order, then Uncategorised. */
@Composable
private fun categorySummary(metric: Metric, categories: List<Category>): String {
    if (metric.categoryIds.isEmpty() && !metric.uncategorised) return stringResource(R.string.metric_all_categories)
    val names = categories.filter { it.id in metric.categoryIds }.map { it.name } +
        listOfNotNull(stringResource(R.string.uncategorised).takeIf { metric.uncategorised })
    return names.joinToString(", ")
}

/**
 * The Category filter: [categories] (of the source's kind) as their tree, then Uncategorised.
 * Each row toggles (tagged `metric-category-<id>` and `metric-category-uncategorised`); the
 * children of a picked parent show as included and cannot be toggled on their own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterSheet(
    categories: List<Category>,
    metric: Metric,
    actions: MetricBuilderActions,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.XLarge),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.ScreenMargin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.metric_categories),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("metric-categories-done")) {
                    Text(stringResource(R.string.done))
                }
            }
            CheckRow(
                label = stringResource(R.string.metric_all_categories),
                checked = metric.categoryIds.isEmpty() && !metric.uncategorised,
                onToggle = actions::onAllCategories,
                tag = "metric-category-all",
            )
            categories.branches().forEach { branch ->
                val parentPicked = branch.parent.id in metric.categoryIds
                CheckRow(
                    label = branch.parent.name,
                    checked = parentPicked,
                    onToggle = { actions.onToggleCategory(branch.parent.id) },
                    tag = "metric-category-${branch.parent.id}",
                )
                branch.children.forEach { child ->
                    CheckRow(
                        label = child.name,
                        checked = parentPicked || child.id in metric.categoryIds,
                        onToggle = { actions.onToggleCategory(child.id) },
                        tag = "metric-category-${child.id}",
                        isChild = true,
                        enabled = !parentPicked,
                    )
                }
            }
            CheckRow(
                label = stringResource(R.string.uncategorised),
                checked = metric.uncategorised,
                onToggle = actions::onToggleUncategorised,
                tag = "metric-category-uncategorised",
            )
        }
    }
}

/**
 * "All merchants", or the names of the [picked] Merchants that still exist in [merchants] (sorted
 * by name), or "No merchants left" when none of them does.
 */
@Composable
private fun merchantSummary(picked: Set<Long>, merchants: List<Merchant>): String {
    if (picked.isEmpty()) return stringResource(R.string.metric_all_merchants)
    val names = merchants.filter { it.id in picked }.map { it.name }
    return if (names.isEmpty()) stringResource(R.string.metric_merchants_gone) else names.joinToString(", ")
}

/**
 * The Merchant filter: a search field (tagged `metric-merchant-search`), "All merchants" (tagged
 * `metric-merchant-all`), then [merchants] in their order, or those matching the search (see
 * [matching]), each row toggling it (tagged `metric-merchant-<id>`). With none matching, it says
 * so. The search is only the sheet's own and starts blank each time it opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MerchantFilterSheet(
    merchants: List<Merchant>,
    picked: Set<Long>,
    actions: MetricBuilderActions,
    onDismiss: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    val shown = if (search.isBlank()) merchants else merchants.matching(search, limit = Int.MAX_VALUE)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.XLarge),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.ScreenMargin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.metric_merchants),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("metric-merchants-done")) {
                    Text(stringResource(R.string.done))
                }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.ScreenMargin, vertical = Spacing.Small)
                    .testTag("metric-merchant-search"),
                label = { Text(stringResource(R.string.metric_merchant_search)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            CheckRow(
                label = stringResource(R.string.metric_all_merchants),
                checked = picked.isEmpty(),
                onToggle = actions::onAllMerchants,
                tag = "metric-merchant-all",
            )
            if (shown.isEmpty()) {
                Text(
                    text = stringResource(R.string.metric_no_merchants_match),
                    modifier = Modifier.padding(horizontal = Spacing.ScreenMargin, vertical = Spacing.Small),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            shown.forEach { merchant ->
                CheckRow(
                    label = merchant.name,
                    checked = merchant.id in picked,
                    onToggle = { actions.onToggleMerchant(merchant.id) },
                    tag = "metric-merchant-${merchant.id}",
                )
            }
        }
    }
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    tag: String,
    isChild: Boolean = false,
    enabled: Boolean = true,
) {
    val highlight = if (checked) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest) else Modifier
    val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.MinTouchTarget)
            .then(highlight)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(
                start = if (isChild) Spacing.ScreenMargin + Spacing.XLarge else Spacing.ScreenMargin,
                end = Spacing.ScreenMargin,
            )
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = if (isChild) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
            color = color,
        )
        if (checked) {
            Spacer(Modifier.width(Spacing.Small))
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = color)
        }
    }
}

private object PreviewActions : MetricBuilderActions {
    override fun onNameChange(name: String) = Unit
    override fun onSourceChange(source: MetricSource) = Unit
    override fun onCombine() = Unit
    override fun onCombinationKindChange(kind: CombinationKind) = Unit
    override fun onSideAChange(metricId: Long) = Unit
    override fun onSideBChange(metricId: Long) = Unit
    override fun onToggleCategory(categoryId: Long) = Unit
    override fun onToggleUncategorised() = Unit
    override fun onAllCategories() = Unit
    override fun onToggleAccount(accountId: Long) = Unit
    override fun onAllAccounts() = Unit
    override fun onToggleMerchant(merchantId: Long) = Unit
    override fun onAllMerchants() = Unit
    override fun onMeasureChange(measure: Measure) = Unit
    override fun onDivisorChange(divisor: AverageDivisor) = Unit
    override fun onPeriodChange(period: MetricPeriod) = Unit
    override fun onCustomPeriod() = Unit
    override fun onCustomStartChange(start: LocalDate) = Unit
    override fun onCustomEndChange(end: LocalDate) = Unit
    override fun onPinChange(pinned: Boolean) = Unit
    override fun onTargetChange(text: String) = Unit
    override fun onToggleDirection(direction: KpiDirection) = Unit
    override fun onSave() = Unit
    override fun onDelete() = Unit
}

private val previewCategories = listOf(
    Category(1, "Groceries", CategoryKind.Spending),
    Category(2, "Food", CategoryKind.Spending, parentId = 1),
)
private val previewAccounts = listOf(
    Account(1, "Current", AccountType.Current, 0),
    Account(2, "Cash", AccountType.Cash, 0),
)
private val previewMerchants = listOf(
    Merchant(1, "Lidl"),
    Merchant(2, "Mercadona"),
)

@Preview(showBackground = true)
@Composable
private fun MetricBuilderPreview() {
    FinanceTrackerTheme(dynamicColor = false) {
        MetricBuilderScreen(
            state = MetricBuilderState(
                metric = Metric(name = "Groceries", categoryIds = setOf(1), uncategorised = true, merchantIds = setOf(1, 2)),
            ),
            result = MetricResult.Money(cents = 36_420, uncategorisedCents = 1_250),
            categories = previewCategories,
            accounts = previewAccounts,
            merchants = previewMerchants,
            sides = emptyList(),
            actions = PreviewActions,
            onDone = {},
            onBack = {},
        )
    }
}

@Preview(name = "No value, custom dates, dark", showBackground = true)
@Composable
private fun MetricBuilderNoValuePreview() {
    FinanceTrackerTheme(darkTheme = true, dynamicColor = false) {
        MetricBuilderScreen(
            state = MetricBuilderState(
                metric = Metric(
                    name = "",
                    measure = Measure.AveragePerTransaction,
                    period = MetricPeriod.Custom(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 20)),
                ),
                editing = true,
                nameError = true,
            ),
            result = MetricResult.NoValue(NoValueReason.NoTransactions),
            categories = previewCategories,
            accounts = previewAccounts,
            merchants = previewMerchants,
            sides = emptyList(),
            actions = PreviewActions,
            onDone = {},
            onBack = {},
        )
    }
}

private val previewSides = listOf(
    Metric(id = 1, name = "Income", source = MetricSource.Income),
    Metric(id = 2, name = "Spending"),
)

@Preview(name = "Combined, used by another", showBackground = true)
@Composable
private fun MetricBuilderCombinedPreview() {
    FinanceTrackerTheme(dynamicColor = false) {
        MetricBuilderScreen(
            state = MetricBuilderState(
                metric = Metric(
                    id = 3,
                    name = "Saved",
                    combination = Combination(CombinationKind.Difference, a = 1, b = 2),
                ),
                editing = true,
                usedBy = listOf("Savings rate"),
            ),
            result = MetricResult.Money(cents = 50_000),
            categories = previewCategories,
            accounts = previewAccounts,
            merchants = previewMerchants,
            sides = previewSides,
            actions = PreviewActions,
            onDone = {},
            onBack = {},
        )
    }
}

@Preview(name = "Combined, no sides yet, dark", showBackground = true)
@Composable
private fun MetricBuilderCombinedEmptyPreview() {
    FinanceTrackerTheme(darkTheme = true, dynamicColor = false) {
        MetricBuilderScreen(
            state = MetricBuilderState(
                metric = Metric(name = "", combination = Combination(CombinationKind.Change, a = 0)),
            ),
            result = MetricResult.NoValue(NoValueReason.ChooseMetrics),
            categories = previewCategories,
            accounts = previewAccounts,
            merchants = previewMerchants,
            sides = emptyList(),
            actions = PreviewActions,
            onDone = {},
            onBack = {},
        )
    }
}

@Preview(name = "Pinned as KPI, target without a direction", showBackground = true)
@Composable
private fun MetricBuilderPinnedPreview() {
    FinanceTrackerTheme(dynamicColor = false) {
        MetricBuilderScreen(
            state = MetricBuilderState(
                metric = Metric(id = 3, name = "Savings rate", combination = Combination(CombinationKind.Ratio, 1, 2)),
                editing = true,
                pinned = true,
                target = "25,0",
                directionError = true,
            ),
            result = MetricResult.Percentage(tenths = 250),
            categories = previewCategories,
            accounts = previewAccounts,
            merchants = previewMerchants,
            sides = previewSides,
            actions = PreviewActions,
            onDone = {},
            onBack = {},
        )
    }
}
