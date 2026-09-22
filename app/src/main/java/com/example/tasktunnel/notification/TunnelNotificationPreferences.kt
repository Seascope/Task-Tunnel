package com.example.tasktunnel.notification

import android.content.Context

object TunnelNotificationPreferences {
    private const val NAME = "tunnel_notification_permission"
    private const val OFFERED = "permission_offer_shown"

    fun wasPermissionOffered(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(OFFERED, false)

    fun markPermissionOffered(context: Context) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putBoolean(OFFERED, true).apply()
    }
}
