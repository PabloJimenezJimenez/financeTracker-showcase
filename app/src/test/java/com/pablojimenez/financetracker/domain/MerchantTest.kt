package com.pablojimenez.financetracker.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantTest {
    @Test
    fun `names that differ only in case, accents or surrounding spaces are the same Merchant`() {
        listOf("mercadona", "Mercadona ", "MERCADONA", "Mércadona").forEach { name ->
            assertTrue("'$name' is Mercadona", sameMerchantName("Mercadona", name))
        }
    }

    @Test
    fun `names that differ only in spacing are the same Merchant`() {
        assertTrue(sameMerchantName("Burger King", "burgerking"))
    }

    @Test
    fun `different names are different Merchants`() {
        assertFalse(sameMerchantName("Lidl", "Aldi"))
    }
}
