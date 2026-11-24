package com.pablojimenez.financetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pablojimenez.financetracker.R
import com.pablojimenez.financetracker.ui.theme.FinanceTrackerTheme
import com.pablojimenez.financetracker.ui.theme.Spacing

/** How much room a [BentoTile] takes, and so how important it looks (docs/design.md §6). */
enum class TileSpan {
    /** 1×1: one grid column, as wide as its parent lets it be. */
    Single,

    /** 2×1: the full width. */
    Wide,

    /** 2×2: the full width in `primaryContainer`; at most one per screen. */
    Hero,
}

/**
 * A bento tile holding one idea: a [caption] top-left over its [content] (usually one number).
 *
 * The tile is a single accessibility node that reads the caption followed by the content. With
 * [onClick] the whole tile is the touch target; without it, the tile has no click action.
 */
@Composable
fun BentoTile(
    caption: String,
    modifier: Modifier = Modifier,
    span: TileSpan = TileSpan.Single,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hero = span == TileSpan.Hero
    val colors = tileColors(hero)
    val captionColor = if (hero) colors.contentColor else MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (hero) null else BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant)
    val elevation = CardDefaults.cardElevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        focusedElevation = 0.dp,
        hoveredElevation = 0.dp,
        draggedElevation = 0.dp,
        disabledElevation = 0.dp,
    )
    val sized = if (span == TileSpan.Single) modifier else modifier.fillMaxWidth()
    val body: @Composable ColumnScope.() -> Unit = { TileBody(caption, hero, captionColor, content) }
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = sized.sizeIn(
                minWidth = Spacing.MinTouchTarget,
                minHeight = Spacing.MinTouchTarget,
            ),
            shape = MaterialTheme.shapes.large,
            colors = colors,
            elevation = elevation,
            border = border,
            content = body,
        )
    } else {
        Card(
            modifier = sized.semantics(mergeDescendants = true) {},
            shape = MaterialTheme.shapes.large,
            colors = colors,
            elevation = elevation,
            border = border,
            content = body,
        )
    }
}

@Composable
private fun tileColors(hero: Boolean): CardColors {
    val scheme = MaterialTheme.colorScheme
    return if (hero) {
        CardDefaults.cardColors(
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = scheme.surfaceContainerLow,
            contentColor = scheme.onSurface,
        )
    }
}

@Composable
private fun TileBody(
    caption: String,
    hero: Boolean,
    captionColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.padding(if (hero) Spacing.HeroTilePadding else Spacing.TilePadding),
        verticalArrangement = Arrangement.spacedBy(if (hero) Spacing.XLarge else Spacing.Small),
    ) {
        Text(
            text = caption.uppercase(LocalLocale.current.platformLocale),
            // Screen readers get the caption as written, not shouted letter by letter.
            modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(caption) },
            style = MaterialTheme.typography.labelMedium,
            color = captionColor,
        )
        content()
    }
}

@Preview(showBackground = true)
@Composable
private fun BentoTilePreview() {
    FinanceTrackerTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(Spacing.ScreenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.Gutter),
        ) {
            BentoTile(caption = stringResource(R.string.net_worth), span = TileSpan.Hero) {
                AnimatedMoneyText(cents = 160_050, style = MaterialTheme.typography.displaySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
                BentoTile(
                    caption = stringResource(R.string.net_worth),
                    modifier = Modifier.weight(1f),
                    onClick = {},
                ) {
                    AnimatedMoneyText(cents = 42_000, style = MaterialTheme.typography.headlineSmall)
                }
                BentoTile(
                    caption = stringResource(R.string.net_worth),
                    modifier = Modifier.weight(1f),
                ) {
                    AnimatedMoneyText(cents = -9_050, style = MaterialTheme.typography.headlineSmall)
                }
            }
            BentoTile(caption = stringResource(R.string.net_worth), span = TileSpan.Wide) {
                AnimatedMoneyText(cents = 1_234_567, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
