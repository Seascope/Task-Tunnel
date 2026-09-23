package com.example.tasktunnel.notification

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object TunnelNotificationPreferences {
    private const val NAME = "tunnel_notification_permission"
    private const val OFFERED = "permission_offer_shown"

    fun wasPermissionOffered(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(OFFERED, false)

    fun markPermissionOffered(context: Context) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putBoolean(OFFERED, true).apply()
    }

    fun controlsEnabled(context: Context): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(TunnelNotificationController.CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }
}
