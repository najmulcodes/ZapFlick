package com.najmulcodes.zapflick.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** Helpers for asking the system not to stop downloads when the screen is off. */
object BatteryOptimization {
    private const val PREFS = "zapflick_ui"
    private const val KEY_DISMISSED = "battery_card_dismissed"

    fun isRestricted(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isDismissed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DISMISSED, false)

    fun dismiss(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DISMISSED, true).apply()
    }

    /** Shows the system's one-tap "allow background activity" dialog, or the settings list as a fallback. */
    fun requestExemption(context: Context) {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        try {
            context.startActivity(direct)
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: ActivityNotFoundException) {
                // Some builds ship neither screen; nothing more can be done from here.
            }
        }
    }
}
