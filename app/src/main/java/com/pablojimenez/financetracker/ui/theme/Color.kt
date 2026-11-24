package com.pablojimenez.financetracker.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Grey fallback scheme from docs/design.md §2, used before Android 12, in previews and tests.

internal val GreyLightColorScheme = lightColorScheme(
    primary = Color(0xFF141414),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2B2B2B),
    onPrimaryContainer = Color(0xFFF0F0F0),
    background = Color(0xFFF9F9F9),
    onBackground = Color(0xFF141414),
    surface = Color(0xFFF9F9F9),
    onSurface = Color(0xFF141414),
    surfaceVariant = Color(0xFFE2E2E2),
    onSurfaceVariant = Color(0xFF525252),
    surfaceTint = Color(0xFF141414),
    surfaceBright = Color(0xFFF9F9F9),
    surfaceDim = Color(0xFFDBDBDB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F0F0),
    surfaceContainer = Color(0xFFE9E9E9),
    surfaceContainerHigh = Color(0xFFE2E2E2),
    surfaceContainerHighest = Color(0xFFDBDBDB),
    outline = Color(0xFF757575),
    outlineVariant = Color(0xFFD6D6D6),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

internal val GreyDarkColorScheme = darkColorScheme(
    primary = Color(0xFFEDEDED),
    onPrimary = Color(0xFF141414),
    primaryContainer = Color(0xFFE2E2E2),
    onPrimaryContainer = Color(0xFF1C1C1C),
    background = Color(0xFF0E0E0E),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF0E0E0E),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF252525),
    onSurfaceVariant = Color(0xFFA6A6A6),
    surfaceTint = Color(0xFFEDEDED),
    surfaceBright = Color(0xFF2F2F2F),
    surfaceDim = Color(0xFF0E0E0E),
    surfaceContainerLowest = Color(0xFF090909),
    surfaceContainerLow = Color(0xFF161616),
    surfaceContainer = Color(0xFF1C1C1C),
    surfaceContainerHigh = Color(0xFF252525),
    surfaceContainerHighest = Color(0xFF2F2F2F),
    outline = Color(0xFF808080),
    outlineVariant = Color(0xFF2E2E2E),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)
