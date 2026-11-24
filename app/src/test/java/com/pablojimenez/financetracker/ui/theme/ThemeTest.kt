package com.pablojimenez.financetracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun schemeOf(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme {
        lateinit var scheme: ColorScheme
        composeRule.setContent {
            FinanceTrackerTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                scheme = MaterialTheme.colorScheme
            }
        }
        composeRule.waitForIdle()
        return scheme
    }

    @Test
    fun `grey fallback light scheme uses the design greys`() {
        val scheme = schemeOf(darkTheme = false, dynamicColor = false)

        assertEquals(Color(0xFFF9F9F9), scheme.surface)
        assertEquals(Color(0xFFF0F0F0), scheme.surfaceContainerLow)
        assertEquals(Color(0xFF2B2B2B), scheme.primaryContainer)
        assertEquals(Color(0xFFF0F0F0), scheme.onPrimaryContainer)
        assertEquals(Color(0xFF525252), scheme.onSurfaceVariant)
    }

    @Test
    fun `grey fallback dark scheme uses the design greys`() {
        val scheme = schemeOf(darkTheme = true, dynamicColor = false)

        assertEquals(Color(0xFF0E0E0E), scheme.surface)
        assertEquals(Color(0xFFE2E2E2), scheme.primaryContainer)
        assertEquals(Color(0xFF1C1C1C), scheme.onPrimaryContainer)
        assertEquals(Color(0xFF161616), scheme.surfaceContainerLow)
    }

    @Test
    @Config(sdk = [36])
    fun `dynamic colour on Android 12 and later uses the Material You scheme`() {
        lateinit var expected: ColorScheme
        lateinit var actual: ColorScheme
        composeRule.setContent {
            expected = dynamicLightColorScheme(LocalContext.current)
            FinanceTrackerTheme(darkTheme = false, dynamicColor = true) {
                actual = MaterialTheme.colorScheme
            }
        }
        composeRule.waitForIdle()

        assertEquals(expected.toString(), actual.toString())
    }

    @Test
    fun `large shape is 20 dp rounded and the rest keep the M3 defaults`() {
        lateinit var shapes: Shapes
        composeRule.setContent {
            FinanceTrackerTheme(darkTheme = false, dynamicColor = false) {
                shapes = MaterialTheme.shapes
            }
        }
        composeRule.waitForIdle()

        assertEquals(RoundedCornerShape(20.dp), shapes.large)
        assertEquals(RoundedCornerShape(4.dp), shapes.extraSmall)
        assertEquals(RoundedCornerShape(8.dp), shapes.small)
        assertEquals(RoundedCornerShape(12.dp), shapes.medium)
        assertEquals(RoundedCornerShape(28.dp), shapes.extraLarge)
    }
}
