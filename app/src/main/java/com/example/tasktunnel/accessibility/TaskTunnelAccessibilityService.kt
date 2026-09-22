package com.example.tasktunnel.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.res.ColorStateList
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.PowerManager
import android.view.Gravity
import android.view.HapticFeedbackConstants
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
    private val surfaceUsageTracker by lazy {
        SurfaceUsageTracker(
            SurfaceUsageRepository(AttentionDatabase.getInstance(applicationContext).surfaceUsageSegmentDao()),
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO.limitedParallelism(1)),
        )
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            surfaceUsageTracker.setInteractive(intent.action == Intent.ACTION_SCREEN_ON, System.currentTimeMillis())
        }
    }
    private var screenReceiverRegistered = false
    private var testOverlayView: LinearLayout? = null
    private var tunnelOverlayView: LinearLayout? = null
    private var driftOverlayView: LinearLayout? = null
    private var visualQaOverlayView: LinearLayout? = null
    private var shownTunnelPrompt: TunnelPrompt? = null
    private var instagramDirectedNavigationInProgress = false
    private var youTubeDirectedNavigationInProgress = false
    private var tikTokDirectedNavigationInProgress = false
    private var lastCaptureAtElapsed = 0L
    private var lastTargetPackage: String? = null
    private val showOverlay = Runnable { showTestOverlayNow() }
    private val trailingCapture = Runnable { captureCurrentRoot() }
    private val tunnelDeadline = object : Runnable {
        override fun run() {
            val nowMillis = System.currentTimeMillis()
            val packageName = activeRootPackage()
            if (packageName == null) {
                handler.postDelayed(this, DEADLINE_RETRY_MS)
            } else {
                tunnelCoordinator.observeForeground(packageName, nowMillis)
                updateTunnelUi()
            }
        }
    }
    private val notificationProgressRefresh = object : Runnable {
        override fun run() {
            tunnelNotificationController.sync(tunnelCoordinator.state)
            scheduleNotificationProgressRefresh()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = this
        tunnelNotificationController.ensureChannel()
        driftCoordinator.updateSelectedPackages(DriftPoolPreferences.load(this))
        syncTunnelState { it.copy(connected = true, lastHeartbeatMillis = System.currentTimeMillis()) }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) }
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(screenReceiver, filter)
        screenReceiverRegistered = true
        surfaceUsageTracker.setInteractive(getSystemService(PowerManager::class.java).isInteractive, System.currentTimeMillis())
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
        AccessibilityRuntime.heartbeat()
        val state = AccessibilityRuntime.state.value
        val packageName = activeRootPackage() ?: run {
            AccessibilityRuntime.clearCurrentYouTubeDetection()
            AccessibilityRuntime.clearCurrentInstagramDetection()
            AccessibilityRuntime.clearCurrentTikTokCapture()
            failOpenCurrentSurface()
            return
        }
        if (packageName != YouTubeSurfaceDetector.YOUTUBE_PACKAGE) AccessibilityRuntime.clearCurrentYouTubeDetection()
        if (packageName != InstagramSurfaceDetector.INSTAGRAM_PACKAGE) AccessibilityRuntime.clearCurrentInstagramDetection()
        if (packageName != TikTokSurfaceDetector.TIKTOK_PACKAGE) AccessibilityRuntime.clearCurrentTikTokCapture()
        val nowMillis = System.currentTimeMillis()
        surfaceUsageTracker.foregroundChanged(packageName, nowMillis)
        tunnelCoordinator.observeForeground(packageName, nowMillis)
        driftCoordinator.observeForeground(
            packageName = packageName,
            nowMillis = nowMillis,
            activeTunnel = tunnelCoordinator.state.activeSession != null,
        )
        updateTunnelUi()
        if (!isWindowEvent) {
            if (shouldCapture(packageName, state.inspectionArmed)) {
                val settleDelay = if (
                    event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
                    packageName == TikTokSurfaceDetector.TIKTOK_PACKAGE
                ) TIKTOK_CLICK_SETTLE_MS else 0L
                scheduleCapture(settleDelay)
            }
            return
        }
        val targetChanged = packageName in TARGET_PACKAGES &&
            lastTargetPackage != null && packageName != lastTargetPackage
        if (packageName in TARGET_PACKAGES) lastTargetPackage = packageName

        AccessibilityRuntime.update {
            val history = if (it.foregroundPackage != packageName) {
                (listOf(PackageTransition(packageName, System.currentTimeMillis())) + it.packageHistory).take(MAX_HISTORY)
            } else it.packageHistory
            it.copy(
                foregroundPackage = packageName,
                lastRelevantEvent = eventTypeName(event.eventType),
                packageHistory = history,
                snapshot = if (targetChanged) null else it.snapshot,
                inspectionStatus = if (targetChanged && it.inspectionArmed) InspectionStatus.ARMED else it.inspectionStatus,
                inspectionDetail = if (targetChanged && it.inspectionArmed) "Target changed; waiting for a fresh tree." else it.inspectionDetail,
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
        handler.removeCallbacks(showOverlay)
        handler.removeCallbacks(trailingCapture)
        handler.removeCallbacks(tunnelDeadline)
        handler.removeCallbacks(notificationProgressRefresh)
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
        dismissNotificationShadeThen(sessionId) {
            if (tunnelCoordinator.requestPurposeChange(sessionId, System.currentTimeMillis())) {
                updateTunnelUi()
            }
        }
    }

    fun onNotificationContinue(sessionId: String) {
        if (tunnelCoordinator.state.activeSession?.id != sessionId) return
        dismissNotificationShadeThen(sessionId) {
            val state = tunnelCoordinator.state
            if (state.activeSession?.id != sessionId) return@dismissNotificationShadeThen
            when (state.prompt) {
                is TunnelPrompt.SessionExpired -> tunnelCoordinator.continueExpiredSession(System.currentTimeMillis())
                is TunnelPrompt.IntentionCheckIn -> tunnelCoordinator.continueCheckIn(System.currentTimeMillis())
                else -> return@dismissNotificationShadeThen
            }
            updateTunnelUi()
            scheduleCapture(NOTIFICATION_ACTION_SETTLE_MS)
        }
    }

    fun onNotificationEnd(sessionId: String) {
        if (tunnelCoordinator.state.activeSession?.id != sessionId) return
        tunnelCoordinator.endSession()
        updateTunnelUi()
    }

    fun onNotificationDismissed(sessionId: String) {
        if (tunnelCoordinator.state.activeSession?.id != sessionId) return
        tunnelNotificationController.suppressForSession(sessionId)
    }

    /**
     * Notification buttons are handled by a BroadcastReceiver, so SystemUI can still own the
     * active accessibility window when the command arrives. Dismiss the shade first, then wait
     * until the protected app is actually foreground before showing overlays or inspecting its tree.
     */
    private fun dismissNotificationShadeThen(
        sessionId: String,
        attemptsRemaining: Int = NOTIFICATION_ACTION_MAX_ATTEMPTS,
        action: () -> Unit,
    ) {
        val session = tunnelCoordinator.state.activeSession?.takeIf { it.id == sessionId } ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        }
        fun awaitProtectedApp(remaining: Int) {
            if (tunnelCoordinator.state.activeSession?.id != sessionId) return
            if (activeRootPackage() == session.app.packageName) {
                action()
                return
            }
            if (remaining <= 0) return
            handler.postDelayed({ awaitProtectedApp(remaining - 1) }, NOTIFICATION_ACTION_RETRY_MS)
        }
        handler.postDelayed({ awaitProtectedApp(attemptsRemaining) }, NOTIFICATION_ACTION_RETRY_MS)
    }

    fun onInspectionArmed(armed: Boolean) {
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
        if (!BuildConfig.DEBUG) return false
        if (tunnelCoordinator.state.prompt != null || driftOverlayView != null) return false
        handler.removeCallbacks(showOverlay)
        removeTestOverlay()
        AccessibilityRuntime.update { it.copy(overlayPending = true, overlayVisible = false) }
        handler.postDelayed(showOverlay, OVERLAY_DELAY_MS)
        return true
    }

    fun showVisualQaOverlay(overlay: VisualQaOverlay): Boolean {
        if (!BuildConfig.DEBUG) return false
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
                    packages.mapNotNull(DriftAppCatalog::labelFor),
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

    fun onDriftPoolChanged(packages: Set<String>) {
        driftCoordinator.updateSelectedPackages(packages)
        removeDriftOverlay()
        updateTunnelUi()
    }

    private fun scheduleCapture(minDelayMillis: Long = 0L) {
        val elapsed = SystemClock.elapsedRealtime()
        val throttleDelay = CAPTURE_THROTTLE_MS - (elapsed - lastCaptureAtElapsed)
        val delay = maxOf(throttleDelay, minDelayMillis)
        handler.removeCallbacks(trailingCapture)
        if (delay <= 0) captureCurrentRoot() else handler.postDelayed(trailingCapture, delay)
    }

    private fun captureCurrentRoot() {
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
            stack.addLast(PendingNode(root, 0, null))
            var truncated = false
            while (stack.isNotEmpty()) {
                val (node, depth, parentIndex) = stack.removeLast()
                if (nodes.size >= MAX_NODES) { recycleNode(node); truncated = true; break }
                try {
                    val nodeIndex = nodes.size
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
                YouTubeSurfaceDetector.detect(packageName, sanitizedNodes)
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
                val surface = it.surface.toTunnelSurface()
                val task = tunnelCoordinator.state.activeSession?.takeIf { session -> session.app.packageName == packageName }?.task
                surfaceUsageTracker.observe(packageName, surface, task, capturedAt)
                tunnelCoordinator.state.activeSession
                    ?.takeIf { session -> session.status == TunnelStatus.ACTIVE && session.app.packageName == packageName }
                    ?.let { session -> attentionRecorder.surfaceObserved(session, surface, capturedAt) }
                tunnelCoordinator.observeSurface(surface, capturedAt)
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

    private fun inferWhitelistedChromeRole(
        packageName: String,
        node: AccessibilityNodeInfo,
    ): UiChromeRole? {
        if (packageName != YouTubeSurfaceDetector.YOUTUBE_PACKAGE) return null
        val visible = try { node.isVisibleToUser && node.isEnabled } catch (_: RuntimeException) { false }
        if (!visible) return null
        val className = try { node.className?.toString() } catch (_: RuntimeException) { null }
        if (className?.endsWith("Button") != true) return null

        // Only derive one of four fixed navigation roles. Raw text/content descriptions are never
        // stored in the sanitized snapshot, fingerprint, database, or Attention history.
        val labels = buildList {
            try { node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add) } catch (_: RuntimeException) { }
            try { node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add) } catch (_: RuntimeException) { }
        }
        fun matches(label: String) = labels.any { it == label || it.startsWith("$label,") }
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
        }
        return buildList {
            try { node.text?.toString()?.trim()?.lowercase(java.util.Locale.ROOT)?.let(::add) } catch (_: RuntimeException) { }
            try { node.contentDescription?.toString()?.trim()?.lowercase(java.util.Locale.ROOT)?.let(::add) } catch (_: RuntimeException) { }
        }.any { it.startsWith(roleLabel) && "selected" in it }
    }

    private fun activeRootPackage(): String? {
        val root = try { rootInActiveWindow } catch (_: RuntimeException) { null } ?: return null
        return try { root.packageName?.toString() } catch (_: RuntimeException) { null } finally { recycleNode(root) }
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
            testOverlayView = layout
            AccessibilityRuntime.update { it.copy(overlayVisible = true) }
        } catch (_: RuntimeException) {
            AccessibilityRuntime.update { it.copy(overlayVisible = false) }
        }
    }

    private fun removeTestOverlay() {
        val view = testOverlayView ?: return
        try { getSystemService(WindowManager::class.java).removeView(view) } catch (_: RuntimeException) { }
        testOverlayView = null
        AccessibilityRuntime.update { it.copy(overlayVisible = false) }
    }

    private fun syncTunnelState(
        transform: (AccessibilityState) -> AccessibilityState = { it },
    ) = AccessibilityRuntime.update { state ->
        transform(state).copy(tunnelState = tunnelCoordinator.state)
    }

    private fun failOpenCurrentSurface() {
        surfaceUsageTracker.foregroundChanged(null, System.currentTimeMillis())
        tunnelCoordinator.observeSurface(DetectedSurface.UNKNOWN, System.currentTimeMillis())
        updateTunnelUi()
    }

    private fun updateTunnelUi() {
        tunnelCoordinator.setCheckInsEnabled(IntentionalCheckInPreferences.load(applicationContext))
        syncTunnelState()
        refreshTunnelOverlay()
        refreshDriftOverlay()
        scheduleTunnelDeadline()
        tunnelNotificationController.sync(tunnelCoordinator.state)
        scheduleNotificationProgressRefresh()
    }

    private fun scheduleNotificationProgressRefresh() {
        handler.removeCallbacks(notificationProgressRefresh)
        if (!tunnelNotificationController.shouldRefreshProgress(tunnelCoordinator.state)) return
        handler.postDelayed(notificationProgressRefresh, TunnelNotificationController.PROGRESS_REFRESH_MILLIS)
    }

    private fun scheduleTunnelDeadline() {
        handler.removeCallbacks(tunnelDeadline)
        val deadline = tunnelCoordinator.nextDeadlineMillis() ?: return
        handler.postDelayed(tunnelDeadline, (deadline - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    private fun refreshTunnelOverlay() {
        val prompt = tunnelCoordinator.state.prompt
        if (prompt != null) removeDriftOverlay()
        if (prompt == shownTunnelPrompt && tunnelOverlayView != null) return
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
            surfaceUsageTracker.setOverlayVisible(true, System.currentTimeMillis())
            getSystemService(WindowManager::class.java).addView(layout, params)
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
            tunnelOverlayView = null
            shownTunnelPrompt = null
        }
    }

    private fun refreshDriftOverlay() {
        val tunnelState = tunnelCoordinator.state
        val foregroundPackage = driftCoordinator.foregroundPackage
        if (
            tunnelState.prompt != null ||
            tunnelState.activeSession != null ||
            !driftCoordinator.isSelected(foregroundPackage)
        ) {
            removeDriftOverlay()
            return
        }
        if (driftOverlayView != null) return
        val episode = driftCoordinator.checkInCandidate(higherPriorityPromptVisible = false) ?: return
        val labels = episode.involvedPackages.mapNotNull(DriftAppCatalog::labelFor)
        if (labels.size < 2) return

        handler.removeCallbacks(showOverlay)
        removeTestOverlay()
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
            surfaceUsageTracker.setOverlayVisible(true, System.currentTimeMillis())
            getSystemService(WindowManager::class.java).addView(layout, params)
            animateOverlayEntrance(layout)
            driftOverlayView = layout
            driftCoordinator.markCheckInShown(episode.id)
            attentionRecorder.driftCheckInShown(episode, System.currentTimeMillis())
        } catch (_: RuntimeException) {
            driftOverlayView = null
        }
    }

    private fun driftCheckInView(episode: DriftEpisode, labels: List<String>) = baseTunnelOverlay().apply {
        addView(overlayAppSequence(episode.involvedPackages, labels))
        addView(overlayText("Looking for something?", heading = true))
        addView(overlayText("You moved between ${humanReadableList(labels)} in under a minute.", secondary = true))
        addView(overlayPrimaryButton("Set an intention") {
            attentionRecorder.driftDecision(
                episode = episode,
                subtype = AttentionSubtype.SET_INTENTION,
                decision = AttentionDecision.SET_INTENTION,
                foregroundPackage = driftCoordinator.foregroundPackage,
                nowMillis = System.currentTimeMillis(),
            )
            val app = driftCoordinator.setAnIntention(episode.id)
            removeDriftOverlay()
            if (app != null) tunnelCoordinator.requestPurposeGate(app)
            updateTunnelUi()
        })
        addView(overlayTextButton("Keep going") {
            attentionRecorder.driftDecision(
                episode = episode,
                subtype = AttentionSubtype.KEEP_GOING,
                decision = AttentionDecision.KEEP_GOING,
                foregroundPackage = driftCoordinator.foregroundPackage,
                nowMillis = System.currentTimeMillis(),
            )
            driftCoordinator.keepGoing(episode.id)
            removeDriftOverlay()
            updateTunnelUi()
        })
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

        val allowLabel = "Allow $surface for now"
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
    ): String = when (variant) {
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

    private fun interventionReminder(
        prompt: TunnelPrompt.Intervention,
        surface: String,
        variant: InterventionVariant,
    ): String = when (variant) {
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

    private fun sessionExpiredView(prompt: TunnelPrompt.SessionExpired) = baseTunnelOverlay().apply {
        addView(overlayText("Your chosen time is complete", heading = true))
        addView(overlayText("What would you like to do next?", secondary = true))
        addView(overlayPrimaryButton(if (prompt.task == TunnelTask.INSTAGRAM_BROWSE || prompt.task == TunnelTask.YOUTUBE_BROWSE) {
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
        val directedDestination = task.hasDirectedDestination()
        tunnelCoordinator.startSession(
            task = task,
            nowMillis = nowMillis,
            intendedDurationMillis = intendedDurationMillis,
            evaluateCurrentSurface = !directedDestination,
        )
        tunnelCoordinator.state.activeSession?.let { attentionRecorder.purposeSelected(it, nowMillis) }
        driftCoordinator.clear()
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

    private fun navigateToTaskDestinationAfterOverlayDismiss(task: TunnelTask) {
        handler.postDelayed({
            if (task == TunnelTask.TIKTOK_SEARCH_WATCH) {
                navigateToTikTokSearch()
                return@postDelayed
            }
            if (task == TunnelTask.TIKTOK_INBOX) {
                navigateToTikTokInbox()
                return@postDelayed
            }
            if (task == TunnelTask.INSTAGRAM_SEARCH || task == TunnelTask.INSTAGRAM_POST) {
                navigateToInstagramDestination(task)
                return@postDelayed
            }
            if (task == TunnelTask.YOUTUBE_SEARCH_WATCH || task == TunnelTask.YOUTUBE_SUBSCRIPTIONS || task == TunnelTask.YOUTUBE_SHORTS) {
                navigateToYouTubeDestination(task)
                return@postDelayed
            }
            val navigated = navigateToTaskDestination(task)
            if (!navigated) {
                // A directed tunnel must never silently accept the surface it started on.
                tunnelCoordinator.reevaluateCurrentSurface(System.currentTimeMillis())
                updateTunnelUi()
            }
            handler.postDelayed({ captureCurrentRoot() }, DESTINATION_NAVIGATION_SETTLE_MS)
        }, OVERLAY_DISMISS_SETTLE_MS)
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
                handler.postDelayed({ retryInstagramCreateFromMainShell() }, DESTINATION_NAVIGATION_SETTLE_MS)
            openInstagramMainFeed() ->
                handler.postDelayed({ retryInstagramCreateFromMainShell() }, INSTAGRAM_MAIN_FEED_SETTLE_MS)
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
        handler.postDelayed({
            if (isInstagramCreateFlowVisible() || clickInstagramCreateAffordance()) {
                finishInstagramDirectedNavigationAfterSettle()
            } else {
                failInstagramDirectedNavigation()
            }
        }, DESTINATION_NAVIGATION_SETTLE_MS)
    }

    private fun backTowardInstagramMainShell(task: TunnelTask, backStepsRemaining: Int) {
        if (backStepsRemaining <= 0 || !performGlobalAction(GLOBAL_ACTION_BACK)) {
            failInstagramDirectedNavigation()
            return
        }
        handler.postDelayed({
            if (navigateToTaskDestination(task)) {
                finishInstagramDirectedNavigationAfterSettle()
            } else {
                backTowardInstagramMainShell(task, backStepsRemaining - 1)
            }
        }, DESTINATION_NAVIGATION_SETTLE_MS)
    }

    private fun finishInstagramDirectedNavigationAfterSettle() {
        handler.postDelayed({
            instagramDirectedNavigationInProgress = false
            handler.removeCallbacks(trailingCapture)
            captureCurrentRoot()
        }, DESTINATION_NAVIGATION_SETTLE_MS)
    }

    private fun failInstagramDirectedNavigation() {
        instagramDirectedNavigationInProgress = false
        handler.removeCallbacks(trailingCapture)
        captureCurrentRoot()
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
        handler.postDelayed({
            if (navigateToTaskDestination(task)) {
                finishYouTubeDirectedNavigationAfterSettle()
            } else if (hasYouTubeTopLevelChrome()) {
                // We have already reached YouTube's main shell. Do not issue another Back merely
                // because the requested tab is not clickable in this app build.
                failYouTubeDirectedNavigation()
            } else {
                backTowardYouTubeMainShell(task, backStepsRemaining - 1)
            }
        }, DESTINATION_NAVIGATION_SETTLE_MS)
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
        handler.postDelayed({
            youTubeDirectedNavigationInProgress = false
            handler.removeCallbacks(trailingCapture)
            captureCurrentRoot()
        }, DESTINATION_NAVIGATION_SETTLE_MS)
    }

    private fun failYouTubeDirectedNavigation() {
        youTubeDirectedNavigationInProgress = false
        handler.removeCallbacks(trailingCapture)
        captureCurrentRoot()
    }

    private fun clickYouTubeSearchAffordance(): Boolean = clickAppAffordance(
        packageName = YouTubeSurfaceDetector.YOUTUBE_PACKAGE,
        contentDescriptions = setOf("Search"),
        textLabels = setOf("Search"),
    )

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
            tikTokDirectedNavigationInProgress = false
            tunnelCoordinator.reevaluateCurrentSurface(System.currentTimeMillis())
            updateTunnelUi()
            handler.postDelayed({ captureCurrentRoot() }, DESTINATION_NAVIGATION_SETTLE_MS)
            return
        }

        handler.postDelayed({
            if (clickTikTokSearchAffordance()) {
                finishTikTokDirectedNavigationAfterSettle()
            } else {
                // Fail visibly on the settled surface instead of pretending navigation worked.
                tikTokDirectedNavigationInProgress = false
                handler.removeCallbacks(trailingCapture)
                captureCurrentRoot()
            }
        }, TIKTOK_ROUTE_STEP_SETTLE_MS)
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
            handler.postDelayed(
                { verifyTikTokInboxOrBack(TIKTOK_INBOX_MAX_BACK_STEPS) },
                DESTINATION_NAVIGATION_SETTLE_MS,
            )
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
        handler.postDelayed({
            if (isTikTokInboxSelected()) {
                finishTikTokDirectedNavigationAfterSettle()
                return@postDelayed
            }
            if (clickTikTokInboxAffordance()) {
                handler.postDelayed(
                    { verifyTikTokInboxOrBack(backStepsRemaining - 1) },
                    DESTINATION_NAVIGATION_SETTLE_MS,
                )
            } else {
                backTowardTikTokMainShell(backStepsRemaining - 1)
            }
        }, TIKTOK_ROUTE_STEP_SETTLE_MS)
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
        tikTokDirectedNavigationInProgress = false
        handler.removeCallbacks(trailingCapture)
        captureCurrentRoot()
    }

    private fun finishTikTokDirectedNavigationAfterSettle() {
        handler.postDelayed({
            tikTokDirectedNavigationInProgress = false
            handler.removeCallbacks(trailingCapture)
            captureCurrentRoot()
        }, DESTINATION_NAVIGATION_SETTLE_MS)
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
        setBackgroundResource(android.R.drawable.list_selector_background)
        setOnClickListener { action() }
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
                    ColorStateList.valueOf(COLOR_PRIMARY),
                    GradientDrawable().apply {
                        setColor(if (selected) COLOR_DURATION_SELECTED else COLOR_DURATION_UNSELECTED)
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

    private fun removeTunnelOverlay() {
        val hadOverlay = tunnelOverlayView != null
        tunnelOverlayView?.let { view ->
            try { getSystemService(WindowManager::class.java).removeView(view) } catch (_: RuntimeException) { }
        }
        tunnelOverlayView = null
        shownTunnelPrompt = null
        if (hadOverlay) surfaceUsageTracker.setOverlayVisible(false, System.currentTimeMillis())
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
        DetectedSurface.INSTAGRAM_REELS -> "Reels"
        DetectedSurface.INSTAGRAM_EXPLORE -> "Explore"
        DetectedSurface.INSTAGRAM_HOME -> "Home"
        DetectedSurface.INSTAGRAM_PROFILE -> "Profile"
        DetectedSurface.INSTAGRAM_CREATE -> "Create"
        DetectedSurface.YOUTUBE_SHORTS -> "Shorts"
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
        internal var current: TaskTunnelAccessibilityService? = null
        private val TARGET_PACKAGES = setOf(
            InstagramSurfaceDetector.INSTAGRAM_PACKAGE,
            YouTubeSurfaceDetector.YOUTUBE_PACKAGE,
            TikTokFingerprint.TIKTOK_PACKAGE,
        )
        private const val MAX_NODES = 200
        private const val MAX_DEPTH = 12
        private const val MAX_HISTORY = 8
        private const val MAX_FIELD_LENGTH = 200
        private const val CAPTURE_THROTTLE_MS = 1_000L
        private const val DEADLINE_RETRY_MS = 1_000L
        private const val OVERLAY_DISMISS_SETTLE_MS = 120L
        private const val DESTINATION_NAVIGATION_SETTLE_MS = 350L
        private const val INSTAGRAM_MAIN_FEED_SETTLE_MS = 550L
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
        private const val COLOR_DURATION_SELECTED = 0xFF14345F.toInt()
        private const val COLOR_DURATION_UNSELECTED = 0xFF202428.toInt()
        private val DURATION_CHOICES = listOf(
            DurationChoice("No limit", null),
            DurationChoice("5 min", 5 * 60_000L),
            DurationChoice("10 min", 10 * 60_000L),
            DurationChoice("20 min", 20 * 60_000L),
        )
    }

    private data class DurationChoice(val label: String, val durationMillis: Long?)
}
