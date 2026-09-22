package com.example.tasktunnel.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.tasktunnel.accessibility.AccessibilityRuntime
import com.example.tasktunnel.accessibility.AccessibilityState
import com.example.tasktunnel.accessibility.ObservedInstagramDetection
import com.example.tasktunnel.accessibility.ObservedTikTokCapture
import com.example.tasktunnel.accessibility.ObservedYouTubeDetection
import com.example.tasktunnel.accessibility.SanitizedNode
import com.example.tasktunnel.accessibility.VisualQaOverlay
import com.example.tasktunnel.attention.AttentionDatabase
import com.example.tasktunnel.usage.SurfaceUsageInspectorState
import com.example.tasktunnel.usage.SurfaceUsageRepository
import com.example.tasktunnel.ui.theme.TaskTunnelTokens

@Composable
fun DeveloperScreen(
    runtime: AccessibilityState,
    enabledInSettings: Boolean,
    openSettings: () -> Unit,
    openInspector: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember { SurfaceUsageRepository(AttentionDatabase.getInstance(context).surfaceUsageSegmentDao()) }
    var usage by remember { mutableStateOf<SurfaceUsageInspectorState?>(null) }
    val scope = rememberCoroutineScope()
    fun refreshUsage() { scope.launch { usage = repository.inspect(System.currentTimeMillis()) } }
    LaunchedEffect(Unit) { refreshUsage() }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(TaskTunnelTokens.ScreenHorizontalPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Debug build only. These fields never appear in release UI.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Accessibility setting: ${if (enabledInSettings) "Enabled" else "Disabled"}")
                    Text("Service connection: ${if (runtime.connected) "Active" else "Disconnected"}")
                    Text("Foreground package: ${runtime.foregroundPackage ?: "Not observed"}")
                    Text("Relevant event: ${runtime.lastRelevantEvent ?: "None"}")
                }
            }
        }
        item { Button(onClick = openSettings) { Text("Open accessibility settings") } }
        item { val current = runtime.currentYouTubeDetection; DetectionCard(current ?: runtime.lastYouTubeDetection, current != null) }
        item { FingerprintCopyAction("Copy sanitized YouTube fingerprint", (runtime.currentYouTubeDetection ?: runtime.lastYouTubeDetection)?.fingerprint) }
        item { val current = runtime.currentInstagramDetection; InstagramDiagnosticCard(current ?: runtime.lastInstagramDetection, current != null) }
        item { FingerprintCopyAction("Copy sanitized Instagram fingerprint", (runtime.currentInstagramDetection ?: runtime.lastInstagramDetection)?.fingerprint) }
        item { val current = runtime.currentTikTokCapture; TikTokDiagnosticCard(current ?: runtime.lastTikTokCapture, current != null) }
        item { FingerprintCopyAction("Copy sanitized TikTok fingerprint", (runtime.currentTikTokCapture ?: runtime.lastTikTokCapture)?.fingerprint) }
        item { Button(onClick = openInspector, enabled = runtime.connected) { Text("Open sanitized inspector") } }
        item {
            SurfaceUsageInspector(usage, { refreshUsage() }) {
                scope.launch { repository.clear(); usage = repository.inspect(System.currentTimeMillis()) }
            }
        }
        item { Button(onClick = AccessibilityRuntime::requestTestOverlay, enabled = runtime.connected && !runtime.overlayPending) { Text(if (runtime.overlayPending) "Overlay scheduled…" else "Show test overlay in 3 seconds") } }
        item { Text("Visual QA overlays", style = MaterialTheme.typography.titleMedium) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VisualQaOverlay.entries.forEach { overlay ->
                    Button(
                        onClick = { AccessibilityRuntime.requestVisualQaOverlay(overlay) },
                        enabled = runtime.connected,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(overlay.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)) }
                }
            }
        }
        item { Text("Recent foreground transitions", style = MaterialTheme.typography.titleMedium) }
        if (runtime.packageHistory.isEmpty()) item { Text("None observed") }
        items(runtime.packageHistory) { Text("${it.packageName} — ${formatTime(it.observedAtMillis)}") }
    }
}

