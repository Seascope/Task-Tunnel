package com.example.tasktunnel.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.example.tasktunnel.BuildConfig
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
import com.example.tasktunnel.tunnel.DetectedSurface
import com.example.tasktunnel.tunnel.SupportedApp
import com.example.tasktunnel.tunnel.TunnelCoordinator
import com.example.tasktunnel.tunnel.TunnelPrompt
import com.example.tasktunnel.tunnel.TunnelTask
import java.util.ArrayDeque

class TaskTunnelAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val tunnelCoordinator = TunnelCoordinator()
    private val driftCoordinator = DriftCoordinator(DriftAppCatalog.knownPackages)
    private var testOverlayView: LinearLayout? = null
    private var tunnelOverlayView: LinearLayout? = null
    private var driftOverlayView: LinearLayout? = null
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
        syncTunnelState { it.copy(connected = true) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val isWindowEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        val isInspectionEvent = isWindowEvent ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        if (!isInspectionEvent) return
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
                tunnelCoordinator.observeSurface(it.surface.toTunnelSurface(), capturedAt)
            }
            instagramDetection?.let {
                tunnelCoordinator.observeSurface(it.surface.toTunnelSurface(), capturedAt)
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
        tunnelCoordinator.observeSurface(DetectedSurface.UNKNOWN, System.currentTimeMillis())
        updateTunnelUi()
    }

    private fun updateTunnelUi() {
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
            is TunnelPrompt.Intervention -> interventionView(prompt)
            is TunnelPrompt.SessionExpired -> sessionExpiredView(prompt)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = OVERLAY_TOP_OFFSET_PX
        }
        try {
            getSystemService(WindowManager::class.java).addView(layout, params)
            tunnelOverlayView = layout
            shownTunnelPrompt = prompt
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = OVERLAY_TOP_OFFSET_PX
        }
        try {
            getSystemService(WindowManager::class.java).addView(layout, params)
            driftOverlayView = layout
            driftCoordinator.markCheckInShown(episode.id)
        } catch (_: RuntimeException) {
            driftOverlayView = null
        }
    }

    private fun driftCheckInView(episode: DriftEpisode, labels: List<String>) = baseTunnelOverlay().apply {
        addView(overlayText("Looking for something?", heading = true))
        addView(overlayText("You've moved between ${humanReadableList(labels)} in under a minute."))
        addView(overlayButton("Set an intention") {
            val app = driftCoordinator.setAnIntention(episode.id)
            removeDriftOverlay()
            if (app != null) tunnelCoordinator.requestPurposeGate(app)
            updateTunnelUi()
        })
        addView(overlayButton("Keep going") {
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

    private fun purposeGateView(prompt: TunnelPrompt.PurposeGate) = baseTunnelOverlay().apply {
        var selectedDurationMillis: Long? = null
        addView(overlayText("What did you open ${prompt.app.displayName} to do?", heading = true))
        addView(overlayText("Optional duration"))
        addView(RadioGroup(context).apply {
            orientation = RadioGroup.HORIZONTAL
            DURATION_CHOICES.forEachIndexed { index, choice ->
                addView(RadioButton(context).apply {
                    id = View.generateViewId()
                    text = choice.label
                    setTextColor(0xFFFFFFFF.toInt())
                    isChecked = index == 0
                    setOnClickListener { selectedDurationMillis = choice.durationMillis }
                })
            }
        })
        when (prompt.app) {
            SupportedApp.INSTAGRAM -> {
                addView(overlayButton("Reply to messages") {
                    startTunnel(TunnelTask.INSTAGRAM_MESSAGES, selectedDurationMillis)
                })
                addView(overlayButton("Browse intentionally") {
                    startTunnel(TunnelTask.INSTAGRAM_BROWSE, selectedDurationMillis)
                })
            }
            SupportedApp.YOUTUBE -> {
                addView(overlayButton("Search / watch something specific") {
                    startTunnel(TunnelTask.YOUTUBE_SEARCH_WATCH, selectedDurationMillis)
                })
                addView(overlayButton("Browse intentionally") {
                    startTunnel(TunnelTask.YOUTUBE_BROWSE, selectedDurationMillis)
                })
            }
        }
        addView(overlayButton("Not now") {
            tunnelCoordinator.dismissPurposeGate()
            updateTunnelUi()
        })
    }

    private fun interventionView(prompt: TunnelPrompt.Intervention) = baseTunnelOverlay().apply {
        addView(overlayText(taskReminder(prompt.task), heading = true))
        addView(overlayText("${surfaceLabel(prompt.surface)} is outside this Task Tunnel."))
        addView(overlayButton("Return") {
            if (tunnelCoordinator.returnFromIntervention(System.currentTimeMillis())) {
                updateTunnelUi()
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        })
        addView(overlayButton("End Tunnel") {
            tunnelCoordinator.endSession()
            updateTunnelUi()
        })
        addView(overlayButton("Allow anyway") {
            tunnelCoordinator.allowAnyway(System.currentTimeMillis())
            updateTunnelUi()
        })
    }

    private fun sessionExpiredView(prompt: TunnelPrompt.SessionExpired) = baseTunnelOverlay().apply {
        addView(overlayText("Your Task Tunnel time is complete.", heading = true))
        addView(overlayText("What would you like to do in ${prompt.app.displayName}?"))
        addView(overlayButton("Finish") {
            tunnelCoordinator.endSession()
            updateTunnelUi()
        })
        addView(overlayButton(if (prompt.task == TunnelTask.INSTAGRAM_BROWSE || prompt.task == TunnelTask.YOUTUBE_BROWSE) {
            "Keep browsing"
        } else {
            "Continue"
        }) {
            tunnelCoordinator.continueExpiredSession(System.currentTimeMillis())
            updateTunnelUi()
            scheduleCapture()
        })
        addView(overlayButton("Choose another purpose") {
            tunnelCoordinator.chooseAnotherPurpose()
            updateTunnelUi()
        })
    }

    private fun startTunnel(task: TunnelTask, intendedDurationMillis: Long?) {
        tunnelCoordinator.startSession(task, System.currentTimeMillis(), intendedDurationMillis)
        driftCoordinator.clear()
        updateTunnelUi()
        scheduleCapture()
    }

    private fun baseTunnelOverlay() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(OVERLAY_PADDING_PX, OVERLAY_PADDING_PX, OVERLAY_PADDING_PX, OVERLAY_PADDING_PX)
        background = GradientDrawable().apply {
            setColor(0xFA202124.toInt())
            cornerRadius = OVERLAY_CORNER_RADIUS_PX
        }
        elevation = OVERLAY_ELEVATION_PX
    }

    private fun overlayText(value: String, heading: Boolean = false) = TextView(this).apply {
        text = value
        setTextColor(0xFFFFFFFF.toInt())
        textSize = if (heading) 20f else 16f
        setPadding(0, 0, 0, OVERLAY_ITEM_GAP_PX)
    }

    private fun overlayButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun removeTunnelOverlay() {
        tunnelOverlayView?.let { view ->
            try { getSystemService(WindowManager::class.java).removeView(view) } catch (_: RuntimeException) { }
        }
        tunnelOverlayView = null
        shownTunnelPrompt = null
    }

    private fun removeDriftOverlay() {
        driftOverlayView?.let { view ->
            try { getSystemService(WindowManager::class.java).removeView(view) } catch (_: RuntimeException) { }
        }
        driftOverlayView = null
    }

    private fun taskReminder(task: TunnelTask): String = when (task) {
        TunnelTask.INSTAGRAM_MESSAGES -> "You opened Instagram to reply to messages."
        TunnelTask.INSTAGRAM_BROWSE -> "You opened Instagram to browse intentionally."
        TunnelTask.YOUTUBE_SEARCH_WATCH -> "You opened YouTube to search for or watch something."
        TunnelTask.YOUTUBE_BROWSE -> "You opened YouTube to browse intentionally."
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
        private const val OVERLAY_TOP_OFFSET_PX = 72
        private const val OVERLAY_PADDING_PX = 32
        private const val OVERLAY_ITEM_GAP_PX = 12
        private const val OVERLAY_CORNER_RADIUS_PX = 24f
        private const val OVERLAY_ELEVATION_PX = 12f
        private val DURATION_CHOICES = listOf(
            DurationChoice("No limit", null),
            DurationChoice("5 min", 5 * 60_000L),
            DurationChoice("10 min", 10 * 60_000L),
            DurationChoice("20 min", 20 * 60_000L),
        )
    }

    private data class DurationChoice(val label: String, val durationMillis: Long?)
}
