package com.pablojimenez.financetracker.ui.lock

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.AppLock
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppLockGateTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val lock = AppLock()
    private val authenticator = FakeDeviceAuthenticator()
    private var settingsOpened = 0
    private var contentComposed = false

    private fun showGate(content: @Composable () -> Unit = { SecretContent() }) {
        composeRule.setContent { Gate(content) }
    }

    @Composable
    private fun Gate(content: @Composable () -> Unit) {
        FinanceTrackerTheme(dynamicColor = false) {
            AppLockGate(
                lock = lock,
                authenticator = authenticator,
                onOpenSecuritySettings = { settingsOpened++ },
                content = content,
            )
        }
    }

    @Composable
    private fun SecretContent() {
        contentComposed = true
        Text(SECRET)
    }

    @Test
    fun `a locked gate shows the lock screen, prompts once on its own and never composes the content`() {
        showGate()

        composeRule.onNodeWithText("Finance Tracker").assertIsDisplayed()
        composeRule.onNodeWithText("Unlock").assertIsDisplayed().assert(hasClickAction())
        composeRule.waitForIdle()
        assertEquals(1, authenticator.prompts)
        assertFalse(contentComposed)
    }

    @Test
    fun `passing the prompt shows the content and removes the lock screen`() {
        showGate()
        composeRule.waitForIdle()

        authenticator.succeed()

        composeRule.onNodeWithText(SECRET).assertIsDisplayed()
        composeRule.onNodeWithText("Unlock").assertDoesNotExist()
    }

    @Test
    fun `a dismissed prompt leaves the lock screen up and only reopens on a tap`() {
        showGate()
        composeRule.waitForIdle()

        authenticator.fail()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Unlock").assertIsDisplayed()
        assertEquals(1, authenticator.prompts)
        assertFalse(contentComposed)

        composeRule.onNodeWithText("Unlock").performClick()
        composeRule.waitForIdle()
        assertEquals(2, authenticator.prompts)
        assertFalse(contentComposed)
    }

    @Test
    fun `a relock hides the content without losing what was typed and prompts once`() {
        showGate {
            var note by remember { mutableStateOf("") }
            TextField(value = note, onValueChange = { note = it }, modifier = Modifier.testTag("note"))
        }
        composeRule.waitForIdle()
        authenticator.succeed()
        composeRule.onNodeWithTag("note").performTextInput(TYPED)
        composeRule.onNodeWithText(TYPED).assertIsDisplayed()

        lock.onBackground()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(TYPED).assertDoesNotExist()
        composeRule.onNodeWithText("Unlock").assertIsDisplayed()
        assertEquals(2, authenticator.prompts)

        authenticator.succeed()
        composeRule.onNodeWithText(TYPED).assertIsDisplayed()
    }

    @Test
    fun `without a screen lock the gate asks for one and shows nothing else`() {
        authenticator.screenLock = false
        showGate()

        composeRule.onNodeWithText("Set a screen lock to use Finance Tracker").assertIsDisplayed()
        composeRule.onNodeWithText("Open settings").assertIsDisplayed().assert(hasClickAction())
        composeRule.onNodeWithText("Unlock").assertDoesNotExist()
        composeRule.waitForIdle()
        assertEquals(0, authenticator.prompts)
        assertFalse(contentComposed)
    }

    @Test
    fun `coming back from settings with a new screen lock shows the lock screen and prompts`() {
        authenticator.screenLock = false
        showGate()

        composeRule.onNodeWithText("Open settings").performClick()
        assertEquals(1, settingsOpened)

        // The owner sets a screen lock in Settings, then comes back.
        val scenario = composeRule.activityRule.scenario
        scenario.moveToState(Lifecycle.State.CREATED)
        authenticator.screenLock = true
        scenario.moveToState(Lifecycle.State.RESUMED)

        composeRule.onNodeWithText("Unlock").assertIsDisplayed()
        composeRule.onNodeWithText("Open settings").assertDoesNotExist()
        assertEquals(1, authenticator.prompts)
        assertFalse(contentComposed)
    }

    @Test
    fun `a relock while the app is away prompts only once it is back`() {
        showGate()
        composeRule.waitForIdle()
        authenticator.succeed()
        composeRule.waitForIdle()

        val scenario = composeRule.activityRule.scenario
        scenario.moveToState(Lifecycle.State.CREATED)
        lock.onBackground()
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(1, authenticator.prompts)

        scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        assertEquals(2, authenticator.prompts)
    }

    @Test
    fun `the keyboard cannot move focus into the hidden content`() {
        var fieldFocused = false
        showGate {
            var note by remember { mutableStateOf("") }
            TextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.testTag("note").onFocusChanged { fieldFocused = it.isFocused },
            )
        }
        composeRule.waitForIdle()
        authenticator.succeed()
        composeRule.onNodeWithTag("note").performClick()
        composeRule.waitForIdle()
        assertTrue(fieldFocused)

        lock.onBackground()
        composeRule.waitForIdle()
        assertFalse(fieldFocused)

        repeat(4) {
            composeRule.onNodeWithText("Unlock").performKeyInput { pressKey(Key.Tab) }
            composeRule.waitForIdle()
            assertFalse(fieldFocused)
        }
    }

    @Test
    fun `back on the lock screen does not reach the hidden content`() {
        var contentBacks = 0
        showGate {
            // Like FinanceApp: a navigation back stack two screens deep.
            NavDisplay(
                backStack = listOf("Home", "Detail"),
                onBack = { contentBacks++ },
                entryProvider = { key -> NavEntry(key) { Text(key) } },
            )
        }
        composeRule.waitForIdle()
        authenticator.succeed()
        composeRule.waitForIdle()
        lock.onBackground()
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(0, contentBacks)
        composeRule.onNodeWithText("Unlock").assertIsDisplayed()
    }

    @Test
    fun `an unlocked gate keeps showing the content after its state is restored`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { Gate { SecretContent() } }
        composeRule.waitForIdle()
        authenticator.succeed()
        composeRule.onNodeWithText(SECRET).assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText(SECRET).assertIsDisplayed()
        composeRule.onNodeWithText("Unlock").assertDoesNotExist()
        assertEquals(1, authenticator.prompts)
    }

    @Test
    fun `a dismissed prompt is not reopened on its own after the state is restored`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { Gate { SecretContent() } }
        composeRule.waitForIdle()
        authenticator.fail()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Unlock").assertIsDisplayed()
        assertEquals(1, authenticator.prompts)
        assertFalse(contentComposed)
    }

    @Test
    @Config(qualifiers = "w731dp-h340dp-land", fontScale = 2.0f)
    fun `the only way past the lock stays reachable at double font size in landscape`() {
        authenticator.screenLock = false
        showGate()

        composeRule.onNodeWithText("Open settings").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, settingsOpened)
    }

    private companion object {
        const val TYPED = "Rent 900"
        const val SECRET = "Balance 1,234"
    }
}
