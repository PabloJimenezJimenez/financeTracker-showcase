package com.pablojimenez.financetracker.domain

import java.util.Currency
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    private val eur = Currency.getInstance("EUR")

    private fun format(cents: Long, explicitSign: Boolean = false) =
        formatCents(cents, Locale.US, eur, explicitSign)

    @Test
    fun `formats cents as currency`() {
        assertEquals("€1,600.50", format(160_050))
    }

    @Test
    fun `formats amounts a Double cannot represent exactly`() {
        assertEquals("€90,071,992,547,409.93", format(9_007_199_254_740_993))
    }

    @Test
    fun `formats the most negative amount without overflowing`() {
        assertEquals("−€92,233,720,368,547,758.08", format(Long.MIN_VALUE))
    }

    @Test
    fun `negative amounts use the minus sign instead of a hyphen`() {
        assertEquals("−€900.00", format(-90_000))
    }

    @Test
    fun `positive amounts have no sign by default`() {
        assertEquals("€0.01", format(1))
    }

    @Test
    fun `explicit sign prefixes positive amounts with plus`() {
        assertEquals("+€1,600.50", format(160_050, explicitSign = true))
    }

    @Test
    fun `explicit sign keeps the minus sign on negative amounts`() {
        assertEquals("−€0.05", format(-5, explicitSign = true))
    }

    @Test
    fun `zero never gets a sign`() {
        assertEquals("€0.00", format(0))
        assertEquals("€0.00", format(0, explicitSign = true))
    }

    @Test
    fun `sign follows the locale's placement`() {
        assertEquals(
            "−1.600,50 €",
            formatCents(-160_050, Locale.GERMANY, eur),
        )
        assertEquals(
            "+1.600,50 €",
            formatCents(160_050, Locale.GERMANY, eur, explicitSign = true),
        )
    }
}
