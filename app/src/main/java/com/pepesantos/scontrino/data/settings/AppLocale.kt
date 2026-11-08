package com.pepesantos.scontrino.data.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/**
 * In-app language, applied by wrapping the activity's base context. Works on every
 * supported API level without AppCompat; the activity is recreated after a change.
 */
object AppLocale {

    /** Languages the app is translated to, as BCP 47 tags. Empty tag = system language. */
    val supported = listOf("en", "es", "it", "fr", "de")

    fun wrap(base: Context): Context {
        val tag = SettingsRepository.storedLanguage(base)
        val locale = if (tag.isEmpty()) systemLocale() else Locale.forLanguageTag(tag)
        // Dates and number formats use the default locale.
        Locale.setDefault(locale)
        if (tag.isEmpty()) return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /** Name of a language in that same language, e.g. "Español". */
    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }

    private fun systemLocale(): Locale = Resources.getSystem().configuration.locales[0]
}
