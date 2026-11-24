package com.pablojimenez.financetracker

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.pablojimenez.financetracker.ui.FinanceApp
import com.pablojimenez.financetracker.ui.lock.AppLockGate
import com.pablojimenez.financetracker.ui.lock.BiometricDeviceAuthenticator
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import java.time.Clock

/** A [FragmentActivity] because the biometric prompt runs in a fragment. */
class MainActivity : FragmentActivity() {
    private val app get() = application as FinanceTrackerApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Recents shows a blank thumbnail instead of the owner's finances. Screenshots stay allowed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        val authenticator = BiometricDeviceAuthenticator(this, getString(R.string.unlock_prompt_title))
        setContent {
            FinanceTrackerTheme {
                AppLockGate(
                    lock = app.appLock,
                    authenticator = authenticator,
                    onOpenSecuritySettings = ::openSecuritySettings,
                ) {
                    // Only read here, so the database is not opened before the first unlock.
                    FinanceApp(repository = app.repository, clock = Clock.systemDefaultZone())
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.appLock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // A rotation is not a trip to the background.
        if (!isChangingConfigurations) app.appLock.onBackground()
    }

    private fun openSecuritySettings() {
        try {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            // No Settings screen to open: the owner stays on the page that asks for a screen lock.
        }
    }
}