@Composable
private fun SurfaceUsageInspector(
    state: SurfaceUsageInspectorState?,
    refresh: () -> Unit,
    clear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Surface usage — today", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = refresh) { Text("Refresh") }
            Button(onClick = clear) { Text("Clear surface usage") }
        }
        state?.summaries?.forEach { summary ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(summary.app.displayName, style = MaterialTheme.typography.titleMedium)
                    Text("Tracked ${formatUsageDuration(summary.totalTrackedDurationMillis)} · Classified ${formatUsageDuration(summary.classifiedDurationMillis)} · Unclassified ${formatUsageDuration(summary.unclassifiedDurationMillis)}")
                    Text("Coverage ${(summary.coverage * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    summary.bySurface.entries.filter { it.key != null }.forEach { (surface, duration) ->
                        Text("${surface?.name}: ${formatUsageDuration(duration)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Text("Recent segments", style = MaterialTheme.typography.titleMedium)
        state?.recentSegments?.forEach { segment ->
            Text(
                "${segment.app.displayName} · ${segment.surface?.name ?: "Unclassified"}\n" +
                    "${formatTime(segment.startedAtMillis)} -> ${formatTime(segment.endedAtMillis)} · ${formatUsageDuration(segment.endedAtMillis - segment.startedAtMillis)}\n" +
                    "Task: ${segment.tunnelTask?.name ?: "No active Tunnel"} · ${segment.classification.name}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatUsageDuration(durationMillis: Long): String {
    val seconds = (durationMillis / 1_000L).coerceAtLeast(0L)
    val minutes = seconds / 60
    return if (minutes > 0) "${minutes}m ${seconds % 60}s" else "${seconds}s"
}

@Composable
private fun FingerprintCopyAction(label: String, fingerprint: String?) {
    val context = LocalContext.current
    var copied by remember(fingerprint) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(enabled = fingerprint != null, onClick = {
            fingerprint ?: return@Button
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Sanitized diagnostic", fingerprint))
            copied = true
        }) { Text(if (copied) "Sanitized fingerprint copied" else label) }
        Text("Developer debug only. Copying places this diagnostic in Android's system clipboard.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InstagramDiagnosticCard(observed: ObservedInstagramDetection?, current: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (current) "Current observed Instagram diagnostic" else "Last observed Instagram diagnostic", style = MaterialTheme.typography.titleMedium)
            if (observed == null) Text("No Instagram tree captured this service session.") else {
                Text("Surface: ${observed.detection.surface.name}")
                Text("Confidence: ${(observed.detection.confidence * 100).toInt()}% evidence strength")
                Text("Package: ${observed.packageName.ifEmpty { "Unavailable" }}")
                Text("Captured: ${formatTime(observed.capturedAtMillis)}")
                observed.detection.strongestSignals.forEach { Text("• $it") }
            }
        }
    }
}

@Composable
private fun DetectionCard(observed: ObservedYouTubeDetection?, current: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (current) "Current YouTube surface" else "Last observed YouTube detection", style = MaterialTheme.typography.titleMedium)
            if (observed == null) Text("No YouTube tree captured this service session.") else {
                Text("Surface: ${observed.detection.surface.name}")
                Text("Creator subscription: ${observed.detection.creatorSubscriptionState?.name ?: "UNKNOWN"}")
                Text("Confidence: ${(observed.detection.confidence * 100).toInt()}% evidence strength")
                Text("Package: ${observed.packageName.ifEmpty { "Unavailable" }}")
                Text("Captured: ${formatTime(observed.capturedAtMillis)}")
                observed.detection.strongestSignals.forEach { Text("• $it") }
            }
        }
    }
}

@Composable
private fun TikTokDiagnosticCard(observed: ObservedTikTokCapture?, current: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (current) "Current TikTok evidence" else "Last observed TikTok evidence", style = MaterialTheme.typography.titleMedium)
            if (observed == null) Text("No TikTok tree captured this service session.") else {
                Text("Classification: Unclassified evidence only")
                Text("Package: ${observed.packageName}")
                Text("Version: ${observed.versionName ?: "unknown"} (code ${observed.versionCode ?: "unknown"})")
                Text("Surface: ${observed.detection.surface.name}")
                Text("Confidence: ${(observed.detection.confidence * 100).toInt()}% evidence strength")
                Text("Captured: ${formatTime(observed.capturedAtMillis)}")
                Text("Freshness: ${if (current) "current" else "last observed; navigate and wait for a fresh capture"}", style = MaterialTheme.typography.bodySmall)
                observed.detection.strongestSignals.forEach { Text("• $it") }
            }
        }
    }
}

@Composable
fun InspectorScreen(runtime: AccessibilityState, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(TaskTunnelTokens.ScreenHorizontalPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Developer debug only. Captures class, resource ID, structure, and booleans; never text or content descriptions.") }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Capture supported app"); Text("Arm this, then switch apps.", style = MaterialTheme.typography.bodySmall) }
                Switch(runtime.inspectionArmed, AccessibilityRuntime::setInspectionArmed)
            }
        }
        item { Text("Status: ${runtime.inspectionStatus.name.lowercase().replace('_', ' ')}") }
        runtime.inspectionDetail?.let { detail -> item { Text(detail) } }
        item { Button(onClick = AccessibilityRuntime::clearSnapshot, enabled = runtime.snapshot != null) { Text("Clear captured tree") } }
        runtime.snapshot?.let { snapshot ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Last captured (not live)", style = MaterialTheme.typography.titleMedium)
                        Text("Source: ${snapshot.packageName}")
                        Text("Captured: ${formatTime(snapshot.capturedAtMillis)}")
                        Text("Nodes: ${snapshot.nodes.size}${if (snapshot.truncated) " (bounded/truncated)" else ""}")
                    }
                }
            }
            items(snapshot.nodes) { NodeRow(it) }
        }
    }
}

@Composable
private fun NodeRow(node: SanitizedNode) {
    Card(Modifier.fillMaxWidth().padding(start = (node.depth.coerceAtMost(8) * 6).dp)) {
        Column(Modifier.padding(8.dp)) {
            Text("d${node.depth} ${node.className ?: "unknown class"}")
            Text(node.resourceId ?: "no resource ID", style = MaterialTheme.typography.bodySmall)
            Text("children=${node.childCount} clickable=${node.clickable} scrollable=${node.scrollable} editable=${node.editable} selected=${node.selected} enabled=${node.enabled} visible=${node.visibleToUser} parent=${node.parentIndex ?: "none"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
