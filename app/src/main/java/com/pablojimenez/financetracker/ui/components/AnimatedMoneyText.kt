package com.pablojimenez.financetracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import com.pablojimenez.financetracker.domain.formatCents
import com.pablojimenez.financetracker.ui.theme.FinanceMotion
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.LocalFinanceMotion
import com.pablojimenez.financetracker.ui.theme.LocalReduceMotion

/**
 * Shows [cents] via [formatCents] in the current locale. When the value changes, only the
 * characters that differ roll up (value grew) or down (value shrank) over the medium duration;
 * with [LocalReduceMotion] the new value just replaces the old one.
 *
 * The value always stays on one line: when it is wider than the space it gets (a long amount,
 * a large font scale), the font shrinks until it fits.
 *
 * Accessibility services see one node whose content description is the final formatted value;
 * the per-character nodes are hidden, so intermediate values are never announced.
 */
@Composable
fun AnimatedMoneyText(
    cents: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    explicitSign: Boolean = false,
) {
    val locale = LocalLocale.current.platformLocale
    val formatted = formatCents(cents, locale, explicitSign = explicitSign)
    val resolvedStyle = LocalTextStyle.current.merge(style)
    val measurer = rememberTextMeasurer(cacheSize = MEASURE_CACHE_SIZE)
    val semanticsModifier = modifier.clearAndSetSemantics { contentDescription = formatted }
    if (LocalReduceMotion.current) {
        BoxWithConstraints(modifier = semanticsModifier) {
            val fitted = remember(formatted, resolvedStyle, constraints.maxWidth, measurer) {
                resolvedStyle.fitted(constraints.maxWidth) { measurer.widthOf(formatted, it) }
            }
            OneLineText(text = formatted, style = fitted)
        }
        return
    }
    val motion = LocalFinanceMotion.current
    val transition = updateTransition(targetState = cents, label = "money")
    val currentState = transition.currentState
    val current = formatCents(currentState, locale, explicitSign = explicitSign)
    val textOf = { value: Long ->
        when (value) {
            cents -> formatted
            currentState -> current
            else -> formatCents(value, locale, explicitSign = explicitSign)
        }
    }
    val slots = maxOf(current.length, formatted.length)
    BoxWithConstraints(modifier = semanticsModifier) {
        // Mid-roll a slot is at most as wide as the wider of its outgoing and incoming character.
        val fitted = remember(current, formatted, resolvedStyle, constraints.maxWidth, measurer) {
            resolvedStyle.fitted(constraints.maxWidth) { candidate ->
                (0 until slots).sumOf { slot ->
                    maxOf(
                        measurer.widthOf(current.characterFromEnd(slot), candidate),
                        measurer.widthOf(formatted.characterFromEnd(slot), candidate),
                    )
                }
            }
        }
        Row {
            // Slots count from the end so digits keep their place when the number gains a digit.
            for (slot in slots - 1 downTo 0) {
                key(slot) {
                    val characterAt = { value: Long -> textOf(value).characterFromEnd(slot) }
                    transition.AnimatedContent(
                        transitionSpec = { roll(up = targetState >= initialState, motion) },
                        contentKey = characterAt,
                    ) { value ->
                        val character = characterAt(value)
                        if (character.isNotEmpty()) OneLineText(text = character, style = fitted)
                    }
                }
            }
        }
    }
}

private const val MEASURE_CACHE_SIZE = 32
private const val MAX_FIT_STEPS = 4

/** The character [slot] places from the end, or "" when the text is shorter than that. */
private fun String.characterFromEnd(slot: Int): String =
    getOrNull(lastIndex - slot)?.toString().orEmpty()

private fun TextMeasurer.widthOf(text: String, style: TextStyle): Int =
    if (text.isEmpty()) 0 else measure(text, style, softWrap = false, maxLines = 1).size.width

/**
 * This style, with its font size (and line height) scaled down until [widthAt] fits in
 * [maxWidth] pixels. Unchanged when it already fits or the width is unbounded.
 */
private fun TextStyle.fitted(maxWidth: Int, widthAt: (TextStyle) -> Int): TextStyle {
    if (maxWidth == Constraints.Infinity || !fontSize.isSp) return this
    var fitted = this
    repeat(MAX_FIT_STEPS) {
        val width = widthAt(fitted)
        if (width <= maxWidth) return fitted
        val scale = maxWidth.toFloat() / width
        fitted = fitted.copy(
            fontSize = fitted.fontSize * scale,
            lineHeight = if (fitted.lineHeight.isSp) fitted.lineHeight * scale else fitted.lineHeight,
        )
    }
    return fitted
}

@Composable
private fun OneLineText(text: String, style: TextStyle) {
    Text(text = text, style = style, softWrap = false, maxLines = 1)
}

/**
 * The changed character slides in from below (value grew) or above while the old one slides
 * away. Both move for the medium duration; the old one fades out faster (short), so the exit
 * reads shorter than the enter (docs/design.md §8).
 */
private fun roll(up: Boolean, motion: FinanceMotion): ContentTransform {
    val direction = if (up) 1 else -1
    val move = tween<IntOffset>(motion.DURATION_MEDIUM_MS, easing = motion.EasingStandard)
    val fadeIn = tween<Float>(motion.DURATION_MEDIUM_MS, easing = motion.EasingStandard)
    val fadeOut = tween<Float>(motion.DURATION_SHORT_MS, easing = motion.EasingExit)
    val enter = slideInVertically(move) { height -> height * direction } + fadeIn(fadeIn)
    val exit = slideOutVertically(move) { height -> -height * direction } + fadeOut(fadeOut)
    return ContentTransform(
        targetContentEnter = enter,
        initialContentExit = exit,
        sizeTransform = SizeTransform(clip = false) { _, _ ->
            tween(motion.DURATION_MEDIUM_MS, easing = motion.EasingStandard)
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun AnimatedMoneyTextPreview() {
    FinanceTrackerTheme(dynamicColor = false) {
        Column {
            AnimatedMoneyText(cents = 160_050, style = MaterialTheme.typography.displaySmall)
            AnimatedMoneyText(cents = -90_000, style = MaterialTheme.typography.displaySmall)
            AnimatedMoneyText(
                cents = 42_000,
                style = MaterialTheme.typography.titleMedium,
                explicitSign = true,
            )
        }
    }
}
