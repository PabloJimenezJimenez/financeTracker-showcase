package com.pablojimenez.financetracker.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The App lock of one process: whether the owner must unlock with the phone's screen lock before
 * anything is shown. It starts locked (every cold start) and locks again each time the app goes
 * to the background, unless the app itself sent the owner to a system screen it expected.
 */
class AppLock {
    private val locked = MutableStateFlow(true)

    /** Set by [expectSystemScreen]; used up when the app comes back to the foreground. */
    private var systemScreenExpected = false

    /** True while the owner must unlock before the app shows anything. */
    val isLocked: StateFlow<Boolean> = locked.asStateFlow()

    /**
     * Unlocks it after the owner passed the phone's screen lock. Any expectation made while it
     * was locked (for the prompt itself, or Settings) is dropped, so it can't spare a later trip.
     */
    fun unlock() {
        systemScreenExpected = false
        locked.value = false
    }

    /**
     * The app is about to open a system screen (a file picker, Settings, the unlock prompt): the
     * next trip to the background and back does not lock it.
     */
    fun expectSystemScreen() {
        systemScreenExpected = true
    }

    /** The app left the screen: lock it, unless it is on an expected system screen. */
    fun onBackground() {
        if (!systemScreenExpected) locked.value = true
    }

    /** The app is back on screen. This never unlocks it; it uses up a system-screen expectation. */
    fun onForeground() {
        systemScreenExpected = false
    }
}
