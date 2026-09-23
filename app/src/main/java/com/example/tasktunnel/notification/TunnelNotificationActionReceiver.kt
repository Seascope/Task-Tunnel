package com.example.tasktunnel.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.tasktunnel.accessibility.TaskTunnelAccessibilityService

class TunnelNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(TunnelNotificationController.EXTRA_SESSION_ID) ?: return
        val service = TaskTunnelAccessibilityService.current ?: run {
            // If the process was recreated but the AccessibilityService has not restored a live
            // coordinator, this notification is stale. Do not leave dead controls in the shade.
            TunnelNotificationController(context.applicationContext).cancel()
            return
        }
        when (intent.action) {
            TunnelNotificationController.ACTION_CHANGE_PURPOSE -> service.onNotificationChangePurpose(sessionId)
            TunnelNotificationController.ACTION_CONTINUE -> service.onNotificationContinue(sessionId)
            TunnelNotificationController.ACTION_END -> service.onNotificationEnd(sessionId)
            TunnelNotificationController.ACTION_DISMISSED -> service.onNotificationDismissed(sessionId)
        }
    }
}
