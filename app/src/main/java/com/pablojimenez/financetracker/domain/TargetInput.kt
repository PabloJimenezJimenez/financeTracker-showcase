package com.pablojimenez.financetracker.domain

import java.math.BigDecimal

/**
 * What the owner types as a KPI target, in a Metric's [ResultScale]: digits with `,` or `.` as the
 * decimal separator and at most as many decimals as the scale has (two for money, none for a
 * count, one for percentages, points and changes), optionally after "-" or "−" (U+2212).
 */
object TargetInput {
    private const val MAX_WHOLE_DIGITS = 9

    private fun pattern(scale: ResultScale) =
        Regex("""([-−])?(\d{0,$MAX_WHOLE_DIGITS})(?:[.,](\d{0,${scale.decimals}}))?""")

    /** Whether [text] is a complete or partly typed target in [scale], so a field can refuse anything else. */
    fun accepts(text: String, scale: ResultScale): Boolean =
        pattern(scale).matchEntire(text) != null && (scale.decimals > 0 || text.none { it == ',' || it == '.' })

    /**
     * [text] in [scale]'s whole numbers (e.g. "250,5" money is 25050 cents), exactly and without
     * floating point, or null when it is not a target in [scale].
     */
    fun parse(text: String, scale: ResultScale): Long? {
        if (!accepts(text.trim(), scale)) return null
        val match = pattern(scale).matchEntire(text.trim()) ?: return null
        val whole = match.groupValues[2]
        val fraction = match.groupValues[3]
        if (whole.isEmpty() && fraction.isEmpty()) return null
        val digits = whole.ifEmpty { "0" } + fraction.padEnd(scale.decimals, '0')
        val amount = digits.toLong()
        return if (match.groups[1] != null) -amount else amount
    }

    /** [target] in [scale] as text the owner could have typed, with [decimalSeparator] and no grouping. */
    fun text(target: Long, scale: ResultScale, decimalSeparator: Char): String {
        val plain = BigDecimal.valueOf(target, scale.decimals).toPlainString()
        return plain.replace('.', decimalSeparator)
    }
}
