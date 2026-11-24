package com.pablojimenez.financetracker.ui.lock

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import com.pablojimenez.financetracker.domain.AppLock
import com.pablojimenez.financetracker.ui.theme.LocalFinanceMotion

/**
 * Shows [content] only once the owner has unlocked [lock] with the phone's screen lock.
 *
 * Until the first unlock of the process [content] is never composed, so nothing behind it is
 * read. On a relock it stays composed (forms keep what was typed) but is neither drawn nor
 * exposed to accessibility services, and the lock screen covers it.
 */
@Composable
fun AppLockGate(
    lock: AppLock,
    authenticator: DeviceAuthenticator,
    onOpenSecuritySettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    val locked by lock.isLocked.collectAsState()
    // Read again on every resume: the owner may come back from Settings with a new screen lock.
    var hasScreenLock by remember { mutableStateOf(authenticator.hasScreenLock()) }
    LifecycleResumeEffect(authenticator) {
        hasScreenLock = authenticator.hasScreenLock()
        onPauseOrDispose {}
    }
    // Whether the prompt already opened on its own for this lock; saved so that a rotation on the
    // lock screen doesn't reopen it.
    var prompted by rememberSaveable { mutableStateOf(false) }
    val prompt = {
        lock.expectSystemScreen()
        authenticator.authenticate { passed -> if (passed) lock.unlock() }
    }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(locked) {
        if (locked) {
            // A focused field under the lock screen would keep taking keyboard input.
            focusManager.clearFocus(force = true)
        } else {
            prompted = false
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(locked, hasScreenLock, prompted) {
        if (locked && hasScreenLock && !prompted) {
            // A relock happens in the background; the prompt waits until the app is back.
            lifecycle.withResumed {
                prompted = true
                prompt()
            }
        }
    }

    var unlockedOnce by remember { mutableStateOf(false) }
    if (!locked) unlockedOnce = true

    Box {
        if (unlockedOnce) {
            Box(Modifier.hiddenWhen(locked)) {
                content()
            }
        }
        val motion = LocalFinanceMotion.current
        AnimatedVisibility(
            visible = locked,
            // A relock happens while the app is away, so the lock screen is simply there on return.
            enter = EnterTransition.None,
            exit = fadeOut(tween(motion.DURATION_SHORT_MS, easing = motion.EasingExit)),
        ) {
            // Back must not reach the hidden content's navigation; it leaves the app instead.
            // Off during the fade-out after unlocking, so back already reaches the app.
            val activity = LocalActivity.current
            BackHandler(enabled = locked) { activity?.moveTaskToBack(true) }
            AnimatedContent(
                targetState = hasScreenLock,
                transitionSpec = {
                    fadeIn(tween(motion.DURATION_MEDIUM_MS, easing = motion.EasingEnter)) togetherWith
                        fadeOut(tween(motion.DURATION_SHORT_MS, easing = motion.EasingExit))
                },
                label = "lock page",
            ) { canUnlock ->
                if (canUnlock) {
                    LockScreen(onUnlock = prompt)
                } else {
                    NoScreenLockScreen(
                        onOpenSettings = {
                            lock.expectSystemScreen()
                            onOpenSecuritySettings()
                        },
                    )
                }
            }
        }
    }
}

/**
 * While [hidden], neither drawn, exposed to accessibility services nor reachable by focus (a
 * keyboard or D-pad can't move into it), while staying composed and laid out.
 */
private fun Modifier.hiddenWhen(hidden: Boolean): Modifier =
    focusProperties { if (hidden) onEnter = { cancelFocusChange() } }
        .focusGroup()
        .then(if (hidden) Modifier.clearAndSetSemantics {}.drawWithContent {} else Modifier)
