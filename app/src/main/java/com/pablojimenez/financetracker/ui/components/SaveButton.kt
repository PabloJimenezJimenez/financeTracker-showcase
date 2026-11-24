package com.pablojimenez.financetracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.LocalFinanceMotion
import com.pablojimenez.financetracker.ui.theme.LocalReduceMotion
import com.pablojimenez.financetracker.ui.theme.Spacing

/**
 * A form's one filled button. Once [saved] turns true its "Save" label crossfades to a check, a
 * confirm haptic fires, and after the check has shown for the medium duration [onDone] runs
 * (straight away with [LocalReduceMotion]).
 */
@Composable
fun SaveButton(
    saved: Boolean,
    onClick: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val reduceMotion = LocalReduceMotion.current
    val motion = LocalFinanceMotion.current
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(saved) {
        if (!saved) return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        if (!reduceMotion) awaitFrameTime(motion.DURATION_MEDIUM_MS.toLong())
        currentOnDone()
    }
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.MinTouchTarget),
    ) {
        AnimatedContent(
            targetState = saved,
            transitionSpec = {
                fadeIn(tween(motion.DURATION_SHORT_MS, easing = motion.EasingStandard)) togetherWith
                    fadeOut(tween(motion.DURATION_SHORT_MS, easing = motion.EasingStandard))
            },
            label = "save",
        ) { done ->
            if (done) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.saved))
            } else {
                Text(stringResource(R.string.save))
            }
        }
    }
}

/**
 * Suspends for [millis] of frame time. Unlike `delay`, this runs on the composition's frame
 * clock, like the animations it waits for, so tests can drive it with their main clock.
 */
private suspend fun awaitFrameTime(millis: Long) {
    val start = withFrameMillis { it }
    while (withFrameMillis { it } - start < millis) {
        // Wait for the next frame.
    }
}

@Preview(showBackground = true)
@Composable
private fun SaveButtonPreview() {
    FinanceTrackerTheme(dynamicColor = false) {
        SaveButton(saved = false, onClick = {}, onDone = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun SaveButtonSavedPreview() {
    FinanceTrackerTheme(dynamicColor = false) {
        SaveButton(saved = true, onClick = {}, onDone = {})
    }
}
