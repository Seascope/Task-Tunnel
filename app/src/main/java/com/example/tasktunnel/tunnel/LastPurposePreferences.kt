package com.example.tasktunnel.tunnel

import android.content.Context

/**
 * Remembers the last successfully-started Task Tunnel purpose for each supported app and the
 * last successfully-used duration for each purpose.
 *
 * Only enum names and duration millis are persisted; no accessibility content or user-entered
 * text is stored. Invalid or obsolete values fail open to the normal Purpose Gate defaults.
 */
object LastPurposePreferences {
    private const val PREFERENCES_NAME = "task_tunnel_last_purpose"
    private const val PURPOSE_KEY_PREFIX = "last_purpose_"
    private const val DURATION_KEY_PREFIX = "last_duration_"
    private const val NO_LIMIT_SENTINEL = -1L

    data class RememberedDuration(val durationMillis: Long?)

    fun load(context: Context, app: SupportedApp): TunnelTask? {
        val stored = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(purposeKey(app), null)
        return parseStoredTask(stored, app)
    }

    fun loadDuration(context: Context, task: TunnelTask): RememberedDuration? {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val key = durationKey(task)
        if (!preferences.contains(key)) return null
        val stored = runCatching { preferences.getLong(key, Long.MIN_VALUE) }.getOrNull()
        return parseStoredDuration(stored)
    }

    fun save(
        context: Context,
        task: TunnelTask,
        intendedDurationMillis: Long?,
        rememberDuration: Boolean = true,
    ) {
        val editor = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(purposeKey(task.app), task.name)
        if (rememberDuration) {
            editor.putLong(durationKey(task), intendedDurationMillis ?: NO_LIMIT_SENTINEL)
        }
        editor.apply()
    }

    internal fun parseStoredTask(stored: String?, app: SupportedApp): TunnelTask? = stored
        ?.let { runCatching { TunnelTask.valueOf(it) }.getOrNull() }
        ?.takeIf { it.app == app }

    internal fun parseStoredDuration(stored: Long?): RememberedDuration? = when {
        stored == null || stored == Long.MIN_VALUE -> null
        stored == NO_LIMIT_SENTINEL -> RememberedDuration(null)
        stored > 0L -> RememberedDuration(stored)
        else -> null
    }

    private fun purposeKey(app: SupportedApp): String = PURPOSE_KEY_PREFIX + app.packageName

    private fun durationKey(task: TunnelTask): String = DURATION_KEY_PREFIX + task.name
}
