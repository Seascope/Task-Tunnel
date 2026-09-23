package com.example.tasktunnel.protection

import android.content.Context

/**
 * User-facing master switch for Task Tunnel's app-level behavior.
 *
 * This deliberately does not change Android's Accessibility permission or any individual feature
 * preference. Turning protection back on restores the user's existing Drift/check-in settings.
 */
object ProtectionMasterPreferences {
    private const val PREFERENCES_NAME = "protection_master"
    private const val ENABLED = "enabled"

    fun load(context: Context): Boolean = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    ).getBoolean(ENABLED, true)

    fun save(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED, enabled)
            .apply()
    }
}
