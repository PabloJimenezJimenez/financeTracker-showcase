package com.pablojimenez.financetracker.ui.home

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toOffset
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.domain.MetricPeriod
import com.pablojimenez.financetracker.domain.MetricResult
import com.pablojimenez.financetracker.domain.Verdict
import com.pablojimenez.financetracker.domain.amount
import com.pablojimenez.financetracker.ui.components.BentoTile
import com.pablojimenez.financetracker.ui.components.TileSpan
import com.pablojimenez.financetracker.ui.components.VerdictRow
import com.pablojimenez.financetracker.ui.components.kpiContainer
import com.pablojimenez.financetracker.ui.components.metricSpokenValueText
import com.pablojimenez.financetracker.ui.components.metricValueText
import com.pablojimenez.financetracker.ui.components.verdictMark
import com.pablojimenez.financetracker.ui.theme.FinanceMotion
import com.pablojimenez.financetracker.ui.theme.LocalReduceMotion
import com.pablojimenez.financetracker.ui.theme.Spacing
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The KPIs (each tagged `kpi-<id>`) in their order: a 2×1 tile takes a row of its own, consecutive
 * 1×1 tiles pair up, each pair as tall as its taller tile, and a lone 1×1 tile keeps half the
 * width. Each grows into its detail when tapped.
 *
 * A long-press lifts a tile (a haptic and a slight scale) and dragging it, inside this section,
 * moves it among the others, which slide aside; on drop [onReorder] gets the new order of ids,
 * once, unless the tile is back in its place. Under reduce motion the tiles jump instead. For
 * TalkBack, each tile has the actions "Move earlier" (but the first) and "Move later" (but the
 * last), which move it one place and save the order the same way.
 */
