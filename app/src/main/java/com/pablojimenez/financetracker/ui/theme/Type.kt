package com.pablojimenez.financetracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.pablojimenez.financetracker.R

// docs/design.md §3: Newsreader (serif) for display and headline, Inter (sans) for the rest.
// Both are variable fonts; each family pins the weight and, for Newsreader, the optical size.

private fun newsreader(opticalSize: Float) = FontFamily(
    Font(
        R.font.newsreader_variable,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.Normal.weight),
            FontVariation.Setting("opsz", opticalSize),
        ),
    ),
)

/** Newsreader at its largest optical size, for the display roles (the hero number). */
val NewsreaderDisplay = newsreader(opticalSize = 72f)
val NewsreaderHeadlineLarge = newsreader(opticalSize = 32f)
val NewsreaderHeadlineMedium = newsreader(opticalSize = 28f)
val NewsreaderHeadlineSmall = newsreader(opticalSize = 24f)

val Inter = FontFamily(
    Font(
        R.font.inter_variable,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(FontWeight.Normal.weight)),
    ),
    Font(
        R.font.inter_variable,
        FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(FontWeight.Medium.weight)),
    ),
)

private const val TABULAR_FIGURES = "tnum"

private fun TextStyle.with(family: FontFamily, weight: FontWeight) = copy(
    fontFamily = family,
    fontWeight = weight,
    fontFeatureSettings = TABULAR_FIGURES,
)

private val Defaults = Typography()

/** M3 sizes and line heights kept; only families, weights and tabular figures change. */
internal val FinanceTypography = Typography(
    displayLarge = Defaults.displayLarge.with(NewsreaderDisplay, FontWeight.Normal),
    displayMedium = Defaults.displayMedium.with(NewsreaderDisplay, FontWeight.Normal),
    displaySmall = Defaults.displaySmall.with(NewsreaderDisplay, FontWeight.Normal),
    headlineLarge = Defaults.headlineLarge.with(NewsreaderHeadlineLarge, FontWeight.Normal),
    headlineMedium = Defaults.headlineMedium.with(NewsreaderHeadlineMedium, FontWeight.Normal),
    headlineSmall = Defaults.headlineSmall.with(NewsreaderHeadlineSmall, FontWeight.Normal),
    titleLarge = Defaults.titleLarge.with(Inter, FontWeight.Medium),
    titleMedium = Defaults.titleMedium.with(Inter, FontWeight.Medium),
    titleSmall = Defaults.titleSmall.with(Inter, FontWeight.Medium),
    bodyLarge = Defaults.bodyLarge.with(Inter, FontWeight.Normal),
    bodyMedium = Defaults.bodyMedium.with(Inter, FontWeight.Normal),
    bodySmall = Defaults.bodySmall.with(Inter, FontWeight.Normal),
    labelLarge = Defaults.labelLarge.with(Inter, FontWeight.Medium),
    labelMedium = Defaults.labelMedium.with(Inter, FontWeight.Medium),
    labelSmall = Defaults.labelSmall.with(Inter, FontWeight.Medium),
)
