package com.pablojimenez.financetracker

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * On Android 8.x androidx.biometric draws its own fingerprint dialog, an AppCompat [AlertDialog]
 * on the activity. If the app theme can't host it, the unlock prompt crashes and the owner is
 * locked out.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [27])
class BiometricDialogThemeTest {
    @Test
    fun `the app theme can show the fingerprint dialog androidx biometric uses on Android 8`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val dialog = AlertDialog.Builder(activity).setTitle("Unlock Finance Tracker").show()

        assertTrue(dialog.isShowing)
    }
}
