package com.example.tasktunnel.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.res.ColorStateList
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
import com.example.tasktunnel.detector.InstagramSurfaceDetector
import com.example.tasktunnel.detector.InstagramSurface
import com.example.tasktunnel.detector.YouTubeSurfaceDetector
import com.example.tasktunnel.detector.YouTubeSurface
import com.example.tasktunnel.diagnostics.SanitizedFingerprint
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = this
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
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        if (!isInspectionEvent) return
        AccessibilityRuntime.heartbeat()
        val state = AccessibilityRuntime.state.value
        val packageName = activeRootPackage() ?: run {
            AccessibilityRuntime.clearCurrentYouTubeDetection()
            AccessibilityRuntime.clearCurrentInstagramDetection()
            failOpenCurrentSurface()
            return
        }
        if (packageName != YouTubeSurfaceDetector.YOUTUBE_PACKAGE) AccessibilityRuntime.clearCurrentYouTubeDetection()
        if (packageName != InstagramSurfaceDetector.INSTAGRAM_PACKAGE) AccessibilityRuntime.clearCurrentInstagramDetection()
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
            if (shouldCapture(packageName, state.inspectionArmed)) scheduleCapture()
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
                tunnelState = com.example.tasktunnel.tunnel.TunnelRuntimeState(),
            )
        }
    }

    fun onInspectionArmed(armed: Boolean) {
        if (armed && BuildConfig.DEBUG) {
            val packageName = activeRootPackage()
            if (packageName in TARGET_PACKAGES) scheduleCapture() else AccessibilityRuntime.update {
                it.copy(inspectionStatus = InspectionStatus.UNSUPPORTED_APP, inspectionDetail = "Inspection supports Instagram and YouTube only.")
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

    private fun scheduleCapture() {
        val elapsed = SystemClock.elapsedRealtime()
        val remaining = CAPTURE_THROTTLE_MS - (elapsed - lastCaptureAtElapsed)
        handler.removeCallbacks(trailingCapture)
        if (remaining <= 0) captureCurrentRoot() else handler.postDelayed(trailingCapture, remaining)
    }

    private fun captureCurrentRoot() {
        val state = AccessibilityRuntime.state.value
        lastCaptureAtElapsed = SystemClock.elapsedRealtime()
        val root = try { rootInActiveWindow } catch (_: RuntimeException) { null }
        if (root == null) {
            AccessibilityRuntime.clearCurrentYouTubeDetection()
            AccessibilityRuntime.clearCurrentInstagramDetection()
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
                        selected = node.isSelected,
                        parentIndex = parentIndex,
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
                )
            }
            detection?.let {
                val surface = it.surface.toTunnelSurface()
                val task = tunnelCoordinator.state.activeSession?.takeIf { session -> session.app.packageName == packageName }?.task
                surfaceUsageTracker.observe(packageName, surface, task, capturedAt)
                tunnelCoordinator.state.activeSession
                    ?.takeIf { session -> session.status == TunnelStatus.ACTIVE && session.app.packageName == packageName }
                    ?.let { session -> attentionRecorder.surfaceObserved(session, surface, capturedAt) }
                tunnelCoordinator.observeSurface(surface, capturedAt)
            }
            instagramDetection?.let {
                val surface = it.surface.toTunnelSurface()
                val task = tunnelCoordinator.state.activeSession?.takeIf { session -> session.app.packageName == packageName }?.task
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
            failOpenCurrentSurface()
            AccessibilityRuntime.update {
                if (BuildConfig.DEBUG && state.inspectionArmed) it.copy(
                    inspectionStatus = InspectionStatus.ERROR,
                    inspectionDetail = "Tree inspection failed safely; any shown capture is older.",
                ) else it
            }
        }
    }

    private fun activeRootPackage(): String? {
        val root = try { rootInActiveWindow } catch (_: RuntimeException) { null } ?: return null
        return try { root.packageName?.toString() } catch (_: RuntimeException) { null } finally { recycleNode(root) }
    }

    private fun shouldCapture(packageName: String?, inspectionArmed: Boolean): Boolean =
        packageName == YouTubeSurfaceDetector.YOUTUBE_PACKAGE ||
            packageName == InstagramSurfaceDetector.INSTAGRAM_PACKAGE ||
            (BuildConfig.DEBUG && inspectionArmed && packageName in TARGET_PACKAGES)

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
        var selectedDurationMillis: Long? = null
        addView(purposeGateAppIdentity(prompt.app))
        addView(purposeGateQuestion())
        val durationSelector = purposeDurationSelector()
        val durationValue = TextView(context).apply {
            text = DURATION_CHOICES.first().label
            textSize = 14f
            setTextColor(COLOR_SECONDARY_TEXT)
        }
        when (prompt.app) {
            SupportedApp.INSTAGRAM -> {
                addView(purposeChoiceRow(R.drawable.ic_purpose_message, "Reply to messages", "Go straight to conversations") { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    startTunnel(TunnelTask.INSTAGRAM_MESSAGES, selectedDurationMillis)
                })
                addView(overlayDivider())
                addView(purposeChoiceRow(R.drawable.ic_purpose_browse, "Browse intentionally", "Explore on your terms") { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    startTunnel(TunnelTask.INSTAGRAM_BROWSE, selectedDurationMillis)
                })
            }
            SupportedApp.YOUTUBE -> {
                addView(purposeChoiceRow(R.drawable.ic_purpose_search, "Search / watch something specific", "Find what you came to watch") { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    startTunnel(TunnelTask.YOUTUBE_SEARCH_WATCH, selectedDurationMillis)
                })
                addView(overlayDivider())
                addView(purposeChoiceRow(R.drawable.ic_purpose_browse, "Browse intentionally", "Explore on your terms") { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    startTunnel(TunnelTask.YOUTUBE_BROWSE, selectedDurationMillis)
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
            selectedDurationMillis = choice.durationMillis
            durationValue.text = choice.label
            durationSelector.visibility = View.GONE
            timeLimitRow.visibility = View.VISIBLE
        }
        addView(durationSelector)
        addView(purposeDismissButton {
            tunnelCoordinator.dismissPurposeGate()
            updateTunnelUi()
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
            if (tunnelCoordinator.returnFromIntervention(nowMillis)) {
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
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        })

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
                if (tunnelCoordinator.returnFromCheckIn(nowMillis)) {
                    session?.let {
                        attentionRecorder.tunnelDecision(it, AttentionSubtype.CHECK_IN_RETURN, AttentionDecision.CHECK_IN_RETURN, nowMillis, prompt.surface)
                    }
                    updateTunnelUi()
                    performGlobalAction(GLOBAL_ACTION_BACK)
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
            TunnelTask.YOUTUBE_SEARCH_WATCH -> "Still here for something specific?"
            TunnelTask.INSTAGRAM_BROWSE,
            TunnelTask.YOUTUBE_BROWSE,
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
            TunnelTask.YOUTUBE_SEARCH_WATCH ->
                "You opened YouTube to search for or watch something. $surface is outside that purpose."
            TunnelTask.INSTAGRAM_BROWSE,
            TunnelTask.YOUTUBE_BROWSE,
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
        tunnelCoordinator.startSession(task, nowMillis, intendedDurationMillis)
        tunnelCoordinator.state.activeSession?.let { attentionRecorder.purposeSelected(it, nowMillis) }
        driftCoordinator.clear()
        updateTunnelUi()
        scheduleCapture()
    }

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

    private fun purposeDismissButton(action: () -> Unit) = TextView(this).apply {
        text = "Not now"
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
        TunnelTask.INSTAGRAM_BROWSE -> "You came here to browse intentionally."
        TunnelTask.YOUTUBE_SEARCH_WATCH -> "You came here to search for or watch something."
        TunnelTask.YOUTUBE_BROWSE -> "You came here to browse intentionally."
    }

    private fun returnLabel(task: TunnelTask): String = when (task) {
        TunnelTask.INSTAGRAM_MESSAGES -> "Return to Messages"
        TunnelTask.YOUTUBE_SEARCH_WATCH -> "Return to Search / video"
        TunnelTask.INSTAGRAM_BROWSE,
        TunnelTask.YOUTUBE_BROWSE,
        -> "Return"
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
        DetectedSurface.YOUTUBE_SHORTS -> "Shorts"
        else -> "This screen"
    }

    private fun InstagramSurface.toTunnelSurface(): DetectedSurface = when (this) {
        InstagramSurface.INSTAGRAM_MESSAGES -> DetectedSurface.INSTAGRAM_MESSAGES
        InstagramSurface.INSTAGRAM_EXPLORE -> DetectedSurface.INSTAGRAM_EXPLORE
        InstagramSurface.INSTAGRAM_REELS -> DetectedSurface.INSTAGRAM_REELS
        InstagramSurface.INSTAGRAM_HOME -> DetectedSurface.INSTAGRAM_HOME
        InstagramSurface.INSTAGRAM_OTHER -> DetectedSurface.INSTAGRAM_OTHER
        InstagramSurface.UNKNOWN -> DetectedSurface.UNKNOWN
    }

    private fun YouTubeSurface.toTunnelSurface(): DetectedSurface = when (this) {
        YouTubeSurface.YOUTUBE_SEARCH -> DetectedSurface.YOUTUBE_SEARCH
        YouTubeSurface.YOUTUBE_VIDEO -> DetectedSurface.YOUTUBE_VIDEO
        YouTubeSurface.YOUTUBE_SHORTS -> DetectedSurface.YOUTUBE_SHORTS
        YouTubeSurface.YOUTUBE_OTHER -> DetectedSurface.YOUTUBE_OTHER
        YouTubeSurface.UNKNOWN -> DetectedSurface.UNKNOWN
    }

    private fun eventTypeName(type: Int) = when (type) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "Window state changed"
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "Windows changed"
        else -> "Event $type"
    }

    companion object {
        internal var current: TaskTunnelAccessibilityService? = null
        private val TARGET_PACKAGES = setOf(InstagramSurfaceDetector.INSTAGRAM_PACKAGE, YouTubeSurfaceDetector.YOUTUBE_PACKAGE)
        private const val MAX_NODES = 200
        private const val MAX_DEPTH = 12
        private const val MAX_HISTORY = 8
        private const val MAX_FIELD_LENGTH = 200
        private const val CAPTURE_THROTTLE_MS = 1_000L
        private const val DEADLINE_RETRY_MS = 1_000L
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
