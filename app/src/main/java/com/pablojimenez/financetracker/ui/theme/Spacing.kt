package com.pablojimenez.financetracker.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing tokens from docs/design.md §4: a 4 dp base unit and no other values. */
object Spacing {
    val XSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 12.dp
    val Large: Dp = 16.dp
    val XLarge: Dp = 24.dp
    val XXLarge: Dp = 32.dp
    val XXXLarge: Dp = 48.dp

    /** Left and right page margin. */
    val ScreenMargin: Dp = Large

    /** Gap between bento tiles. */
    val Gutter: Dp = Medium

    /** Gap between sections. */
    val SectionGap: Dp = XXLarge

    /** Gap between the hero tile and what follows it. */
    val HeroGap: Dp = XLarge

    /** Inner padding of a bento tile. */
    val TilePadding: Dp = Large

    /** Inner padding of the hero tile: the one value off the 4 dp scale, set by §6. */
    val HeroTilePadding: Dp = 20.dp

    /** Minimum size of anything tappable. */
    val MinTouchTarget: Dp = XXXLarge
}
