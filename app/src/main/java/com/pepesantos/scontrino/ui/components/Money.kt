package com.pepesantos.scontrino.ui.components

import androidx.compose.runtime.compositionLocalOf
import java.util.Currency
import java.util.Locale

/** Currency chosen in the settings, for amounts not tied to one receipt (statistics). */
val LocalCurrency = compositionLocalOf { "EUR" }

/**
 * Formats amounts with a currency that is either an ISO 4217 code or a custom symbol
 * typed by the user ("£", "zł"...).
 */
object Money {

    /** Currencies offered directly in the settings. */
    val presets = listOf("EUR", "USD", "GBP", "CHF")

    private val knownSymbols = mapOf("EUR" to "€", "USD" to "$", "CHF" to "CHF", "GBP" to "£", "JPY" to "¥")

    fun symbol(currency: String): String {
        knownSymbols[currency.uppercase(Locale.ROOT)]?.let { return it }
        val iso = currency.uppercase(Locale.ROOT).takeIf { it.length == 3 && it.all(Char::isLetter) }
        val symbol = iso?.let { runCatching { Currency.getInstance(it).getSymbol(Locale.getDefault()) }.getOrNull() }
        return symbol ?: currency
    }

    /** "€12.50", "$3.00", "CHF 4.20". */
    fun format(amount: Double, currency: String): String {
        val symbol = symbol(currency)
        val number = String.format(Locale.getDefault(), "%.2f", amount)
        return if (symbol.length == 1) "$symbol$number" else "$symbol $number"
    }

    /** Name for the settings, e.g. "Euro (€)"; custom symbols are shown as typed. */
    fun displayName(currency: String): String {
        val iso = currency.uppercase(Locale.ROOT)
        val name = runCatching { Currency.getInstance(iso).getDisplayName(Locale.getDefault()) }.getOrNull()
            ?: return currency
        val symbol = symbol(currency)
        val capitalized = name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        return if (symbol == iso) "$capitalized ($iso)" else "$capitalized ($symbol)"
    }
}
