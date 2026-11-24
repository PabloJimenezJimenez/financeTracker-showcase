package com.pablojimenez.financetracker.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.RemeasureToBounds
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.pablojimenez.financetracker.ui.theme.FinanceMotion
import com.pablojimenez.financetracker.ui.theme.LocalReduceMotion

/**
 * The [SharedTransitionScope] around the app's screens, so an Account's or a KPI's tile can grow
 * into its detail screen. Null outside the app (screen tests, previews), where nothing is shared.
 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

private enum class AccountPart { Container, Balance }

private data class AccountSharedKey(val accountId: Long, val part: AccountPart)

/** A KPI's own key type, so a Metric id never matches an Account id. */
private data class KpiSharedKey(val metricId: Long)

/** The rest of the container fades in once the bounds are about a third of the way there. */
private const val CONTENT_DELAY_MS = FinanceMotion.DURATION_HERO_MS / 3

/**
 * The container transform of docs/design.md §9, on the Account [accountId]'s tile and on its
 * detail screen: the bounds grow from one to the other on the slow spatial spring, clipped to
 * the tile's shape, while the outgoing content fades out quickly and the incoming content fades
 * in after about a third. Does nothing outside the app or with [LocalReduceMotion].
 */
@Composable
fun Modifier.accountContainer(accountId: Long): Modifier =
    container(AccountSharedKey(accountId, AccountPart.Container))

/**
 * The same container transform as [accountContainer], on the KPI [metricId]'s tile on Home and on
 * its detail screen. Does nothing outside the app or with [LocalReduceMotion].
 */
@Composable
fun Modifier.kpiContainer(metricId: Long): Modifier = container(KpiSharedKey(metricId))

/** The container transform, between the two places that share bounds under [key]. */
@Composable
private fun Modifier.container(key: Any): Modifier = shared { scope ->
    with(scope.transition) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = scope.animated,
            enter = fadeIn(
                tween(
                    FinanceMotion.DURATION_HERO_MS - CONTENT_DELAY_MS,
                    delayMillis = CONTENT_DELAY_MS,
                    easing = FinanceMotion.EasingEnter,
                ),
            ),
            exit = fadeOut(tween(CONTENT_DELAY_MS, easing = FinanceMotion.EasingExit)),
            boundsTransform = { _, _ -> FinanceMotion.spatialSlow() },
            resizeMode = RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(MaterialTheme.shapes.large),
        )
    }
}

/** The Account [accountId]'s balance, the hero number shared by its tile and its detail screen. */
@Composable
fun Modifier.accountBalance(accountId: Long): Modifier = shared { scope ->
    with(scope.transition) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(AccountSharedKey(accountId, AccountPart.Balance)),
            animatedVisibilityScope = scope.animated,
            enter = fadeIn(FinanceMotion.effects()),
            exit = fadeOut(FinanceMotion.effects()),
            boundsTransform = { _, _ -> FinanceMotion.spatialSlow() },
            resizeMode = scaleToBounds(ContentScale.Fit, Alignment.CenterStart),
        )
    }
}

private class SharedScopes(
    val transition: SharedTransitionScope,
    val animated: AnimatedVisibilityScope,
)

@Composable
private fun Modifier.shared(block: @Composable Modifier.(SharedScopes) -> Modifier): Modifier {
    val transition = LocalSharedTransitionScope.current ?: return this
    if (LocalReduceMotion.current) return this
    return block(SharedScopes(transition, LocalNavAnimatedContentScope.current))
}
