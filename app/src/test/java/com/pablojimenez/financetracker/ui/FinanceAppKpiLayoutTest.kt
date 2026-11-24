package com.pablojimenez.financetracker.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.Account
import com.pablojimenez.financetracker.domain.AccountType
import com.pablojimenez.financetracker.domain.FakeFinanceRepository
import com.pablojimenez.financetracker.domain.Kpi
import com.pablojimenez.financetracker.domain.Metric
import com.pablojimenez.financetracker.domain.MetricPeriod
import com.pablojimenez.financetracker.domain.Transaction
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.LocalReduceMotion
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The KPI tiles' sizes, sparklines and order on Home, through [FinanceApp]. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rGB-w411dp-h2000dp")
class FinanceAppKpiLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repository = FakeFinanceRepository()

    /** Wednesday 2026-03-11 at noon, UTC. */
    private val clock = Clock.fixed(Instant.parse("2026-03-11T12:00:00Z"), ZoneOffset.UTC)

    /** Bumped by [restart]: a new app on the same repository, with no view model or UI state kept. */
    private var generation by mutableIntStateOf(0)

    private fun launch(reduceMotion: Boolean = false) {
        composeRule.setContent {
            key(generation) {
                val owner = remember {
                    object : ViewModelStoreOwner {
                        override val viewModelStore = ViewModelStore()
                    }
                }
                DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    FinanceTrackerTheme(dynamicColor = false) {
                        CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                            FinanceApp(repository = repository, clock = clock)
                        }
                    }
                }
            }
        }
    }

    private fun restart() {
        generation++
        composeRule.waitForIdle()
    }

    private val current by lazy {
        repository.givenAccount(Account(name = "Current", type = AccountType.Current, startingBalanceCents = 0))
    }

    private fun spent(cents: Long, date: LocalDate) {
        val food = repository.savedCategories.first { it.name == "Food" }.id
        repository.givenTransaction(Transaction(accountId = current, categoryId = food, amountCents = -cents, date = date))
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun sparkline(id: Long) = composeRule.onNodeWithTag("kpi-sparkline-$id", useUnmergedTree = true)

    private fun metric(id: Long): Metric = repository.savedMetrics.single { it.id == id }

    /** The KPI [id]'s tile spans the full width, as wide as the hero. */
    private fun assertWide(id: Long) {
        assertEquals("kpi-$id spans the full width", bounds("balance").width, bounds("kpi-$id").width, 1f)
    }

    /** The KPI [id]'s tile is 1×1: half the width, less the gutter. */
    private fun assertSingle(id: Long) {
        assertTrue("kpi-$id is 1×1", bounds("kpi-$id").width < bounds("balance").width / 2)
    }

    private fun openDetail(id: Long) {
        composeRule.onNodeWithTag("kpi-$id").performClick()
        composeRule.waitForIdle()
    }

    private fun back() {
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `turning on Full-width tile on the detail makes the Home tile 2x1 with a sparkline, and turning it off makes it 1x1 again`() {
        spent(2_000, LocalDate.of(2026, 2, 5))
        val id = repository.givenMetric(Metric(name = "Spending", kpi = Kpi()))
        val other = repository.givenMetric(Metric(name = "Other", kpi = Kpi()))
        launch()
        assertSingle(id)
        sparkline(id).assertDoesNotExist()

        openDetail(id)
        composeRule.onNodeWithTag("kpi-wide")
            .assert(hasText("Full-width tile"))
            .assert(hasText("Adds a trend of the last 6 periods"))
            .assertIsOff()
            .performClick()
        composeRule.onNodeWithTag("kpi-wide").assertIsOn()
        assertEquals(true, metric(id).kpi?.wide)
        back()

        assertWide(id)
        sparkline(id).assertIsDisplayed().assert(hasContentDescription("Trend over the last 6 months"))
        assertTrue("the next tile starts its own row", bounds("kpi-$other").top >= bounds("kpi-$id").bottom)
        assertSingle(other)

        openDetail(id)
        composeRule.onNodeWithTag("kpi-wide").assertIsOn().performClick()
        back()

        assertSingle(id)
        sparkline(id).assertDoesNotExist()
        assertEquals(Kpi(), metric(id).kpi)
    }

    @Test
    fun `a newly pinned KPI is 1x1`() {
        val id = repository.givenMetric(Metric(name = "Spending"))
        repository.givenMetric(Metric(name = "Wide one", kpi = Kpi(wide = true)))
        launch()

        composeRule.onNodeWithTag("nav-metrics").performClick()
        composeRule.onNodeWithTag("pin-$id").performClick()
        composeRule.onNodeWithTag("nav-home").performClick()
        composeRule.waitForIdle()

        assertEquals(Kpi(), metric(id).kpi)
        assertSingle(id)
        sparkline(id).assertDoesNotExist()
    }

    @Test
    fun `a 2x1 KPI over custom dates spans the full width but has no sparkline`() {
        val id = repository.givenMetric(
            Metric(
                name = "March",
                period = MetricPeriod.Custom(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)),
                kpi = Kpi(wide = true),
            ),
        )
        launch()

        assertWide(id)
        sparkline(id).assertDoesNotExist()
    }

    @Test
    fun `the sparkline is described by the kind of its periods`() {
        val weekly = repository.givenMetric(Metric(name = "Weekly", period = MetricPeriod.ThisWeek, kpi = Kpi(wide = true)))
        val yearly = repository.givenMetric(Metric(name = "Yearly", period = MetricPeriod.ThisYear, kpi = Kpi(wide = true)))
        launch()

        sparkline(weekly).assert(hasContentDescription("Trend over the last 6 weeks"))
        sparkline(yearly).assert(hasContentDescription("Trend over the last 6 years"))
    }

    private fun givenKpis(vararg names: String): List<Long> = names.map { repository.givenMetric(Metric(name = it, kpi = Kpi())) }

    /** Where the centre of the KPI [id]'s tile is, relative to the KPI [from]'s top-left corner. */
    private fun centreOf(id: Long, from: Long) = bounds("kpi-$id").center - bounds("kpi-$from").topLeft

    /** Asserts the KPI tiles show in [ids]' order, two per row. */
    private fun assertOrder(vararg ids: Long) {
        ids.toList().chunked(2).forEach { row ->
            if (row.size == 2) {
                val (first, second) = row.map { bounds("kpi-$it") }
                assertEquals("kpi-${row[0]} and kpi-${row[1]} share a row", first.top, second.top, 1f)
                assertTrue("kpi-${row[0]} comes before kpi-${row[1]}", first.right <= second.left)
            }
        }
        ids.toList().zipWithNext().forEach { (before, after) ->
            assertTrue("kpi-$before is not below kpi-$after", bounds("kpi-$before").top <= bounds("kpi-$after").top)
        }
    }

    @Test
    fun `long-pressing a KPI and dragging it above another moves it there, saving the new order once, on drop`() {
        val (a, b, c) = givenKpis("A", "B", "C")
        launch()
        val target = centreOf(a, from = b)

        composeRule.onNodeWithTag("kpi-$b").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(10) { moveBy((target - center) / 10f, delayMillis = 16) }
        }
        composeRule.waitForIdle()
        assertEquals("nothing is saved while dragging", 0, repository.kpiOrderSaves)

        composeRule.onNodeWithTag("kpi-$b").performTouchInput { up() }
        composeRule.waitForIdle()

        assertOrder(b, a, c)
        assertEquals(listOf(b, a, c), repository.savedKpiOrder)
        assertEquals("the order is saved once", 1, repository.kpiOrderSaves)
        composeRule.onNodeWithTag("kpi-detail").assertDoesNotExist()
    }

    @Test
    fun `dragging a KPI down past the next row moves it after the tiles it passed`() {
        val (a, b, c, d) = givenKpis("A", "B", "C", "D")
        launch()
        val target = centreOf(c, from = a) + Offset(0f, 1f)

        composeRule.onNodeWithTag("kpi-$a").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(10) { moveBy((target - center) / 10f, delayMillis = 16) }
            up()
        }
        composeRule.waitForIdle()

        assertOrder(b, c, a, d)
        assertEquals(listOf(b, c, a, d), repository.savedKpiOrder)
    }

    @Test
    fun `a plain tap on a KPI opens its detail, and a long-press without a drag neither opens it nor saves an order`() {
        val (a, b) = givenKpis("A", "B")
        launch()

        composeRule.onNodeWithTag("kpi-$b").performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("kpi-detail").assertDoesNotExist()
        assertOrder(a, b)
        assertEquals(0, repository.kpiOrderSaves)

        openDetail(b)
        composeRule.onNodeWithTag("kpi-detail").assertIsDisplayed()
    }

    /** The labels of the custom accessibility actions on the KPI [id]'s tile. */
    private fun actions(id: Long): List<String> =
        composeRule.onNodeWithTag("kpi-$id").fetchSemanticsNode().config
            .getOrElse(SemanticsActions.CustomActions) { emptyList() }
            .map { it.label }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Move earlier on the second KPI swaps it with the first and saves the order`() {
        val (a, b, c) = givenKpis("A", "B", "C")
        launch()

        composeRule.onNodeWithTag("kpi-$b").performCustomAccessibilityActionWithLabel("Move earlier")
        composeRule.waitForIdle()

        assertOrder(b, a, c)
        assertEquals(listOf(b, a, c), repository.savedKpiOrder)
        assertEquals(1, repository.kpiOrderSaves)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Move later on the first KPI swaps it with the second and saves the order`() {
        val (a, b, c) = givenKpis("A", "B", "C")
        launch()

        composeRule.onNodeWithTag("kpi-$a").performCustomAccessibilityActionWithLabel("Move later")
        composeRule.waitForIdle()

        assertOrder(b, a, c)
        assertEquals(listOf(b, a, c), repository.savedKpiOrder)
    }

    @Test
    fun `the first KPI cannot move earlier and the last cannot move later, and the actions keep the tile's description`() {
        spent(2_000, LocalDate.of(2026, 3, 2))
        val (a, b, c) = givenKpis("A", "B", "C")
        launch()

        assertEquals(listOf("Move later"), actions(a))
        assertEquals(listOf("Move earlier", "Move later"), actions(b))
        assertEquals(listOf("Move earlier"), actions(c))
        composeRule.onNodeWithTag("kpi-$a").assert(hasContentDescription("A, €20.00"))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `after a restart on the same repository Home shows the same order and sizes`() {
        val (a, b, c) = givenKpis("A", "B", "C")
        launch()
        openDetail(b)
        composeRule.onNodeWithTag("kpi-wide").performClick()
        back()
        composeRule.onNodeWithTag("kpi-$c").performCustomAccessibilityActionWithLabel("Move earlier")
        composeRule.waitForIdle()

        restart()

        composeRule.onNodeWithTag("kpi-$c").assertIsDisplayed()
        assertSingle(a)
        assertSingle(c)
        assertWide(b)
        val (first, second, third) = listOf(a, c, b).map { bounds("kpi-$it") }
        assertEquals("A and C share the first row", first.top, second.top, 1f)
        assertTrue("A comes before C", first.right <= second.left)
        assertTrue("B takes the next row", third.top >= first.bottom)
        sparkline(b).assertExists()
    }

    @Test
    fun `a KPI pinned later appears last, after the saved order`() {
        val (a, b) = givenKpis("A", "B")
        val later = repository.givenMetric(Metric(name = "Later"))
        repository.givenKpiOrder(b, a)
        launch()

        composeRule.onNodeWithTag("nav-metrics").performClick()
        composeRule.onNodeWithTag("pin-$later").performClick()
        composeRule.onNodeWithTag("nav-home").performClick()
        composeRule.waitForIdle()

        assertOrder(b, a, later)
    }

    @Test
    fun `unpinning or deleting a KPI leaves the others in their order`() {
        val (a, b, c, d) = givenKpis("A", "B", "C", "D")
        repository.givenKpiOrder(d, c, b, a)
        launch()

        openDetail(b)
        composeRule.onNodeWithTag("kpi-remove").performClick()
        composeRule.waitForIdle()
        assertOrder(d, c, a)

        openDetail(c)
        composeRule.onNodeWithTag("kpi-edit").performClick()
        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()
        assertOrder(d, a)
        assertEquals("nothing rewrites the order", 1, repository.kpiOrderSaves)
    }

    @Test
    fun `editing a 2x1 KPI in the Metric builder keeps it 2x1 and in its place`() {
        val (a, b, c) = givenKpis("A", "B", "C")
        repository.givenMetric(metric(b).copy(kpi = Kpi(wide = true)))
        repository.givenKpiOrder(c, b, a)
        launch()

        openDetail(b)
        composeRule.onNodeWithTag("kpi-edit").performClick()
        composeRule.onNodeWithTag("metric-name").performTextReplacement("B renamed")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()
        back()

        assertEquals(Kpi(wide = true), metric(b).kpi)
        assertEquals("B renamed", metric(b).name)
        assertWide(b)
        assertTrue("C comes first", bounds("kpi-$c").bottom <= bounds("kpi-$b").top)
        assertTrue("A comes last", bounds("kpi-$b").bottom <= bounds("kpi-$a").top)
    }

    /** Moves the KPI [id] one place earlier, then lets [frames] frames go by. */
    @OptIn(ExperimentalTestApi::class)
    private fun moveEarlierAndWait(id: Long, frames: Int) {
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("kpi-$id").performCustomAccessibilityActionWithLabel("Move earlier")
        composeRule.mainClock.advanceTimeByFrame()
        repeat(frames) { composeRule.mainClock.advanceTimeByFrame() }
    }

    @Test
    fun `the other KPI tiles slide aside on a spring`() {
        val (a, b) = givenKpis("A", "B")
        launch()
        val start = bounds("kpi-$a").left
        val end = bounds("kpi-$b").left

        moveEarlierAndWait(b, frames = 3)

        val left = bounds("kpi-$a").left
        assertTrue("A is on its way: $left between $start and $end", left > start && left < end)
        composeRule.mainClock.advanceTimeBy(2_000)
        assertEquals(end, bounds("kpi-$a").left, 1f)
    }

    @Test
    fun `under reduce motion the KPI tiles move without animation`() {
        val (a, b) = givenKpis("A", "B")
        launch(reduceMotion = true)
        val end = bounds("kpi-$b").left

        moveEarlierAndWait(b, frames = 3)

        assertEquals(end, bounds("kpi-$a").left, 1f)
    }
}
