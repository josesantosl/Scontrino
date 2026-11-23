package com.pepesantos.scontrino.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App preferences: language, currency, theme, whether prices include taxes and the languages
 * receipts are printed in. Kept in SharedPreferences because the language
 * has to be read synchronously before the activity is created (see [AppLocale]).
 */
class SettingsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences = preferences(context)

    private val _language = MutableStateFlow(prefs.getString(KEY_LANGUAGE, null).orEmpty())
    /** BCP 47 tag ("es", "it"...), or empty to follow the system language. */
    val language: StateFlow<String> = _language.asStateFlow()

    private val _currency = MutableStateFlow(prefs.getString(KEY_CURRENCY, null) ?: DEFAULT_CURRENCY)
    /** Currency of new receipts and of the statistics. */
    val currency: StateFlow<String> = _currency.asStateFlow()

    private val _taxIncluded = MutableStateFlow(prefs.getBoolean(KEY_TAX_INCLUDED, true))
    /**
     * Whether shelf prices already include taxes (VAT in Europe). When false (US sales
     * tax), receipts get a tax field added on top of the items.
     */
    val taxIncluded: StateFlow<Boolean> = _taxIncluded.asStateFlow()

    fun setTaxIncluded(included: Boolean) {
        prefs.edit { putBoolean(KEY_TAX_INCLUDED, included) }
        _taxIncluded.value = included
    }

    private val _darkMode = MutableStateFlow(prefs.getBoolean(KEY_DARK_MODE, true))
    /** Dark theme (the default and the intended look) or light theme, regardless of the system setting. */
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DARK_MODE, enabled) }
        _darkMode.value = enabled
    }

    private val _collapsedStatsCards = MutableStateFlow(
        prefs.getStringSet(KEY_COLLAPSED_STATS_CARDS, null)?.toSet() ?: emptySet()
    )
    /** Keys of the statistics cards the user has collapsed. */
    val collapsedStatsCards: StateFlow<Set<String>> = _collapsedStatsCards.asStateFlow()

    fun setStatsCardCollapsed(key: String, collapsed: Boolean) {
        val updated = if (collapsed) _collapsedStatsCards.value + key else _collapsedStatsCards.value - key
        prefs.edit { putStringSet(KEY_COLLAPSED_STATS_CARDS, updated) }
        _collapsedStatsCards.value = updated
    }

    fun setLanguage(tag: String) {
        prefs.edit { putString(KEY_LANGUAGE, tag) }
        _language.value = tag
    }

    fun setCurrency(currency: String) {
        prefs.edit { putString(KEY_CURRENCY, currency) }
        _currency.value = currency
    }

    private val _receiptLanguages = MutableStateFlow(storedReceiptLanguages(prefs))
    /**
     * Tesseract codes of the languages receipts are printed in, the main one first.
     * Empty until the user picks them: the scanner then guesses from the locales.
     */
    val receiptLanguages: StateFlow<List<String>> = _receiptLanguages.asStateFlow()

    fun setReceiptLanguages(codes: List<String>) {
        prefs.edit { putString(KEY_RECEIPT_LANGUAGES, codes.joinToString(",")) }
        _receiptLanguages.value = codes
    }

    private val _paymentApp = MutableStateFlow(prefs.getString(KEY_PAYMENT_APP, null).orEmpty())
    /** Package of the app opened from the top of the wallet to pay, or empty for none. */
    val paymentApp: StateFlow<String> = _paymentApp.asStateFlow()

    private val _paymentAppName = MutableStateFlow(prefs.getString(KEY_PAYMENT_APP_NAME, null).orEmpty())
    /** Name shown on the wallet's payment app card, or empty for the app's own label. */
    val paymentAppName: StateFlow<String> = _paymentAppName.asStateFlow()

    private val _paymentAppColor = MutableStateFlow(
        prefs.getLong(KEY_PAYMENT_APP_COLOR, NO_COLOR).takeIf { it != NO_COLOR }
    )
    /** Colour (ARGB) of the wallet's payment app card, or null for the default one. */
    val paymentAppColor: StateFlow<Long?> = _paymentAppColor.asStateFlow()

    /** Changes the app; its custom name and colour are for the previous app, so they're reset. */
    fun setPaymentApp(packageName: String) {
        if (packageName != _paymentApp.value) setPaymentAppCard(name = "", color = null)
        prefs.edit { putString(KEY_PAYMENT_APP, packageName) }
        _paymentApp.value = packageName
    }

    fun setPaymentAppCard(name: String, color: Long?) {
        prefs.edit {
            putString(KEY_PAYMENT_APP_NAME, name)
            if (color == null) remove(KEY_PAYMENT_APP_COLOR) else putLong(KEY_PAYMENT_APP_COLOR, color)
        }
        _paymentAppName.value = name
        _paymentAppColor.value = color
    }

    companion object {
        const val DEFAULT_CURRENCY = "EUR"
        private const val FILE = "settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_TAX_INCLUDED = "tax_included"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_COLLAPSED_STATS_CARDS = "collapsed_stats_cards"
        private const val KEY_RECEIPT_LANGUAGES = "receipt_languages"
        private const val KEY_PAYMENT_APP = "payment_app"
        private const val KEY_PAYMENT_APP_NAME = "payment_app_name"
        private const val KEY_PAYMENT_APP_COLOR = "payment_app_color"
        private const val NO_COLOR = 0L

        @Volatile
        private var instance: SettingsRepository? = null

        /**
         * The single instance, so that every screen sees a change at once (each instance
         * keeps its own copy of the values).
         */
        fun getInstance(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }

        private fun preferences(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        /** Language tag stored, readable before any repository exists. */
        fun storedLanguage(context: Context): String =
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null).orEmpty()

        /** Receipt languages stored (see [receiptLanguages]), read fresh at each scan. */
        fun storedReceiptLanguages(context: Context): List<String> = storedReceiptLanguages(preferences(context))

        private fun storedReceiptLanguages(prefs: SharedPreferences): List<String> =
            prefs.getString(KEY_RECEIPT_LANGUAGES, null).orEmpty().split(',').filter { it.isNotBlank() }
    }
}
