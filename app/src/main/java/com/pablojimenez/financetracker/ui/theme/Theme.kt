package com.pablojimenez.financetracker.ui.theme

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * The app theme from docs/design.md. With [dynamicColor] on Android 12+ the colours come from
 * Material You; otherwise (older Android, or `dynamicColor = false` in previews and tests) the
 * grey fallback scheme is used. It also provides [LocalFinanceMotion] and [LocalReduceMotion].
 */
@Composable
fun FinanceTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colorScheme(darkTheme, dynamicColor),
        typography = FinanceTypography,
        shapes = FinanceShapes,
    ) {
        CompositionLocalProvider(
            LocalFinanceMotion provides FinanceMotion,
            LocalReduceMotion provides !ValueAnimator.areAnimatorsEnabled(),
            content = content,
        )
    }
}

@Composable
private fun colorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme =
    if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) GreyDarkColorScheme else GreyLightColorScheme
    }
