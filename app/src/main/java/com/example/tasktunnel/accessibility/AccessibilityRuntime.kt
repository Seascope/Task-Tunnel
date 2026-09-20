package com.example.tasktunnel.accessibility

import com.example.tasktunnel.detector.InstagramDetection
import com.example.tasktunnel.detector.YouTubeDetection
import com.example.tasktunnel.tunnel.TunnelRuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SanitizedNode(
    val depth: Int,
    val className: String?,
    val resourceId: String?,
    val childCount: Int,
    val clickable: Boolean,
    val scrollable: Boolean,
    val editable: Boolean,
    val enabled: Boolean,
    val visibleToUser: Boolean,
    val selected: Boolean = false,
    val parentIndex: Int? = null,
)

data class TreeSnapshot(
    val packageName: String,
    val capturedAtMillis: Long,
    val nodes: List<SanitizedNode>,
    val truncated: Boolean,
)

data class PackageTransition(val packageName: String, val observedAtMillis: Long)

data class ObservedYouTubeDetection(
    val packageName: String,
    val capturedAtMillis: Long,
    val detection: YouTubeDetection,
    val fingerprint: String?,
)

data class ObservedInstagramDetection(
    val packageName: String,
    val capturedAtMillis: Long,
    val detection: InstagramDetection,
    val fingerprint: String,
)

enum class InspectionStatus { IDLE, ARMED, CAPTURED, UNSUPPORTED_APP, ROOT_UNAVAILABLE, ERROR }

data class AccessibilityState(
    val connected: Boolean = false,
    val foregroundPackage: String? = null,
    val lastRelevantEvent: String? = null,
    val packageHistory: List<PackageTransition> = emptyList(),
    val inspectionArmed: Boolean = false,
    val inspectionStatus: InspectionStatus = InspectionStatus.IDLE,
    val inspectionDetail: String? = null,
    val snapshot: TreeSnapshot? = null,
    val overlayPending: Boolean = false,
    val overlayVisible: Boolean = false,
    val currentYouTubeDetection: ObservedYouTubeDetection? = null,
    val lastYouTubeDetection: ObservedYouTubeDetection? = null,
    val currentInstagramDetection: ObservedInstagramDetection? = null,
    val lastInstagramDetection: ObservedInstagramDetection? = null,
    val tunnelState: TunnelRuntimeState = TunnelRuntimeState(),
)

object AccessibilityRuntime {
    private val mutableState = MutableStateFlow(AccessibilityState())
    val state = mutableState.asStateFlow()

    internal fun update(transform: (AccessibilityState) -> AccessibilityState) =
        mutableState.update(transform)

    fun setInspectionArmed(armed: Boolean) {
        mutableState.update {
            it.copy(
                inspectionArmed = armed,
                inspectionStatus = if (armed) InspectionStatus.ARMED else InspectionStatus.IDLE,
                inspectionDetail = if (armed) "Switch to Instagram or YouTube to capture." else null,
            )
        }
        TaskTunnelAccessibilityService.current?.onInspectionArmed(armed)
    }

    fun clearSnapshot() = mutableState.update {
        it.copy(
            snapshot = null,
            inspectionStatus = if (it.inspectionArmed) InspectionStatus.ARMED else InspectionStatus.IDLE,
            inspectionDetail = if (it.inspectionArmed) "Switch to Instagram or YouTube to capture." else null,
        )
    }

    fun requestTestOverlay() = TaskTunnelAccessibilityService.current?.scheduleTestOverlay() ?: false

    internal fun clearCurrentYouTubeDetection() = mutableState.update { it.copy(currentYouTubeDetection = null) }

    internal fun clearCurrentInstagramDetection() = mutableState.update { it.copy(currentInstagramDetection = null) }
}
