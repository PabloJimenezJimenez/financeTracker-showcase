package com.pablojimenez.financetracker.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    private val lock = AppLock()

    private val isLocked get() = lock.isLocked.value

    @Test
    fun `a new app lock is locked`() {
        assertTrue(isLocked)
    }

    @Test
    fun `unlocking opens it and going to the background locks it again`() {
        lock.unlock()
        assertFalse(isLocked)

        lock.onBackground()
        assertTrue(isLocked)
    }

    @Test
    fun `coming back to the foreground never unlocks it`() {
        lock.onForeground()
        assertTrue(isLocked)

        lock.unlock()
        lock.onBackground()
        lock.onForeground()
        assertTrue(isLocked)
    }

    @Test
    fun `a trip to an expected system screen keeps it unlocked once`() {
        lock.unlock()
        lock.expectSystemScreen()

        lock.onBackground()
        lock.onForeground()
        assertFalse(isLocked)

        lock.onBackground()
        assertTrue(isLocked)
    }

    @Test
    fun `an expectation made while locked does not spare the first trip after unlocking`() {
        // The prompt expects a system screen, but on newer phones it never sends the app to the
        // background, so the expectation is still there when the owner unlocks.
        lock.expectSystemScreen()
        lock.unlock()

        lock.onBackground()
        assertTrue(isLocked)
    }
}
