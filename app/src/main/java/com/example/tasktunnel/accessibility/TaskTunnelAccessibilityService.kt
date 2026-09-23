package com.example.tasktunnel.accessibility

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.res.ColorStateList
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.PowerManager
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.tasktunnel.BuildConfig
import com.example.tasktunnel.R
import com.example.tasktunnel.attention.AttentionDecision
import com.example.tasktunnel.attention.AttentionHistory
import com.example.tasktunnel.attention.AttentionSubtype
import com.example.tasktunnel.attention.AttentionDatabase
import com.example.tasktunnel.attention.taskLabel
import com.example.tasktunnel.detector.InstagramSurfaceDetector
import com.example.tasktunnel.detector.InstagramSurface
import com.example.tasktunnel.detector.YouTubeSurfaceDetector
import com.example.tasktunnel.detector.YouTubeSurface
import com.example.tasktunnel.detector.TikTokSurfaceDetector
import com.example.tasktunnel.detector.TikTokSurface
import com.example.tasktunnel.diagnostics.SanitizedFingerprint
import com.example.tasktunnel.diagnostics.TikTokFingerprint
import com.example.tasktunnel.diagnostics.YouTubeFingerprint
import com.example.tasktunnel.drift.DriftAppCatalog
import com.example.tasktunnel.drift.DriftCoordinator
import com.example.tasktunnel.drift.DriftEpisode
import com.example.tasktunnel.drift.DriftPoolPreferences
import com.example.tasktunnel.friction.AdaptiveFrictionEngine
import com.example.tasktunnel.friction.AdaptiveFrictionStore
import com.example.tasktunnel.friction.FrictionContext
import com.example.tasktunnel.friction.FrictionOutcome
import com.example.tasktunnel.friction.InterventionVariant
import com.example.tasktunnel.notification.TunnelNotificationController
import com.example.tasktunnel.protection.ProtectionMasterPreferences
import com.example.tasktunnel.tunnel.DetectedSurface
import com.example.tasktunnel.tunnel.IntentionCheckInKind
import com.example.tasktunnel.tunnel.IntentionalCheckInPreferences
import com.example.tasktunnel.tunnel.SupportedApp
import com.example.tasktunnel.tunnel.TunnelCoordinator
import com.example.tasktunnel.tunnel.TunnelPrompt
import com.example.tasktunnel.tunnel.TunnelTask
import com.example.tasktunnel.tunnel.TunnelStatus
import com.example.tasktunnel.usage.SurfaceUsageRepository
import com.example.tasktunnel.usage.SurfaceUsageTracker
import java.util.ArrayDeque

class TaskTunnelAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val tunnelCoordinator = TunnelCoordinator()
    private val driftCoordinator = DriftCoordinator(DriftAppCatalog.knownPackages)
    private val adaptiveFrictionEngine = AdaptiveFrictionEngine()
    private val adaptiveFrictionStore by lazy { AdaptiveFrictionStore(applicationContext) }
    private val attentionRecorder by lazy { AttentionHistory.recorder(applicationContext) }
    private val tunnelNotificationController by lazy { TunnelNotificationController(applicationContext) }
    private var protectionEnabled = true
    private val surfaceUsageTracker by lazy {
        SurfaceUsageTracker(
            SurfaceUsageRepository(AttentionDatabase.getInstance(applicationContext).surfaceUsageSegmentDao()),
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO.limitedParallelism(1)),
        )
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            if (!protectionEnabled) return
            surfaceUsageTracker.setInteractive(intent.action == Intent.ACTION_SCREEN_ON, System.currentTimeMillis())
        }
    }
    private var screenReceiverRegistered = false
    private var testOverlayView: LinearLayout? = null
    private var tunnelOverlayView: LinearLayout? = null
    private var exitingTunnelOverlayView: LinearLayout? = null
    private var tunnelOverlayDimAnimator: ValueAnimator? = null
    private var driftOverlayView: LinearLayout? = null
    private var visualQaOverlayView: LinearLayout? = null
    private var shownTunnelPrompt: TunnelPrompt? = null
    private var instagramDirectedNavigationInProgress = false
    private var youTubeDirectedNavigationInProgress = false
    private var tikTokDirectedNavigationInProgress = false
    private var directedNavigationGeneration = 0L
    private var directedNavigationOwnerSessionId: String? = null
    private var directedNavigationOwnerPackage: String? = null
    /**
     * YouTube keeps the previously selected bottom tab highlighted while Search is layered on top.
     * This bounded, content-free context lets the detector distinguish Search results from the
     * originating tab without retaining the user's query. It clears when a top-level YouTube tab
     * is actually selected again.
     */
    private var youTubeSearchContextActive = false
    /**
     * A YouTube click can briefly leave the previous watch-page accessibility nodes alive while
     * the next video is loading. When a subscriptions tunnel is active, take two bounded settled
     * re-captures so creator subscription state is read from the new video, not the old one.
     */
    private var youTubeSubscriptionProbeAttemptsRemaining = 0
    private var youTubeSubscriptionProbePending = false
    private var lastCaptureAtElapsed = 0L
    private var lastTargetPackage: String? = null
    private val showOverlay = Runnable { if (protectionEnabled) showTestOverlayNow() }
    private val trailingCapture = Runnable { if (protectionEnabled) captureCurrentRoot() }
    private val youTubeSubscriptionProbe = Runnable {
        youTubeSubscriptionProbePending = false
        if (!protectionEnabled) return@Runnable
        val session = tunnelCoordinator.state.activeSession
        if (session?.task == TunnelTask.YOUTUBE_SUBSCRIPTIONS &&
            session.app.packageName == YouTubeSurfaceDetector.YOUTUBE_PACKAGE &&
            activeRootPackage() == YouTubeSurfaceDetector.YOUTUBE_PACKAGE
        ) {
            captureCurrentRoot()
        }
    }
    private val tunnelDeadline = object : Runnable {
        override fun run() {
            if (!protectionEnabled) return
            val nowMillis = System.currentTimeMillis()
            val packageName = activeRootPackage()
            if (packageName == null) {
                handler.postDelayed(this, DEADLINE_RETRY_MS)
            } else if (isTransientSystemUi(packageName)) {
                // The unlocked notification/quick-settings shade is a transient shell, not an
                // app switch. Keep timers/check-ins moving without making SystemUI the tunnel's
                // foreground package or trying to render a prompt over the shade.
                tunnelCoordinator.advanceTime(nowMillis)
                syncTunnelState()
                tunnelNotificationController.sync(tunnelCoordinator.state)
                scheduleNotificationProgressRefresh()
                scheduleTunnelDeadline()
            } else {
                tunnelCoordinator.observeForeground(packageName, nowMillis)
                updateTunnelUi()
            }
        }
    }
    private val notificationProgressRefresh = object : Runnable {
        override fun run() {
            if (!protectionEnabled) return
            tunnelNotificationController.sync(tunnelCoordinator.state)
            scheduleNotificationProgressRefresh()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = this
        protectionEnabled = ProtectionMasterPreferences.load(this)
        tunnelNotificationController.ensureChannel()
        // Reconcile any notification left behind by an abrupt prior process/service death. The
        // coordinator is the source of truth; a fresh coordinator means there is no live tunnel.
        tunnelNotificationController.sync(tunnelCoordinator.state)
        driftCoordinator.updateSelectedPackages(DriftPoolPreferences.load(this))
        tunnelCoordinator.setCheckInsEnabled(IntentionalCheckInPreferences.load(applicationContext))
        if (!protectionEnabled) {
            tunnelCoordinator.reset()
            driftCoordinator.clear()
            tunnelNotificationController.cancel()
        }
        val connectedAtMillis = System.currentTimeMillis()
        syncTunnelState { it.copy(connected = true, lastHeartbeatMillis = connectedAtMillis) }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) }
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(screenReceiver, filter)
        screenReceiverRegistered = true
        if (protectionEnabled) {
            resumeProtectionFromCurrentForeground(connectedAtMillis)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val isWindowEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        val isInspectionEvent = isWindowEvent ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        if (!isInspectionEvent) return
        if (!protectionEnabled) return
        AccessibilityRuntime.heartbeat()
        val state = AccessibilityRuntime.state.value
        val packageName = activeRootPackage() ?: run {
            AccessibilityRuntime.clearCurrentYouTubeDetection()
            AccessibilityRuntime.clearCurrentInstagramDetection()
            AccessibilityRuntime.clearCurrentTikTokCapture()
            failOpenCurrentSurface()
            return
        }
        if (isTransientSystemUi(packageName)) {
            // Opening the notification shade must not count as leaving the protected app. It used
            // to clear check-ins, start the quick-return timer, contaminate Drift, and could even
            // destroy an expired session before its notification action was pressed.
            val nowMillis = System.currentTimeMillis()
            surfaceUsageTracker.foregroundChanged(null, nowMillis)
            tunnelCoordinator.advanceTime(nowMillis)
            syncTunnelState()
            tunnelNotificationController.sync(tunnelCoordinator.state)
            scheduleNotificationProgressRefresh()
            scheduleTunnelDeadline()
            return
        }
        if (packageName != YouTubeSurfaceDetector.YOUTUBE_PACKAGE) {
            AccessibilityRuntime.clearCurrentYouTubeDetection()
            youTubeSearchContextActive = false
            clearYouTubeSubscriptionProbe()
        }
        if (packageName != InstagramSurfaceDetector.INSTAGRAM_PACKAGE) AccessibilityRuntime.clearCurrentInstagramDetection()
        if (packageName != TikTokSurfaceDetector.TIKTOK_PACKAGE) AccessibilityRuntime.clearCurrentTikTokCapture()
        val nowMillis = System.currentTimeMillis()
        if (directedNavigationOwnerPackage != null && directedNavigationOwnerPackage != packageName) {
            invalidateDirectedNavigation()
        }
        surfaceUsageTracker.foregroundChanged(packageName, nowMillis)
        tunnelCoordinator.observeForeground(packageName, nowMillis)
        driftCoordinator.observeForeground(
            packageName = packageName,
            nowMillis = nowMillis,
        )
        updateTunnelUi()
        val activeSession = tunnelCoordinator.state.activeSession
        if (packageName == YouTubeSurfaceDetector.YOUTUBE_PACKAGE &&
            activeSession?.task == TunnelTask.YOUTUBE_SUBSCRIPTIONS &&
            activeSession.app.packageName == packageName &&
            (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED)
        ) {
            // A new watch surface can retain stale accessibility nodes for a short moment.
            // Start the settled probe immediately from the navigation event itself. Previously
            // we waited for the first capture to already classify as Video/Shorts; a transitional
            // UNKNOWN/tab capture could therefore prevent the verification pass entirely.
            youTubeSubscriptionProbeAttemptsRemaining = YOUTUBE_SUBSCRIPTION_PROBE_ATTEMPTS
            scheduleYouTubeSubscriptionProbeIfNeeded()
        }
        if (!isWindowEvent) {
            if (
                packageName == YouTubeSurfaceDetector.YOUTUBE_PACKAGE &&
                event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
                eventTargetsYouTubeTopLevelNavigation(event)
            ) {
                youTubeSearchContextActive = false
            }
            if (shouldCapture(packageName, state.inspectionArmed)) {
                val settleDelay = if (
                    event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
                    packageName == TikTokSurfaceDetector.TIKTOK_PACKAGE
                ) TIKTOK_CLICK_SETTLE_MS else 0L
                scheduleCapture(settleDelay)
            }
            return
        }
        val previousTargetPackage = lastTargetPackage
        val targetChanged = packageName in TARGET_PACKAGES && packageName != previousTargetPackage
        val leftTarget = packageName !in TARGET_PACKAGES && previousTargetPackage != null
        lastTargetPackage = packageName.takeIf { it in TARGET_PACKAGES }

        AccessibilityRuntime.update {
            val history = if (it.foregroundPackage != packageName) {
                (listOf(PackageTransition(packageName, System.currentTimeMillis())) + it.packageHistory).take(MAX_HISTORY)
            } else it.packageHistory
            val inspectionStatus = when {
                !it.inspectionArmed -> it.inspectionStatus
                packageName !in TARGET_PACKAGES -> InspectionStatus.UNSUPPORTED_APP
                targetChanged -> InspectionStatus.ARMED
                else -> it.inspectionStatus
            }
            val inspectionDetail = when {
                !it.inspectionArmed -> it.inspectionDetail
                packageName !in TARGET_PACKAGES -> "Inspection supports Instagram, YouTube, and TikTok."
                targetChanged -> "Target changed; waiting for a fresh tree."
                else -> it.inspectionDetail
            }
            it.copy(
                foregroundPackage = packageName,
                lastRelevantEvent = eventTypeName(event.eventType),
                packageHistory = history,
                snapshot = if (targetChanged || leftTarget) null else it.snapshot,
                inspectionStatus = inspectionStatus,
                inspectionDetail = inspectionDetail,
            )
        }

        if (packageName !in TARGET_PACKAGES) handler.removeCallbacks(trailingCapture)
        if (shouldCapture(packageName, state.inspectionArmed)) scheduleCapture()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        tearDownRuntime()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        tearDownRuntime()
        super.onDestroy()
    }

    private fun tearDownRuntime() {
        youTubeSearchContextActive = false
        clearYouTubeSubscriptionProbe()
        handler.removeCallbacksAndMessages(null)
        removeTestOverlay()
        removeTunnelOverlay()
        removeDriftOverlay()
        removeVisualQaOverlay()
        surfaceUsageTracker.close(System.currentTimeMillis())
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
        driftCoordinator.clear()
        tunnelCoordinator.reset()
        invalidateDirectedNavigation()
        lastTargetPackage = null
        lastCaptureAtElapsed = 0L
        tunnelNotificationController.cancel()
        if (current === this) current = null
        AccessibilityRuntime.update {
            it.copy(
                connected = false,
                inspectionArmed = false,
                inspectionStatus = InspectionStatus.IDLE,
                inspectionDetail = null,
                snapshot = null,
                overlayPending = false,
                overlayVisible = false,
                currentYouTubeDetection = null,
                currentInstagramDetection = null,
                currentTikTokCapture = null,
                lastTikTokCapture = null,
                tunnelState = com.example.tasktunnel.tunnel.TunnelRuntimeState(),
            )
        }
    }

    fun onNotificationChangePurpose(sessionId: String) {
        if (!protectionEnabled) return
        if (tunnelCoordinator.state.activeSession?.id != sessionId) return
        dismissNotificationShadeThen(sessionId, requireProtectedApp = false) {
            val nowMillis = System.currentTimeMillis()
            tunnelCoordinator.advanceTime(nowMillis)
            val state = tunnelCoordinator.state
            val session = state.activeSession?.takeIf { it.id == sessionId }
            if (session == null) {
                // The quick-return grace or timer may have elapsed while the shade was closing.
                // Reconcile the notification immediately instead of leaving a dead card behind.
                updateTunnelUi()
                return@dismissNotificationShadeThen
            }
            val changed = when (val prompt = state.prompt) {
                is TunnelPrompt.SessionExpired -> {
                    if (prompt.sessionId != sessionId) false
                    else {
                        attentionRecorder.tunnelDecision(
                            session,
                            AttentionSubtype.EXPIRY_CHOOSE_ANOTHER,
                            AttentionDecision.CHOOSE_ANOTHER_PURPOSE,
                            nowMillis,
                        )
                        tunnelCoordinator.chooseAnotherPurpose()
                        true
                    }
                }
                is TunnelPrompt.IntentionCheckIn -> {
                    if (prompt.sessionId != sessionId) false
                    else {
                        attentionRecorder.tunnelDecision(
                            session,
                            AttentionSubtype.CHECK_IN_CHOOSE_ANOTHER,
                            AttentionDecision.CHECK_IN_CHOOSE_ANOTHER,
                            nowMillis,
                            prompt.surface,
                        )
                        // Match the overlay action exactly: a check-in purpose change resolves the
                        // old session rather than creating a replacement gate that can retain the
                        // stale check-in underneath it.
                        tunnelCoordinator.chooseAnotherPurpose()
                        true
                    }
                }
                else -> tunnelCoordinator.requestPurposeChange(sessionId, nowMillis)
            }
            if (changed) updateTunnelUi()
        }
    }

    fun onNotificationContinue(sessionId: String) {
        if (!protectionEnabled) return
        if (tunnelCoordinator.state.activeSession?.id != sessionId) return
        dismissNotificationShadeThen(sessionId, requireProtectedApp = false) {
            val nowMillis = System.currentTimeMillis()
            tunnelCoordinator.advanceTime(nowMillis)
            val state = tunnelCoordinator.state
            val session = state.activeSession?.takeIf { it.id == sessionId }
                ?: return@dismissNotificationShadeThen
            when (val prompt = state.prompt) {
                is TunnelPrompt.SessionExpired -> {
                    if (prompt.sessionId != sessionId) return@dismissNotificationShadeThen
                    attentionRecorder.tunnelDecision(
                        session,
                        AttentionSubtype.EXPIRY_CONTINUE,
                        AttentionDecision.CONTINUE,
                        nowMillis,
                    )
                    tunnelCoordinator.continueExpiredSession(nowMillis)
                }
                is TunnelPrompt.IntentionCheckIn -> {
                    if (prompt.sessionId != sessionId) return@dismissNotificationShadeThen
                    attentionRecorder.tunnelDecision(
                        session,
                        AttentionSubtype.CHECK_IN_CONTINUE,
                        AttentionDecision.CHECK_IN_CONTINUE,
                        nowMillis,
                        prompt.surface,
                    )
                    tunnelCoordinator.continueCheckIn(nowMillis)
                }
                else -> return@dismissNotificationShadeThen
            }
            updateTunnelUi()
            if (activeRootPackage() == session.app.packageName) {
                scheduleCapture(NOTIFICATION_ACTION_SETTLE_MS)
            }
        }
    }

    fun onNotificationEnd(sessionId: String) {
        if (!protectionEnabled) return
        val state = tunnelCoordinator.state
        val session = state.activeSession?.takeIf { it.id == sessionId } ?: return
        val nowMillis = System.currentTimeMillis()
        when (val prompt = state.prompt) {
            is TunnelPrompt.SessionExpired -> if (prompt.sessionId == sessionId) {
                attentionRecorder.tunnelDecision(
                    session,
                    AttentionSubtype.EXPIRY_FINISH,
                    AttentionDecision.FINISH,
                    nowMillis,
                )
            }
            is TunnelPrompt.IntentionCheckIn -> if (prompt.sessionId == sessionId) {
                attentionRecorder.tunnelDecision(
                    session,
                    AttentionSubtype.CHECK_IN_END,
                    AttentionDecision.CHECK_IN_END,
                    nowMillis,
                    prompt.surface,
                )
            }
            is TunnelPrompt.Intervention -> if (prompt.sessionId == sessionId) {
                attentionRecorder.tunnelDecision(
                    session,
                    AttentionSubtype.END_TUNNEL,
                    AttentionDecision.END_TUNNEL,
                    nowMillis,
                    prompt.surface,
                )
            }
            else -> attentionRecorder.tunnelDecision(
                session,
                AttentionSubtype.END_TUNNEL,
                AttentionDecision.END_TUNNEL,
                nowMillis,
                state.currentSurface,
            )
        }
        tunnelCoordinator.endSession()
        updateTunnelUi()
    }

    fun onNotificationDismissed(sessionId: String) {
        if (!protectionEnabled) return
        if (tunnelCoordinator.state.activeSession?.id != sessionId) return
        tunnelNotificationController.suppressForSession(sessionId)
    }

    /**
     * Notification buttons arrive while SystemUI still owns the active accessibility window.
     * Always dismiss the shade before touching overlay UI. Actions that inspect or manipulate the
     * protected app can additionally wait for that app; the Purpose picker must not, because it is
     * valid to change the tunnel while the user is on Home or in another app.
     */
    private fun dismissNotificationShadeThen(
        sessionId: String,
        requireProtectedApp: Boolean,
        attemptsRemaining: Int = NOTIFICATION_ACTION_MAX_ATTEMPTS,
        action: () -> Unit,
    ) {
        val session = tunnelCoordinator.state.activeSession?.takeIf { it.id == sessionId } ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val dismissed = performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            if (!dismissed && isTransientSystemUi(activeRootPackage())) {
                // GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE is a system action and can be absent
                // on some implementations. Back is a safe fallback only while the unlocked
                // SystemUI shade actually owns the active window.
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } else {
            @Suppress("DEPRECATION")
            sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        }
        fun runWhenReady(remaining: Int) {
            if (!protectionEnabled) return
            if (tunnelCoordinator.state.activeSession?.id != sessionId) return
            val foregroundPackage = activeRootPackage()
            val systemUiGone = foregroundPackage != null && foregroundPackage != SYSTEM_UI_PACKAGE
            if (systemUiGone && (!requireProtectedApp || foregroundPackage == session.app.packageName)) {
                action()
                return
            }
            if (remaining <= 0) return
            handler.postDelayed({ runWhenReady(remaining - 1) }, NOTIFICATION_ACTION_RETRY_MS)
        }
        handler.postDelayed(
            { runWhenReady(attemptsRemaining) },
            if (requireProtectedApp) NOTIFICATION_ACTION_RETRY_MS else NOTIFICATION_ACTION_SETTLE_MS,
        )
    }

    fun onInspectionArmed(armed: Boolean) {
        if (!protectionEnabled) {
            handler.removeCallbacks(trailingCapture)
            return
        }
        if (armed && BuildConfig.DEBUG) {
            val packageName = activeRootPackage()
            if (packageName in TARGET_PACKAGES) scheduleCapture() else AccessibilityRuntime.update {
                it.copy(inspectionStatus = InspectionStatus.UNSUPPORTED_APP, inspectionDetail = "Inspection supports Instagram, YouTube, and TikTok.")
            }
        } else {
            val packageName = activeRootPackage()
            if (!shouldCapture(packageName, inspectionArmed = false)) handler.removeCallbacks(trailingCapture)
        }
    }

    fun scheduleTestOverlay(): Boolean {
        if (!protectionEnabled || !BuildConfig.DEBUG) return false
        if (tunnelCoordinator.state.prompt != null || driftOverlayView != null) return false
        handler.removeCallbacks(showOverlay)
        removeTestOverlay()
        AccessibilityRuntime.update { it.copy(overlayPending = true, overlayVisible = false) }
        handler.postDelayed(showOverlay, OVERLAY_DELAY_MS)
        return true
    }

    fun showVisualQaOverlay(overlay: VisualQaOverlay): Boolean {
        if (!protectionEnabled || !BuildConfig.DEBUG) return false
        removeVisualQaOverlay()
        val now = System.currentTimeMillis()
        val layout = when (overlay) {
            VisualQaOverlay.PURPOSE_GATE -> purposeGateView(TunnelPrompt.PurposeGate(SupportedApp.INSTAGRAM))
            VisualQaOverlay.INTERVENTION -> interventionView(
                prompt = TunnelPrompt.Intervention(
                    "visual-qa",
                    TunnelTask.INSTAGRAM_MESSAGES,
                    DetectedSurface.INSTAGRAM_REELS,
                ),
                variant = InterventionVariant.DIRECT,
                learningEnabled = false,
            )
            VisualQaOverlay.DRIFT_CHECK_IN -> {
                val packages = listOf("com.instagram.android", "com.reddit.frontpage", "com.google.android.youtube")
                driftCheckInView(
                    DriftEpisode("visual-qa", now - 45_000L, packages, now),
                    packages.map { packageName -> DriftAppCatalog.labelFor(this, packageName) },
                )
            }
            VisualQaOverlay.SESSION_EXPIRY -> sessionExpiredView(
                TunnelPrompt.SessionExpired("visual-qa", SupportedApp.INSTAGRAM, TunnelTask.INSTAGRAM_MESSAGES),
            )
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            dimAmount = if (overlay == VisualQaOverlay.DRIFT_CHECK_IN) 0.32f else 0.42f
        }
        return try {
            getSystemService(WindowManager::class.java).addView(layout, params)
            visualQaOverlayView = layout
            if (overlay == VisualQaOverlay.PURPOSE_GATE) {
                layout.alpha = 0f
                layout.translationY = dp(28).toFloat()
                layout.animate().alpha(1f).translationY(0f).setDuration(150L).start()
            } else {
                animateOverlayEntrance(layout)
            }
            handler.postDelayed({ if (visualQaOverlayView === layout) removeVisualQaOverlay() }, VISUAL_QA_TIMEOUT_MS)
            true
        } catch (_: RuntimeException) {
            visualQaOverlayView = null
            false
        }
    }

    fun onProtectionEnabledChanged(enabled: Boolean) {
        if (protectionEnabled == enabled) return
        protectionEnabled = enabled
        val nowMillis = System.currentTimeMillis()
        if (!enabled) {
            tunnelCoordinator.state.activeSession?.let { session ->
                attentionRecorder.tunnelDecision(
                    session,
                    AttentionSubtype.END_TUNNEL,
                    AttentionDecision.END_TUNNEL,
                    nowMillis,
                    tunnelCoordinator.state.currentSurface,
                )
            }

            // OFF is a hard quiescent boundary. Remove every delayed service callback, including
            // notification-shade retries and multi-step directed navigation, so no stale work can
            // wake the feature back up after the user has paused it.
            handler.removeCallbacksAndMessages(null)
            clearYouTubeSubscriptionProbe()
            removeTestOverlay()
            removeTunnelOverlay()
            removeDriftOverlay()
            removeVisualQaOverlay()
            surfaceUsageTracker.close(nowMillis)
            tunnelCoordinator.reset()
            driftCoordinator.clear()
            tunnelNotificationController.cancel()
            youTubeSearchContextActive = false
            invalidateDirectedNavigation()
            lastTargetPackage = null
            lastCaptureAtElapsed = 0L
            syncTunnelState {
                it.copy(
                    foregroundPackage = null,
                    lastRelevantEvent = null,
                    currentYouTubeDetection = null,
                    currentInstagramDetection = null,
                    currentTikTokCapture = null,
                    overlayPending = false,
                    overlayVisible = false,
                )
            }
            return
        }

        resumeProtectionFromCurrentForeground(nowMillis)
    }

    private fun resumeProtectionFromCurrentForeground(nowMillis: Long) {
        if (!protectionEnabled) return

        driftCoordinator.updateSelectedPackages(DriftPoolPreferences.load(this))
        tunnelCoordinator.setCheckInsEnabled(IntentionalCheckInPreferences.load(applicationContext))
        surfaceUsageTracker.setInteractive(
            getSystemService(PowerManager::class.java).isInteractive,
            nowMillis,
        )
        AccessibilityRuntime.heartbeat(nowMillis)

        val packageName = activeRootPackage()
        if (packageName == null || isTransientSystemUi(packageName)) {
            syncTunnelState { it.copy(lastHeartbeatMillis = nowMillis) }
            return
        }

        surfaceUsageTracker.foregroundChanged(packageName, nowMillis)
        tunnelCoordinator.observeForeground(packageName, nowMillis)
        driftCoordinator.observeForeground(packageName, nowMillis)
        lastTargetPackage = packageName.takeIf { it in TARGET_PACKAGES }
        AccessibilityRuntime.update {
            val history = if (it.foregroundPackage != packageName) {
                (listOf(PackageTransition(packageName, nowMillis)) + it.packageHistory).take(MAX_HISTORY)
            } else {
                it.packageHistory
            }
            it.copy(
                foregroundPackage = packageName,
                lastRelevantEvent = "Protection resumed",
                packageHistory = history,
                currentYouTubeDetection = null,
                currentInstagramDetection = null,
                currentTikTokCapture = null,
            )
        }
        updateTunnelUi()
        if (shouldCapture(packageName, AccessibilityRuntime.state.value.inspectionArmed)) {
            scheduleCapture()
        }
    }

    fun onDriftPoolChanged(packages: Set<String>) {
        if (!protectionEnabled) return
        driftCoordinator.updateSelectedPackages(packages)
        removeDriftOverlay()
        updateTunnelUi()
    }

    fun onIntentionalCheckInsEnabledChanged(enabled: Boolean) {
        if (!protectionEnabled) return
        tunnelCoordinator.setCheckInsEnabled(enabled)
        updateTunnelUi()
    }

    fun onAttentionHistoryCleared() {
        // History clear is a generation boundary for both writers. Queued pre-clear writes are
        // discarded so old events/usage cannot reappear after Room has been cleared.
        attentionRecorder.onHistoryCleared()
        surfaceUsageTracker.discardActive()
    }

    private fun scheduleCapture(minDelayMillis: Long = 0L) {
        if (!protectionEnabled) return
        val elapsed = SystemClock.elapsedRealtime()
        val throttleDelay = CAPTURE_THROTTLE_MS - (elapsed - lastCaptureAtElapsed)
        val delay = maxOf(throttleDelay, minDelayMillis)
        handler.removeCallbacks(trailingCapture)
        if (delay <= 0) captureCurrentRoot() else handler.postDelayed(trailingCapture, delay)
    }

    private fun captureCurrentRoot() {
        if (!protectionEnabled) return
        val state = AccessibilityRuntime.state.value
        lastCaptureAtElapsed = SystemClock.elapsedRealtime()
        val root = try { rootInActiveWindow } catch (_: RuntimeException) { null }
        if (root == null) {
            AccessibilityRuntime.clearCurrentYouTubeDetection()
            AccessibilityRuntime.clearCurrentInstagramDetection()
            AccessibilityRuntime.clearCurrentTikTokCapture()
            failOpenCurrentSurface()
            AccessibilityRuntime.update {
                if (BuildConfig.DEBUG && state.inspectionArmed) it.copy(
                    inspectionStatus = InspectionStatus.ROOT_UNAVAILABLE,
                    inspectionDetail = "The active accessibility root is unavailable; any shown capture is older.",
                ) else it
            }
            return
        }
        val packageName = try { root.packageName?.toString() } catch (_: RuntimeException) {
            recycleNode(root)
            AccessibilityRuntime.clearCurrentYouTubeDetection()
            AccessibilityRuntime.clearCurrentInstagramDetection()
            AccessibilityRuntime.clearCurrentTikTokCapture()
            failOpenCurrentSurface()
            AccessibilityRuntime.update {
                if (BuildConfig.DEBUG && state.inspectionArmed) it.copy(
                    inspectionStatus = InspectionStatus.ERROR,
                    inspectionDetail = "Tree inspection failed safely; any shown capture is older.",
                ) else it
            }
            return
        } ?: run {
            recycleNode(root)
            AccessibilityRuntime.clearCurrentYouTubeDetection()
            AccessibilityRuntime.clearCurrentInstagramDetection()
            AccessibilityRuntime.clearCurrentTikTokCapture()
            failOpenCurrentSurface()
            return
        }
        if (packageName !in TARGET_PACKAGES) { recycleNode(root); return }
        data class PendingNode(val node: AccessibilityNodeInfo, val depth: Int, val parentIndex: Int?)
        val stack = ArrayDeque<PendingNode>()
        try {
            val nodes = ArrayList<SanitizedNode>(MAX_NODES)
            val rootBounds = Rect().also { bounds ->
                try { root.getBoundsInScreen(bounds) } catch (_: RuntimeException) { bounds.setEmpty() }
            }
            val activeWindowHeight = rootBounds.height().takeIf { it > 0 }
            stack.addLast(PendingNode(root, 0, null))
            var truncated = false
            while (stack.isNotEmpty()) {
                val (node, depth, parentIndex) = stack.removeLast()
                if (nodes.size >= MAX_NODES) { recycleNode(node); truncated = true; break }
                try {
                    val nodeIndex = nodes.size
                    val bounds = Rect()
                    val hasBounds = try {
                        node.getBoundsInScreen(bounds)
                        !bounds.isEmpty
                    } catch (_: RuntimeException) { false }
                    val visibleTopFraction = if (hasBounds && activeWindowHeight != null) {
                        ((bounds.top - rootBounds.top).toDouble() / activeWindowHeight.toDouble()).coerceIn(0.0, 1.0)
                    } else null
                    val visibleHeightFraction = if (hasBounds && activeWindowHeight != null && bounds.height() > 0) {
                        (bounds.height().toDouble() / activeWindowHeight.toDouble()).coerceIn(0.0, 1.0)
                    } else null
                    nodes += SanitizedNode(
                        depth = depth,
                        className = node.className?.toString()?.take(MAX_FIELD_LENGTH),
                        resourceId = node.viewIdResourceName?.take(MAX_FIELD_LENGTH),
                        childCount = node.childCount,
                        clickable = node.isClickable,
                        scrollable = node.isScrollable,
                        editable = node.isEditable,
                        enabled = node.isEnabled,
                        visibleToUser = node.isVisibleToUser,
                        selected = node.isSelected || inferWhitelistedChromeSelected(packageName, node),
                        parentIndex = parentIndex,
                        chromeRole = inferWhitelistedChromeRole(packageName, node),
                        youtubeSubscriptionState = inferWhitelistedYouTubeSubscriptionState(packageName, node),
                        visibleTopFraction = visibleTopFraction,
                        visibleHeightFraction = visibleHeightFraction,
                    )
                    if (depth < MAX_DEPTH) {
                        val remainingCapacity = MAX_NODES - nodes.size - stack.size
                        val childLimit = minOf(node.childCount, remainingCapacity.coerceAtLeast(0))
                        if (childLimit < node.childCount) truncated = true
                        for (index in childLimit - 1 downTo 0) {
                            node.getChild(index)?.let { stack.addLast(PendingNode(it, depth + 1, nodeIndex)) }
                        }
                    } else if (node.childCount > 0) truncated = true
                } finally {
                    recycleNode(node)
                }
            }
            while (stack.isNotEmpty()) recycleNode(stack.removeLast().node)
            val capturedAt = System.currentTimeMillis()
            val sanitizedNodes = nodes.toList()
            val snapshot = TreeSnapshot(packageName, capturedAt, sanitizedNodes, truncated)
            val detection = if (packageName == YouTubeSurfaceDetector.YOUTUBE_PACKAGE) {
                YouTubeSurfaceDetector.detect(
                    packageName = packageName,
                    nodes = sanitizedNodes,
                    searchContextActive = youTubeSearchContextActive,
                ).also { result ->
                    when (result.surface) {
                        YouTubeSurface.YOUTUBE_SEARCH -> youTubeSearchContextActive = true
                        YouTubeSurface.YOUTUBE_HOME,
                        YouTubeSurface.YOUTUBE_SUBSCRIPTIONS,
                        YouTubeSurface.YOUTUBE_YOU,
                        -> youTubeSearchContextActive = false
                        // Keep the context through a video opened from Search so Back can return
                        // to Search results even if YouTube still highlights the old source tab.
                        else -> Unit
                    }
                }
            } else null
            val instagramDetection = if (packageName == InstagramSurfaceDetector.INSTAGRAM_PACKAGE) {
                InstagramSurfaceDetector.detect(packageName, sanitizedNodes)
            } else null
            val tikTokDetection = if (packageName == TikTokSurfaceDetector.TIKTOK_PACKAGE) {
                TikTokSurfaceDetector.detect(packageName, sanitizedNodes)
            } else null
            AccessibilityRuntime.update {
                val observedYouTube = detection?.let { result ->
                    ObservedYouTubeDetection(
                        packageName,
                        capturedAt,
                        result,
                        if (BuildConfig.DEBUG) YouTubeFingerprint.format(snapshot, result) else null,
                    )
                }
                val observedInstagram = if (BuildConfig.DEBUG && instagramDetection != null) {
                    ObservedInstagramDetection(
                        packageName,
                        capturedAt,
                        instagramDetection,
                        SanitizedFingerprint.format(
                            snapshot = snapshot,
                            appLabel = "Instagram",
                            classification = instagramDetection.surface.name,
                            confidence = "%.2f".format(java.util.Locale.ROOT, instagramDetection.confidence),
                            classificationLabel = "detector",
                        ),
                    )
                } else null
                val observedTikTok = if (BuildConfig.DEBUG && tikTokDetection != null && it.inspectionArmed) {
                    val (versionName, versionCode) = installedTikTokVersion()
                    ObservedTikTokCapture(
                        packageName = packageName,
                        capturedAtMillis = capturedAt,
                        versionName = versionName,
                        versionCode = versionCode,
                        detection = tikTokDetection,
                        fingerprint = TikTokFingerprint.format(snapshot, versionName, versionCode, tikTokDetection),
                    )
                } else null
                it.copy(
                    inspectionStatus = if (BuildConfig.DEBUG && it.inspectionArmed) InspectionStatus.CAPTURED else it.inspectionStatus,
                    inspectionDetail = if (BuildConfig.DEBUG && it.inspectionArmed) "Last captured sanitized tree." else it.inspectionDetail,
                    snapshot = if (BuildConfig.DEBUG && it.inspectionArmed) {
                        snapshot
                    } else it.snapshot,
                    currentYouTubeDetection = observedYouTube ?: it.currentYouTubeDetection,
                    lastYouTubeDetection = observedYouTube ?: it.lastYouTubeDetection,
                    currentInstagramDetection = observedInstagram ?: it.currentInstagramDetection,
                    lastInstagramDetection = observedInstagram ?: it.lastInstagramDetection,
                    currentTikTokCapture = observedTikTok ?: it.currentTikTokCapture,
                    lastTikTokCapture = observedTikTok ?: it.lastTikTokCapture,
                )
            }
            detection?.let {
                if (youTubeDirectedNavigationInProgress) return@let
                val task = tunnelCoordinator.state.activeSession
                    ?.takeIf { session -> session.app.packageName == packageName }
                    ?.task
                val surface = it.surface.toTunnelSurface()
                val policySurface = it.toPolicyTunnelSurface(task)
                if (task == TunnelTask.YOUTUBE_SUBSCRIPTIONS &&
                    (it.surface == YouTubeSurface.YOUTUBE_VIDEO || it.surface == YouTubeSurface.YOUTUBE_SHORTS)
                ) {
                    scheduleYouTubeSubscriptionProbeIfNeeded()
                } else if (task != TunnelTask.YOUTUBE_SUBSCRIPTIONS) {
                    clearYouTubeSubscriptionProbe()
                }
                surfaceUsageTracker.observe(packageName, surface, task, capturedAt)
                tunnelCoordinator.state.activeSession
                    ?.takeIf { session -> session.status == TunnelStatus.ACTIVE && session.app.packageName == packageName }
                    ?.let { session -> attentionRecorder.surfaceObserved(session, surface, capturedAt) }
                tunnelCoordinator.observeSurface(policySurface, capturedAt)
            }
            instagramDetection?.let {
                if (instagramDirectedNavigationInProgress) return@let
                val surface = it.surface.toTunnelSurface()
                val task = tunnelCoordinator.state.activeSession?.takeIf { session -> session.app.packageName == packageName }?.task
                surfaceUsageTracker.observe(packageName, surface, task, capturedAt)
                tunnelCoordinator.state.activeSession
                    ?.takeIf { session -> session.status == TunnelStatus.ACTIVE && session.app.packageName == packageName }
                    ?.let { session -> attentionRecorder.surfaceObserved(session, surface, capturedAt) }
                tunnelCoordinator.observeSurface(surface, capturedAt)
            }
            tikTokDetection?.let {
                // Routing from Inbox/Profile to Search may briefly pass through For You because
                // TikTok only exposes the Search affordance from feed-like destinations. Do not
                // treat that app-controlled transit surface as user drift. The final settled
                // destination is captured immediately after routing completes.
                if (tikTokDirectedNavigationInProgress) return@let
                val surface = it.surface.toTunnelSurface()
                val task = tunnelCoordinator.state.activeSession
                    ?.takeIf { session -> session.app.packageName == packageName }
                    ?.task
                surfaceUsageTracker.observe(packageName, surface, task, capturedAt)
                tunnelCoordinator.state.activeSession
                    ?.takeIf { session -> session.status == TunnelStatus.ACTIVE && session.app.packageName == packageName }
                    ?.let { session -> attentionRecorder.surfaceObserved(session, surface, capturedAt) }
                tunnelCoordinator.observeSurface(surface, capturedAt)
            }
            updateTunnelUi()
        } catch (_: RuntimeException) {
            while (stack.isNotEmpty()) recycleNode(stack.removeLast().node)
            AccessibilityRuntime.clearCurrentYouTubeDetection()
            AccessibilityRuntime.clearCurrentInstagramDetection()
            AccessibilityRuntime.clearCurrentTikTokCapture()
            failOpenCurrentSurface()
            AccessibilityRuntime.update {
                if (BuildConfig.DEBUG && state.inspectionArmed) it.copy(
                    inspectionStatus = InspectionStatus.ERROR,
                    inspectionDetail = "Tree inspection failed safely; any shown capture is older.",
                ) else it
            }
        }
    }

    private fun eventTargetsYouTubeTopLevelNavigation(event: AccessibilityEvent): Boolean {
        var node = try { event.source } catch (_: RuntimeException) { null } ?: return false
        var ownsNode = true
        return try {
            repeat(4) {
                val role = inferWhitelistedChromeRole(YouTubeSurfaceDetector.YOUTUBE_PACKAGE, node)
                if (role == UiChromeRole.YOUTUBE_HOME ||
                    role == UiChromeRole.YOUTUBE_SHORTS ||
                    role == UiChromeRole.YOUTUBE_SUBSCRIPTIONS ||
                    role == UiChromeRole.YOUTUBE_YOU
                ) {
                    return true
                }
                val parent = try { node.parent } catch (_: RuntimeException) { null } ?: return false
                if (ownsNode) recycleNode(node)
                node = parent
                ownsNode = true
            }
            false
        } finally {
            if (ownsNode) recycleNode(node)
        }
    }

    private fun inferWhitelistedChromeRole(
        packageName: String,
        node: AccessibilityNodeInfo,
    ): UiChromeRole? {
        if (packageName != YouTubeSurfaceDetector.YOUTUBE_PACKAGE) return null
        val visible = try { node.isVisibleToUser && node.isEnabled } catch (_: RuntimeException) { false }
        if (!visible) return null
        val className = try { node.className?.toString() } catch (_: RuntimeException) { null }

        // Only derive fixed YouTube chrome roles. Raw text/content descriptions are never stored
        // in the sanitized snapshot, fingerprint, database, or Attention history.
        val labels = buildList {
            try { node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add) } catch (_: RuntimeException) { }
            try { node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add) } catch (_: RuntimeException) { }
        }
        fun matches(label: String) = labels.any { it == label || it.startsWith("$label,") }
        if (matches("Back") || matches("Navigate up")) return UiChromeRole.YOUTUBE_BACK
        if (className?.endsWith("Button") != true) return null
        return when {
            matches("Home") -> UiChromeRole.YOUTUBE_HOME
            matches("Shorts") -> UiChromeRole.YOUTUBE_SHORTS
            matches("Subscriptions") -> UiChromeRole.YOUTUBE_SUBSCRIPTIONS
            matches("You") -> UiChromeRole.YOUTUBE_YOU
            else -> null
        }
    }

    private fun inferWhitelistedChromeSelected(
        packageName: String,
        node: AccessibilityNodeInfo,
    ): Boolean {
        if (packageName != YouTubeSurfaceDetector.YOUTUBE_PACKAGE) return false
        val role = inferWhitelistedChromeRole(packageName, node) ?: return false
        val roleLabel = when (role) {
            UiChromeRole.YOUTUBE_HOME -> "home"
            UiChromeRole.YOUTUBE_SHORTS -> "shorts"
            UiChromeRole.YOUTUBE_SUBSCRIPTIONS -> "subscriptions"
            UiChromeRole.YOUTUBE_YOU -> "you"
            UiChromeRole.YOUTUBE_BACK -> return false
        }
        return buildList {
            try { node.text?.toString()?.trim()?.lowercase(java.util.Locale.ROOT)?.let(::add) } catch (_: RuntimeException) { }
            try { node.contentDescription?.toString()?.trim()?.lowercase(java.util.Locale.ROOT)?.let(::add) } catch (_: RuntimeException) { }
        }.any { it.startsWith(roleLabel) && "selected" in it }
    }

    /**
     * Derive only the fixed Subscribe/Subscribed state from YouTube accessibility labels.
     * Channel names and the raw labels are intentionally discarded at capture time.
     * Conflicting states later fail open in the detector.
     */
    private fun inferWhitelistedYouTubeSubscriptionState(
        packageName: String,
        node: AccessibilityNodeInfo,
    ): YouTubeSubscriptionState? {
        if (packageName != YouTubeSurfaceDetector.YOUTUBE_PACKAGE) return null
        val interactive = try {
            node.isClickable || node.className?.toString()?.endsWith("Button") == true
        } catch (_: RuntimeException) {
            false
        }
        val subscribeLikeId = try {
            node.viewIdResourceName?.substringAfterLast('/')?.lowercase(java.util.Locale.ROOT)?.contains("subscribe") == true
        } catch (_: RuntimeException) {
            false
        }

        val labels = buildList {
            try { node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add) } catch (_: RuntimeException) { }
            try { node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add) } catch (_: RuntimeException) { }
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                try { node.stateDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add) } catch (_: RuntimeException) { }
            }
        }
        fun exactSubscribed(label: String) = label.equals("Subscribed", ignoreCase = true)
        fun exactSubscribe(label: String) = label.equals("Subscribe", ignoreCase = true)
        fun exactUnsubscribe(label: String) = label.equals("Unsubscribe", ignoreCase = true)
        fun subscribedAction(label: String): Boolean {
            val normalized = label.trim()
            return normalized.startsWith("Subscribed,", ignoreCase = true) ||
                normalized.startsWith("Subscribed ", ignoreCase = true) ||
                normalized.startsWith("Unsubscribe,", ignoreCase = true) ||
                normalized.startsWith("Unsubscribe ", ignoreCase = true)
        }
        fun subscribeAction(label: String): Boolean {
            val normalized = label.trim()
            return normalized.startsWith("Subscribe,", ignoreCase = true) ||
                normalized.startsWith("Subscribe ", ignoreCase = true)
        }
        fun notificationSettingsForSubscribedChannel(label: String): Boolean {
            val normalized = label.trim().lowercase(java.util.Locale.ROOT)
            return normalized.startsWith("current setting is ") &&
                "notification" in normalized &&
                " for " in normalized
        }

        return when {
            labels.any(::exactSubscribed) || labels.any(::exactUnsubscribe) -> YouTubeSubscriptionState.SUBSCRIBED
            labels.any(::exactSubscribe) -> YouTubeSubscriptionState.NOT_SUBSCRIBED
            (interactive || subscribeLikeId) && labels.any(::subscribedAction) -> YouTubeSubscriptionState.SUBSCRIBED
            (interactive || subscribeLikeId) && labels.any(::subscribeAction) -> YouTubeSubscriptionState.NOT_SUBSCRIBED
            interactive && labels.any(::notificationSettingsForSubscribedChannel) -> YouTubeSubscriptionState.SUBSCRIBED
            else -> null
        }
    }

    private fun scheduleYouTubeSubscriptionProbeIfNeeded() {
        if (youTubeSubscriptionProbeAttemptsRemaining <= 0 || youTubeSubscriptionProbePending) return
        youTubeSubscriptionProbeAttemptsRemaining -= 1
        youTubeSubscriptionProbePending = true
        handler.postDelayed(youTubeSubscriptionProbe, YOUTUBE_SUBSCRIPTION_PROBE_SETTLE_MS)
    }

    private fun clearYouTubeSubscriptionProbe() {
        youTubeSubscriptionProbeAttemptsRemaining = 0
        youTubeSubscriptionProbePending = false
        handler.removeCallbacks(youTubeSubscriptionProbe)
    }

    private fun activeRootPackage(): String? {
        val root = try { rootInActiveWindow } catch (_: RuntimeException) { null } ?: return null
        return try { root.packageName?.toString() } catch (_: RuntimeException) { null } finally { recycleNode(root) }
    }

    private fun isTransientSystemUi(packageName: String?): Boolean {
        if (packageName != SYSTEM_UI_PACKAGE) return false
        val keyguard = getSystemService(KeyguardManager::class.java)
        return !keyguard.isKeyguardLocked
    }

    private fun shouldCapture(packageName: String?, inspectionArmed: Boolean): Boolean =
        packageName == YouTubeSurfaceDetector.YOUTUBE_PACKAGE ||
            packageName == InstagramSurfaceDetector.INSTAGRAM_PACKAGE ||
            packageName == TikTokSurfaceDetector.TIKTOK_PACKAGE ||
            (BuildConfig.DEBUG && inspectionArmed && packageName in TARGET_PACKAGES)

    private fun installedTikTokVersion(): Pair<String?, Long?> = runCatching {
        val packageInfo = packageManager.getPackageInfo(TikTokFingerprint.TIKTOK_PACKAGE, 0)
        packageInfo.versionName to packageInfo.longVersionCode
    }.getOrDefault(null to null)

    @Suppress("DEPRECATION")
    private fun recycleNode(node: AccessibilityNodeInfo) {
        if (android.os.Build.VERSION.SDK_INT <= 32) node.recycle()
    }

    private fun showTestOverlayNow() {
        AccessibilityRuntime.update { it.copy(overlayPending = false) }
        if (testOverlayView != null || tunnelCoordinator.state.prompt != null || driftOverlayView != null) return
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(0xEE202124.toInt())
            addView(TextView(context).apply {
                text = "Task Tunnel test overlay"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 18f
            })
            addView(Button(context).apply {
                text = "Close"
                setOnClickListener { removeTestOverlay() }
            })
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 96 }
        try {
            getSystemService(WindowManager::class.java).addView(layout, params)
            testOverlayView = layout
            surfaceUsageTracker.setOverlayVisible(true, System.currentTimeMillis())
            AccessibilityRuntime.update { it.copy(overlayVisible = true) }
        } catch (_: RuntimeException) {
            testOverlayView = null
            AccessibilityRuntime.update { it.copy(overlayVisible = false) }
        }
    }

    private fun removeTestOverlay() {
        val view = testOverlayView ?: return
        try { getSystemService(WindowManager::class.java).removeView(view) } catch (_: RuntimeException) { }
        testOverlayView = null
        surfaceUsageTracker.setOverlayVisible(false, System.currentTimeMillis())
        AccessibilityRuntime.update { it.copy(overlayVisible = false) }
    }

    private fun syncTunnelState(
        transform: (AccessibilityState) -> AccessibilityState = { it },
    ) = AccessibilityRuntime.update { state ->
        transform(state).copy(tunnelState = tunnelCoordinator.state)
    }

    private fun failOpenCurrentSurface() {
        if (!protectionEnabled) return
        surfaceUsageTracker.foregroundChanged(null, System.currentTimeMillis())
        tunnelCoordinator.observeSurface(DetectedSurface.UNKNOWN, System.currentTimeMillis())
        updateTunnelUi()
    }

    private fun updateTunnelUi() {
        if (!protectionEnabled) return
        syncTunnelState()
        // Drift is the entry-level intervention for rapid app hopping. It should beat the
        // ordinary Purpose Gate, but never an active Tunnel interaction. Refresh it first so a
        // qualifying sequence can claim the overlay before the current app's Purpose Gate is
        // rendered.
        refreshDriftOverlay()
        syncTunnelState()
        refreshTunnelOverlay()
        scheduleTunnelDeadline()
        tunnelNotificationController.sync(tunnelCoordinator.state)
        scheduleNotificationProgressRefresh()
    }

    private fun scheduleNotificationProgressRefresh() {
        handler.removeCallbacks(notificationProgressRefresh)
        if (!protectionEnabled) return
        if (!tunnelNotificationController.shouldRefreshProgress(tunnelCoordinator.state)) return
        handler.postDelayed(notificationProgressRefresh, TunnelNotificationController.PROGRESS_REFRESH_MILLIS)
    }

    private fun scheduleTunnelDeadline() {
        handler.removeCallbacks(tunnelDeadline)
        if (!protectionEnabled) return
        val deadline = tunnelCoordinator.nextDeadlineMillis() ?: return
        handler.postDelayed(tunnelDeadline, (deadline - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    private fun refreshTunnelOverlay() {
        val prompt = tunnelCoordinator.state.prompt
        // A visible Drift check-in owns the entry overlay slot. The only Tunnel prompt it can
        // coexist with conceptually is a Purpose Gate, and refreshDriftOverlay() dismisses that
        // gate before showing Drift. Keep this guard as a fail-safe against overlay stacking.
        if (driftOverlayView != null && prompt is TunnelPrompt.PurposeGate && tunnelCoordinator.state.activeSession == null) {
            removeTunnelOverlay()
            return
        }
        if (prompt != null) removeDriftOverlay()
        if (prompt == shownTunnelPrompt && tunnelOverlayView != null) return

        val shownPurposeGate = shownTunnelPrompt as? TunnelPrompt.PurposeGate
        val purposeGateLeftForeground = prompt == null &&
            shownPurposeGate != null &&
            tunnelCoordinator.state.foregroundPackage != shownPurposeGate.app.packageName
        if (purposeGateLeftForeground) {
            animatePurposeGateExit()
            return
        }

        removeTunnelOverlay()
        if (prompt == null) return

        handler.removeCallbacks(showOverlay)
        removeTestOverlay()
        AccessibilityRuntime.update { it.copy(overlayPending = false) }
        val layout = when (prompt) {
            is TunnelPrompt.PurposeGate -> purposeGateView(prompt)
            is TunnelPrompt.Intervention -> interventionView(
                prompt = prompt,
                variant = chooseInterventionVariant(prompt),
                learningEnabled = true,
            )
            is TunnelPrompt.SessionExpired -> sessionExpiredView(prompt)
            is TunnelPrompt.IntentionCheckIn -> intentionCheckInView(prompt)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            dimAmount = 0.42f
        }
        try {
            getSystemService(WindowManager::class.java).addView(layout, params)
            surfaceUsageTracker.setOverlayVisible(true, System.currentTimeMillis())
            val isPurposeGate = prompt is TunnelPrompt.PurposeGate
            if (isPurposeGate) {
                layout.alpha = 0f
                layout.translationY = dp(28).toFloat()
                layout.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(200L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            } else {
                animateOverlayEntrance(layout)
            }
            tunnelOverlayView = layout
            shownTunnelPrompt = prompt
            attentionRecorder.tunnelPromptShown(
                prompt = prompt,
                session = tunnelCoordinator.state.activeSession,
                nowMillis = System.currentTimeMillis(),
            )
        } catch (_: RuntimeException) {
            runCatching { getSystemService(WindowManager::class.java).removeView(layout) }
            surfaceUsageTracker.setOverlayVisible(false, System.currentTimeMillis())
            tunnelOverlayView = null
            shownTunnelPrompt = null
        }
    }

    private fun refreshDriftOverlay() {
        val tunnelState = tunnelCoordinator.state
        val foregroundPackage = driftCoordinator.foregroundPackage
        val promptBlocksDrift = when (val prompt = tunnelState.prompt) {
            null -> false
            is TunnelPrompt.PurposeGate -> prompt.replacingSessionId != null
            else -> true
        }
        val activeTunnelOwnsForeground = tunnelState.activeSession?.app?.packageName == foregroundPackage
        if (activeTunnelOwnsForeground || promptBlocksDrift || !driftCoordinator.isSelected(foregroundPackage)) {
            dismissVisibleDriftOverlay()
            return
        }

        // While Drift is already on screen, moving to another enabled distraction app can make
        // TunnelCoordinator create that app's normal Purpose Gate. Drift still owns the decision
        // point, so discard the hidden gate rather than letting it replace the check-in.
        if (driftOverlayView != null) {
            val purposeGate = tunnelCoordinator.state.prompt as? TunnelPrompt.PurposeGate
            if (purposeGate != null && purposeGate.replacingSessionId == null) {
                tunnelCoordinator.dismissPurposeGate()
            }
            removeTunnelOverlay()
            return
        }

        val episode = driftCoordinator.checkInCandidate(higherPriorityPromptVisible = false) ?: return
        val labels = episode.involvedPackages.map { DriftAppCatalog.labelFor(this, it) }

        // A newly entered supported app normally creates its Purpose Gate immediately. A valid
        // Drift episode is more useful at this point, so consume that ordinary gate. "Set an
        // intention" below can explicitly request a fresh Purpose Gate if the user chooses it.
        val purposeGate = tunnelCoordinator.state.prompt as? TunnelPrompt.PurposeGate
        if (purposeGate != null && purposeGate.replacingSessionId == null) {
            tunnelCoordinator.dismissPurposeGate()
        }

        handler.removeCallbacks(showOverlay)
        removeTestOverlay()
        removeTunnelOverlay()
        AccessibilityRuntime.update { it.copy(overlayPending = false) }
        val layout = driftCheckInView(episode, labels)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            dimAmount = 0.32f
        }
        try {
            getSystemService(WindowManager::class.java).addView(layout, params)
            surfaceUsageTracker.setOverlayVisible(true, System.currentTimeMillis())
            animateOverlayEntrance(layout)
            driftOverlayView = layout
            driftCoordinator.markCheckInShown(episode.id)
            attentionRecorder.driftCheckInShown(episode, System.currentTimeMillis())
        } catch (_: RuntimeException) {
            runCatching { getSystemService(WindowManager::class.java).removeView(layout) }
            surfaceUsageTracker.setOverlayVisible(false, System.currentTimeMillis())
            driftOverlayView = null
        }
    }

    private fun driftCheckInView(episode: DriftEpisode, labels: List<String>) = baseTunnelOverlay().apply {
        addView(overlayAppSequence(episode.involvedPackages, labels))
        addView(overlayText("Looking for something?", heading = true))
        addView(overlayText("You moved between ${humanReadableList(labels)} in under a minute.", secondary = true))
        val foregroundApp = SupportedApp.fromPackage(driftCoordinator.foregroundPackage)
        if (foregroundApp != null) {
            addView(overlayPrimaryButton("Set an intention") {
                val nowMillis = System.currentTimeMillis()
                attentionRecorder.driftDecision(
                    episode = episode,
                    subtype = AttentionSubtype.SET_INTENTION,
                    decision = AttentionDecision.SET_INTENTION,
                    foregroundPackage = driftCoordinator.foregroundPackage,
                    nowMillis = nowMillis,
                )
                val app = driftCoordinator.setAnIntention(episode.id, nowMillis)
                removeDriftOverlay()
                if (app != null) tunnelCoordinator.requestPurposeGate(app)
                updateTunnelUi()
            })
            addView(overlayTextButton("Keep going") {
                acknowledgeDriftAndKeepGoing(episode)
            })
        } else {
            // Drift can include apps that do not have Task Tunnel surface detection or Purpose
            // Gate support. Do not offer a dead-end intention action in those apps; acknowledging
            // the pattern is the only meaningful choice and starts a fresh Drift sequence here.
            addView(overlayPrimaryButton("Understood") {
                acknowledgeDriftAndKeepGoing(episode)
            })
        }
    }

    private fun acknowledgeDriftAndKeepGoing(episode: DriftEpisode) {
        val nowMillis = System.currentTimeMillis()
        attentionRecorder.driftDecision(
            episode = episode,
            subtype = AttentionSubtype.KEEP_GOING,
            decision = AttentionDecision.KEEP_GOING,
            foregroundPackage = driftCoordinator.foregroundPackage,
            nowMillis = nowMillis,
        )
        driftCoordinator.keepGoing(episode.id, nowMillis)
        removeDriftOverlay()
        updateTunnelUi()
    }

    private fun humanReadableList(labels: List<String>): String = when (labels.size) {
        0 -> "these apps"
        1 -> labels.single()
        2 -> "${labels[0]} and ${labels[1]}"
        else -> labels.dropLast(1).joinToString(", ") + " and " + labels.last()
    }

    private fun purposeGateView(prompt: TunnelPrompt.PurposeGate) = baseTunnelOverlay(compact = true).apply {
        var selectedDurationMillis: Long? = prompt.preservedDurationMillis
        var durationWasChanged = false
        val durationForStart = {
            if (prompt.replacingSessionId != null && !durationWasChanged) {
                tunnelCoordinator.state.activeSession
                    ?.takeIf { it.id == prompt.replacingSessionId && it.status == TunnelStatus.ACTIVE }
                    ?.expiresAtMillis
                    ?.let { (it - System.currentTimeMillis()).coerceAtLeast(1L) }
            } else {
                selectedDurationMillis
            }
        }
        addView(purposeGateAppIdentity(prompt.app))
        addView(purposeGateQuestion())
        val durationSelector = purposeDurationSelector()
        val durationValue = TextView(context).apply {
            text = prompt.preservedDurationMillis?.let(::formatRemainingDuration) ?: DURATION_CHOICES.first().label
            textSize = 14f
            setTextColor(COLOR_SECONDARY_TEXT)
        }
        when (prompt.app) {
            SupportedApp.INSTAGRAM -> {
                addView(purposeChoiceRow(R.drawable.ic_purpose_message, "Reply to messages", "Go straight to conversations") { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    startTunnel(TunnelTask.INSTAGRAM_MESSAGES, durationForStart())
                })
                addView(overlayDivider())
                addView(purposeChoiceRow(R.drawable.ic_purpose_search, "Search / look something up", "Find an account, post, or topic") { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    startTunnel(TunnelTask.INSTAGRAM_SEARCH, durationForStart())
                })
                addView(overlayDivider())
                addView(purposeChoiceRow(R.drawable.ic_purpose_create, "Post something", "Create without falling into the feed") { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    startTunnel(TunnelTask.INSTAGRAM_POST, durationForStart())
                })
                addView(overlayDivider())
                addView(purposeChoiceRow(R.drawable.ic_purpose_browse, "Browse intentionally", "Explore on your terms") { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    startTunnel(TunnelTask.INSTAGRAM_BROWSE, durationForStart())
                })
            }
            SupportedApp.YOUTUBE -> {
                addView(purposeChoiceRow(R.drawable.ic_purpose_search, "Search / watch something specific", "Find what you came to watch") { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    startTunnel(TunnelTask.YOUTUBE_SEARCH_WATCH, durationForStart())
                })
                addView(overlayDivider())
                addView(purposeChoiceRow(R.drawable.ic_purpose_subscriptions, "Check subscriptions", "See new videos from channels you chose") { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    startTunnel(TunnelTask.YOUTUBE_SUBSCRIPTIONS, durationForStart())
                })
                addView(overlayDivider())
                addView(purposeChoiceRow(R.drawable.ic_purpose_shorts, "Watch Shorts intentionally", "Short-form, but on purpose") { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    startTunnel(TunnelTask.YOUTUBE_SHORTS, durationForStart())
                })
                addView(overlayDivider())
                addView(purposeChoiceRow(R.drawable.ic_purpose_browse, "Browse intentionally", "Explore on your terms") { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    startTunnel(TunnelTask.YOUTUBE_BROWSE, durationForStart())
                })
            }
            SupportedApp.TIKTOK -> {
                addView(purposeChoiceRow(R.drawable.ic_purpose_search, "Search / watch something specific", "Find what you came to watch") { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    startTunnel(TunnelTask.TIKTOK_SEARCH_WATCH, durationForStart())
                })
                addView(overlayDivider())
                addView(purposeChoiceRow(R.drawable.ic_purpose_message, "Check Inbox", "Check messages and notifications") { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    startTunnel(TunnelTask.TIKTOK_INBOX, durationForStart())
                })
                addView(overlayDivider())
                addView(purposeChoiceRow(R.drawable.ic_purpose_browse, "Browse intentionally", "Explore on your terms") { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    startTunnel(TunnelTask.TIKTOK_BROWSE, durationForStart())
                })
            }
        }
        lateinit var timeLimitRow: View
        timeLimitRow = purposeTimeLimitRow(durationValue) {
            timeLimitRow.visibility = View.GONE
            durationSelector.visibility = View.VISIBLE
        }
        addView(timeLimitRow)
        durationSelector.onDurationSelected = { choice ->
            durationWasChanged = true
            selectedDurationMillis = choice.durationMillis
            durationValue.text = choice.label
            durationSelector.visibility = View.GONE
            timeLimitRow.visibility = View.VISIBLE
        }
        addView(durationSelector)
        addView(purposeDismissButton(if (prompt.replacingSessionId != null) "Keep current purpose" else "Not now") {
            tunnelCoordinator.dismissPurposeGate()
            updateTunnelUi()
            scheduleCapture()
        })
    }

    private fun interventionView(
        prompt: TunnelPrompt.Intervention,
        variant: InterventionVariant,
        learningEnabled: Boolean,
    ) = baseTunnelOverlay().apply {
        val surface = surfaceLabel(prompt.surface)
        val heading = interventionHeading(prompt, surface, variant)
        val reminder = interventionReminder(prompt, surface, variant)
        addView(overlayAppIdentity(prompt.task.app))
        addView(overlayText(heading, heading = true))
        addView(overlayText(reminder, secondary = true))
        addView(overlayPrimaryButton(returnLabel(prompt.task)) {
            val session = tunnelCoordinator.state.activeSession
            val nowMillis = System.currentTimeMillis()
            val directedDestination = prompt.task.hasDirectedDestination()
            if (tunnelCoordinator.returnFromIntervention(nowMillis, addReturnCooldown = !directedDestination)) {
                session?.let {
                    attentionRecorder.tunnelDecision(
                        it,
                        AttentionSubtype.RETURN,
                        AttentionDecision.RETURN,
                        nowMillis,
                        prompt.surface,
                    )
                }
                if (learningEnabled) {
                    recordFrictionOutcome(prompt, variant, FrictionOutcome.RETURN)
                }
                updateTunnelUi()
                if (prompt.task.hasDirectedDestination()) {
                    navigateToTaskDestinationAfterOverlayDismiss(prompt.task)
                } else {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
        })

        if (prompt.task == TunnelTask.TIKTOK_SEARCH_WATCH && prompt.surface == DetectedSurface.TIKTOK_FEED) {
            addView(overlayTextButton("Go to messages") {
                val session = tunnelCoordinator.state.activeSession?.takeIf {
                    it.id == prompt.sessionId && tunnelCoordinator.state.prompt == prompt
                }
                if (session != null) {
                    val nowMillis = System.currentTimeMillis()
                    if (tunnelCoordinator.returnFromIntervention(nowMillis, addReturnCooldown = false)) {
                        attentionRecorder.tunnelDecision(
                            session,
                            AttentionSubtype.RETURN,
                            AttentionDecision.RETURN,
                            nowMillis,
                            prompt.surface,
                        )
                        if (learningEnabled) {
                            recordFrictionOutcome(prompt, variant, FrictionOutcome.RETURN)
                        }
                        updateTunnelUi()
                        navigateToTaskDestinationAfterOverlayDismiss(TunnelTask.TIKTOK_INBOX)
                    }
                }
            })
        }

        val allowLabel = when (prompt.surface) {
            DetectedSurface.YOUTUBE_UNSUBSCRIBED_VIDEO,
            DetectedSurface.YOUTUBE_UNSUBSCRIBED_SHORTS,
            -> "Keep watching for now"
            else -> "Allow $surface for now"
        }
        val allowAction = {
            val session = tunnelCoordinator.state.activeSession?.takeIf {
                it.id == prompt.sessionId && tunnelCoordinator.state.prompt == prompt
            }
            if (session != null) {
                val nowMillis = System.currentTimeMillis()
                attentionRecorder.tunnelDecision(
                    session,
                    AttentionSubtype.ALLOW_ANYWAY,
                    AttentionDecision.ALLOW_ANYWAY,
                    nowMillis,
                    prompt.surface,
                )
                if (learningEnabled) {
                    recordFrictionOutcome(prompt, variant, FrictionOutcome.ALLOW_ANYWAY)
                }
                tunnelCoordinator.allowAnyway(nowMillis)
                updateTunnelUi()
            }
        }
        addView(
            if (variant == InterventionVariant.SHORT_PAUSE) {
                overlayDelayedTextButton(
                    label = allowLabel,
                    delayMillis = AdaptiveFrictionEngine.SHORT_PAUSE_MILLIS,
                    action = allowAction,
                )
            } else {
                overlayTextButton(allowLabel, action = allowAction)
            },
        )

        addView(overlayTextButton("End Tunnel", quiet = true) {
            val session = tunnelCoordinator.state.activeSession?.takeIf {
                it.id == prompt.sessionId && tunnelCoordinator.state.prompt == prompt
            }
            if (session != null) {
                attentionRecorder.tunnelDecision(
                    session,
                    AttentionSubtype.END_TUNNEL,
                    AttentionDecision.END_TUNNEL,
                    System.currentTimeMillis(),
                    prompt.surface,
                )
                if (learningEnabled) {
                    recordFrictionOutcome(prompt, variant, FrictionOutcome.END_TUNNEL)
                }
                tunnelCoordinator.endSession()
                updateTunnelUi()
            }
        })
    }

    private fun intentionCheckInView(prompt: TunnelPrompt.IntentionCheckIn) = baseTunnelOverlay().apply {
        val session = tunnelCoordinator.state.activeSession
        val app = prompt.task.app
        val surface = prompt.surface?.let(::surfaceLabel)
        addView(overlayAppIdentity(app))
        if (prompt.kind == IntentionCheckInKind.DETOUR_RENEWAL) {
            addView(overlayText(
                when (prompt.surface) {
                    DetectedSurface.INSTAGRAM_EXPLORE -> "Still exploring intentionally?"
                    else -> "Still watching intentionally?"
                },
                heading = true,
            ))
            addView(overlayText("You chose to spend a few minutes in ${surface ?: "this screen"}.", secondary = true))
            addView(overlayPrimaryButton(returnLabel(prompt.task)) {
                if (tunnelCoordinator.state.prompt != prompt) return@overlayPrimaryButton
                val nowMillis = System.currentTimeMillis()
                val directedDestination = prompt.task.hasDirectedDestination()
                if (tunnelCoordinator.returnFromCheckIn(nowMillis, addReturnCooldown = !directedDestination)) {
                    session?.let {
                        attentionRecorder.tunnelDecision(it, AttentionSubtype.CHECK_IN_RETURN, AttentionDecision.CHECK_IN_RETURN, nowMillis, prompt.surface)
                    }
                    updateTunnelUi()
                    if (directedDestination) {
                        navigateToTaskDestinationAfterOverlayDismiss(prompt.task)
                    } else {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                }
            })
            addView(overlayTextButton(if (prompt.surface == DetectedSurface.INSTAGRAM_EXPLORE) "Keep exploring" else "Keep watching") {
                if (tunnelCoordinator.state.prompt != prompt) return@overlayTextButton
                session?.let {
                    attentionRecorder.tunnelDecision(it, AttentionSubtype.CHECK_IN_CONTINUE, AttentionDecision.CHECK_IN_CONTINUE, System.currentTimeMillis(), prompt.surface)
                }
                tunnelCoordinator.continueCheckIn(System.currentTimeMillis())
                updateTunnelUi()
            })
            addView(overlayTextButton("End Tunnel", quiet = true) {
                if (tunnelCoordinator.state.prompt != prompt) return@overlayTextButton
                session?.let {
                    attentionRecorder.tunnelDecision(it, AttentionSubtype.CHECK_IN_END, AttentionDecision.CHECK_IN_END, System.currentTimeMillis(), prompt.surface)
                }
                tunnelCoordinator.endSession()
                updateTunnelUi()
            })
        } else {
            addView(overlayText("Still browsing intentionally?", heading = true))
            addView(overlayText("You chose to browse without a time limit.", secondary = true))
            addView(overlayPrimaryButton("Keep browsing") {
                if (tunnelCoordinator.state.prompt != prompt) return@overlayPrimaryButton
                session?.let {
                    attentionRecorder.tunnelDecision(it, AttentionSubtype.CHECK_IN_CONTINUE, AttentionDecision.CHECK_IN_CONTINUE, System.currentTimeMillis())
                }
                tunnelCoordinator.continueCheckIn(System.currentTimeMillis())
                updateTunnelUi()
            })
            addView(overlayTextButton("Choose another purpose") {
                if (tunnelCoordinator.state.prompt != prompt) return@overlayTextButton
                session?.let {
                    attentionRecorder.tunnelDecision(it, AttentionSubtype.CHECK_IN_CHOOSE_ANOTHER, AttentionDecision.CHECK_IN_CHOOSE_ANOTHER, System.currentTimeMillis())
                }
                tunnelCoordinator.chooseAnotherPurpose()
                updateTunnelUi()
            })
            addView(overlayTextButton("Finish", quiet = true) {
                if (tunnelCoordinator.state.prompt != prompt) return@overlayTextButton
                session?.let {
                    attentionRecorder.tunnelDecision(it, AttentionSubtype.CHECK_IN_END, AttentionDecision.CHECK_IN_END, System.currentTimeMillis())
                }
                tunnelCoordinator.endSession()
                updateTunnelUi()
            })
        }
    }

    private fun chooseInterventionVariant(prompt: TunnelPrompt.Intervention): InterventionVariant {
        val context = FrictionContext.from(prompt.task, prompt.surface)
        return adaptiveFrictionEngine.chooseVariant(adaptiveFrictionStore.load(context))
    }

    private fun recordFrictionOutcome(
        prompt: TunnelPrompt.Intervention,
        variant: InterventionVariant,
        outcome: FrictionOutcome,
    ) {
        val context = FrictionContext.from(prompt.task, prompt.surface)
        val currentProfile = adaptiveFrictionStore.load(context)
        val updatedProfile = adaptiveFrictionEngine.recordOutcome(currentProfile, variant, outcome)
        adaptiveFrictionStore.save(context, updatedProfile)
    }

    private fun interventionHeading(
        prompt: TunnelPrompt.Intervention,
        surface: String,
        variant: InterventionVariant,
    ): String {
        if (prompt.task == TunnelTask.YOUTUBE_SUBSCRIPTIONS &&
            prompt.surface in setOf(
                DetectedSurface.YOUTUBE_UNSUBSCRIBED_VIDEO,
                DetectedSurface.YOUTUBE_UNSUBSCRIBED_SHORTS,
            )
        ) return "This creator isn't in your subscriptions"
        return when (variant) {
        InterventionVariant.INTENT_RECALL -> when (prompt.task) {
            TunnelTask.INSTAGRAM_MESSAGES -> "Still here to reply?"
            TunnelTask.INSTAGRAM_SEARCH -> "Still looking for something specific?"
            TunnelTask.INSTAGRAM_POST -> "Still here to post?"
            TunnelTask.YOUTUBE_SEARCH_WATCH -> "Still here for something specific?"
            TunnelTask.YOUTUBE_SUBSCRIPTIONS -> "Still checking subscriptions?"
            TunnelTask.YOUTUBE_SHORTS -> "Still watching Shorts intentionally?"
            TunnelTask.TIKTOK_SEARCH_WATCH -> "Still here for something specific?"
            TunnelTask.TIKTOK_INBOX -> "Still here to check your Inbox?"
            TunnelTask.INSTAGRAM_BROWSE,
            TunnelTask.YOUTUBE_BROWSE,
            TunnelTask.TIKTOK_BROWSE,
            -> "$surface isn't part of this Tunnel"
        }
        InterventionVariant.DIRECT,
        InterventionVariant.SHORT_PAUSE,
        -> "$surface isn't part of this Tunnel"
        }
    }

    private fun interventionReminder(
        prompt: TunnelPrompt.Intervention,
        surface: String,
        variant: InterventionVariant,
    ): String {
        if (prompt.task == TunnelTask.YOUTUBE_SUBSCRIPTIONS) {
            when (prompt.surface) {
                DetectedSurface.YOUTUBE_UNSUBSCRIBED_VIDEO ->
                    return "You came here to check subscriptions. This video is from a channel you don't subscribe to."
                DetectedSurface.YOUTUBE_UNSUBSCRIBED_SHORTS ->
                    return "You came here to check subscriptions. This Short is from a channel you don't subscribe to."
                else -> Unit
            }
        }
        return when (variant) {
        InterventionVariant.INTENT_RECALL -> when (prompt.task) {
            TunnelTask.INSTAGRAM_MESSAGES ->
                "You opened Instagram to reply to messages. $surface is outside that purpose."
            TunnelTask.INSTAGRAM_SEARCH ->
                "You opened Instagram to look something up. $surface is outside that purpose."
            TunnelTask.INSTAGRAM_POST ->
                "You opened Instagram to post something. $surface is outside that purpose."
            TunnelTask.YOUTUBE_SEARCH_WATCH ->
                "You opened YouTube to search for or watch something. $surface is outside that purpose."
            TunnelTask.YOUTUBE_SUBSCRIPTIONS ->
                "You opened YouTube to check subscriptions. $surface is outside that purpose."
            TunnelTask.YOUTUBE_SHORTS ->
                "You chose to watch Shorts intentionally. $surface is outside that purpose."
            TunnelTask.TIKTOK_SEARCH_WATCH ->
                "You opened TikTok to search for or watch something. $surface is outside that purpose."
            TunnelTask.TIKTOK_INBOX ->
                "You opened TikTok to check your Inbox. $surface is outside that purpose."
            TunnelTask.INSTAGRAM_BROWSE,
            TunnelTask.YOUTUBE_BROWSE,
            TunnelTask.TIKTOK_BROWSE,
            -> taskReminder(prompt.task)
        }
        InterventionVariant.DIRECT,
        InterventionVariant.SHORT_PAUSE,
        -> taskReminder(prompt.task)
        }
    }

    private fun sessionExpiredView(prompt: TunnelPrompt.SessionExpired) = baseTunnelOverlay().apply {
        addView(overlayText("Your chosen time is complete", heading = true))
        addView(overlayText("What would you like to do next?", secondary = true))
        addView(overlayPrimaryButton(if (prompt.task in setOf(
                TunnelTask.INSTAGRAM_BROWSE,
                TunnelTask.YOUTUBE_BROWSE,
                TunnelTask.TIKTOK_BROWSE,
            )
        ) {
            "Keep browsing"
        } else {
            "Continue"
        }) {
            val nowMillis = System.currentTimeMillis()
            tunnelCoordinator.state.activeSession?.let {
                attentionRecorder.tunnelDecision(
                    it,
                    AttentionSubtype.EXPIRY_CONTINUE,
                    AttentionDecision.CONTINUE,
                    nowMillis,
                )
            }
            tunnelCoordinator.continueExpiredSession(nowMillis)
            updateTunnelUi()
            scheduleCapture()
        })
        addView(overlayTextButton("Finish") {
            tunnelCoordinator.state.activeSession?.let {
                attentionRecorder.tunnelDecision(
                    it,
                    AttentionSubtype.EXPIRY_FINISH,
                    AttentionDecision.FINISH,
                    System.currentTimeMillis(),
                )
            }
            tunnelCoordinator.endSession()
            updateTunnelUi()
        })
        addView(overlayTextButton("Choose another purpose", quiet = true) {
            tunnelCoordinator.state.activeSession?.let {
                attentionRecorder.tunnelDecision(
                    it,
                    AttentionSubtype.EXPIRY_CHOOSE_ANOTHER,
                    AttentionDecision.CHOOSE_ANOTHER_PURPOSE,
                    System.currentTimeMillis(),
                )
            }
            tunnelCoordinator.chooseAnotherPurpose()
            updateTunnelUi()
        })
    }

    private fun startTunnel(task: TunnelTask, intendedDurationMillis: Long?) {
        val nowMillis = System.currentTimeMillis()
        val stateBeforeStart = tunnelCoordinator.state
        val alreadyAtDestination = stateBeforeStart.foregroundPackage == task.app.packageName &&
            task.acceptsCurrentDirectedDestination(stateBeforeStart.currentSurface)
        val directedDestination = task.hasDirectedDestination() && !alreadyAtDestination
        val started = tunnelCoordinator.startSession(
            task = task,
            nowMillis = nowMillis,
            intendedDurationMillis = intendedDurationMillis,
            evaluateCurrentSurface = !directedDestination,
        )
        if (!started) {
            // A stale gate/callback must never navigate or record a purpose that the coordinator
            // rejected (for example, when a replacement session expired while the picker was open).
            updateTunnelUi()
            return
        }
        tunnelCoordinator.state.activeSession?.let { attentionRecorder.purposeSelected(it, nowMillis) }
        updateTunnelUi()
        if (directedDestination) {
            navigateToTaskDestinationAfterOverlayDismiss(task)
        } else {
            scheduleCapture()
        }
    }

    private fun TunnelTask.hasDirectedDestination(): Boolean = when (this) {
        TunnelTask.INSTAGRAM_MESSAGES,
        TunnelTask.INSTAGRAM_SEARCH,
        TunnelTask.INSTAGRAM_POST,
        TunnelTask.YOUTUBE_SEARCH_WATCH,
        TunnelTask.YOUTUBE_SUBSCRIPTIONS,
        TunnelTask.YOUTUBE_SHORTS,
        TunnelTask.TIKTOK_SEARCH_WATCH,
        TunnelTask.TIKTOK_INBOX,
        -> true
        TunnelTask.INSTAGRAM_BROWSE,
        TunnelTask.YOUTUBE_BROWSE,
        TunnelTask.TIKTOK_BROWSE,
        -> false
    }

    private fun TunnelTask.acceptsCurrentDirectedDestination(surface: DetectedSurface?): Boolean = when (this) {
        TunnelTask.INSTAGRAM_MESSAGES -> surface == DetectedSurface.INSTAGRAM_MESSAGES
        TunnelTask.INSTAGRAM_SEARCH -> surface == DetectedSurface.INSTAGRAM_EXPLORE
        TunnelTask.INSTAGRAM_POST -> surface == DetectedSurface.INSTAGRAM_CREATE
        TunnelTask.YOUTUBE_SEARCH_WATCH -> surface in setOf(
            DetectedSurface.YOUTUBE_SEARCH,
            DetectedSurface.YOUTUBE_VIDEO,
            DetectedSurface.YOUTUBE_UNSUBSCRIBED_VIDEO,
        )
        TunnelTask.YOUTUBE_SUBSCRIPTIONS -> surface == DetectedSurface.YOUTUBE_SUBSCRIPTIONS
        TunnelTask.YOUTUBE_SHORTS -> surface in setOf(
            DetectedSurface.YOUTUBE_SHORTS,
            DetectedSurface.YOUTUBE_UNSUBSCRIBED_SHORTS,
        )
        TunnelTask.TIKTOK_SEARCH_WATCH -> surface == DetectedSurface.TIKTOK_SEARCH
        TunnelTask.TIKTOK_INBOX -> surface == DetectedSurface.TIKTOK_INBOX
        TunnelTask.INSTAGRAM_BROWSE,
        TunnelTask.YOUTUBE_BROWSE,
        TunnelTask.TIKTOK_BROWSE,
        -> false
    }

    private fun navigateToTaskDestinationAfterOverlayDismiss(task: TunnelTask) {
        val sessionId = tunnelCoordinator.state.activeSession
            ?.takeIf { it.status == TunnelStatus.ACTIVE }
            ?.id
            ?: return
        handler.postDelayed({
            if (!canContinueDirectedNavigation(sessionId)) return@postDelayed
            if (activeRootPackage() != task.app.packageName) {
                if (!launchSupportedApp(task.app)) {
                    tunnelCoordinator.reevaluateCurrentSurface(System.currentTimeMillis())
                    updateTunnelUi()
                    return@postDelayed
                }
                awaitTaskAppThenNavigate(task, sessionId, NOTIFICATION_ACTION_MAX_ATTEMPTS)
                return@postDelayed
            }
            navigateToTaskDestinationNow(task)
        }, OVERLAY_DISMISS_SETTLE_MS)
    }

    private fun awaitTaskAppThenNavigate(task: TunnelTask, sessionId: String, attemptsRemaining: Int) {
        if (!canContinueDirectedNavigation(sessionId)) return
        if (activeRootPackage() == task.app.packageName) {
            navigateToTaskDestinationNow(task)
            return
        }
        if (attemptsRemaining <= 0) {
            tunnelCoordinator.reevaluateCurrentSurface(System.currentTimeMillis())
            updateTunnelUi()
            return
        }
        handler.postDelayed(
            { awaitTaskAppThenNavigate(task, sessionId, attemptsRemaining - 1) },
            NOTIFICATION_ACTION_RETRY_MS,
        )
    }

    private fun canContinueDirectedNavigation(sessionId: String): Boolean {
        if (!protectionEnabled) return false
        val state = tunnelCoordinator.state
        return state.activeSession?.let { it.id == sessionId && it.status == TunnelStatus.ACTIVE } == true &&
            state.prompt == null
    }

    private fun beginDirectedNavigation(task: TunnelTask): Boolean {
        val state = tunnelCoordinator.state
        val session = state.activeSession?.takeIf {
            it.status == TunnelStatus.ACTIVE && it.app == task.app && state.prompt == null
        } ?: return false
        if (activeRootPackage() != task.app.packageName) return false

        directedNavigationGeneration += 1
        directedNavigationOwnerSessionId = session.id
        directedNavigationOwnerPackage = task.app.packageName
        instagramDirectedNavigationInProgress = false
        youTubeDirectedNavigationInProgress = false
        tikTokDirectedNavigationInProgress = false
        return true
    }

    private fun directedNavigationIsCurrent(
        generation: Long,
        sessionId: String,
        app: SupportedApp,
    ): Boolean {
        if (!protectionEnabled || generation != directedNavigationGeneration) return false
        if (directedNavigationOwnerSessionId != sessionId || directedNavigationOwnerPackage != app.packageName) return false
        if (activeRootPackage() != app.packageName) return false
        val state = tunnelCoordinator.state
        return state.activeSession?.let {
            it.id == sessionId && it.status == TunnelStatus.ACTIVE && it.app == app
        } == true && state.prompt == null
    }

    private fun postDirectedNavigationStep(
        app: SupportedApp,
        delayMillis: Long,
        action: () -> Unit,
    ) {
        val generation = directedNavigationGeneration
        val sessionId = directedNavigationOwnerSessionId ?: return
        if (directedNavigationOwnerPackage != app.packageName) return
        handler.postDelayed({
            if (!directedNavigationIsCurrent(generation, sessionId, app)) {
                invalidateDirectedNavigation(generation)
                return@postDelayed
            }
            action()
        }, delayMillis)
    }

    private fun invalidateDirectedNavigation(expectedGeneration: Long? = null) {
        if (expectedGeneration != null && expectedGeneration != directedNavigationGeneration) return
        directedNavigationGeneration += 1
        directedNavigationOwnerSessionId = null
        directedNavigationOwnerPackage = null
        instagramDirectedNavigationInProgress = false
        youTubeDirectedNavigationInProgress = false
        tikTokDirectedNavigationInProgress = false
    }

    private fun completeDirectedNavigation(app: SupportedApp) {
        if (directedNavigationOwnerPackage != app.packageName) return
        invalidateDirectedNavigation()
        handler.removeCallbacks(trailingCapture)
        captureCurrentRoot()
    }

    private fun launchSupportedApp(app: SupportedApp): Boolean = runCatching {
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName) ?: return@runCatching false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        startActivity(launchIntent)
        true
    }.getOrDefault(false)

    private fun navigateToTaskDestinationNow(task: TunnelTask) {
        if (!beginDirectedNavigation(task)) {
            tunnelCoordinator.reevaluateCurrentSurface(System.currentTimeMillis())
            updateTunnelUi()
            return
        }
        if (task == TunnelTask.INSTAGRAM_MESSAGES) {
            instagramDirectedNavigationInProgress = true
            if (navigateToTaskDestination(task)) {
                finishInstagramDirectedNavigationAfterSettle()
            } else {
                failInstagramDirectedNavigation()
            }
            return
        }
        if (task == TunnelTask.TIKTOK_SEARCH_WATCH) {
            navigateToTikTokSearch()
            return
        }
        if (task == TunnelTask.TIKTOK_INBOX) {
            navigateToTikTokInbox()
            return
        }
        if (task == TunnelTask.INSTAGRAM_SEARCH || task == TunnelTask.INSTAGRAM_POST) {
            navigateToInstagramDestination(task)
            return
        }
        if (task == TunnelTask.YOUTUBE_SEARCH_WATCH || task == TunnelTask.YOUTUBE_SUBSCRIPTIONS || task == TunnelTask.YOUTUBE_SHORTS) {
            navigateToYouTubeDestination(task)
            return
        }
        val navigated = navigateToTaskDestination(task)
        if (!navigated) {
            // A directed tunnel must never silently accept the surface it started on.
            invalidateDirectedNavigation()
            tunnelCoordinator.reevaluateCurrentSurface(System.currentTimeMillis())
            updateTunnelUi()
            return
        }
        postDirectedNavigationStep(task.app, DESTINATION_NAVIGATION_SETTLE_MS) {
            completeDirectedNavigation(task.app)
        }
    }

    private fun navigateToInstagramDestination(task: TunnelTask) {
        if (task == TunnelTask.INSTAGRAM_POST) {
            navigateToInstagramCreate()
            return
        }

        instagramDirectedNavigationInProgress = true
        if (navigateToTaskDestination(task)) {
            finishInstagramDirectedNavigationAfterSettle()
            return
        }
        backTowardInstagramMainShell(task, DIRECTED_TAB_MAX_BACK_STEPS)
    }

    /**
     * Instagram moves its Create entry point between bottom navigation and action/profile bars
     * across builds. Do not use blind GLOBAL_ACTION_BACK retries for posting: from a top-level
     * Instagram screen that can background the app. Instead, try Create in-place, route to a
     * known main-shell destination, and retry from there/profile.
     */
    private fun navigateToInstagramCreate() {
        instagramDirectedNavigationInProgress = true
        if (isInstagramCreateFlowVisible()) {
            finishInstagramDirectedNavigationAfterSettle()
            return
        }
        if (clickInstagramCreateAffordance()) {
            finishInstagramDirectedNavigationAfterSettle()
            return
        }

        when {
            clickInstagramHomeAffordance() ->
                postDirectedNavigationStep(SupportedApp.INSTAGRAM, DESTINATION_NAVIGATION_SETTLE_MS) { retryInstagramCreateFromMainShell() }
            openInstagramMainFeed() ->
                postDirectedNavigationStep(SupportedApp.INSTAGRAM, INSTAGRAM_MAIN_FEED_SETTLE_MS) { retryInstagramCreateFromMainShell() }
            else -> failInstagramDirectedNavigation()
        }
    }

    private fun retryInstagramCreateFromMainShell() {
        if (isInstagramCreateFlowVisible() || clickInstagramCreateAffordance()) {
            finishInstagramDirectedNavigationAfterSettle()
            return
        }
        if (!clickInstagramProfileAffordance()) {
            failInstagramDirectedNavigation()
            return
        }
        postDirectedNavigationStep(SupportedApp.INSTAGRAM, DESTINATION_NAVIGATION_SETTLE_MS) {
            if (isInstagramCreateFlowVisible() || clickInstagramCreateAffordance()) {
                finishInstagramDirectedNavigationAfterSettle()
            } else {
                failInstagramDirectedNavigation()
            }
        }
    }

    private fun backTowardInstagramMainShell(task: TunnelTask, backStepsRemaining: Int) {
        if (backStepsRemaining <= 0 || !performGlobalAction(GLOBAL_ACTION_BACK)) {
            failInstagramDirectedNavigation()
            return
        }
        postDirectedNavigationStep(SupportedApp.INSTAGRAM, DESTINATION_NAVIGATION_SETTLE_MS) {
            if (navigateToTaskDestination(task)) {
                finishInstagramDirectedNavigationAfterSettle()
            } else {
                backTowardInstagramMainShell(task, backStepsRemaining - 1)
            }
        }
    }

    private fun finishInstagramDirectedNavigationAfterSettle() {
        postDirectedNavigationStep(SupportedApp.INSTAGRAM, DESTINATION_NAVIGATION_SETTLE_MS) {
            completeDirectedNavigation(SupportedApp.INSTAGRAM)
        }
    }

    private fun failInstagramDirectedNavigation() {
        completeDirectedNavigation(SupportedApp.INSTAGRAM)
    }

    private fun clickInstagramSearchAffordance(): Boolean = clickAppAffordance(
        packageName = InstagramSurfaceDetector.INSTAGRAM_PACKAGE,
        resourceIds = listOf("search_tab"),
        // Do not match the generic label "Search": Instagram DMs expose their own search box,
        // which can otherwise steal this action. The global Explore tab has a stable search_tab ID.
        contentDescriptions = setOf("Explore"),
        textLabels = setOf("Explore"),
    )

    private fun clickInstagramCreateAffordance(): Boolean = clickAppAffordance(
        packageName = InstagramSurfaceDetector.INSTAGRAM_PACKAGE,
        resourceIds = listOf(
            "creation_tab",
            "create_tab",
            "creation_button",
            "create_button",
            "new_post_button",
            "action_bar_create_button",
            "profile_action_bar_create_button",
            "profile_header_create_button",
        ),
        contentDescriptions = setOf("Create", "New post", "Create post", "Create new post"),
        textLabels = setOf("Create", "New post", "Create post", "Create new post"),
    )

    private fun clickInstagramHomeAffordance(): Boolean = clickAppAffordance(
        packageName = InstagramSurfaceDetector.INSTAGRAM_PACKAGE,
        resourceIds = listOf("feed_tab"),
        contentDescriptions = setOf("Home"),
        textLabels = setOf("Home"),
    )

    private fun clickInstagramProfileAffordance(): Boolean = clickAppAffordance(
        packageName = InstagramSurfaceDetector.INSTAGRAM_PACKAGE,
        resourceIds = listOf("profile_tab"),
        contentDescriptions = setOf("Profile"),
        textLabels = setOf("Profile"),
    )

    private fun isInstagramCreateFlowVisible(): Boolean {
        val root = try { rootInActiveWindow } catch (_: RuntimeException) { null } ?: return false
        val rootPackage = try { root.packageName?.toString() } catch (_: RuntimeException) { null }
        if (rootPackage != InstagramSurfaceDetector.INSTAGRAM_PACKAGE) {
            recycleNode(root)
            return false
        }
        val createFlowIds = setOf(
            "gallery_picker_grid_item_container",
            "gallery_picker_container",
            "media_picker_container",
            "creation_root",
            "creation_main_container",
        )
        val createFlowLabels = setOf("New post", "Create new post")
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        return try {
            stack.addLast(root)
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                val ownsNode = node !== root
                try {
                    val visible = try { node.isVisibleToUser } catch (_: RuntimeException) { false }
                    val resourceId = try { node.viewIdResourceName?.substringAfterLast('/') } catch (_: RuntimeException) { null }
                    val label = try { node.text?.toString()?.trim() } catch (_: RuntimeException) { null }
                    val description = try { node.contentDescription?.toString()?.trim() } catch (_: RuntimeException) { null }
                    if (visible && (
                            (resourceId != null && resourceId in createFlowIds) ||
                                (label != null && label in createFlowLabels) ||
                                (description != null && description in createFlowLabels)
                            )) {
                        while (stack.isNotEmpty()) {
                            val pending = stack.removeLast()
                            if (pending !== root) recycleNode(pending)
                        }
                        return true
                    }
                    for (index in node.childCount - 1 downTo 0) node.getChild(index)?.let(stack::addLast)
                } finally {
                    if (ownsNode) recycleNode(node)
                }
            }
            false
        } finally {
            while (stack.isNotEmpty()) {
                val pending = stack.removeLast()
                if (pending !== root) recycleNode(pending)
            }
            recycleNode(root)
        }
    }

    private fun openInstagramMainFeed(): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/_n/mainfeed/")).apply {
            setPackage(InstagramSurfaceDetector.INSTAGRAM_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        true
    }.getOrDefault(false)

    private fun navigateToYouTubeDestination(task: TunnelTask) {
        youTubeDirectedNavigationInProgress = true

        // Subscriptions needs stronger destination semantics than the other YouTube tabs.
        // YouTube keeps the source bottom tab selected while a nested Search/Video screen is
        // open, so clicking an already-selected Subscriptions tab can return ACTION_CLICK=true
        // without leaving the video at all. Only consider this route complete once the detector
        // confirms that the actual Subscriptions feed is visible.
        if (task == TunnelTask.YOUTUBE_SUBSCRIPTIONS) {
            // YouTube keeps the originating bottom tab selected under nested watch/search screens.
            // Do not infer a route from that selected tab. Prefer YouTube's own exported
            // "open subscriptions" destination, which asks the app to open the feed root directly.
            if (openYouTubeSubscriptionsDestination()) {
                youTubeSearchContextActive = false
                postDirectedNavigationStep(SupportedApp.YOUTUBE, YOUTUBE_SUBSCRIPTIONS_DESTINATION_SETTLE_MS) {
                    completeDirectedNavigation(SupportedApp.YOUTUBE)
                }
            } else {
                navigateToYouTubeSubscriptionsRoot(DIRECTED_TAB_MAX_BACK_STEPS)
            }
            return
        }

        if (navigateToTaskDestination(task)) {
            finishYouTubeDirectedNavigationAfterSettle()
            return
        }

        // Never blindly press Back from YouTube's top-level shell. If a tab affordance is not
        // exposed there, GLOBAL_ACTION_BACK can background/close YouTube. Only unwind surfaces
        // that the detector positively identifies as nested navigation.
        if (currentYouTubeSurfaceAllowsBackNavigation()) {
            backTowardYouTubeMainShell(task, DIRECTED_TAB_MAX_BACK_STEPS)
        } else {
            failYouTubeDirectedNavigation()
        }
    }

    private fun openYouTubeSubscriptionsDestination(): Boolean {
        val shortcutIntent = Intent(YOUTUBE_OPEN_SUBSCRIPTIONS_ACTION).apply {
            setPackage(YouTubeSurfaceDetector.YOUTUBE_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { startActivity(shortcutIntent); true }.getOrDefault(false)) return true

        // Older/variant YouTube builds may not expose the shortcut action. The canonical web
        // subscriptions route is a secondary explicit destination before falling back to UI taps.
        val feedIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(YOUTUBE_SUBSCRIPTIONS_FEED_URI),
        ).apply {
            setPackage(YouTubeSurfaceDetector.YOUTUBE_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { startActivity(feedIntent); true }.getOrDefault(false)
    }

    private fun navigateToYouTubeSubscriptionsRoot(backStepsRemaining: Int) {
        // Refresh first: the cached detection may still describe the surface that was visible
        // before the Purpose Gate / notification action was dismissed. captureCurrentRoot() is
        // safe here because directed navigation suppresses coordinator policy while still
        // updating currentYouTubeDetection.
        captureCurrentRoot()
        when (AccessibilityRuntime.state.value.currentYouTubeDetection?.detection?.surface) {
            YouTubeSurface.YOUTUBE_SUBSCRIPTIONS -> {
                // A watch/search layer can still be on top while Subscriptions remains selected.
                // Only accept this as the feed root when there is no nested Back/up chrome.
                if (!hasYouTubeBackNavigationChrome()) {
                    finishYouTubeDirectedNavigationAfterSettle()
                    return
                }
                if (backStepsRemaining <= 0 || !performGlobalAction(GLOBAL_ACTION_BACK)) {
                    failYouTubeDirectedNavigation()
                    return
                }
                postDirectedNavigationStep(SupportedApp.YOUTUBE, DESTINATION_NAVIGATION_SETTLE_MS) {
                    navigateToYouTubeSubscriptionsRoot(backStepsRemaining - 1)
                }
                return
            }
            YouTubeSurface.YOUTUBE_SEARCH,
            YouTubeSurface.YOUTUBE_VIDEO,
            -> {
                if (backStepsRemaining <= 0 || !performGlobalAction(GLOBAL_ACTION_BACK)) {
                    failYouTubeDirectedNavigation()
                    return
                }
                postDirectedNavigationStep(SupportedApp.YOUTUBE, DESTINATION_NAVIGATION_SETTLE_MS) {
                    navigateToYouTubeSubscriptionsRoot(backStepsRemaining - 1)
                }
                return
            }
            else -> Unit
        }

        // We are on a top-level/other YouTube surface. Click Subscriptions, but do not trust the
        // click result as proof of navigation. Verify the resulting content surface after settle.
        if (!clickYouTubeSubscriptionsAffordance()) {
            failYouTubeDirectedNavigation()
            return
        }
        postDirectedNavigationStep(SupportedApp.YOUTUBE, DESTINATION_NAVIGATION_SETTLE_MS) {
            captureCurrentRoot()
            when (AccessibilityRuntime.state.value.currentYouTubeDetection?.detection?.surface) {
                YouTubeSurface.YOUTUBE_SUBSCRIPTIONS -> finishYouTubeDirectedNavigationAfterSettle()
                YouTubeSurface.YOUTUBE_SEARCH,
                YouTubeSurface.YOUTUBE_VIDEO,
                -> navigateToYouTubeSubscriptionsRoot(backStepsRemaining)
                else -> failYouTubeDirectedNavigation()
            }
        }
    }

    private fun currentYouTubeSurfaceAllowsBackNavigation(): Boolean =
        when (AccessibilityRuntime.state.value.currentYouTubeDetection?.detection?.surface) {
            YouTubeSurface.YOUTUBE_SEARCH,
            YouTubeSurface.YOUTUBE_VIDEO,
            -> true
            else -> false
        }

    private fun backTowardYouTubeMainShell(task: TunnelTask, backStepsRemaining: Int) {
        if (backStepsRemaining <= 0 || !performGlobalAction(GLOBAL_ACTION_BACK)) {
            failYouTubeDirectedNavigation()
            return
        }
        postDirectedNavigationStep(SupportedApp.YOUTUBE, DESTINATION_NAVIGATION_SETTLE_MS) {
            if (navigateToTaskDestination(task)) {
                finishYouTubeDirectedNavigationAfterSettle()
            } else if (hasYouTubeTopLevelChrome()) {
                // We have already reached YouTube's main shell. Do not issue another Back merely
                // because the requested tab is not clickable in this app build.
                failYouTubeDirectedNavigation()
            } else {
                backTowardYouTubeMainShell(task, backStepsRemaining - 1)
            }
        }
    }

    private fun hasYouTubeBackNavigationChrome(): Boolean {
        val root = try { rootInActiveWindow } catch (_: RuntimeException) { null } ?: return false
        val rootPackage = try { root.packageName?.toString() } catch (_: RuntimeException) { null }
        if (rootPackage != YouTubeSurfaceDetector.YOUTUBE_PACKAGE) {
            recycleNode(root)
            return false
        }
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        return try {
            stack.addLast(root)
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                val ownsNode = node !== root
                try {
                    if (inferWhitelistedChromeRole(YouTubeSurfaceDetector.YOUTUBE_PACKAGE, node) ==
                        UiChromeRole.YOUTUBE_BACK
                    ) {
                        while (stack.isNotEmpty()) {
                            val pending = stack.removeLast()
                            if (pending !== root) recycleNode(pending)
                        }
                        return true
                    }
                    for (index in node.childCount - 1 downTo 0) node.getChild(index)?.let(stack::addLast)
                } finally {
                    if (ownsNode) recycleNode(node)
                }
            }
            false
        } finally {
            while (stack.isNotEmpty()) {
                val pending = stack.removeLast()
                if (pending !== root) recycleNode(pending)
            }
            recycleNode(root)
        }
    }

    private fun hasYouTubeTopLevelChrome(): Boolean {
        val root = try { rootInActiveWindow } catch (_: RuntimeException) { null } ?: return false
        val rootPackage = try { root.packageName?.toString() } catch (_: RuntimeException) { null }
        if (rootPackage != YouTubeSurfaceDetector.YOUTUBE_PACKAGE) {
            recycleNode(root)
            return false
        }
        val knownLabels = setOf("Home", "Shorts", "Subscriptions", "You")
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        return try {
            stack.addLast(root)
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                val ownsNode = node !== root
                try {
                    val visible = try { node.isVisibleToUser } catch (_: RuntimeException) { false }
                    val labels = buildList {
                        try { node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add) } catch (_: RuntimeException) { }
                        try { node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add) } catch (_: RuntimeException) { }
                    }
                    if (visible && labels.any { label -> knownLabels.any { label == it || label.startsWith("$it,") } }) {
                        while (stack.isNotEmpty()) {
                            val pending = stack.removeLast()
                            if (pending !== root) recycleNode(pending)
                        }
                        return true
                    }
                    for (index in node.childCount - 1 downTo 0) node.getChild(index)?.let(stack::addLast)
                } finally {
                    if (ownsNode) recycleNode(node)
                }
            }
            false
        } finally {
            while (stack.isNotEmpty()) {
                val pending = stack.removeLast()
                if (pending !== root) recycleNode(pending)
            }
            recycleNode(root)
        }
    }

    private fun finishYouTubeDirectedNavigationAfterSettle() {
        postDirectedNavigationStep(SupportedApp.YOUTUBE, DESTINATION_NAVIGATION_SETTLE_MS) {
            completeDirectedNavigation(SupportedApp.YOUTUBE)
        }
    }

    private fun failYouTubeDirectedNavigation() {
        completeDirectedNavigation(SupportedApp.YOUTUBE)
    }

    private fun clickYouTubeSearchAffordance(): Boolean {
        val clicked = clickAppAffordance(
            packageName = YouTubeSurfaceDetector.YOUTUBE_PACKAGE,
            contentDescriptions = setOf("Search"),
            textLabels = setOf("Search"),
        )
        if (clicked) youTubeSearchContextActive = true
        return clicked
    }

    private fun clickYouTubeSubscriptionsAffordance(): Boolean =
        clickYouTubeChromeAffordance(UiChromeRole.YOUTUBE_SUBSCRIPTIONS)

    private fun clickYouTubeShortsAffordance(): Boolean =
        clickYouTubeChromeAffordance(UiChromeRole.YOUTUBE_SHORTS)

    private fun clickYouTubeChromeAffordance(role: UiChromeRole): Boolean {
        val root = try { rootInActiveWindow } catch (_: RuntimeException) { null } ?: return false
        val rootPackage = try { root.packageName?.toString() } catch (_: RuntimeException) { null }
        if (rootPackage != YouTubeSurfaceDetector.YOUTUBE_PACKAGE) {
            recycleNode(root)
            return false
        }
        val wantedLabel = when (role) {
            UiChromeRole.YOUTUBE_HOME -> "Home"
            UiChromeRole.YOUTUBE_SHORTS -> "Shorts"
            UiChromeRole.YOUTUBE_SUBSCRIPTIONS -> "Subscriptions"
            UiChromeRole.YOUTUBE_YOU -> "You"
            UiChromeRole.YOUTUBE_BACK -> return false
        }
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        try {
            // YouTube's bottom navigation is not guaranteed to expose each tab as a Button class.
            // Route using the exact, whitelisted chrome label and click its clickable ancestor.
            // Prefix-with-comma handles descriptions such as "Subscriptions, new activity" while
            // still avoiding arbitrary content labels.
            stack.addLast(root)
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                val ownsNode = node !== root
                try {
                    val usable = try { node.isVisibleToUser && node.isEnabled } catch (_: RuntimeException) { false }
                    val labels = buildList {
                        try { node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add) } catch (_: RuntimeException) { }
                        try { node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add) } catch (_: RuntimeException) { }
                    }
                    val matchesRole = labels.any { it == wantedLabel || it.startsWith("$wantedLabel,") }
                    if (usable && matchesRole && performClickOnNodeOrParent(node)) {
                        while (stack.isNotEmpty()) {
                            val pending = stack.removeLast()
                            if (pending !== root) recycleNode(pending)
                        }
                        return true
                    }
                    for (index in node.childCount - 1 downTo 0) node.getChild(index)?.let(stack::addLast)
                } finally {
                    if (ownsNode) recycleNode(node)
                }
            }
            return false
        } finally {
            while (stack.isNotEmpty()) {
                val pending = stack.removeLast()
                if (pending !== root) recycleNode(pending)
            }
            recycleNode(root)
        }
    }

    private fun navigateToTikTokSearch() {
        tikTokDirectedNavigationInProgress = true
        val currentSurface = tunnelCoordinator.state.currentSurface
        if (currentSurface == DetectedSurface.TIKTOK_SEARCH) {
            finishTikTokDirectedNavigationAfterSettle()
            return
        }
        if (currentSurface == DetectedSurface.TIKTOK_FEED && clickTikTokSearchAffordance()) {
            finishTikTokDirectedNavigationAfterSettle()
            return
        }

        // TikTok 47.x can expose unrelated Search controls inside Inbox/Profile. Avoid
        // clicking those. Route through the stable For You tab (`omq`) first, then click
        // the global Search affordance once that screen has settled.
        val routedToFeed = clickAppAffordance(
            packageName = TikTokSurfaceDetector.TIKTOK_PACKAGE,
            resourceIds = listOf("omq"),
            contentDescriptions = setOf("For You", "Home"),
            textLabels = setOf("For You", "Home"),
        )
        if (!routedToFeed) {
            invalidateDirectedNavigation()
            tunnelCoordinator.reevaluateCurrentSurface(System.currentTimeMillis())
            updateTunnelUi()
            scheduleCapture(DESTINATION_NAVIGATION_SETTLE_MS)
            return
        }

        postDirectedNavigationStep(SupportedApp.TIKTOK, TIKTOK_ROUTE_STEP_SETTLE_MS) {
            if (clickTikTokSearchAffordance()) {
                finishTikTokDirectedNavigationAfterSettle()
            } else {
                // Fail visibly on the settled surface instead of pretending navigation worked.
                failTikTokDirectedNavigation()
            }
        }
    }

    private fun clickTikTokSearchAffordance(): Boolean = clickAppAffordance(
        packageName = TikTokSurfaceDetector.TIKTOK_PACKAGE,
        resourceIds = listOf("search", "search_button", "search_icon"),
        contentDescriptions = setOf("Search"),
        textLabels = setOf("Search"),
    )

    private fun navigateToTikTokInbox() {
        tikTokDirectedNavigationInProgress = true
        if (isTikTokInboxSelected()) {
            finishTikTokDirectedNavigationAfterSettle()
            return
        }
        if (clickTikTokInboxAffordance()) {
            postDirectedNavigationStep(SupportedApp.TIKTOK, DESTINATION_NAVIGATION_SETTLE_MS) {
                verifyTikTokInboxOrBack(TIKTOK_INBOX_MAX_BACK_STEPS)
            }
            return
        }
        backTowardTikTokMainShell(TIKTOK_INBOX_MAX_BACK_STEPS)
    }

    private fun verifyTikTokInboxOrBack(backStepsRemaining: Int) {
        if (isTikTokInboxSelected()) {
            finishTikTokDirectedNavigationAfterSettle()
        } else {
            backTowardTikTokMainShell(backStepsRemaining)
        }
    }

    private fun backTowardTikTokMainShell(backStepsRemaining: Int) {
        if (backStepsRemaining <= 0 || !performGlobalAction(GLOBAL_ACTION_BACK)) {
            failTikTokDirectedNavigation()
            return
        }
        postDirectedNavigationStep(SupportedApp.TIKTOK, TIKTOK_ROUTE_STEP_SETTLE_MS) {
            if (isTikTokInboxSelected()) {
                finishTikTokDirectedNavigationAfterSettle()
            } else if (clickTikTokInboxAffordance()) {
                postDirectedNavigationStep(SupportedApp.TIKTOK, DESTINATION_NAVIGATION_SETTLE_MS) {
                    verifyTikTokInboxOrBack(backStepsRemaining - 1)
                }
            } else {
                backTowardTikTokMainShell(backStepsRemaining - 1)
            }
        }
    }

    private fun clickTikTokInboxAffordance(): Boolean = clickAppAffordance(
        packageName = TikTokSurfaceDetector.TIKTOK_PACKAGE,
        resourceIds = listOf("omr"),
        contentDescriptions = setOf("Inbox"),
    )

    private fun isTikTokInboxSelected(): Boolean {
        val root = try { rootInActiveWindow } catch (_: RuntimeException) { null } ?: return false
        val rootPackage = try { root.packageName?.toString() } catch (_: RuntimeException) { null }
        if (rootPackage != TikTokSurfaceDetector.TIKTOK_PACKAGE) {
            recycleNode(root)
            return false
        }
        return try {
            val matches = try {
                root.findAccessibilityNodeInfosByViewId("${TikTokSurfaceDetector.TIKTOK_PACKAGE}:id/omr")
            } catch (_: RuntimeException) {
                emptyList()
            }
            try {
                matches.any { node ->
                    try { node.isVisibleToUser && node.isSelected } catch (_: RuntimeException) { false }
                }
            } finally {
                matches.forEach(::recycleNode)
            }
        } finally {
            recycleNode(root)
        }
    }

    private fun failTikTokDirectedNavigation() {
        completeDirectedNavigation(SupportedApp.TIKTOK)
    }

    private fun finishTikTokDirectedNavigationAfterSettle() {
        postDirectedNavigationStep(SupportedApp.TIKTOK, DESTINATION_NAVIGATION_SETTLE_MS) {
            completeDirectedNavigation(SupportedApp.TIKTOK)
        }
    }

    private fun navigateToTaskDestination(task: TunnelTask): Boolean = when (task) {
        TunnelTask.INSTAGRAM_MESSAGES ->
            clickAppAffordance(
                packageName = InstagramSurfaceDetector.INSTAGRAM_PACKAGE,
                resourceIds = listOf("direct_tab"),
                contentDescriptions = setOf("Messages", "Direct"),
                textLabels = setOf("Messages"),
            ) || openInstagramDirectInbox()
        TunnelTask.INSTAGRAM_SEARCH -> clickInstagramSearchAffordance()
        TunnelTask.INSTAGRAM_POST -> clickInstagramCreateAffordance()
        TunnelTask.YOUTUBE_SEARCH_WATCH -> clickYouTubeSearchAffordance()
        TunnelTask.YOUTUBE_SUBSCRIPTIONS -> clickYouTubeSubscriptionsAffordance()
        TunnelTask.YOUTUBE_SHORTS -> clickYouTubeShortsAffordance()
        TunnelTask.TIKTOK_SEARCH_WATCH -> clickTikTokSearchAffordance()
        TunnelTask.TIKTOK_INBOX -> clickTikTokInboxAffordance()
        TunnelTask.INSTAGRAM_BROWSE,
        TunnelTask.YOUTUBE_BROWSE,
        TunnelTask.TIKTOK_BROWSE,
        -> false
    }

    private fun clickAppAffordance(
        packageName: String,
        resourceIds: List<String> = emptyList(),
        contentDescriptions: Set<String> = emptySet(),
        textLabels: Set<String> = emptySet(),
    ): Boolean {
        val root = try { rootInActiveWindow } catch (_: RuntimeException) { null } ?: return false
        val rootPackage = try { root.packageName?.toString() } catch (_: RuntimeException) { null }
        if (rootPackage != packageName) {
            recycleNode(root)
            return false
        }
        try {
            for (id in resourceIds) {
                val matches = try {
                    root.findAccessibilityNodeInfosByViewId("$packageName:id/$id")
                } catch (_: RuntimeException) {
                    emptyList()
                }
                var clicked = false
                try {
                    for (node in matches) {
                        val usable = try { node.isVisibleToUser && node.isEnabled } catch (_: RuntimeException) { false }
                        if (usable && performClickOnNodeOrParent(node)) {
                            clicked = true
                            break
                        }
                    }
                } finally {
                    matches.forEach(::recycleNode)
                }
                if (clicked) return true
            }

            if (contentDescriptions.isEmpty() && textLabels.isEmpty()) return false
            val wantedDescriptions = contentDescriptions.map { it.lowercase(java.util.Locale.ROOT) }.toSet()
            val wantedText = textLabels.map { it.lowercase(java.util.Locale.ROOT) }.toSet()
            val stack = ArrayDeque<AccessibilityNodeInfo>()
            for (index in root.childCount - 1 downTo 0) {
                root.getChild(index)?.let(stack::addLast)
            }
            var clicked = false
            while (stack.isNotEmpty() && !clicked) {
                val node = stack.removeLast()
                try {
                    val description = try { node.contentDescription?.toString()?.trim() } catch (_: RuntimeException) { null }
                    val text = try { node.text?.toString()?.trim() } catch (_: RuntimeException) { null }
                    val isMatch = description?.lowercase(java.util.Locale.ROOT) in wantedDescriptions ||
                        text?.lowercase(java.util.Locale.ROOT) in wantedText
                    val usable = try { node.isVisibleToUser && node.isEnabled } catch (_: RuntimeException) { false }
                    if (isMatch && usable && performClickOnNodeOrParent(node)) {
                        clicked = true
                    } else {
                        for (index in node.childCount - 1 downTo 0) {
                            node.getChild(index)?.let(stack::addLast)
                        }
                    }
                } finally {
                    recycleNode(node)
                }
            }
            while (stack.isNotEmpty()) recycleNode(stack.removeLast())
            return clicked
        } finally {
            recycleNode(root)
        }
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var ownsCurrent = false
        repeat(MAX_CLICK_ANCESTOR_DEPTH) {
            val candidate = current ?: return false
            val clickable = try { candidate.isClickable && candidate.isEnabled } catch (_: RuntimeException) { false }
            if (clickable) {
                val clicked = try { candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK) } catch (_: RuntimeException) { false }
                if (ownsCurrent) recycleNode(candidate)
                return clicked
            }
            val parent = try { candidate.parent } catch (_: RuntimeException) { null }
            if (ownsCurrent) recycleNode(candidate)
            current = parent
            ownsCurrent = parent != null
        }
        current?.takeIf { ownsCurrent }?.let(::recycleNode)
        return false
    }

    private fun openInstagramDirectInbox(): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("instagram://direct-inbox")).apply {
            setPackage(InstagramSurfaceDetector.INSTAGRAM_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        true
    }.getOrDefault(false)

    private fun baseTunnelOverlay(compact: Boolean = false) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(10), dp(20), dp(if (compact) 16 else 20) + navigationBarInset())
        background = GradientDrawable().apply {
            setColor(COLOR_SHEET)
            cornerRadii = floatArrayOf(dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), 0f, 0f, 0f, 0f)
        }
        elevation = dp(12).toFloat()
        addView(View(context).apply {
            background = GradientDrawable().apply { setColor(COLOR_HANDLE); cornerRadius = dp(2).toFloat() }
        }, LinearLayout.LayoutParams(dp(36), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(if (compact) 12 else 18)
        })
    }

    private fun overlayText(value: String, heading: Boolean = false, secondary: Boolean = false) = TextView(this).apply {
        text = value
        setTextColor(if (secondary) COLOR_SECONDARY_TEXT else COLOR_PRIMARY_TEXT)
        textSize = if (heading) 23f else 15f
        if (heading) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setLineSpacing(0f, 1.08f)
        setPadding(0, 0, 0, if (heading) dp(10) else dp(18))
    }

    private fun overlayPrimaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(COLOR_ON_PRIMARY)
        minHeight = dp(50)
        background = RippleDrawable(
            ColorStateList.valueOf(COLOR_ON_PRIMARY),
            GradientDrawable().apply {
                setColor(COLOR_PRIMARY)
                cornerRadius = dp(12).toFloat()
            },
            null,
        )
        setOnClickListener { action() }
    }.also { it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(6); bottomMargin = dp(4) } }

    private fun overlayTextButton(label: String, quiet: Boolean = false, action: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 15f
        setTextColor(if (quiet) COLOR_SECONDARY_TEXT else COLOR_PRIMARY)
        minHeight = dp(48)
        isClickable = true
        isFocusable = true
        if (quiet) {
            setBackgroundResource(android.R.drawable.list_selector_background)
        } else {
            background = RippleDrawable(
                ColorStateList.valueOf(COLOR_PRIMARY_RIPPLE),
                GradientDrawable().apply {
                    setColor(COLOR_TONAL_ACTION)
                    cornerRadius = dp(12).toFloat()
                },
                null,
            )
        }
        setOnClickListener { action() }
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
            topMargin = dp(if (quiet) 0 else 3)
            bottomMargin = dp(if (quiet) 0 else 3)
        }
    }

    private fun overlayDelayedTextButton(
        label: String,
        delayMillis: Long,
        action: () -> Unit,
    ): TextView {
        val button = overlayTextButton(label, action = action)
        val unlockAtElapsed = SystemClock.elapsedRealtime() + delayMillis
        button.isEnabled = false
        button.alpha = 0.48f

        val updateCountdown = object : Runnable {
            override fun run() {
                val remainingMillis = unlockAtElapsed - SystemClock.elapsedRealtime()
                if (remainingMillis <= 0L) {
                    button.text = label
                    button.contentDescription = label
                    button.isEnabled = true
                    button.alpha = 1f
                    return
                }
                val remainingSeconds = ((remainingMillis + 999L) / 1_000L).coerceAtLeast(1L)
                button.text = "$label · ${remainingSeconds}s"
                button.contentDescription = "$label. Available in $remainingSeconds seconds."
                handler.postDelayed(this, minOf(remainingMillis, 250L))
            }
        }
        handler.post(updateCountdown)
        return button
    }

    private fun overlayChoiceRow(label: String, action: (View) -> Unit) = TextView(this).apply {
        text = "$label    ›"
        gravity = Gravity.CENTER_VERTICAL
        textSize = 17f
        setTextColor(COLOR_PRIMARY_TEXT)
        setPadding(dp(12), 0, dp(10), 0)
        minHeight = dp(58)
        isClickable = true
        isFocusable = true
        setBackgroundResource(android.R.drawable.list_selector_background)
        setOnClickListener { action(this) }
    }

    private fun purposeGateAppIdentity(app: SupportedApp) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(8))
        addView(appIconView(app.packageName, app.displayName), LinearLayout.LayoutParams(dp(26), dp(26)))
        addView(TextView(context).apply {
            text = app.displayName
            textSize = 14f
            setTextColor(COLOR_SECONDARY_TEXT)
            setPadding(dp(9), 0, 0, 0)
        })
    }

    private fun purposeGateQuestion() = TextView(this).apply {
        text = "What are you here to do?"
        textSize = 22f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(COLOR_PRIMARY_TEXT)
        setLineSpacing(0f, 1.06f)
        setPadding(0, 0, 0, dp(8))
    }

    private fun purposeChoiceRow(
        iconRes: Int,
        title: String,
        subtitle: String,
        action: (View) -> Unit,
    ) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(64)
        setPadding(dp(2), dp(8), dp(4), dp(8))
        isClickable = true
        isFocusable = true
        setBackgroundResource(android.R.drawable.list_selector_background)
        contentDescription = "$title. $subtitle"
        addView(ImageView(context).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(COLOR_SECONDARY_TEXT)
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(14) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = title
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(COLOR_PRIMARY_TEXT)
            })
            addView(TextView(context).apply {
                text = subtitle
                textSize = 13f
                setTextColor(COLOR_SECONDARY_TEXT)
                setPadding(0, dp(2), 0, 0)
                maxLines = 1
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        setOnClickListener { action(this) }
    }

    private fun purposeTimeLimitRow(valueView: TextView, action: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(48)
        setPadding(dp(2), dp(4), dp(4), dp(2))
        isClickable = true
        isFocusable = true
        setBackgroundResource(android.R.drawable.list_selector_background)
        addView(TextView(context).apply {
            text = "Time limit"
            textSize = 14f
            setTextColor(COLOR_SECONDARY_TEXT)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(valueView)
        setOnClickListener { action() }
    }

    private fun purposeDismissButton(label: String = "Not now", action: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 14f
        setTextColor(COLOR_SECONDARY_TEXT)
        minHeight = dp(48)
        isClickable = true
        isFocusable = true
        setBackgroundResource(android.R.drawable.list_selector_background)
        setOnClickListener { action() }
    }

    private fun purposeDurationSelector(): PurposeDurationSelector = PurposeDurationSelector()

    private inner class PurposeDurationSelector : LinearLayout(this@TaskTunnelAccessibilityService) {
        var onDurationSelected: ((DurationChoice) -> Unit)? = null
        private val optionViews = mutableListOf<TextView>()
        private var selectedIndex = 0

        init {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(0, dp(2), 0, dp(4))
            DURATION_CHOICES.forEachIndexed { index, choice ->
                val option = TextView(context).apply {
                    text = choice.label
                    gravity = Gravity.CENTER
                    textSize = 13f
                    minHeight = dp(48)
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Time limit ${choice.label}"
                    setOnClickListener {
                        selectedIndex = index
                        updateSelection()
                        onDurationSelected?.invoke(choice)
                    }
                }
                optionViews += option
                addView(option, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    marginStart = if (index == 0) 0 else dp(6)
                })
            }
            updateSelection()
        }

        private fun updateSelection() {
            optionViews.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                option.setTextColor(if (selected) COLOR_PRIMARY else COLOR_SECONDARY_TEXT)
                option.background = RippleDrawable(
                    ColorStateList.valueOf(COLOR_PRIMARY_RIPPLE),
                    GradientDrawable().apply {
                        setColor(if (selected) COLOR_DURATION_SELECTED else COLOR_SHEET)
                        setStroke(dp(1), if (selected) COLOR_PRIMARY else COLOR_DIVIDER)
                        cornerRadius = dp(10).toFloat()
                    },
                    null,
                )
            }
        }
    }

    private fun overlayDivider() = View(this).apply { setBackgroundColor(COLOR_DIVIDER) }.also {
        it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(12); marginEnd = dp(12) }
    }

    private fun overlayAppIdentity(app: SupportedApp) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(8))
        addView(appIconView(app.packageName, app.displayName), LinearLayout.LayoutParams(dp(26), dp(26)))
        addView(TextView(context).apply {
            text = app.displayName
            textSize = 14f
            setTextColor(COLOR_SECONDARY_TEXT)
            setPadding(dp(9), 0, 0, 0)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun overlayAppSequence(packages: List<String>, labels: List<String>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(14))
        packages.zip(labels).take(3).forEachIndexed { index, (packageName, label) ->
            if (index > 0) {
                addView(TextView(context).apply {
                    text = "→"
                    gravity = Gravity.CENTER
                    textSize = 16f
                    setTextColor(COLOR_SECONDARY_TEXT)
                    contentDescription = null
                }, LinearLayout.LayoutParams(dp(20), dp(28)))
            }
            addView(appIconView(packageName, label), LinearLayout.LayoutParams(dp(28), dp(28)))
        }
    }

    private fun animateOverlayEntrance(layout: View) {
        layout.alpha = 0f
        layout.translationY = dp(28).toFloat()
        layout.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    private fun appIconView(packageName: String, displayName: String): View = runCatching {
        ImageView(this).apply {
            setImageDrawable(packageManager.getApplicationIcon(packageName))
            contentDescription = null
        }
    }.getOrElse {
        TextView(this).apply {
            text = displayName.take(1)
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(COLOR_PRIMARY_TEXT)
            background = GradientDrawable().apply { setColor(fallbackAppColor(packageName)); cornerRadius = dp(7).toFloat() }
        }
    }

    private fun animatePurposeGateExit() {
        val view = tunnelOverlayView ?: return
        val windowManager = getSystemService(WindowManager::class.java)
        val params = view.layoutParams as? WindowManager.LayoutParams

        // Detach the logical overlay immediately so a newly arriving prompt is free to render.
        // The old sheet remains only as a short, non-interactive visual exit.
        tunnelOverlayView = null
        shownTunnelPrompt = null
        exitingTunnelOverlayView?.let { stale ->
            stale.animate().cancel()
            try { windowManager.removeView(stale) } catch (_: RuntimeException) { }
        }
        exitingTunnelOverlayView = view
        setOverlayInteractionEnabled(view, false)

        tunnelOverlayDimAnimator?.cancel()
        if (params != null) {
            val startDim = params.dimAmount
            tunnelOverlayDimAnimator = ValueAnimator.ofFloat(startDim, 0f).apply {
                duration = PURPOSE_GATE_EXIT_DURATION_MS
                addUpdateListener { animator ->
                    if (exitingTunnelOverlayView !== view) return@addUpdateListener
                    params.dimAmount = animator.animatedValue as Float
                    try { windowManager.updateViewLayout(view, params) } catch (_: RuntimeException) { }
                }
                start()
            }
        }

        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .translationY(dp(18).toFloat())
            .setDuration(PURPOSE_GATE_EXIT_DURATION_MS)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                if (exitingTunnelOverlayView !== view) return@withEndAction
                try { windowManager.removeView(view) } catch (_: RuntimeException) { }
                exitingTunnelOverlayView = null
                tunnelOverlayDimAnimator?.cancel()
                tunnelOverlayDimAnimator = null
                surfaceUsageTracker.setOverlayVisible(false, System.currentTimeMillis())
            }
            .start()
    }

    private fun setOverlayInteractionEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                setOverlayInteractionEnabled(view.getChildAt(index), enabled)
            }
        }
    }

    private fun removeTunnelOverlay() {
        val windowManager = getSystemService(WindowManager::class.java)
        val current = tunnelOverlayView
        val exiting = exitingTunnelOverlayView
        val hadOverlay = current != null || exiting != null

        current?.animate()?.cancel()
        exiting?.animate()?.cancel()
        tunnelOverlayDimAnimator?.cancel()
        tunnelOverlayDimAnimator = null

        current?.let { view ->
            try { windowManager.removeView(view) } catch (_: RuntimeException) { }
        }
        if (exiting != null && exiting !== current) {
            try { windowManager.removeView(exiting) } catch (_: RuntimeException) { }
        }
        tunnelOverlayView = null
        exitingTunnelOverlayView = null
        shownTunnelPrompt = null
        if (hadOverlay) surfaceUsageTracker.setOverlayVisible(false, System.currentTimeMillis())
    }

    private fun dismissVisibleDriftOverlay() {
        if (driftOverlayView == null) return
        driftCoordinator.dismissShownCheckIn(System.currentTimeMillis())
        removeDriftOverlay()
    }

    private fun removeDriftOverlay() {
        val hadOverlay = driftOverlayView != null
        driftOverlayView?.let { view ->
            try { getSystemService(WindowManager::class.java).removeView(view) } catch (_: RuntimeException) { }
        }
        driftOverlayView = null
        if (hadOverlay) surfaceUsageTracker.setOverlayVisible(false, System.currentTimeMillis())
    }

    private fun removeVisualQaOverlay() {
        visualQaOverlayView?.let { view ->
            try { getSystemService(WindowManager::class.java).removeView(view) } catch (_: RuntimeException) { }
        }
        visualQaOverlayView = null
    }

    private fun taskReminder(task: TunnelTask): String = when (task) {
        TunnelTask.INSTAGRAM_MESSAGES -> "You came here to reply to messages."
        TunnelTask.INSTAGRAM_SEARCH -> "You came here to look something up."
        TunnelTask.INSTAGRAM_POST -> "You came here to post something."
        TunnelTask.INSTAGRAM_BROWSE -> "You came here to browse intentionally."
        TunnelTask.YOUTUBE_SEARCH_WATCH -> "You came here to search for or watch something."
        TunnelTask.YOUTUBE_SUBSCRIPTIONS -> "You came here to check subscriptions."
        TunnelTask.YOUTUBE_SHORTS -> "You came here to watch Shorts intentionally."
        TunnelTask.YOUTUBE_BROWSE -> "You came here to browse intentionally."
        TunnelTask.TIKTOK_SEARCH_WATCH -> "You came here to search for or watch something."
        TunnelTask.TIKTOK_INBOX -> "You came here to check your Inbox."
        TunnelTask.TIKTOK_BROWSE -> "You came here to browse intentionally."
    }

    private fun returnLabel(task: TunnelTask): String = when (task) {
        TunnelTask.INSTAGRAM_MESSAGES -> "Return to Messages"
        TunnelTask.INSTAGRAM_SEARCH -> "Return to Search"
        TunnelTask.INSTAGRAM_POST -> "Return to Create"
        TunnelTask.YOUTUBE_SEARCH_WATCH -> "Return to Search"
        TunnelTask.YOUTUBE_SUBSCRIPTIONS -> "Return to Subscriptions"
        TunnelTask.YOUTUBE_SHORTS -> "Return to Shorts"
        TunnelTask.INSTAGRAM_BROWSE,
        TunnelTask.YOUTUBE_BROWSE,
        TunnelTask.TIKTOK_BROWSE,
        -> "Return"
        TunnelTask.TIKTOK_SEARCH_WATCH -> "Return to Search"
        TunnelTask.TIKTOK_INBOX -> "Return to Inbox"
    }

    private fun formatRemainingDuration(durationMillis: Long): String {
        val totalMinutes = ((durationMillis + 59_999L) / 60_000L).coerceAtLeast(1L)
        return if (totalMinutes < 60L) "$totalMinutes min" else {
            val hours = totalMinutes / 60L
            val minutes = totalMinutes % 60L
            if (minutes == 0L) "$hours h" else "$hours h $minutes min"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun navigationBarInset(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id != 0) resources.getDimensionPixelSize(id).coerceAtMost(dp(32)) else 0
    }

    private fun fallbackAppColor(packageName: String): Int = when (packageName) {
        "com.instagram.android" -> 0xFFD65A79.toInt()
        "com.google.android.youtube" -> 0xFFE45B55.toInt()
        "com.reddit.frontpage" -> 0xFFFF6B35.toInt()
        else -> COLOR_PRIMARY
    }

    private fun surfaceLabel(surface: DetectedSurface): String = when (surface) {
        DetectedSurface.INSTAGRAM_MESSAGES -> "Messages"
        DetectedSurface.INSTAGRAM_REELS -> "Reels"
        DetectedSurface.INSTAGRAM_EXPLORE -> "Explore"
        DetectedSurface.INSTAGRAM_HOME -> "Home"
        DetectedSurface.INSTAGRAM_PROFILE -> "Profile"
        DetectedSurface.INSTAGRAM_CREATE -> "Create"
        DetectedSurface.YOUTUBE_SEARCH -> "Search"
        DetectedSurface.YOUTUBE_VIDEO -> "Video"
        DetectedSurface.YOUTUBE_SHORTS -> "Shorts"
        DetectedSurface.YOUTUBE_UNSUBSCRIBED_VIDEO -> "Unsubscribed video"
        DetectedSurface.YOUTUBE_UNSUBSCRIBED_SHORTS -> "Unsubscribed Short"
        DetectedSurface.YOUTUBE_HOME -> "Home"
        DetectedSurface.YOUTUBE_SUBSCRIPTIONS -> "Subscriptions"
        DetectedSurface.YOUTUBE_YOU -> "You"
        DetectedSurface.TIKTOK_FEED -> "For You"
        DetectedSurface.TIKTOK_FRIENDS -> "Friends"
        DetectedSurface.TIKTOK_SEARCH -> "Search"
        DetectedSurface.TIKTOK_INBOX -> "Inbox"
        DetectedSurface.TIKTOK_PROFILE -> "Profile"
        DetectedSurface.TIKTOK_OTHER -> "This screen"
        else -> "This screen"
    }

    private fun InstagramSurface.toTunnelSurface(): DetectedSurface = when (this) {
        InstagramSurface.INSTAGRAM_MESSAGES -> DetectedSurface.INSTAGRAM_MESSAGES
        InstagramSurface.INSTAGRAM_EXPLORE -> DetectedSurface.INSTAGRAM_EXPLORE
        InstagramSurface.INSTAGRAM_REELS -> DetectedSurface.INSTAGRAM_REELS
        InstagramSurface.INSTAGRAM_HOME -> DetectedSurface.INSTAGRAM_HOME
        InstagramSurface.INSTAGRAM_PROFILE -> DetectedSurface.INSTAGRAM_PROFILE
        InstagramSurface.INSTAGRAM_CREATE -> DetectedSurface.INSTAGRAM_CREATE
        InstagramSurface.INSTAGRAM_OTHER -> DetectedSurface.INSTAGRAM_OTHER
        InstagramSurface.UNKNOWN -> DetectedSurface.UNKNOWN
    }

    private fun YouTubeSurface.toTunnelSurface(): DetectedSurface = when (this) {
        YouTubeSurface.YOUTUBE_SEARCH -> DetectedSurface.YOUTUBE_SEARCH
        YouTubeSurface.YOUTUBE_VIDEO -> DetectedSurface.YOUTUBE_VIDEO
        YouTubeSurface.YOUTUBE_SHORTS -> DetectedSurface.YOUTUBE_SHORTS
        YouTubeSurface.YOUTUBE_HOME -> DetectedSurface.YOUTUBE_HOME
        YouTubeSurface.YOUTUBE_SUBSCRIPTIONS -> DetectedSurface.YOUTUBE_SUBSCRIPTIONS
        YouTubeSurface.YOUTUBE_YOU -> DetectedSurface.YOUTUBE_YOU
        YouTubeSurface.YOUTUBE_OTHER -> DetectedSurface.YOUTUBE_OTHER
        YouTubeSurface.UNKNOWN -> DetectedSurface.UNKNOWN
    }

    private fun com.example.tasktunnel.detector.YouTubeDetection.toPolicyTunnelSurface(
        task: TunnelTask?,
    ): DetectedSurface {
        val base = surface.toTunnelSurface()
        if (task != TunnelTask.YOUTUBE_SUBSCRIPTIONS ||
            creatorSubscriptionState != YouTubeSubscriptionState.NOT_SUBSCRIBED
        ) return base

        return when (surface) {
            YouTubeSurface.YOUTUBE_VIDEO -> DetectedSurface.YOUTUBE_UNSUBSCRIBED_VIDEO
            YouTubeSurface.YOUTUBE_SHORTS -> DetectedSurface.YOUTUBE_UNSUBSCRIBED_SHORTS
            else -> base
        }
    }

    private fun TikTokSurface.toTunnelSurface(): DetectedSurface = when (this) {
        TikTokSurface.TIKTOK_FEED -> DetectedSurface.TIKTOK_FEED
        TikTokSurface.TIKTOK_FRIENDS -> DetectedSurface.TIKTOK_FRIENDS
        TikTokSurface.TIKTOK_SEARCH -> DetectedSurface.TIKTOK_SEARCH
        TikTokSurface.TIKTOK_INBOX -> DetectedSurface.TIKTOK_INBOX
        TikTokSurface.TIKTOK_PROFILE -> DetectedSurface.TIKTOK_PROFILE
        TikTokSurface.TIKTOK_OTHER -> DetectedSurface.TIKTOK_OTHER
        TikTokSurface.UNKNOWN -> DetectedSurface.UNKNOWN
    }

    private fun eventTypeName(type: Int) = when (type) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "Window state changed"
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "Windows changed"
        else -> "Event $type"
    }

    companion object {
        private const val PURPOSE_GATE_EXIT_DURATION_MS = 140L
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        internal var current: TaskTunnelAccessibilityService? = null
        private val TARGET_PACKAGES = setOf(
            InstagramSurfaceDetector.INSTAGRAM_PACKAGE,
            YouTubeSurfaceDetector.YOUTUBE_PACKAGE,
            TikTokFingerprint.TIKTOK_PACKAGE,
        )
        private const val YOUTUBE_OPEN_SUBSCRIPTIONS_ACTION =
            "com.google.android.youtube.action.open.subscriptions"
        private const val YOUTUBE_SUBSCRIPTIONS_FEED_URI =
            "https://www.youtube.com/feed/subscriptions"
        private const val MAX_NODES = 200
        private const val MAX_DEPTH = 12
        private const val MAX_HISTORY = 8
        private const val MAX_FIELD_LENGTH = 200
        private const val CAPTURE_THROTTLE_MS = 1_000L
        private const val YOUTUBE_SUBSCRIPTION_PROBE_ATTEMPTS = 2
        private const val YOUTUBE_SUBSCRIPTION_PROBE_SETTLE_MS = 650L
        private const val DEADLINE_RETRY_MS = 1_000L
        private const val OVERLAY_DISMISS_SETTLE_MS = 120L
        private const val DESTINATION_NAVIGATION_SETTLE_MS = 350L
        private const val INSTAGRAM_MAIN_FEED_SETTLE_MS = 550L
        private const val YOUTUBE_SUBSCRIPTIONS_DESTINATION_SETTLE_MS = 700L
        private const val TIKTOK_ROUTE_STEP_SETTLE_MS = 500L
        private const val TIKTOK_INBOX_MAX_BACK_STEPS = 2
        private const val DIRECTED_TAB_MAX_BACK_STEPS = 2
        private const val TIKTOK_CLICK_SETTLE_MS = 250L
        private const val NOTIFICATION_ACTION_RETRY_MS = 120L
        private const val NOTIFICATION_ACTION_SETTLE_MS = 220L
        private const val NOTIFICATION_ACTION_MAX_ATTEMPTS = 12
        private const val MAX_CLICK_ANCESTOR_DEPTH = 4
        private const val OVERLAY_DELAY_MS = 3_000L
        private const val VISUAL_QA_TIMEOUT_MS = 45_000L
        private const val COLOR_SHEET = 0xFF191C1F.toInt()
        private const val COLOR_PRIMARY_TEXT = 0xFFF1F3F4.toInt()
        private const val COLOR_SECONDARY_TEXT = 0xFFA7ADB4.toInt()
        private const val COLOR_DIVIDER = 0xFF2A2E32.toInt()
        private const val COLOR_HANDLE = 0xFF5E646B.toInt()
        private const val COLOR_PRIMARY = 0xFF79AFFF.toInt()
        private const val COLOR_ON_PRIMARY = 0xFF061A32.toInt()
        private const val COLOR_PRIMARY_RIPPLE = 0x3379AFFF
        private const val COLOR_TONAL_ACTION = 0xFF14345F.toInt()
        private const val COLOR_DURATION_SELECTED = COLOR_TONAL_ACTION
        private val DURATION_CHOICES = listOf(
            DurationChoice("No limit", null),
            DurationChoice("5 min", 5 * 60_000L),
            DurationChoice("10 min", 10 * 60_000L),
            DurationChoice("20 min", 20 * 60_000L),
        )
    }

    private data class DurationChoice(val label: String, val durationMillis: Long?)
}
