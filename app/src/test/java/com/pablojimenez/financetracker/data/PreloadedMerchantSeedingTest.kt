package com.pablojimenez.financetracker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pablojimenez.financetracker.domain.Merchant
import com.pablojimenez.financetracker.domain.PreloadedMerchants
import com.pablojimenez.financetracker.domain.sameMerchantName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reopening a database file, as the app does after an update: the preloaded Merchants are seeded
 * once per entry, so the owner's renames and deletions stay and only new entries are added.
 */
@RunWith(AndroidJUnit4::class)
class PreloadedMerchantSeedingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "seeding-test.db"

    @After
    fun deleteFile() {
        context.deleteDatabase(name)
    }

    /** Opens the database file, runs [sql] on it, and closes it. Returns the Merchants it had before [sql]. */
    private fun openAndRun(vararg sql: String): List<Merchant> {
        val database = FinanceDatabase.create(context, name)
        try {
            val merchants = runBlocking { RoomFinanceRepository(database).merchants.first() }
            sql.forEach { database.openHelper.writableDatabase.execSQL(it) }
            return merchants
        } finally {
            database.close()
        }
    }

    private fun reopenedMerchants(): List<Merchant> = openAndRun()

    private fun idOf(merchants: List<Merchant>, name: String): Long = merchants.single { it.name == name }.id

    @Test
    fun `a renamed preloaded Merchant keeps its name and a deleted one does not come back`() {
        val first = openAndRun()
        val mercadona = idOf(first, "Mercadona")
        val lidl = idOf(first, "Lidl")
        openAndRun(
            "UPDATE merchants SET name = 'Mercadona Centro' WHERE id = $mercadona",
            "DELETE FROM merchants WHERE id = $lidl",
        )

        val merchants = reopenedMerchants()

        assertEquals("Mercadona Centro", merchants.single { it.id == mercadona }.name)
        assertNull(merchants.firstOrNull { sameMerchantName(it.name, "Mercadona") })
        assertNull(merchants.firstOrNull { sameMerchantName(it.name, "Lidl") })
        assertEquals(PreloadedMerchants.size - 1, merchants.size)
    }

    @Test
    fun `a preloaded Merchant the database was never seeded with is added`() {
        val temu = idOf(openAndRun(), "Temu")
        // As if the database came from a version that did not ship Temu yet.
        openAndRun(
            "DELETE FROM merchants WHERE id = $temu",
            "DELETE FROM preloaded_merchant_seeds WHERE `key` = 'temu'",
        )

        val merchants = reopenedMerchants()

        val added = merchants.single { it.name == "Temu" }
        assertEquals(listOf("temu"), added.keywords)
        val shopping = runBlocking {
            val database = FinanceDatabase.create(context, name)
            try {
                RoomFinanceRepository(database).categories.first().single { it.name == "Shopping" }
            } finally {
                database.close()
            }
        }
        assertEquals(shopping.id, added.defaultCategoryId)
        assertEquals(PreloadedMerchants.size, merchants.size)
    }

    @Test
    fun `a new preloaded entry named like an owner's Merchant keeps that Merchant and adds no duplicate`() {
        val aldi = idOf(openAndRun(), "Aldi")
        // As if the owner had typed "ALDI" before a version that ships Aldi.
        openAndRun(
            "DELETE FROM merchants WHERE id = $aldi",
            "DELETE FROM preloaded_merchant_seeds WHERE `key` = 'aldi'",
            "INSERT INTO merchants (name, keywords, defaultCategoryId) VALUES ('ALDI', '', NULL)",
        )
        val owners = reopenedMerchants().single { sameMerchantName(it.name, "Aldi") }

        val merchants = reopenedMerchants()

        assertEquals(owners, merchants.single { sameMerchantName(it.name, "Aldi") })
        assertEquals("ALDI", owners.name)
        assertEquals(emptyList<String>(), owners.keywords)
        assertNull(owners.defaultCategoryId)
        assertEquals(PreloadedMerchants.size, merchants.size)
    }
}
