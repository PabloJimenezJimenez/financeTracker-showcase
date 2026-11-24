package com.pablojimenez.financetracker.domain

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** U+2212, the typographic minus used for every negative amount. */
const val MINUS_SIGN = '−'

/**
 * Formats [cents] as [currency] in [locale] without going through floating point, so every
 * `Long` amount is exact.
 *
 * Negative amounts always use [MINUS_SIGN] where the locale puts its minus. With
 * [explicitSign], positive amounts get a "+" in the same place. Zero is never signed.
 */
fun formatCents(
    cents: Long,
    locale: Locale = Locale.getDefault(),
    currency: Currency = Currency.getInstance("EUR"),
    explicitSign: Boolean = false,
): String {
    val format = NumberFormat.getCurrencyInstance(locale)
    format.currency = currency
    val localeMinus = (format as? DecimalFormat)?.decimalFormatSymbols?.minusSign ?: '-'
    val amount = BigDecimal.valueOf(cents, 2)
    return when {
        cents < 0 -> format.format(amount).replaceMinus(localeMinus, MINUS_SIGN)
        cents > 0 && explicitSign -> format.format(amount.negate()).replaceMinus(localeMinus, '+')
        else -> format.format(amount)
    }
}

/** Replaces only the sign: the first minus, so a hyphen elsewhere (e.g. in a symbol) is kept. */
private fun String.replaceMinus(localeMinus: Char, sign: Char): String {
    val index = indexOfFirst { it == localeMinus || it == '-' }
    return if (index < 0) this else replaceRange(index, index + 1, sign.toString())
}
