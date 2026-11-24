package com.pablojimenez.financetracker.domain

/** What [MerchantRules.match] makes of a bank's text. */
sealed interface MerchantMatch {
    /** The text is this Merchant's. */
    data class Assigned(val merchant: Merchant) : MerchantMatch

    /** No single Merchant: the owner picks, among [choices] when there are any. */
    data class Uncategorised(val choices: List<Merchant>) : MerchantMatch
}

/**
 * The Merchant rules: they recognise a Merchant in a bank's text, remember the owner's choice
 * for a bank text, and work out which earlier Transactions a changed Merchant recategorises.
 *
 * [merchants] are the owner's Merchants as they are now. [remembered] maps a bank text, folded
 * with [foldMerchantText], to the id of the Merchant the owner chose for it; bank sync stores it
 * and passes it back in. Rules are a value: [remember] returns new rules and leaves these as
 * they are.
 */
class MerchantRules(
    private val merchants: List<Merchant>,
    remembered: Map<String, Long> = emptyMap(),
) {
    /** The owner's choices by folded bank text, to store and pass back in later. */
    val remembered: Map<String, Long> = remembered.toMap()

    /**
     * The Merchant of [bankText]. A blank text is [MerchantMatch.Uncategorised] with no choices.
     * A choice remembered for the same text apart from case, accents and spacing wins, as long as
     * its Merchant is still among the rules' Merchants. Otherwise keywords decide: a keyword
     * matches as a whole word, regardless of case and accents, and a matching keyword is dropped
     * when another matching one contains it as a whole word ("uber eats" over "uber"). One
     * Merchant left is [MerchantMatch.Assigned]; several are offered as choices, sorted by name
     * ignoring case and accents. A Merchant with no keywords never matches.
     *
     * The Transaction of an assigned Merchant takes its default Category, or stays Uncategorised
     * when it has none.
     */
    fun match(bankText: String): MerchantMatch {
        val text = foldMerchantText(bankText)
        if (text.isEmpty()) return MerchantMatch.Uncategorised(emptyList())
        remembered[text]?.let { id -> merchants.find { it.id == id } }?.let { return MerchantMatch.Assigned(it) }
        val found = merchantsByKeyword(text)
        return found.singleOrNull()?.let { MerchantMatch.Assigned(it) } ?: MerchantMatch.Uncategorised(found)
    }

    /**
     * These rules with the owner's choice of the Merchant [merchantId] for [bankText], replacing
     * any earlier choice for the same folded text. A blank text is never remembered.
     */
    fun remember(bankText: String, merchantId: Long): MerchantRules {
        val text = foldMerchantText(bankText)
        if (text.isEmpty()) return this
        return MerchantRules(merchants, remembered + (text to merchantId))
    }

    /**
     * The earlier [transactions] that [changed] recategorises, with its new default Category and
     * every other field as it was. The rules hold the Merchants as they were before the change;
     * a Merchant not among them is a new rule.
     *
     * Only the changed Merchant's Transactions that are not Transfer legs move, and only those
     * that are Uncategorised or in the Merchant's old default Category: any other Category was
     * set by hand and is kept. Nothing moves when the default Category did not change (only the
     * keywords did) or is now none. The app asks the owner once about the whole list, and
     * nothing when it is empty.
     */
    fun reapply(changed: Merchant, transactions: List<Transaction>): List<Transaction> {
        val newDefault = changed.defaultCategoryId ?: return emptyList()
        val oldDefault = merchants.find { it.id == changed.id }?.defaultCategoryId
        if (oldDefault == newDefault) return emptyList()
        return transactions
            .filter { it.merchantId == changed.id && it.transferId == null }
            .filter { it.categoryId == null || it.categoryId == oldDefault }
            .map { it.copy(categoryId = newDefault) }
    }

    /** The Merchants whose most specific matching keywords are in [text], sorted by folded name. */
    private fun merchantsByKeyword(text: String): List<Merchant> {
        val hits = merchants.flatMap { merchant ->
            merchant.keywords.map(::foldMerchantText).filter { text.containsWord(it) }.map { merchant to it }
        }
        val keywords = hits.map { (_, keyword) -> keyword }.toSet()
        return hits
            .filter { (_, keyword) -> keywords.none { other -> other != keyword && other.containsWord(keyword) } }
            .map { (merchant, _) -> merchant }
            .distinct()
            .sortedBy { foldMerchantText(it.name) }
    }
}

/**
 * Whether [word] occurs in this text with a text edge or a character that is not a letter or
 * digit on each side: "h&m" in "h&m online", but not "dia" in "diagonal". Never a blank [word].
 */
private fun String.containsWord(word: String): Boolean {
    if (word.isEmpty()) return false
    var start = indexOf(word)
    while (start >= 0) {
        val end = start + word.length
        val openEdge = start == 0 || !this[start - 1].isLetterOrDigit()
        val closeEdge = end == length || !this[end].isLetterOrDigit()
        if (openEdge && closeEdge) return true
        start = indexOf(word, start + 1)
    }
    return false
}
