package com.pablojimenez.financetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreloadedMerchantsTest {
    @Test
    fun `the preloaded list holds about 100 Merchants with distinct keys and names`() {
        assertTrue("${PreloadedMerchants.size} Merchants", PreloadedMerchants.size in 95..110)
        assertEquals(PreloadedMerchants.size, PreloadedMerchants.map { it.key }.toSet().size)
        assertEquals(PreloadedMerchants.size, PreloadedMerchants.map { merchantNameKey(it.name) }.toSet().size)
    }

    @Test
    fun `every preloaded Merchant points at a starter Category and has its folded name as a keyword`() {
        val starterIds = SeededStarterCategories.map { it.id }.toSet()
        PreloadedMerchants.forEach { merchant ->
            assertTrue(merchant.name, merchant.defaultCategorySeedId in starterIds)
            assertEquals(merchant.name, foldMerchantText(merchant.name), merchant.keywords.first())
        }
    }

    @Test
    fun `bank variants are extra keywords`() {
        fun keywords(name: String) = PreloadedMerchants.single { it.name == name }.keywords
        assertEquals(listOf("moeve", "cepsa"), keywords("Moeve"))
        assertEquals(listOf("amazon", "amzn"), keywords("Amazon"))
        assertEquals(listOf("el corte ingles", "corte ingles"), keywords("El Corte Inglés"))
        assertEquals(listOf("masmovil"), keywords("MásMóvil"))
    }
}
