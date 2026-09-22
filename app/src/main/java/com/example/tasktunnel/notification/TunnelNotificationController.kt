package com.example.tasktunnel.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.tasktunnel.R
import com.example.tasktunnel.attention.surfaceLabel
import com.example.tasktunnel.attention.taskLabel
import com.example.tasktunnel.tunnel.IntentionCheckInKind
import com.example.tasktunnel.tunnel.TunnelPrompt
import com.example.tasktunnel.tunnel.TunnelRuntimeState
import com.example.tasktunnel.tunnel.TunnelStatus

/**
 * A deliberately small notification surface for the active tunnel.
 *
 * It renders coordinator state and emits commands back to the AccessibilityService. It does not
 * own tunnel state, launch a second UI, or perform navigation itself.
 */
class TunnelNotificationController(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private var lastRenderKey: String? = null

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val systemManager = context.getSystemService(NotificationManager::class.java)
        if (systemManager.getNotificationChannel(CHANNEL_ID) != null) return
        systemManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Active tunnel controls",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Silent controls for an active Task Tunnel"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
    }

    fun sync(state: TunnelRuntimeState) {
        ensureChannel()
        val session = state.activeSession
        if (session == null) {
            clearDismissedSession()
            lastRenderKey = null
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (preferences.getString(KEY_DISMISSED_SESSION, null) == session.id) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!canPostNotifications()) return

        val renderKey = renderKey(state)
        if (renderKey == lastRenderKey) return
        val notification = buildNotification(state)
        if (runCatching { manager.notify(NOTIFICATION_ID, notification) }.isSuccess) {
            lastRenderKey = renderKey
        }
    }

    fun suppressForSession(sessionId: String) {
        preferences.edit().putString(KEY_DISMISSED_SESSION, sessionId).apply()
        lastRenderKey = null
        manager.cancel(NOTIFICATION_ID)
    }

    fun cancel() {
        lastRenderKey = null
        manager.cancel(NOTIFICATION_ID)
    }

    private fun clearDismissedSession() {
        if (preferences.contains(KEY_DISMISSED_SESSION)) {
            preferences.edit().remove(KEY_DISMISSED_SESSION).apply()
        }
    }

    private fun canPostNotifications(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permissionGranted && manager.areNotificationsEnabled()
    }

    private fun renderKey(state: TunnelRuntimeState): String {
        val session = requireNotNull(state.activeSession)
        val prompt = state.prompt
        return buildString {
            append(session.id).append('|')
            append(session.task).append('|')
            append(session.status).append('|')
            append(session.expiresAtMillis).append('|')
            append(state.overrideScope?.takeIf { it.sessionId == session.id }?.surface).append('|')
            when (prompt) {
                is TunnelPrompt.Intervention -> append("intervention:").append(prompt.surface)
                is TunnelPrompt.IntentionCheckIn -> append("checkin:").append(prompt.kind).append(':').append(prompt.surface)
                is TunnelPrompt.SessionExpired -> append("expired")
                is TunnelPrompt.PurposeGate -> append("purpose")
                null -> append("none")
            }
        }
    }

    private fun buildNotification(state: TunnelRuntimeState): Notification {
        val session = requireNotNull(state.activeSession)
        val expired = session.status == TunnelStatus.EXPIRED || state.prompt is TunnelPrompt.SessionExpired
        val detourSurface = state.overrideScope?.takeIf { it.sessionId == session.id }?.surface
        val intervention = (state.prompt as? TunnelPrompt.Intervention)?.takeIf { it.sessionId == session.id }
        val checkIn = (state.prompt as? TunnelPrompt.IntentionCheckIn)?.takeIf { it.sessionId == session.id }

        val title = when {
            expired -> "${session.app.displayName} · Time complete"
            detourSurface != null -> "${session.app.displayName} · Detour active"
            intervention != null || checkIn?.kind == IntentionCheckInKind.DETOUR_RENEWAL -> "${session.app.displayName} · Refocus"
            else -> "${session.app.displayName} · Tunnel active"
        }
        val content = when {
            expired -> "${taskLabel(session.task)} · Choose what to do next"
            detourSurface != null -> "${surfaceLabel(detourSurface)} allowed temporarily · ${taskLabel(session.task)}"
            intervention != null -> "${surfaceLabel(intervention.surface)} is outside ${taskLabel(session.task)}"
            checkIn?.kind == IntentionCheckInKind.DETOUR_RENEWAL -> "Still want ${checkIn.surface?.let(::surfaceLabel) ?: "this detour"}? · ${taskLabel(session.task)}"
            checkIn?.kind == IntentionCheckInKind.OPEN_ENDED_BROWSE -> "Intentional check-in · ${taskLabel(session.task)}"
            session.expiresAtMillis == null -> "${taskLabel(session.task)} · No time limit"
            else -> taskLabel(session.task)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_task_tunnel)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setDeleteIntent(actionPendingIntent(ACTION_DISMISSED, session.id, REQUEST_DISMISSED))
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_task_tunnel)
                    .setContentTitle("Task Tunnel active")
                    .setContentText("Tunnel controls available after unlock")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build(),
            )

        if (!expired && detourSurface == null && intervention == null && checkIn == null && session.expiresAtMillis != null) {
            builder
                .setWhen(session.expiresAtMillis!!)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
        } else {
            builder.setShowWhen(false)
        }

        when {
            expired || checkIn?.kind == IntentionCheckInKind.OPEN_ENDED_BROWSE -> {
                builder.addAction(0, "Continue", actionPendingIntent(ACTION_CONTINUE, session.id, REQUEST_CONTINUE))
                builder.addAction(0, "Change purpose", actionPendingIntent(ACTION_CHANGE_PURPOSE, session.id, REQUEST_CHANGE_PURPOSE))
                builder.addAction(0, "End", actionPendingIntent(ACTION_END, session.id, REQUEST_END))
            }
            detourSurface != null || intervention != null || checkIn?.kind == IntentionCheckInKind.DETOUR_RENEWAL -> {
                builder.addAction(0, "Change purpose", actionPendingIntent(ACTION_CHANGE_PURPOSE, session.id, REQUEST_CHANGE_PURPOSE))
                builder.addAction(0, "End", actionPendingIntent(ACTION_END, session.id, REQUEST_END))
            }
            else -> {
                builder.addAction(0, "Change purpose", actionPendingIntent(ACTION_CHANGE_PURPOSE, session.id, REQUEST_CHANGE_PURPOSE))
                builder.addAction(0, "End", actionPendingIntent(ACTION_END, session.id, REQUEST_END))
            }
        }
        return builder.build()
    }

    private fun actionPendingIntent(action: String, sessionId: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, TunnelNotificationActionReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("tasktunnel://notification/$sessionId/${action.substringAfterLast('.')}")
            putExtra(EXTRA_SESSION_ID, sessionId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "active_tunnel_controls"
        const val ACTION_CHANGE_PURPOSE = "com.example.tasktunnel.notification.CHANGE_PURPOSE"
        const val ACTION_CONTINUE = "com.example.tasktunnel.notification.CONTINUE"
        const val ACTION_END = "com.example.tasktunnel.notification.END"
        const val ACTION_DISMISSED = "com.example.tasktunnel.notification.DISMISSED"
        const val EXTRA_SESSION_ID = "session_id"
        private const val NOTIFICATION_ID = 4107
        private const val PREFERENCES = "tunnel_notification"
        private const val KEY_DISMISSED_SESSION = "dismissed_session"
        private const val REQUEST_CHANGE_PURPOSE = 4103
        private const val REQUEST_CONTINUE = 4104
        private const val REQUEST_END = 4105
        private const val REQUEST_DISMISSED = 4106
    }
}
