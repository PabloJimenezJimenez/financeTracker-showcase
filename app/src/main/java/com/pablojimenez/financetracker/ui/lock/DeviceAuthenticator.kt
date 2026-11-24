package com.pablojimenez.financetracker.ui.lock

/** The phone's own screen lock (fingerprint, face, PIN, pattern or password), as the gate uses it. */
interface DeviceAuthenticator {
    /** True when the phone has a screen lock set, so the owner can unlock the app with it. */
    fun hasScreenLock(): Boolean

    /**
     * Shows the system unlock prompt. [onResult] gets true once the owner passed it, or false when
     * they dismissed it or it gave up (failed attempts and lockouts are the system's to handle).
     * Call it only while the screen is resumed.
     */
    fun authenticate(onResult: (Boolean) -> Unit)
}
