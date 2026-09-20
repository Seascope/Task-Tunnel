package com.example.tasktunnel

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tasktunnel.accessibility.AccessibilityRuntime
import com.example.tasktunnel.accessibility.AccessibilityState
import com.example.tasktunnel.accessibility.ObservedYouTubeDetection
import com.example.tasktunnel.accessibility.SanitizedNode
import com.example.tasktunnel.ui.theme.TaskTunnelTheme
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private var serviceEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaskTunnelTheme {
                val runtime by AccessibilityRuntime.state.collectAsState()
                var inspectorOpen by remember { mutableStateOf(false) }
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    if (inspectorOpen && BuildConfig.DEBUG) {
                        InspectorScreen(runtime, { inspectorOpen = false }, Modifier.padding(padding))
                    } else {
                        DeveloperScreen(
                            runtime,
                            serviceEnabled,
                            { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            { inspectorOpen = true },
                            Modifier.padding(padding),
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        serviceEnabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName && it.resolveInfo.serviceInfo.name.endsWith("TaskTunnelAccessibilityService") }
    }
}

@Composable
private fun DeveloperScreen(
    runtime: AccessibilityState,
    enabledInSettings: Boolean,
    openSettings: () -> Unit,
    openInspector: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Task Tunnel — M1 developer proof", style = MaterialTheme.typography.headlineSmall) }
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
        item {
            val current = runtime.currentYouTubeDetection
            DetectionCard(current ?: runtime.lastYouTubeDetection, current != null)
        }
        if (BuildConfig.DEBUG) {
            item { Button(onClick = openInspector, enabled = runtime.connected) { Text("Open sanitized inspector") } }
            item {
                Button(onClick = { AccessibilityRuntime.requestTestOverlay() }, enabled = runtime.connected && !runtime.overlayPending) {
                    Text(if (runtime.overlayPending) "Overlay scheduled…" else "Show test overlay in 3 seconds")
                }
            }
            item { Text("After scheduling the overlay, switch to another app. Close the overlay to interact with that app again.") }
        }
        item { Text("Recent foreground transitions", style = MaterialTheme.typography.titleMedium) }
        if (runtime.packageHistory.isEmpty()) item { Text("None observed") }
        items(runtime.packageHistory) { transition ->
            Text("${transition.packageName} — ${formatTime(transition.observedAtMillis)}")
        }
    }
}

@Composable
private fun DetectionCard(observed: ObservedYouTubeDetection?, current: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (current) "Current YouTube surface" else "Last observed YouTube detection",
                style = MaterialTheme.typography.titleMedium,
            )
            if (observed == null) {
                Text("No YouTube tree captured this service session.")
            } else {
                Text("Surface: ${observed.detection.surface.name}")
                Text("Confidence: ${(observed.detection.confidence * 100).toInt()}% evidence strength")
                Text("Package: ${observed.packageName.ifEmpty { "Unavailable" }}")
                Text("Captured: ${formatTime(observed.capturedAtMillis)}")
                Text("Strongest sanitized signals:")
                if (observed.detection.strongestSignals.isEmpty()) Text("None")
                observed.detection.strongestSignals.forEach { Text("• $it") }
            }
        }
    }
}

@Composable
private fun InspectorScreen(runtime: AccessibilityState, goBack: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Button(onClick = goBack) { Text("Back") } }
        item { Text("Sanitized tree inspector", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Developer debug only. Captures class, resource ID, structure, and booleans; never text or content descriptions.") }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Capture when a supported app is foreground")
                    Text("Arm this, then switch to Instagram or YouTube.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(runtime.inspectionArmed, onCheckedChange = AccessibilityRuntime::setInspectionArmed)
            }
        }
        item { Text("Status: ${runtime.inspectionStatus.name.lowercase().replace('_', ' ')}") }
        runtime.inspectionDetail?.let { detail -> item { Text(detail) } }
        item { Button(onClick = AccessibilityRuntime::clearSnapshot, enabled = runtime.snapshot != null) { Text("Clear captured tree") } }
        runtime.snapshot?.let { snapshot ->
            item {
                val ageSeconds = ((System.currentTimeMillis() - snapshot.capturedAtMillis).coerceAtLeast(0) / 1_000)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Last captured (not live)", style = MaterialTheme.typography.titleMedium)
                        Text("Source: ${snapshot.packageName}")
                        Text("Captured: ${formatTime(snapshot.capturedAtMillis)} ($ageSeconds seconds ago)")
                        Text("Nodes: ${snapshot.nodes.size}${if (snapshot.truncated) " (bounded/truncated)" else ""}")
                    }
                }
            }
            items(snapshot.nodes) { node -> NodeRow(node) }
        }
    }
}

@Composable
private fun NodeRow(node: SanitizedNode) {
    Card(Modifier.fillMaxWidth().padding(start = (node.depth.coerceAtMost(8) * 6).dp)) {
        Column(Modifier.padding(8.dp)) {
            Text("d${node.depth} ${node.className ?: "unknown class"}")
            Text(node.resourceId ?: "no resource ID", style = MaterialTheme.typography.bodySmall)
            Text(
                "children=${node.childCount} clickable=${node.clickable} scrollable=${node.scrollable} " +
                    "editable=${node.editable} enabled=${node.enabled} visible=${node.visibleToUser}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatTime(timeMillis: Long): String = DateFormat.getTimeInstance().format(Date(timeMillis))
