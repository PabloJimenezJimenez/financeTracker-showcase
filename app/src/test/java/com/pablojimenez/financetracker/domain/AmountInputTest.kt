package com.pablojimenez.financetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountInputTest {
    @Test
    fun `parses a comma or a dot as the decimal separator`() {
        assertEquals(1_250L, AmountInput.parseCents("12,50"))
        assertEquals(1_250L, AmountInput.parseCents("12.50"))
    }

    @Test
    fun `parses whole amounts and one decimal`() {
        assertEquals(5_000L, AmountInput.parseCents("50"))
        assertEquals(1_250L, AmountInput.parseCents("12,5"))
        assertEquals(1_200L, AmountInput.parseCents("12,"))
        assertEquals(50L, AmountInput.parseCents(",5"))
    }

    @Test
    fun `parses amounts a Double cannot hold exactly`() {
        assertEquals(99_999_999_999_999L, AmountInput.parseCents("999999999999,99"))
    }

    @Test
    fun `parses a leading hyphen or minus sign as negative`() {
        assertEquals(-5_000L, AmountInput.parseCents("-50"))
        assertEquals(-5_050L, AmountInput.parseCents("−50,50"))
    }

    @Test
    fun `blank text, a lone sign or separator and three decimals are not amounts`() {
        listOf("", " ", "-", "−", ",", "-,", "1,234", "1,2,3", "abc", "1 000").forEach {
            assertNull("'$it'", AmountInput.parseCents(it))
        }
    }

    @Test
    fun `accepts partly typed amounts and refuses anything else`() {
        listOf("", "1", "12,", "12,5", "12.50").forEach { assertTrue("'$it'", AmountInput.accepts(it)) }
        listOf("12,505", "1a", "1,2,3", "1234567890123").forEach {
            assertFalse("'$it'", AmountInput.accepts(it))
        }
    }

    @Test
    fun `accepts a sign only where negative amounts are allowed`() {
        assertFalse(AmountInput.accepts("-5"))
        assertTrue(AmountInput.accepts("-5", allowNegative = true))
        assertTrue(AmountInput.accepts("−", allowNegative = true))
    }

    @Test
    fun `cents are written back as the owner would type them, with two decimals`() {
        assertEquals("12,50", AmountInput.text(1_250, decimalSeparator = ','))
        assertEquals("20.00", AmountInput.text(2_000, decimalSeparator = '.'))
        assertEquals("0,05", AmountInput.text(5, decimalSeparator = ','))
        assertEquals("123456,78", AmountInput.text(12_345_678, decimalSeparator = ','))
    }
}