@Composable
internal fun KpiTiles(
    kpis: List<KpiTile>,
    onOpen: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val haptics = LocalHapticFeedback.current
    val ids = kpis.map { it.id }
    val grid = remember { TileGrid() }
    var drag by remember { mutableStateOf<TileDrag?>(null) }
    // Read only while placing the tiles, so a move re-places them without recomposing.
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    // The order just dropped, shown until the new order comes back in [kpis].
    var dropped by remember { mutableStateOf<List<Long>?>(null) }
    LaunchedEffect(ids) { dropped = null }
    val activeDrag = drag?.takeIf { it.order.toSet() == ids.toSet() }
    val order = activeDrag?.preview ?: dropped?.takeIf { it.toSet() == ids.toSet() } ?: ids
    val byId = kpis.associateBy { it.id }
    val shown = order.map { checkNotNull(byId[it]) }
    // The gesture outlives a composition, so it reads these through state.
    val currentIds by rememberUpdatedState(ids)
    val currentOrder by rememberUpdatedState(order)
    val currentOnReorder by rememberUpdatedState(onReorder)

    fun startDrag(id: Long) {
        val order = currentOrder
        val slots = grid.slots.takeIf { it.size == order.size } ?: return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        dragOffset = Offset.Zero
        drag = TileDrag(order = order, from = order.indexOf(id), slots = slots, bounds = grid.size)
    }

    fun dragBy(delta: Offset) {
        val current = drag ?: return
        dragOffset = current.clamp(dragOffset + delta)
        val target = current.nearest(dragOffset)
        if (target != current.target) drag = current.copy(target = target)
    }

    /** Moves the tile [id] one place [by] -1 (earlier) or 1 (later) and saves the new order. */
    fun move(id: Long, by: Int) {
        val order = currentOrder.toMutableList()
        val from = order.indexOf(id)
        order.add(from + by, order.removeAt(from))
        dropped = order
        currentOnReorder(order)
    }

    fun drop() {
        val finished = drag ?: return
        drag = null
        // KPIs pinned or unpinned meanwhile make the drag's order stale: drop nothing.
        if (finished.target != finished.from && finished.order.toSet() == currentIds.toSet()) {
            dropped = finished.preview
            currentOnReorder(finished.preview)
        }
    }

    val moveEarlier = stringResource(R.string.kpi_move_earlier)
    val moveLater = stringResource(R.string.kpi_move_later)
    Layout(
        content = {
            shown.forEachIndexed { index, kpi ->
                key(kpi.id) {
                    val lifted = activeDrag?.id == kpi.id
                    val lift by animateFloatAsState(
                        targetValue = if (lifted) LIFTED_SCALE else 1f,
                        animationSpec = if (reduceMotion) snap() else FinanceMotion.spatialFast(),
                        label = "lift",
                    )
                    KpiTileCard(
                        kpi = kpi,
                        onClick = { onOpen(kpi.id) },
                        modifier = Modifier
                            .slideToPlace(snap = reduceMotion || lifted)
                            .pointerInput(kpi.id) {
                                detectLongPressDrag(
                                    onStart = { startDrag(kpi.id) },
                                    onDrag = ::dragBy,
                                    onEnd = { drop() },
                                    onCancel = { drag = null },
                                )
                            }
                            .semantics {
                                customActions = listOfNotNull(
                                    CustomAccessibilityAction(moveEarlier) {
                                        move(kpi.id, -1)
                                        true
                                    }.takeIf { index > 0 },
                                    CustomAccessibilityAction(moveLater) {
                                        move(kpi.id, 1)
                                        true
                                    }.takeIf { index < shown.lastIndex },
                                )
                            }
                            .graphicsLayer {
                                scaleX = lift
                                scaleY = lift
                            }
                            .kpiContainer(kpi.id),
                    )
                }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gutter = Spacing.Gutter.roundToPx()
        val half = (width - gutter) / 2
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        val positions = arrayOfNulls<IntOffset>(measurables.size)
        var y = 0
        kpiRows(shown.map { it.wide }).forEachIndexed { row, tiles ->
            if (row > 0) y += gutter
            if (tiles.size == 1 && shown[tiles[0]].wide) {
                placeables[tiles[0]] = measurables[tiles[0]].measure(Constraints.fixedWidth(width))
            } else {
                val height = tiles.maxOf { measurables[it].minIntrinsicHeight(half) }
                tiles.forEach { placeables[it] = measurables[it].measure(Constraints.fixed(half, height)) }
            }
            tiles.forEachIndexed { column, tile ->
                val tileWidth = checkNotNull(placeables[tile]).width
                val start = if (column == 0) 0 else width - tileWidth
                val x = if (layoutDirection == LayoutDirection.Rtl) width - start - tileWidth else start
                positions[tile] = IntOffset(x, y)
            }
            y += tiles.maxOf { checkNotNull(placeables[it]).height }
        }
        grid.size = IntSize(width, y)
        grid.slots = placeables.indices.map { index ->
            IntRect(checkNotNull(positions[index]), checkNotNull(placeables[index]).let { IntSize(it.width, it.height) })
        }
        layout(width, y) {
            placeables.forEachIndexed { index, placeable ->
                val lifted = activeDrag?.takeIf { it.id == shown[index].id }
                if (lifted != null) {
                    checkNotNull(placeable).place(lifted.position(dragOffset), zIndex = 1f)
                } else {
                    checkNotNull(placeable).place(checkNotNull(positions[index]))
                }
            }
        }
    }
}

/** Where the KPI tiles were last laid out, for a drag to start from: each tile's [slots] in order, and the grid's [size]. */
private class TileGrid {
    var slots: List<IntRect> = emptyList()
    var size: IntSize = IntSize.Zero
}

/**
 * A KPI tile being dragged: the tile at [from] in [order], laid out in [slots] inside [bounds] when
 * the drag started. It goes to [target], the slot whose centre is nearest to its own.
 */
private data class TileDrag(
    val order: List<Long>,
    val from: Int,
    val slots: List<IntRect>,
    val bounds: IntSize,
    val target: Int = from,
) {
    val id: Long get() = order[from]

    /** The order with the tile at [target]. */
    val preview: List<Long> get() = order.toMutableList().apply { add(target, removeAt(from)) }

    /** Where the tile is when moved by [offset] from its slot. */
    fun position(offset: Offset): IntOffset = slots[from].topLeft + offset.round()

    /** [offset] cut back so that the tile stays inside the grid. */
    fun clamp(offset: Offset): Offset {
        val slot = slots[from]
        return Offset(
            x = offset.x.coerceIn(-slot.left.toFloat(), (bounds.width - slot.right).toFloat()),
            y = offset.y.coerceIn(-slot.top.toFloat(), (bounds.height - slot.bottom).toFloat()),
        )
    }

    /** The slot whose centre is nearest to the tile's when moved by [offset]. */
    fun nearest(offset: Offset): Int {
        val centre = slots[from].center.toOffset() + offset
        return slots.indices.minBy { (slots[it].center.toOffset() - centre).getDistanceSquared() }
    }
}

/** Waits until the press that started with [down] ends, moves past the touch slop or is consumed. */
private suspend fun AwaitPointerEventScope.awaitReleaseOrSlop(down: PointerInputChange) {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return
        val moved = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
        if (!change.pressed || change.isConsumed || moved) return
    }
}

/** How much a lifted KPI tile grows while it is dragged. */
private const val LIFTED_SCALE = 1.03f

/**
 * Calls [onStart] once a press stays down, within the touch slop, for the long-press timeout, then
 * [onDrag] with each move until [onEnd] on release, or [onCancel] if the gesture is cancelled. It
 * reads the events before the tile's own click does and, once the long-press has started, consumes
 * them: so a long-press, with or without a drag, never also counts as a tap, while a plain tap or a
 * scroll that starts before the long-press goes through untouched.
 */
private suspend fun PointerInputScope.detectLongPressDrag(
    onStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        awaitReleaseOrSlop(down)
        true
    }
    if (endedEarly == true) return@awaitEachGesture
    onStart()
    try {
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
            if (change == null) {
                onCancel()
                return@awaitEachGesture
            }
            val delta = change.positionChange()
            change.consume()
            if (!change.pressed) {
                onEnd()
                return@awaitEachGesture
            }
            onDrag(delta)
        }
    } catch (cancelled: CancellationException) {
        onCancel()
        throw cancelled
    }
}

