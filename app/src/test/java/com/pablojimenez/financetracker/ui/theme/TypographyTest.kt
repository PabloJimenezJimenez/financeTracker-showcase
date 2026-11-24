package com.pablojimenez.financetracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypographyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var typography: Typography

    @Before
    fun readTypography() {
        composeRule.setContent {
            FinanceTrackerTheme(darkTheme = false, dynamicColor = false) {
                typography = MaterialTheme.typography
            }
        }
        composeRule.waitForIdle()
    }

    private fun Typography.serifRoles() = mapOf(
        "displayLarge" to displayLarge, "displayMedium" to displayMedium,
        "displaySmall" to displaySmall, "headlineLarge" to headlineLarge,
        "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
    )

    private fun Typography.sansRoles() = mapOf(
        "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
        "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
        "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
    )

    private fun TextStyle.fontResources(): Set<Int> =
        (fontFamily as FontListFontFamily).fonts.map { (it as ResourceFont).resId }.toSet()

    @Test
    fun `display and headline roles use the bundled Newsreader font`() {
        typography.serifRoles().forEach { (role, style) ->
            assertEquals(role, setOf(R.font.newsreader_variable), style.fontResources())
            assertEquals(role, FontWeight.Normal, style.fontWeight)
        }
    }

    @Test
    fun `title body and label roles use the bundled Inter font`() {
        typography.sansRoles().forEach { (role, style) ->
            assertEquals(role, setOf(R.font.inter_variable), style.fontResources())
        }
        assertEquals(FontWeight.Medium, typography.titleMedium.fontWeight)
        assertEquals(FontWeight.Normal, typography.bodyMedium.fontWeight)
        assertEquals(FontWeight.Medium, typography.labelMedium.fontWeight)
    }

    @Test
    fun `every role uses tabular figures`() {
        (typography.serifRoles() + typography.sansRoles()).forEach { (role, style) ->
            assertEquals(role, "tnum", style.fontFeatureSettings)
        }
    }

    @Test
    fun `roles keep the Material 3 default sizes`() {
        val expected = listOf(57, 45, 36, 32, 28, 24, 22, 16, 14, 16, 14, 12, 14, 12, 11)
        val actual = (typography.serifRoles() + typography.sansRoles()).values.map { it.fontSize }

        assertEquals(expected.map { it.sp }, actual)
        assertEquals(36.sp, typography.displaySmall.fontSize)
    }
}
