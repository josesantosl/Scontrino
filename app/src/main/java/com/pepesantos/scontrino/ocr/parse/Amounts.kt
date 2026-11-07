package com.pepesantos.scontrino.ocr.parse

import kotlin.math.abs

/** Reads money amounts and VAT rates from single OCR words. */
object Amounts {

    /**
     * An amount always has exactly two decimals ("1,99", "1.45"). A leading "~" is
     * how the OCR often reads the minus of a discount ("~0,90").
     */
    private val AMOUNT = Regex("""^([-–~]?)(\d{1,5})[,.](\d{2})$""")
    private val PERCENT = Regex("""^(\d{1,2}(?:[,.]\d{1,2})?)%$""")

    /** Stray marks the OCR glues to numbers: quotes, underscores, bars... */
    private const val JUNK = "‘’'`“”\"_|>«»*/"

    /** Amount in cents (negative for discounts), or null if the word is not an amount. */
    fun parseCents(word: String): Long? {
        val match = AMOUNT.matchEntire(clean(word)) ?: return null
        val (sign, units, decimals) = match.destructured
        val cents = units.toLong() * 100 + decimals.toLong()
        return if (sign.isEmpty()) cents else -cents
    }

    /** VAT rate in percent ("22%", "/22%", "10%." → 22.0, 22.0, 10.0). */
    fun parsePercent(word: String): Double? {
        val match = PERCENT.matchEntire(clean(word)) ?: return null
        return match.groupValues[1].replace(',', '.').toDoubleOrNull()
    }

    fun format(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val positive = abs(cents)
        return "$sign${positive / 100},${(positive % 100).toString().padStart(2, '0')}"
    }

    private fun clean(word: String) =
        word.trim().trimStart { it in JUNK }.trimEnd { it in JUNK || it == '.' }
}
