package com.example.tasktunnel.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.tasktunnel.BuildConfig
import com.example.tasktunnel.detector.YouTubeSurfaceDetector
import java.util.ArrayDeque

class TaskTunnelAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlayView: LinearLayout? = null
    private var lastCaptureAtElapsed = 0L
    private var lastTargetPackage: String? = null
    private val showOverlay = Runnable { showTestOverlayNow() }
    private val trailingCapture = Runnable { captureCurrentRoot() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = this
        AccessibilityRuntime.update { it.copy(connected = true) }
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
            return
        }
        if (!isWindowEvent) {
            if (packageName == YouTubeSurfaceDetector.YOUTUBE_PACKAGE ||
                (BuildConfig.DEBUG && state.inspectionArmed && packageName in TARGET_PACKAGES)
            ) scheduleCapture()
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
        if (packageName != YouTubeSurfaceDetector.YOUTUBE_PACKAGE) AccessibilityRuntime.clearCurrentYouTubeDetection()
        if (packageName == YouTubeSurfaceDetector.YOUTUBE_PACKAGE ||
            (BuildConfig.DEBUG && state.inspectionArmed && packageName in TARGET_PACKAGES)
        ) scheduleCapture()
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
        removeOverlay()
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
            )
        }
    }

    fun onInspectionArmed(armed: Boolean) {
        if (armed && BuildConfig.DEBUG) {
            val packageName = activeRootPackage()
            if (packageName in TARGET_PACKAGES) scheduleCapture() else AccessibilityRuntime.update {
                it.copy(inspectionStatus = InspectionStatus.UNSUPPORTED_APP, inspectionDetail = "Inspection supports Instagram and YouTube only.")
            }
        } else if (activeRootPackage() != YouTubeSurfaceDetector.YOUTUBE_PACKAGE) handler.removeCallbacks(trailingCapture)
    }

    fun scheduleTestOverlay(): Boolean {
        if (!BuildConfig.DEBUG) return false
        handler.removeCallbacks(showOverlay)
        removeOverlay()
        AccessibilityRuntime.update { it.copy(overlayPending = true, overlayVisible = false) }
        handler.postDelayed(showOverlay, OVERLAY_DELAY_MS)
        return true
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
            AccessibilityRuntime.update {
                if (BuildConfig.DEBUG && state.inspectionArmed) it.copy(
                    inspectionStatus = InspectionStatus.ERROR,
                    inspectionDetail = "Tree inspection failed safely; any shown capture is older.",
                ) else it
            }
            return
        } ?: run {
            recycleNode(root)
            return
        }
        if (packageName !in TARGET_PACKAGES) { recycleNode(root); return }
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        try {
            val nodes = ArrayList<SanitizedNode>(MAX_NODES)
            stack.addLast(root to 0)
            var truncated = false
            while (stack.isNotEmpty()) {
                val (node, depth) = stack.removeLast()
                if (nodes.size >= MAX_NODES) { recycleNode(node); truncated = true; break }
                try {
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
                    )
                    if (depth < MAX_DEPTH) {
                        val remainingCapacity = MAX_NODES - nodes.size - stack.size
                        val childLimit = minOf(node.childCount, remainingCapacity.coerceAtLeast(0))
                        if (childLimit < node.childCount) truncated = true
                        for (index in childLimit - 1 downTo 0) node.getChild(index)?.let { stack.addLast(it to depth + 1) }
                    } else if (node.childCount > 0) truncated = true
                } finally {
                    recycleNode(node)
                }
            }
            while (stack.isNotEmpty()) recycleNode(stack.removeLast().first)
            val capturedAt = System.currentTimeMillis()
            val sanitizedNodes = nodes.toList()
            val detection = if (packageName == YouTubeSurfaceDetector.YOUTUBE_PACKAGE) {
                YouTubeSurfaceDetector.detect(packageName, sanitizedNodes)
            } else null
            AccessibilityRuntime.update {
                val observed = detection?.let { result -> ObservedYouTubeDetection(packageName, capturedAt, result) }
                it.copy(
                    inspectionStatus = if (BuildConfig.DEBUG && it.inspectionArmed) InspectionStatus.CAPTURED else it.inspectionStatus,
                    inspectionDetail = if (BuildConfig.DEBUG && it.inspectionArmed) "Last captured sanitized tree." else it.inspectionDetail,
                    snapshot = if (BuildConfig.DEBUG && it.inspectionArmed) {
                        TreeSnapshot(packageName, capturedAt, sanitizedNodes, truncated)
                    } else it.snapshot,
                    currentYouTubeDetection = observed ?: it.currentYouTubeDetection,
                    lastYouTubeDetection = observed ?: it.lastYouTubeDetection,
                )
            }
        } catch (_: RuntimeException) {
            while (stack.isNotEmpty()) recycleNode(stack.removeLast().first)
            AccessibilityRuntime.clearCurrentYouTubeDetection()
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

    @Suppress("DEPRECATION")
    private fun recycleNode(node: AccessibilityNodeInfo) {
        if (android.os.Build.VERSION.SDK_INT <= 32) node.recycle()
    }

    private fun showTestOverlayNow() {
        AccessibilityRuntime.update { it.copy(overlayPending = false) }
        if (overlayView != null) return
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
                setOnClickListener { removeOverlay() }
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
            overlayView = layout
            AccessibilityRuntime.update { it.copy(overlayVisible = true) }
        } catch (_: RuntimeException) {
            AccessibilityRuntime.update { it.copy(overlayVisible = false) }
        }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        try { getSystemService(WindowManager::class.java).removeView(view) } catch (_: RuntimeException) { }
        overlayView = null
        AccessibilityRuntime.update { it.copy(overlayVisible = false) }
    }

    private fun eventTypeName(type: Int) = when (type) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "Window state changed"
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "Windows changed"
        else -> "Event $type"
    }

    companion object {
        internal var current: TaskTunnelAccessibilityService? = null
        private val TARGET_PACKAGES = setOf("com.instagram.android", "com.google.android.youtube")
        private const val MAX_NODES = 200
        private const val MAX_DEPTH = 12
        private const val MAX_HISTORY = 8
        private const val MAX_FIELD_LENGTH = 200
        private const val CAPTURE_THROTTLE_MS = 1_000L
        private const val OVERLAY_DELAY_MS = 3_000L
    }
}
