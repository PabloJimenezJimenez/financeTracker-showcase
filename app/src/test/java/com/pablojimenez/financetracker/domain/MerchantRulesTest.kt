package com.pablojimenez.financetracker.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantRulesTest {
    private val merchants = PreloadedMerchants.mapIndexed { index, preloaded ->
        Merchant(
            id = index + 1L,
            name = preloaded.name,
            keywords = preloaded.keywords,
            defaultCategoryId = preloaded.defaultCategorySeedId,
        )
    }
    private val rules = MerchantRules(merchants)

    private fun merchant(name: String) = merchants.single { it.name == name }

    private fun assigned(name: String) = MerchantMatch.Assigned(merchant(name))

    @Test
    fun `recognises a Merchant's keyword anywhere in the bank text, ignoring case and accents`() {
        assertEquals(assigned("Mercadona"), rules.match("COMPRA TARJ. MERCADONA VALENCIA"))
        assertEquals(assigned("Mercadona"), rules.match("mércadona"))
    }

    @Test
    fun `keywords with symbols or next to digits are recognised`() {
        assertEquals(assigned("H&M"), rules.match("H&M ONLINE 0231"))
        assertEquals(assigned("Dia"), rules.match("SUPERMERCADO DIA 1234"))
    }

    @Test
    fun `a keyword matches only as a whole word`() {
        assertEquals(MerchantMatch.Uncategorised(emptyList()), rules.match("DIAGONAL MAR"))
        assertEquals(MerchantMatch.Uncategorised(emptyList()), rules.match("MERCADONAS"))
    }

    @Test
    fun `the most specific keyword wins`() {
        assertEquals(assigned("Uber Eats"), rules.match("UBER EATS MADRID"))
        assertEquals(assigned("Uber"), rules.match("UBER *TRIP HELP.UBER.COM"))
    }

    @Test
    fun `a text two Merchants match is Uncategorised with both offered`() {
        assertEquals(
            MerchantMatch.Uncategorised(listOf(merchant("Amazon"), merchant("Prime Video"))),
            rules.match("AMAZON PRIME VIDEO"),
        )
    }

    @Test
    fun `offered Merchants are sorted by name ignoring case and accents`() {
        val zeta = Merchant(id = 1, name = "Zeta", keywords = listOf("pago"))
        val ebano = Merchant(id = 2, name = "Ébano", keywords = listOf("tienda"))
        val alfa = Merchant(id = 3, name = "alfa", keywords = listOf("caja"))

        assertEquals(
            MerchantMatch.Uncategorised(listOf(alfa, ebano, zeta)),
            MerchantRules(listOf(zeta, ebano, alfa)).match("PAGO TIENDA CAJA"),
        )
    }

    @Test
    fun `a text that matches nothing, or a blank one, is Uncategorised with no choices`() {
        assertEquals(MerchantMatch.Uncategorised(emptyList()), rules.match("TRANSFERENCIA RECIBIDA 0042"))
        assertEquals(MerchantMatch.Uncategorised(emptyList()), rules.match(""))
        assertEquals(MerchantMatch.Uncategorised(emptyList()), rules.match("   "))
    }

    @Test
    fun `a Merchant with no keywords never matches, not even by its name`() {
        val bakery = Merchant(id = 1, name = "Panadería Pepa")

        assertEquals(MerchantMatch.Uncategorised(emptyList()), MerchantRules(listOf(bakery)).match("PANADERIA PEPA"))
    }

    @Test
    fun `two of a Merchant's own keywords matching count as one match`() {
        assertEquals(assigned("Amazon"), rules.match("AMZN MKTP AMAZON.ES"))
    }

    @Test
    fun `a remembered choice assigns the same text apart from case, accents and spacing`() {
        val learnt = rules.remember("AMAZON PRIME VIDEO", merchant("Prime Video").id)

        assertEquals(assigned("Prime Video"), learnt.match("amazon  prime  vídeo"))
    }

    @Test
    fun `a remembered choice is only for that text`() {
        val learnt = rules.remember("AMAZON PRIME VIDEO", merchant("Prime Video").id)

        assertEquals(
            MerchantMatch.Uncategorised(listOf(merchant("Amazon"), merchant("Prime Video"))),
            learnt.match("AMAZON PRIME VIDEO ES"),
        )
    }

    @Test
    fun `a remembered choice wins over keywords`() {
        assertEquals(assigned("Amazon"), rules.remember("UBER EATS MADRID", merchant("Amazon").id).match("UBER EATS MADRID"))
    }

    @Test
    fun `remembering a text again replaces the earlier choice`() {
        val learnt = rules
            .remember("AMAZON PRIME VIDEO", merchant("Prime Video").id)
            .remember("Amazon Prime Video", merchant("Amazon").id)

        assertEquals(assigned("Amazon"), learnt.match("AMAZON PRIME VIDEO"))
        assertEquals(mapOf("amazon prime video" to merchant("Amazon").id), learnt.remembered)
    }

    @Test
    fun `remembering leaves the original rules unchanged`() {
        rules.remember("UBER EATS MADRID", merchant("Amazon").id)

        assertEquals(assigned("Uber Eats"), rules.match("UBER EATS MADRID"))
        assertEquals(emptyMap<String, Long>(), rules.remembered)
    }

    @Test
    fun `stored remembered choices are used again`() {
        val stored = rules.remember("UBER EATS MADRID", merchant("Amazon").id).remembered

        assertEquals(assigned("Amazon"), MerchantRules(merchants, stored).match("uber eats madrid"))
    }

    @Test
    fun `a remembered Merchant that is no longer in the list is ignored`() {
        val withoutAmazon = MerchantRules(merchants - merchant("Amazon"), mapOf("uber eats madrid" to merchant("Amazon").id))

        assertEquals(assigned("Uber Eats"), withoutAmazon.match("UBER EATS MADRID"))
    }

    @Test
    fun `a blank text is never remembered`() {
        val learnt = rules.remember("  ", merchant("Amazon").id)

        assertEquals(MerchantMatch.Uncategorised(emptyList()), learnt.match(""))
        assertEquals(emptyMap<String, Long>(), learnt.remembered)
    }

    private val food = 10L
    private val groceries = 11L
    private val restaurants = 12L
    private val mercadona = Merchant(id = 1, name = "Mercadona", keywords = listOf("mercadona"), defaultCategoryId = food)
    private val lidl = Merchant(id = 2, name = "Lidl", keywords = listOf("lidl"), defaultCategoryId = food)
    private val before = MerchantRules(listOf(mercadona, lidl))

    private val date = LocalDate.of(2026, 5, 4)
    private val mercadonaInFood = Transaction(
        id = 1, accountId = 1, categoryId = food, amountCents = -4_250, date = date,
        time = LocalTime.of(18, 30), merchantId = mercadona.id, note = "Weekly shop",
    )
    private val mercadonaUncategorised = Transaction(
        id = 2, accountId = 2, categoryId = null, amountCents = -1_199, date = date.minusDays(3), merchantId = mercadona.id,
    )
    private val mercadonaInRestaurants = Transaction(
        id = 3, accountId = 1, categoryId = restaurants, amountCents = -800, date = date, merchantId = mercadona.id,
    )
    private val transferLeg = Transaction(
        id = 4, accountId = 1, categoryId = null, amountCents = -5_000, date = date, merchantId = mercadona.id, transferId = 9,
    )
    private val lidlInFood = Transaction(id = 5, accountId = 1, categoryId = food, amountCents = -2_000, date = date, merchantId = lidl.id)
    private val lidlUncategorised = Transaction(id = 6, accountId = 1, categoryId = null, amountCents = -300, date = date, merchantId = lidl.id)
    private val noMerchant = Transaction(id = 7, accountId = 1, categoryId = null, amountCents = -100, date = date)
    private val transactions = listOf(
        mercadonaInFood, mercadonaUncategorised, mercadonaInRestaurants, transferLeg, lidlInFood, lidlUncategorised, noMerchant,
    )

    @Test
    fun `a new default Category moves the Merchant's Transactions in the old default and its Uncategorised ones`() {
        assertEquals(
            listOf(mercadonaInFood.copy(categoryId = groceries), mercadonaUncategorised.copy(categoryId = groceries)),
            before.reapply(mercadona.copy(defaultCategoryId = groceries), transactions),
        )
    }

    @Test
    fun `changing only the keywords asks nothing`() {
        assertEquals(emptyList<Transaction>(), before.reapply(mercadona.copy(keywords = listOf("mercadona", "mdona")), transactions))
    }

    @Test
    fun `clearing the default Category asks nothing`() {
        assertEquals(emptyList<Transaction>(), before.reapply(mercadona.copy(defaultCategoryId = null), transactions))
    }

    @Test
    fun `a new default that would change no earlier Transaction asks nothing`() {
        assertEquals(
            emptyList<Transaction>(),
            before.reapply(mercadona.copy(defaultCategoryId = groceries), listOf(mercadonaInRestaurants, transferLeg, lidlInFood)),
        )
    }

    @Test
    fun `a new rule applies only to its Merchant's Uncategorised Transactions`() {
        val dia = Merchant(id = 3, name = "Dia", keywords = listOf("dia"), defaultCategoryId = groceries)
        val diaInFood = Transaction(id = 8, accountId = 1, categoryId = food, amountCents = -650, date = date, merchantId = dia.id)
        val diaUncategorised = Transaction(id = 9, accountId = 1, categoryId = null, amountCents = -720, date = date, merchantId = dia.id)

        assertEquals(
            listOf(diaUncategorised.copy(categoryId = groceries)),
            before.reapply(dia, transactions + diaInFood + diaUncategorised),
        )
    }
}