/**
 * The rows of a KPI grid whose tiles, in order, are [wide] (2×1) or not (1×1), as tile indexes:
 * a 2×1 tile alone, two consecutive 1×1 tiles together, and a 1×1 tile alone when the next one is
 * 2×1 or there is none. The order is never changed to fill a gap.
 */
internal fun kpiRows(wide: List<Boolean>): List<List<Int>> {
    val rows = mutableListOf<List<Int>>()
    var index = 0
    while (index < wide.size) {
        val pair = !wide[index] && index + 1 < wide.size && !wide[index + 1]
        rows += if (pair) listOf(index, index + 1) else listOf(index)
        index += if (pair) 2 else 1
    }
    return rows
}

/**
 * Slides this element from where its parent placed it last to where it places it now, with the
 * default spatial spring; with [snap] it jumps there instead.
 */
@Composable
private fun Modifier.slideToPlace(snap: Boolean): Modifier {
    val scope = rememberCoroutineScope()
    val slide = remember { PlacementSlide() }
    slide.snap = snap
    return onPlaced { slide.target = it.positionInParent().round() }
        .offset { slide.offset(scope) }
}

/** Where a [slideToPlace] element is placed ([target]) and where it shows on its way there. */
private class PlacementSlide {
    var target by mutableStateOf(IntOffset.Zero)
    var snap = false
    private var animatable: Animatable<IntOffset, AnimationVector2D>? = null

