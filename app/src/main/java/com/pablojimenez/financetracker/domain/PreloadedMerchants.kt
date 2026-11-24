package com.pablojimenez.financetracker.domain

/**
 * A Merchant the app comes with. [key] is stable for good, whatever [name] says, so the app
 * knows which entries a database was ever seeded with and never seeds one twice. Its default
 * Category is the starter Category with seed id [defaultCategorySeedId] (see
 * [SeededStarterCategories]), or none when that Category no longer exists.
 */
data class PreloadedMerchant(
    val key: String,
    val name: String,
    val keywords: List<String>,
    val defaultCategorySeedId: Long,
)

/** Keywords beyond the folded name, by preload key: how some banks write these Merchants. */
private val bankVariants: Map<String, List<String>> = mapOf(
    "moeve" to listOf("cepsa"),
    "amazon" to listOf("amzn"),
    "elcorteingles" to listOf("corte ingles"),
)

/**
 * The Merchants a database is seeded with: on creation, and on any later update that adds
 * entries. Once seeded they are the owner's data, so the names are not translated, and a renamed
 * or deleted one stays that way. Keywords are the folded name plus obvious bank variants.
 */
val PreloadedMerchants: List<PreloadedMerchant> = buildList {
    fun category(category: String, vararg merchants: Pair<String, String>) {
        val seedId = SeededStarterCategories.single { it.name == category }.id
        merchants.forEach { (key, name) ->
            val keywords = listOf(foldMerchantText(name)) + bankVariants[key].orEmpty()
            add(PreloadedMerchant(key, name, keywords, seedId))
        }
    }
    category(
        "Food",
        "mercadona" to "Mercadona", "carrefour" to "Carrefour", "lidl" to "Lidl", "aldi" to "Aldi",
        "dia" to "Dia", "alcampo" to "Alcampo", "eroski" to "Eroski", "consum" to "Consum",
        "hipercor" to "Hipercor", "ahorramas" to "Ahorramas", "bonpreu" to "Bonpreu",
        "caprabo" to "Caprabo", "condis" to "Condis", "coviran" to "Covirán",
    )
    category("Personal care", "primor" to "Primor", "druni" to "Druni", "sephora" to "Sephora")
    category(
        "Fuel",
        "repsol" to "Repsol", "moeve" to "Moeve", "galp" to "Galp", "bp" to "BP", "shell" to "Shell",
        "ballenoil" to "Ballenoil",
    )
    category(
        "Public transport",
        "renfe" to "Renfe", "ouigo" to "Ouigo", "iryo" to "Iryo", "alsa" to "Alsa",
        "metrodemadrid" to "Metro de Madrid", "tmb" to "TMB",
    )
    category("Taxi", "uber" to "Uber", "cabify" to "Cabify", "bolt" to "Bolt", "freenow" to "FreeNow")
    category(
        "Clothes",
        "zara" to "Zara", "pull&bear" to "Pull&Bear", "bershka" to "Bershka", "stradivarius" to "Stradivarius",
        "massimodutti" to "Massimo Dutti", "lefties" to "Lefties", "mango" to "Mango", "primark" to "Primark",
        "h&m" to "H&M", "shein" to "Shein",
    )
    category(
        "Electronics",
        "mediamarkt" to "MediaMarkt", "pccomponentes" to "PcComponentes", "fnac" to "Fnac", "worten" to "Worten",
    )
    category("Home", "ikea" to "IKEA", "leroymerlin" to "Leroy Merlin", "bricomart" to "Bricomart")
    category(
        "Shopping",
        "amazon" to "Amazon", "elcorteingles" to "El Corte Inglés", "aliexpress" to "AliExpress", "temu" to "Temu",
    )
    category(
        "Restaurants",
        "mcdonald's" to "McDonald's", "burgerking" to "Burger King", "kfc" to "KFC", "telepizza" to "Telepizza",
        "domino's" to "Domino's", "fiveguys" to "Five Guys", "100montaditos" to "100 Montaditos",
        "foster'shollywood" to "Foster's Hollywood",
    )
    category("Coffee", "starbucks" to "Starbucks", "timhortons" to "Tim Hortons")
    category("Delivery", "glovo" to "Glovo", "justeat" to "Just Eat", "ubereats" to "Uber Eats")
    category(
        "Utilities",
        "iberdrola" to "Iberdrola", "endesa" to "Endesa", "naturgy" to "Naturgy",
        "totalenergies" to "TotalEnergies", "holaluz" to "Holaluz",
    )
    category(
        "Internet & phone",
        "movistar" to "Movistar", "vodafone" to "Vodafone", "orange" to "Orange", "yoigo" to "Yoigo",
        "masmovil" to "MásMóvil", "digi" to "Digi", "pepephone" to "Pepephone", "o2" to "O2",
    )
    category(
        "Subscriptions",
        "netflix" to "Netflix", "spotify" to "Spotify", "hbomax" to "HBO Max", "disney+" to "Disney+",
        "primevideo" to "Prime Video", "dazn" to "DAZN", "apple" to "Apple", "google" to "Google",
    )
    category(
        "Travel",
        "ryanair" to "Ryanair", "vueling" to "Vueling", "iberia" to "Iberia", "aireuropa" to "Air Europa",
        "volotea" to "Volotea", "booking.com" to "Booking.com", "airbnb" to "Airbnb",
    )
    category("Events", "ticketmaster" to "Ticketmaster", "cinesa" to "Cinesa")
    category("Sport", "decathlon" to "Decathlon", "basic-fit" to "Basic-Fit", "mcfit" to "McFit")
    category("Insurance", "mapfre" to "Mapfre", "mutuamadrilena" to "Mutua Madrileña", "sanitas" to "Sanitas")
}
