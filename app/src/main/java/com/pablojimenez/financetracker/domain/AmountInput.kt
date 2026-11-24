package com.pablojimenez.financetracker.domain

/**
 * What the owner types as an amount: digits with `,` or `.` as the decimal separator and at most
 * two decimals, e.g. `12,50`. A signed amount may start with "-" or "−" (U+2212).
 */
object AmountInput {
    private const val MAX_WHOLE_DIGITS = 12
    private val signed = Regex("""([-−])?(\d{0,$MAX_WHOLE_DIGITS})(?:[.,](\d{0,2}))?""")

    /**
     * Whether [text] is a complete or partly typed amount, so a field can refuse anything else.
     * A sign is accepted only when [allowNegative].
     */
    fun accepts(text: String, allowNegative: Boolean = false): Boolean {
        val match = signed.matchEntire(text) ?: return false
        return allowNegative || match.groups[1] == null
    }

    /**
     * [text] in cents, exactly and without floating point, or null when it is not an amount
     * (blank, only a sign or a separator, or more than two decimals).
     */
    fun parseCents(text: String): Long? {
        val match = signed.matchEntire(text.trim()) ?: return null
        val whole = match.groupValues[2]
        val fraction = match.groupValues[3]
        if (whole.isEmpty() && fraction.isEmpty()) return null
        val cents = (whole.ifEmpty { "0" }.toLong() * 100) + fraction.padEnd(2, '0').toLong()
        return if (match.groups[1] != null) -cents else cents
    }

    /**
     * [cents] (zero or more) as text the owner could have typed, so a form can show a saved
     * amount: whole units, [decimalSeparator] and two decimals, e.g. `12,50`, with no grouping.
     */
    fun text(cents: Long, decimalSeparator: Char): String {
        require(cents >= 0) { "Only amounts typed without a sign are written back" }
        return "${cents / 100}$decimalSeparator${(cents % 100).toString().padStart(2, '0')}"
    }
}
