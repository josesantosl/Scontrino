package com.pepesantos.scontrino.data.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Launcher icon of the app: dark (the default) or light, the inverted version for the light theme.
 *
 * Each icon is an `<activity-alias>` of MainActivity in the manifest; switching enables one and
 * disables the other. The system keeps that state, so nothing is stored in the preferences.
 * Launchers may take a few seconds to show the new icon, and some remove the home screen
 * shortcut, which then has to be added again from the app drawer.
 */
object AppIcon {

    private const val DARK_ALIAS = "com.pepesantos.scontrino.LauncherDark"
    private const val LIGHT_ALIAS = "com.pepesantos.scontrino.LauncherLight"

    fun isLight(context: Context): Boolean {
        val state = context.packageManager.getComponentEnabledSetting(ComponentName(context, LIGHT_ALIAS))
        // DEFAULT means "as in the manifest", where the light alias is disabled.
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    fun setLight(context: Context, light: Boolean) {
        if (isLight(context) == light) return
        val pm = context.packageManager
        // Enable the new one first so that the app always has a launcher entry.
        pm.setComponentEnabledSetting(
            ComponentName(context, if (light) LIGHT_ALIAS else DARK_ALIAS),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        pm.setComponentEnabledSetting(
            ComponentName(context, if (light) DARK_ALIAS else LIGHT_ALIAS),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
