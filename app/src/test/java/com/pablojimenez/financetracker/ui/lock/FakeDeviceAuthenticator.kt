package com.pablojimenez.financetracker.ui.lock

/** A screen lock the test controls: it counts prompts and answers them when told to. */
class FakeDeviceAuthenticator(var screenLock: Boolean = true) : DeviceAuthenticator {
    /** How many times the prompt was requested. */
    var prompts = 0
        private set

    private var pending: ((Boolean) -> Unit)? = null

    override fun hasScreenLock() = screenLock

    override fun authenticate(onResult: (Boolean) -> Unit) {
        prompts++
        pending = onResult
    }

    /** The owner passes the open prompt. */
    fun succeed() = answer(true)

    /** The owner dismisses the open prompt, or it gives up. */
    fun fail() = answer(false)

    private fun answer(passed: Boolean) {
        val onResult = checkNotNull(pending) { "No prompt is open" }
        pending = null
        onResult(passed)
    }
}