    /** How far from [target] the element shows now; starts the slide when [target] has moved. */
    fun offset(scope: CoroutineScope): IntOffset {
        val target = target
        val animatable = animatable ?: Animatable(target, IntOffset.VectorConverter).also { animatable = it }
        if (snap) {
            if (animatable.value != target) scope.launch { animatable.snapTo(target) }
            return IntOffset.Zero
        }
        if (animatable.targetValue != target) {
            scope.launch { animatable.animateTo(target, FinanceMotion.spatialDefault()) }
        }
        return animatable.value - target
    }
}

/**
 * One KPI in greys: its name as the caption, its value, its Verdict row and, for a sum or count
 * judged on its pace, the pace bar; a 2×1 tile ends with its sparkline. The whole tile is one
 * click target, read as its name, value and Verdict word.
 */
@Composable
private fun KpiTileCard(kpi: KpiTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val mark = verdictMark(kpi.value, kpi.verdict)
    val spokenValue = metricSpokenValueText(kpi.value)
    val word = mark?.let { stringResource(it.word) }
    val description = when {
        // "No value" is already the spoken value.
        word == null || kpi.value is MetricResult.NoValue ->
            stringResource(R.string.kpi_tile_description_no_verdict, kpi.name, spokenValue)
        else -> stringResource(R.string.kpi_tile_description, kpi.name, spokenValue, word)
    }
    BentoTile(
        caption = kpi.name,
        modifier = modifier
            .testTag("kpi-${kpi.id}")
            .semantics { contentDescription = description },
        span = if (kpi.wide) TileSpan.Wide else TileSpan.Single,
        onClick = onClick,
    ) {
        Text(
            text = metricValueText(kpi.value),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (mark != null && word != null) VerdictRow(mark.icon, word)
        when (val verdict = kpi.verdict) {
            is Verdict.OnPace -> PaceBar(
                reached = verdict.reached,
                elapsed = kpi.periodElapsed,
                modifier = Modifier.testTag("kpi-pace-${kpi.id}"),
                markModifier = Modifier.testTag("kpi-pace-mark-${kpi.id}"),
            )
            // The value is right above, so the line holds only the target.
            is Verdict.AgainstTarget -> Text(
                text = stringResource(R.string.kpi_target_text, metricValueText(verdict.target)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is Verdict.AgainstPrevious, null -> Unit
        }
        val trend = kpi.period.sparklineDescription
        if (kpi.wide && kpi.sparkline.isNotEmpty() && trend != null) {
            val trendDescription = stringResource(trend)
            Sparkline(
                values = kpi.sparkline.map { it.amount() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SparklineHeight)
                    .testTag("kpi-sparkline-${kpi.id}")
                    .semantics { contentDescription = trendDescription },
            )
        }
    }
}

/**
 * A monochrome sparkline (docs/design.md §6.7) of [values], oldest first from the start side: an
 * `onSurface` line with no fill, axes or target, broken where a period has no value (null). A value
 * with no neighbour to join is a dot.
 */
@Composable
private fun Sparkline(values: List<Long?>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val known = values.filterNotNull()
        if (known.isEmpty()) return@Canvas
        val line = SparklineStroke.toPx()
        val high = known.max()
        val span = (high - known.min()).toFloat()
        // Inset by the line's width, so its round ends stay inside.
        fun x(index: Int): Float {
            val fromStart = if (values.size == 1) size.width / 2 else line + index * (size.width - 2 * line) / values.lastIndex
            return if (layoutDirection == LayoutDirection.Rtl) size.width - fromStart else fromStart
        }
        fun y(value: Long): Float = if (span == 0f) size.height / 2 else line + (high - value) / span * (size.height - 2 * line)
        values.forEachIndexed { index, value ->
            if (value == null) return@forEachIndexed
            val previous = values.getOrNull(index - 1)
            if (previous != null) {
                drawLine(color, Offset(x(index - 1), y(previous)), Offset(x(index), y(value)), line, StrokeCap.Round)
            } else if (values.getOrNull(index + 1) == null) {
                drawCircle(color, radius = line, center = Offset(x(index), y(value)))
            }
        }
    }
}

/** What a sparkline over this kind of period is read as; null for custom dates, which have none. */
private val MetricPeriod.sparklineDescription: Int?
    @StringRes get() = when (this) {
        MetricPeriod.ThisWeek -> R.string.kpi_sparkline_weeks
        MetricPeriod.ThisMonth -> R.string.kpi_sparkline_months
        MetricPeriod.ThisYear -> R.string.kpi_sparkline_years
        is MetricPeriod.Custom -> null
    }

private val SparklineHeight = 32.dp
private val SparklineStroke = 2.dp

/**
 * A monochrome progress bar: the share of the target [reached] so far in `onSurface` on a
 * `surfaceContainerHighest` track, with a thin `onSurfaceVariant` mark where [elapsed] of the
 * period puts the pace. Both run from the start side, so they mirror in right-to-left layouts.
 */
@Composable
private fun PaceBar(reached: Float, elapsed: Float, modifier: Modifier = Modifier, markModifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.onSurface
    val markColor = MaterialTheme.colorScheme.onSurfaceVariant
    Layout(
        content = {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(track)
                    .drawBehind {
                        val width = size.width * reached
                        val left = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                        drawRect(fill, topLeft = Offset(left, 0f), size = Size(width, size.height))
                    },
            )
            Box(markModifier.background(markColor))
        },
        modifier = modifier
            .fillMaxWidth()
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(reached, 0f..1f) },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val barHeight = PaceBarHeight.roundToPx()
        val markWidth = DividerDefaults.Thickness.roundToPx().coerceAtLeast(1)
        val height = PaceMarkHeight.roundToPx()
        val bar = measurables[0].measure(Constraints.fixed(width, barHeight))
        val mark = measurables[1].measure(Constraints.fixed(markWidth, height))
        layout(width, height) {
            bar.placeRelative(0, (height - barHeight) / 2)
            val x = (width * elapsed).roundToInt() - markWidth / 2
            mark.placeRelative(x.coerceIn(0, (width - markWidth).coerceAtLeast(0)), 0)
        }
    }
}

