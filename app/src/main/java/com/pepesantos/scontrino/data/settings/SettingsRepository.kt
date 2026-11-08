package com.pepesantos.scontrino.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App preferences: language and currency. Kept in SharedPreferences because the language
 * has to be read synchronously before the activity is created (see [AppLocale]).
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = preferences(context)

    private val _language = MutableStateFlow(prefs.getString(KEY_LANGUAGE, null).orEmpty())
    /** BCP 47 tag ("es", "it"...), or empty to follow the system language. */
    val language: StateFlow<String> = _language.asStateFlow()

    private val _currency = MutableStateFlow(prefs.getString(KEY_CURRENCY, null) ?: DEFAULT_CURRENCY)
    /** Currency of new receipts and of the statistics. */
    val currency: StateFlow<String> = _currency.asStateFlow()

    fun setLanguage(tag: String) {
        prefs.edit { putString(KEY_LANGUAGE, tag) }
        _language.value = tag
    }

    fun setCurrency(currency: String) {
        prefs.edit { putString(KEY_CURRENCY, currency) }
        _currency.value = currency
    }

    companion object {
        const val DEFAULT_CURRENCY = "EUR"
        private const val FILE = "settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_CURRENCY = "currency"

        private fun preferences(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        /** Language tag stored, readable before any repository exists. */
        fun storedLanguage(context: Context): String =
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null).orEmpty()
    }
}
