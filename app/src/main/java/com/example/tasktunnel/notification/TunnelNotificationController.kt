package com.example.tasktunnel.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.tasktunnel.R
import com.example.tasktunnel.attention.surfaceLabel
import com.example.tasktunnel.tunnel.IntentionCheckInKind
import com.example.tasktunnel.tunnel.TunnelPrompt
import com.example.tasktunnel.tunnel.TunnelRuntimeState
import com.example.tasktunnel.tunnel.TunnelStatus
import com.example.tasktunnel.tunnel.TunnelTask
import kotlin.math.roundToInt

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

    fun sync(state: TunnelRuntimeState, nowMillis: Long = System.currentTimeMillis()) {
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

        val renderKey = renderKey(state, nowMillis)
        if (renderKey == lastRenderKey) return
        val notification = buildNotification(state, nowMillis)
        if (runCatching { manager.notify(NOTIFICATION_ID, notification) }.isSuccess) {
            lastRenderKey = renderKey
        }
    }


    fun shouldRefreshProgress(state: TunnelRuntimeState): Boolean {
        val session = state.activeSession ?: return false
        return session.status == TunnelStatus.ACTIVE &&
            session.expiresAtMillis != null &&
            preferences.getString(KEY_DISMISSED_SESSION, null) != session.id &&
            canPostNotifications()
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

    private fun renderKey(state: TunnelRuntimeState, nowMillis: Long): String {
        val session = requireNotNull(state.activeSession)
        val prompt = state.prompt
        return buildString {
            append(session.id).append('|')
            append(session.task).append('|')
            append(session.status).append('|')
            append(session.expiresAtMillis).append('|')
            append(state.overrideScope?.takeIf { it.sessionId == session.id }?.surface).append('|')
            append(progressBucket(state, nowMillis)).append('|')
            when (prompt) {
                is TunnelPrompt.Intervention -> append("intervention:").append(prompt.surface)
                is TunnelPrompt.IntentionCheckIn -> append("checkin:").append(prompt.kind).append(':').append(prompt.surface)
                is TunnelPrompt.SessionExpired -> append("expired")
                is TunnelPrompt.PurposeGate -> append("purpose")
                null -> append("none")
            }
        }
    }

    private fun progressBucket(state: TunnelRuntimeState, nowMillis: Long): Int {
        val session = state.activeSession ?: return -1
        val total = session.intendedDurationMillis ?: return -1
        if (total <= 0L || session.status == TunnelStatus.EXPIRED) return 0
        val remaining = (session.expiresAtMillis.orZero() - nowMillis).coerceIn(0L, total)
        return (remaining / PROGRESS_REFRESH_MILLIS).toInt()
    }

    private fun buildNotification(state: TunnelRuntimeState, nowMillis: Long): Notification {
        val session = requireNotNull(state.activeSession)
        val expired = session.status == TunnelStatus.EXPIRED || state.prompt is TunnelPrompt.SessionExpired
        val detourSurface = state.overrideScope?.takeIf { it.sessionId == session.id }?.surface
        val intervention = (state.prompt as? TunnelPrompt.Intervention)?.takeIf { it.sessionId == session.id }
        val checkIn = (state.prompt as? TunnelPrompt.IntentionCheckIn)?.takeIf { it.sessionId == session.id }

        val purpose = notificationTaskLabel(session.task)
        val contextLine = when {
            expired -> "Time complete"
            detourSurface != null -> "${surfaceLabel(detourSurface)} allowed temporarily"
            intervention != null -> "${surfaceLabel(intervention.surface)} is outside this tunnel"
            checkIn?.kind == IntentionCheckInKind.DETOUR_RENEWAL -> "Temporary detour check-in"
            checkIn?.kind == IntentionCheckInKind.OPEN_ENDED_BROWSE -> "Intentional check-in"
            else -> "Tunnel active"
        }
        val collapsedStatus = when {
            expired -> "Time complete · Choose what to do next"
            detourSurface != null -> "${surfaceLabel(detourSurface)} allowed temporarily"
            intervention != null -> "${surfaceLabel(intervention.surface)} is outside this tunnel"
            checkIn?.kind == IntentionCheckInKind.DETOUR_RENEWAL -> "Temporary detour check-in"
            checkIn?.kind == IntentionCheckInKind.OPEN_ENDED_BROWSE -> "Intentional check-in"
            session.expiresAtMillis == null -> "No time limit"
            else -> "Tunnel active"
        }

        val iconSizePx = (APP_ICON_DP * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
        val largeIcon = runCatching {
            context.packageManager.getApplicationIcon(session.app.packageName).toBitmap(iconSizePx, iconSizePx)
        }.getOrNull()

        val expanded = buildExpandedView(
            state = state,
            nowMillis = nowMillis,
            contextLine = contextLine,
            largeIcon = largeIcon,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_task_tunnel)
            .setContentTitle("${session.app.displayName} · $purpose")
            .setContentText(collapsedStatus)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(expanded)
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

        if (!expired && session.expiresAtMillis != null) {
            builder
                .setWhen(session.expiresAtMillis!!)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
        } else {
            builder.setShowWhen(false)
        }

        return builder.build()
    }

    private fun buildExpandedView(
        state: TunnelRuntimeState,
        nowMillis: Long,
        contextLine: String,
        largeIcon: android.graphics.Bitmap?,
    ): RemoteViews {
        val session = requireNotNull(state.activeSession)
        val checkIn = (state.prompt as? TunnelPrompt.IntentionCheckIn)?.takeIf { it.sessionId == session.id }
        val expired = session.status == TunnelStatus.EXPIRED || state.prompt is TunnelPrompt.SessionExpired
        val showContinue = expired || checkIn?.kind == IntentionCheckInKind.OPEN_ENDED_BROWSE

        return RemoteViews(context.packageName, R.layout.notification_tunnel_expanded).apply {
            setTextViewText(R.id.notification_purpose, notificationTaskLabel(session.task))
            setTextViewText(R.id.notification_context, "${session.app.displayName} · $contextLine")

            if (largeIcon != null) {
                setImageViewBitmap(R.id.notification_target_icon, largeIcon)
                setViewVisibility(R.id.notification_target_icon, View.VISIBLE)
            } else {
                setViewVisibility(R.id.notification_target_icon, View.GONE)
            }

            val total = session.intendedDurationMillis
            val expiresAt = session.expiresAtMillis
            if (!expired && total != null && total > 0L && expiresAt != null) {
                val remaining = (expiresAt - nowMillis).coerceIn(0L, total)
                val chronometerBase = SystemClock.elapsedRealtime() + remaining
                setViewVisibility(R.id.notification_timer, View.VISIBLE)
                setViewVisibility(R.id.notification_status, View.GONE)
                setViewVisibility(R.id.notification_progress, View.VISIBLE)
                setChronometer(R.id.notification_timer, chronometerBase, "Tunnel %s", true)
                setChronometerCountDown(R.id.notification_timer, true)
                val progress = ((remaining.toDouble() / total.toDouble()) * PROGRESS_MAX).roundToInt()
                    .coerceIn(0, PROGRESS_MAX)
                setProgressBar(R.id.notification_progress, PROGRESS_MAX, progress, false)
            } else {
                setViewVisibility(R.id.notification_timer, View.GONE)
                setViewVisibility(R.id.notification_progress, View.GONE)
                setViewVisibility(R.id.notification_status, View.VISIBLE)
                setTextViewText(
                    R.id.notification_status,
                    if (expired) "Time complete" else "No time limit",
                )
            }

            setViewVisibility(R.id.notification_continue, if (showContinue) View.VISIBLE else View.GONE)
            if (showContinue) {
                setOnClickPendingIntent(
                    R.id.notification_continue,
                    actionPendingIntent(ACTION_CONTINUE, session.id, REQUEST_CONTINUE),
                )
            }
            setOnClickPendingIntent(
                R.id.notification_change_purpose,
                actionPendingIntent(ACTION_CHANGE_PURPOSE, session.id, REQUEST_CHANGE_PURPOSE),
            )
            setOnClickPendingIntent(
                R.id.notification_end,
                actionPendingIntent(ACTION_END, session.id, REQUEST_END),
            )
        }
    }

    private fun notificationTaskLabel(task: TunnelTask): String = when (task) {
        TunnelTask.INSTAGRAM_MESSAGES -> "Reply to messages"
        TunnelTask.INSTAGRAM_SEARCH -> "Search Instagram"
        TunnelTask.INSTAGRAM_POST -> "Post something"
        TunnelTask.INSTAGRAM_BROWSE -> "Browse intentionally"
        TunnelTask.YOUTUBE_SEARCH_WATCH -> "Search / watch specific"
        TunnelTask.YOUTUBE_SUBSCRIPTIONS -> "Check subscriptions"
        TunnelTask.YOUTUBE_SHORTS -> "Watch Shorts"
        TunnelTask.YOUTUBE_BROWSE -> "Browse intentionally"
        TunnelTask.TIKTOK_SEARCH_WATCH -> "Search / watch specific"
        TunnelTask.TIKTOK_INBOX -> "Check messages"
        TunnelTask.TIKTOK_BROWSE -> "Browse intentionally"
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

    private fun Long?.orZero(): Long = this ?: 0L

    companion object {
        const val CHANNEL_ID = "active_tunnel_controls"
        const val ACTION_CHANGE_PURPOSE = "com.example.tasktunnel.notification.CHANGE_PURPOSE"
        const val ACTION_CONTINUE = "com.example.tasktunnel.notification.CONTINUE"
        const val ACTION_END = "com.example.tasktunnel.notification.END"
        const val ACTION_DISMISSED = "com.example.tasktunnel.notification.DISMISSED"
        const val EXTRA_SESSION_ID = "session_id"
        const val PROGRESS_REFRESH_MILLIS = 30_000L
        private const val PROGRESS_MAX = 1000
        private const val APP_ICON_DP = 48
        private const val NOTIFICATION_ID = 4107
        private const val PREFERENCES = "tunnel_notification"
        private const val KEY_DISMISSED_SESSION = "dismissed_session"
        private const val REQUEST_CHANGE_PURPOSE = 4103
        private const val REQUEST_CONTINUE = 4104
        private const val REQUEST_END = 4105
        private const val REQUEST_DISMISSED = 4106
    }
}
