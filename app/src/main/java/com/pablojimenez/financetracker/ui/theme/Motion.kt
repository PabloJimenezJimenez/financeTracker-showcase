package com.pablojimenez.financetracker.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Motion tokens from docs/design.md §8. Replace with `MaterialTheme.motionScheme` once it is
 * public in a stable material3. No spring here is bouncier than damping 0.9.
 */
@Immutable
object FinanceMotion {
    const val DURATION_SHORT_MS = 150
    const val DURATION_MEDIUM_MS = 300
    const val DURATION_LONG_MS = 400
    const val DURATION_HERO_MS = 500

    /** EmphasizedDecelerate: things entering the screen. */
    val EasingEnter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** EmphasizedAccelerate: things leaving the screen. */
    val EasingExit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Standard: things moving while staying on screen. */
    val EasingStandard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    val SharedAxisSlideDistance: Dp = 30.dp

    private const val SPATIAL_DAMPING = 0.9f
    private const val EFFECTS_DAMPING = 1f

    fun <T> spatialFast(): SpringSpec<T> = spring(SPATIAL_DAMPING, stiffness = 1400f)
    fun <T> spatialDefault(): SpringSpec<T> = spring(SPATIAL_DAMPING, stiffness = 700f)
    fun <T> spatialSlow(): SpringSpec<T> = spring(SPATIAL_DAMPING, stiffness = 300f)

    /** Alpha and colour. */
    fun <T> effects(): SpringSpec<T> = spring(EFFECTS_DAMPING, stiffness = 1600f)

    /** Alpha and colour of small parts. */
    fun <T> effectsFast(): SpringSpec<T> = spring(EFFECTS_DAMPING, stiffness = 3800f)
}

/** The motion tokens; provided by [FinanceTrackerTheme]. */
val LocalFinanceMotion = staticCompositionLocalOf { FinanceMotion }

/**
 * True when the system "Remove animations" setting is on (docs/design.md §10): skip count-ups,
 * delays and rolling digits, and use plain fades instead of slides. Provided by
 * [FinanceTrackerTheme]; tests override it inside the theme's content.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }
