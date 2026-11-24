package com.pablojimenez.financetracker.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Where money was spent or came from (e.g. Mercadona). Its Transactions get [defaultCategoryId]
 * unless the owner picks another; it is null when the Merchant has none. [keywords] recognise it
 * in a bank's text; a Merchant the owner creates starts with none.
 *
 * [id] is 0 until the repository saves the Merchant.
 */
data class Merchant(
    val id: Long = 0,
    val name: String,
    val keywords: List<String> = emptyList(),
    val defaultCategoryId: Long? = null,
)

/**
 * [text] lower-cased, without accents, trimmed and with every run of whitespace as one space:
 * "  El Corte  Inglés" is "el corte ingles". Merchant keywords are written this way.
 */
fun foldMerchantText(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .lowercase(Locale.ROOT)
        .trim()
        .replace(whitespace, " ")

/** [text] folded as [foldMerchantText] and without any whitespace: what two same names share. */
fun merchantNameKey(text: String): String = foldMerchantText(text).replace(whitespace, "")

/**
 * Whether [a] and [b] name the same Merchant: they differ only in case, accents or spacing
 * ("Burger King" and "burgerking", "Mercadona" and "Mércadona ").
 */
fun sameMerchantName(a: String, b: String): Boolean = merchantNameKey(a) == merchantNameKey(b)

/**
 * The Merchant rules typed as [text], keywords separated by commas: each folded with
 * [foldMerchantText], without blank or repeated ones, in their first order.
 * " Mercadona, MERCADONA online ,, Mércadona" is ["mercadona", "mercadona online"].
 */
fun merchantKeywords(text: String): List<String> =
    text.split(',').map(::foldMerchantText).filter { it.isNotEmpty() }.distinct()

/** Why a name can't be a Merchant's. */
enum class MerchantNameError {
    /** It has no letters, only spaces. */
    Blank,

    /** Another Merchant has the same name (see [sameMerchantName]). */
    Taken,
}

/**
 * Why [name] can't be the name of the Merchant [renaming] among these, or null when it can: it
 * must not be blank, and no other Merchant may have the same name. The Merchant's own name in
 * another case or spacing is allowed.
 */
fun List<Merchant>.merchantNameError(name: String, renaming: Long): MerchantNameError? = when {
    name.isBlank() -> MerchantNameError.Blank
    any { it.id != renaming && sameMerchantName(it.name, name) } -> MerchantNameError.Taken
    else -> null
}

/**
 * The Merchants whose name contains [text], ignoring case, accents and spacing: those whose name
 * starts with it first, then alphabetically, at most [limit]. None when [text] is blank.
 */
fun List<Merchant>.matching(text: String, limit: Int = 5): List<Merchant> {
    val key = merchantNameKey(text)
    if (key.isEmpty()) return emptyList()
    return map { it to merchantNameKey(it.name) }
        .filter { (_, name) -> key in name }
        .sortedWith(
            compareBy<Pair<Merchant, String>> { (_, name) -> !name.startsWith(key) }
                .thenBy { (merchant, _) -> foldMerchantText(merchant.name) },
        )
        .take(limit)
        .map { (merchant, _) -> merchant }
}

private val combiningMarks = Regex("\\p{M}+")
private val whitespace = Regex("\\s+")
