package com.pablojimenez.financetracker.ui.lock

import android.app.KeyguardManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * The phone's screen lock through `androidx.biometric`: any enrolled biometric (strong or weak)
 * or the PIN, pattern or password. Create it in the activity's `onCreate`.
 */
class BiometricDeviceAuthenticator(
    private val activity: FragmentActivity,
    private val promptTitle: String,
) : DeviceAuthenticator {
    // Kept in a ViewModel so that an answer arriving after a rotation still reaches the caller.
    private val pending = ViewModelProvider(activity)[PendingAuthentication::class.java]

    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                pending.answer(true)
            }

            // Dismissed, cancelled or locked out. A single failed attempt only goes to
            // onAuthenticationFailed, while the system prompt stays open for another try.
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                pending.answer(false)
            }
        },
    )

    override fun hasScreenLock(): Boolean =
        activity.getSystemService(KeyguardManager::class.java).isDeviceSecure

    override fun authenticate(onResult: (Boolean) -> Unit) {
        pending.onResult = onResult
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(promptTitle)
                // WEAK (which includes STRONG) plus the device credential works on every API level
                // from 26; STRONG plus the credential does not on 28 and 29.
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build(),
        )
    }
}

/** The caller waiting for the open prompt's answer. */
internal class PendingAuthentication : ViewModel() {
    var onResult: ((Boolean) -> Unit)? = null

    fun answer(passed: Boolean) {
        val waiting = onResult ?: return
        onResult = null
        waiting(passed)
    }
}
