package com.example.tasktunnel.tunnel

import android.content.Context

object IntentionalCheckInPreferences {
    private const val PREFERENCES_NAME = "task_tunnel_preferences"
    private const val ENABLED_KEY = "intentional_check_ins_enabled"

    fun load(context: Context): Boolean = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    ).getBoolean(ENABLED_KEY, true)

    fun save(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED_KEY, enabled)
            .apply()
    }
}