/** The pace bar's track is 4 dp thick; its mark stands 12 dp tall, past the track on both sides. */
private val PaceBarHeight = 4.dp
private val PaceMarkHeight = 12.dp

/**
 * The "No KPIs yet" tile (tagged `kpi-suggestions`): a full-width tile offering each of
 * [suggestions] in one tap, then "Build a Metric".
 */
@Composable
internal fun KpiSuggestions(
    suggestions: List<KpiSuggestion>,
    onSuggestion: (KpiSuggestion) -> Unit,
    onBuildMetric: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BentoTile(
        caption = stringResource(R.string.kpi_suggested),
        modifier = modifier.testTag("kpi-suggestions"),
        span = TileSpan.Wide,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            suggestions.forEach { suggestion ->
                SuggestionChip(
                    onClick = { onSuggestion(suggestion) },
                    label = { Text(stringResource(suggestion.label)) },
                )
            }
        }
        TextButton(onClick = onBuildMetric) {
            Text(stringResource(R.string.kpi_build_metric))
        }
    }
}

/** What a suggestion is called, on its chip and as the name of the KPI it saves. */
internal val KpiSuggestion.label: Int
    @StringRes get() = when (this) {
        KpiSuggestion.Spending -> R.string.kpi_suggestion_spending
        KpiSuggestion.SavingsRate -> R.string.kpi_suggestion_savings_rate
        KpiSuggestion.Groceries -> R.string.kpi_suggestion_groceries
    }

/** The names the suggested KPIs save their Metrics under, from the app's strings. */
@Composable
internal fun suggestionNames() = SuggestionNames(
    spending = stringResource(R.string.kpi_suggestion_spending),
    savingsRate = stringResource(R.string.kpi_suggestion_savings_rate),
    groceries = stringResource(R.string.kpi_suggestion_groceries),
    income = stringResource(R.string.kpi_suggestion_income),
    savings = stringResource(R.string.kpi_suggestion_savings),
)
