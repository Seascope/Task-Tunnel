package com.example.tasktunnel

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tasktunnel.accessibility.AccessibilityRuntime
import com.example.tasktunnel.accessibility.AccessibilityState
import com.example.tasktunnel.accessibility.ObservedInstagramDetection
import com.example.tasktunnel.accessibility.ObservedYouTubeDetection
import com.example.tasktunnel.accessibility.SanitizedNode
import com.example.tasktunnel.attention.AttentionEpisode
import com.example.tasktunnel.attention.AttentionUiState
import com.example.tasktunnel.attention.AttentionViewModel
import com.example.tasktunnel.attention.episodeSubtitle
import com.example.tasktunnel.attention.episodeTitle
import com.example.tasktunnel.attention.eventDescription
import com.example.tasktunnel.attention.taskLabel
import com.example.tasktunnel.drift.DriftAppCatalog
import com.example.tasktunnel.drift.DriftPoolPreferences
import com.example.tasktunnel.onboarding.OnboardingFlow
import com.example.tasktunnel.onboarding.OnboardingPreferences
import com.example.tasktunnel.onboarding.OnboardingProgress
import com.example.tasktunnel.onboarding.OnboardingStep
import com.example.tasktunnel.protection.AppCompatibility
import com.example.tasktunnel.protection.DeveloperDiagnosticsGate
import com.example.tasktunnel.protection.DiagnosticReport
import com.example.tasktunnel.protection.InstalledAppStatus
import com.example.tasktunnel.protection.ProtectionHealth
import com.example.tasktunnel.protection.ProtectionLevel
import com.example.tasktunnel.protection.ProtectionSnapshot
import com.example.tasktunnel.protection.ProtectionSnapshotFactory
import com.example.tasktunnel.ui.theme.TaskTunnelTheme
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private var serviceEnabled by mutableStateOf(false)
    private var lifecycleRefresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshAccessibilityState()
        setContent {
            TaskTunnelTheme {
                val runtime by AccessibilityRuntime.state.collectAsState()
                val attentionViewModel: AttentionViewModel = viewModel()
                val attention by attentionViewModel.uiState.collectAsState()
                var destination by remember { mutableStateOf(MainDestination.ATTENTION) }
                var selectedEpisodeId by remember { mutableStateOf<String?>(null) }
                var disclosureReturn by remember { mutableStateOf(MainDestination.PROTECTION) }
                var onboarding by remember { mutableStateOf(OnboardingPreferences.load(this@MainActivity)) }
                var selectedDriftPackages by remember { mutableStateOf(DriftPoolPreferences.load(this@MainActivity)) }

                val saveOnboarding: (OnboardingProgress) -> Unit = { progress ->
                    onboarding = progress
                    OnboardingPreferences.save(this@MainActivity, progress)
                }
                val setDriftAppEnabled: (String, Boolean) -> Unit = { packageName, enabled ->
                    selectedDriftPackages = if (enabled) selectedDriftPackages + packageName else selectedDriftPackages - packageName
                    DriftPoolPreferences.save(this@MainActivity, selectedDriftPackages)
                    AccessibilityRuntime.setDriftPool(selectedDriftPackages)
                }
                val setDriftEnabled: (Boolean) -> Unit = { enabled ->
                    selectedDriftPackages = if (enabled) DriftAppCatalog.knownPackages else emptySet()
                    DriftPoolPreferences.save(this@MainActivity, selectedDriftPackages)
                    AccessibilityRuntime.setDriftPool(selectedDriftPackages)
                }
                val openAccessibilitySettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                val snapshot = remember(
                    serviceEnabled, runtime.connected, runtime.lastHeartbeatMillis, selectedDriftPackages,
                    attention.historyAvailable, lifecycleRefresh,
                ) {
                    ProtectionSnapshotFactory.create(
                        this@MainActivity, serviceEnabled, runtime, selectedDriftPackages, attention.historyAvailable,
                    )
                }

                if (!onboarding.completed) {
                    OnboardingScreen(
                        progress = onboarding,
                        accessibilityEnabled = serviceEnabled,
                        selectedDriftPackages = selectedDriftPackages,
                        setDriftEnabled = setDriftEnabled,
                        setDriftAppEnabled = setDriftAppEnabled,
                        advance = { saveOnboarding(OnboardingFlow.next(onboarding)) },
                        openAccessibilitySettings = {
                            if (onboarding.step == OnboardingStep.DISCLOSURE) {
                                saveOnboarding(OnboardingFlow.next(onboarding))
                            }
                            openAccessibilitySettings()
                        },
                        completeLater = { saveOnboarding(OnboardingFlow.completeLater()) },
                    )
                } else {
                    val showNavigation = destination == MainDestination.ATTENTION || destination == MainDestination.PROTECTION
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = { if (showNavigation) PrimaryNavigation(destination) { destination = it } },
                    ) { padding ->
                        val contentModifier = Modifier.padding(padding)
                        when (destination) {
                            MainDestination.ATTENTION -> AttentionScreen(
                                attention, runtime, snapshot.health,
                                openEpisode = { selectedEpisodeId = it.id; destination = MainDestination.EPISODE },
                                openSettings = { destination = MainDestination.SETTINGS },
                                openDeveloper = { destination = MainDestination.DEVELOPER },
                                modifier = contentModifier,
                            )
                            MainDestination.EPISODE -> EpisodeDetailScreen(
                                attention.episodes.firstOrNull { it.id == selectedEpisodeId },
                                { destination = MainDestination.ATTENTION }, contentModifier,
                            )
                            MainDestination.PROTECTION -> ProtectionScreen(
                                snapshot, selectedDriftPackages, setDriftEnabled, setDriftAppEnabled,
                                repairAccessibility = {
                                    disclosureReturn = MainDestination.PROTECTION
                                    destination = MainDestination.DISCLOSURE
                                },
                                openDiagnostics = { destination = MainDestination.DIAGNOSTICS },
                                openSettings = { destination = MainDestination.SETTINGS },
                                modifier = contentModifier,
                            )
                            MainDestination.SETTINGS -> SettingsScreen(
                                BuildConfig.VERSION_NAME, attention.historyAvailable, attentionViewModel::clearHistory,
                                openDiagnostics = { destination = MainDestination.DIAGNOSTICS },
                                openDisclosure = {
                                    disclosureReturn = MainDestination.SETTINGS
                                    destination = MainDestination.DISCLOSURE
                                },
                                openDeveloper = { destination = MainDestination.DEVELOPER },
                                goBack = { destination = MainDestination.ATTENTION },
                                modifier = contentModifier,
                            )
                            MainDestination.DIAGNOSTICS -> DiagnosticsScreen(
                                snapshot.diagnosticReport, { destination = MainDestination.SETTINGS }, contentModifier,
                            )
                            MainDestination.DISCLOSURE -> AccessibilityDisclosureScreen(
                                serviceEnabled,
                                continueToSettings = { destination = disclosureReturn; openAccessibilitySettings() },
                                goBack = { destination = disclosureReturn },
                                modifier = contentModifier,
                            )
                            MainDestination.DEVELOPER -> if (DeveloperDiagnosticsGate.isAvailable(BuildConfig.DEBUG)) {
                                DeveloperScreen(
                                    runtime, serviceEnabled, openAccessibilitySettings,
                                    { destination = MainDestination.INSPECTOR },
                                    { destination = MainDestination.SETTINGS }, contentModifier,
                                )
                            } else {
                                DiagnosticsScreen(snapshot.diagnosticReport, { destination = MainDestination.SETTINGS }, contentModifier)
                            }
                            MainDestination.INSPECTOR -> if (DeveloperDiagnosticsGate.isAvailable(BuildConfig.DEBUG)) {
                                InspectorScreen(runtime, { destination = MainDestination.DEVELOPER }, contentModifier)
                            } else {
                                DiagnosticsScreen(snapshot.diagnosticReport, { destination = MainDestination.SETTINGS }, contentModifier)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAccessibilityState()
        lifecycleRefresh += 1
    }

    private fun refreshAccessibilityState() {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        serviceEnabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName && it.resolveInfo.serviceInfo.name.endsWith("TaskTunnelAccessibilityService") }
    }
}

private enum class MainDestination { ATTENTION, EPISODE, PROTECTION, SETTINGS, DIAGNOSTICS, DISCLOSURE, DEVELOPER, INSPECTOR }

@Composable
private fun PrimaryNavigation(destination: MainDestination, navigate: (MainDestination) -> Unit) {
    NavigationBar {
        NavigationBarItem(destination == MainDestination.ATTENTION, { navigate(MainDestination.ATTENTION) }, {}, label = { Text("Attention") })
        NavigationBarItem(destination == MainDestination.PROTECTION, { navigate(MainDestination.PROTECTION) }, {}, label = { Text("Protection") })
    }
}

@Composable
private fun AttentionScreen(
    uiState: AttentionUiState,
    runtime: AccessibilityState,
    health: ProtectionHealth,
    openEpisode: (AttentionEpisode) -> Unit,
    openSettings: () -> Unit,
    openDeveloper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            PageHeader("Attention", openSettings)
            Text(health.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        runtime.tunnelState.activeSession?.let { session ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Active Task Tunnel", style = MaterialTheme.typography.labelLarge)
                        Text(session.app.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(taskLabel(session.task), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (BuildConfig.DEBUG) item { TextButton(onClick = openDeveloper) { Text("Developer diagnostics") } }
        item { Text("Recent episodes", style = MaterialTheme.typography.titleLarge) }
        if (!uiState.historyAvailable) {
            item { Text("Attention history is unavailable. Protection can still run; reopen Task Tunnel and use diagnostics if this continues.") }
        } else if (uiState.episodes.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your attention history will appear here as you use Task Tunnel.")
                    Text("You’ll see intentions, meaningful transitions, check-ins, and your choices — stored only on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(uiState.episodes, key = { it.id }) { episode ->
                Card(onClick = { openEpisode(episode) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(episodeTitle(episode), style = MaterialTheme.typography.titleMedium)
                            Text(formatTime(episode.startedAtMillis), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(episodeSubtitle(episode), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatEpisodeRange(episode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Last seven days", style = MaterialTheme.typography.titleSmall)
                    Text("${uiState.metrics.driftEpisodesLastSevenDays} Drift episodes")
                    Text("A quiet count of check-ins, not a score.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PageHeader(title: String, openSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        TextButton(onClick = openSettings) { Text("Settings") }
    }
}

@Composable
private fun EpisodeDetailScreen(episode: AttentionEpisode?, goBack: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedButton(onClick = goBack) { Text("Back to Attention") } }
        if (episode == null) item { Text("This episode is no longer in recent history.") } else {
            item {
                Text(episodeTitle(episode), style = MaterialTheme.typography.headlineSmall)
                Text(episodeSubtitle(episode), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatEpisodeRange(episode), style = MaterialTheme.typography.bodySmall)
            }
            items(episode.events, key = { "${it.id}:${it.timestampMillis}:${it.subtype}" }) { event ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(formatTime(event.timestampMillis), style = MaterialTheme.typography.labelMedium)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(eventDescription(event)); HorizontalDivider()
                    }
                }
            }
            item { Text("This history stays on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ProtectionScreen(
    snapshot: ProtectionSnapshot,
    selectedDriftPackages: Set<String>,
    setDriftEnabled: (Boolean) -> Unit,
    setDriftAppEnabled: (String, Boolean) -> Unit,
    repairAccessibility: () -> Unit,
    openDiagnostics: () -> Unit,
    openSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(12.dp)); PageHeader("Protection", openSettings) }
        item {
            Text("Is it working?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(snapshot.health.title, style = MaterialTheme.typography.headlineSmall)
            Text(snapshot.health.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (snapshot.health.level != ProtectionLevel.ACTIVE) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = repairAccessibility) { Text("Enable Accessibility access") }
                if (snapshot.health.backgroundConcern) Text(
                    "If access is already enabled, turn Task Tunnel off and on in Accessibility settings. If interruptions continue, check whether Android is restricting Task Tunnel in the background.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item { HorizontalDivider() }
        item { Text("What is protected?", style = MaterialTheme.typography.titleLarge) }
        item { SupportedAppSection(snapshot.apps.first { it.displayName == "Instagram" }, listOf("Reply to messages", "Browse intentionally")) }
        item { HorizontalDivider() }
        item { SupportedAppSection(snapshot.apps.first { it.displayName == "YouTube" }, listOf("Search / watch something specific", "Browse intentionally")) }
        item { HorizontalDivider() }
        item { Text("How is it configured?", style = MaterialTheme.typography.titleLarge) }
        item { DriftConfigurationSection(selectedDriftPackages, setDriftEnabled, setDriftAppEnabled) }
        item {
            OutlinedButton(onClick = openDiagnostics) { Text("Protection diagnostics") }
            Text("Copy technical status only — never app content or Attention history.", style = MaterialTheme.typography.bodySmall)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SupportedAppSection(app: InstalledAppStatus, tasks: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(app.displayName, style = MaterialTheme.typography.titleMedium)
        Text(
            if (app.compatibility == AppCompatibility.NOT_INSTALLED) "Not installed — Task Tunnel will be ready if you install it." else "Installed",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        tasks.forEach { Text("• $it") }
    }
}

@Composable
private fun DriftConfigurationSection(
    selectedPackages: Set<String>,
    setDriftEnabled: (Boolean) -> Unit,
    setAppEnabled: (String, Boolean) -> Unit,
) {
    val enabled = selectedPackages.isNotEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Drift Detection", style = MaterialTheme.typography.titleMedium)
                Text("Notice rapid switching between selected distracting apps.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(enabled, setDriftEnabled)
        }
        if (!enabled) Text("Drift Detection is optional and currently off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DriftAppCatalog.apps.forEach { app ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(app.displayName, Modifier.weight(1f))
                Switch(app.packageName in selectedPackages, { setAppEnabled(app.packageName, it) }, enabled = enabled)
            }
        }
    }
}

@Composable
private fun OnboardingScreen(
    progress: OnboardingProgress,
    accessibilityEnabled: Boolean,
    selectedDriftPackages: Set<String>,
    setDriftEnabled: (Boolean) -> Unit,
    setDriftAppEnabled: (String, Boolean) -> Unit,
    advance: () -> Unit,
    openAccessibilitySettings: () -> Unit,
    completeLater: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Task Tunnel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        when (progress.step) {
            OnboardingStep.VALUE -> {
                item { Text("Keep the reason you opened the app", style = MaterialTheme.typography.headlineMedium) }
                item { Text("Use Instagram and YouTube for what you intended. Task Tunnel adds a calm pause when you move into a distracting surface.") }
                item { Button(onClick = advance) { Text("See how it works") } }
            }
            OnboardingStep.HOW_IT_WORKS -> {
                item { Text("A quiet layer over the apps you already use", style = MaterialTheme.typography.headlineMedium) }
                item { Text("Choose a purpose when Instagram or YouTube opens. Stay in the native app. If you enter Reels, Explore, or Shorts outside that purpose, choose Return, Allow anyway, or End Tunnel.") }
                item { Text("Uncertain screens always fail open. Intentional browsing is a first-class choice.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { Button(onClick = advance) { Text("Privacy and Accessibility") } }
            }
            OnboardingStep.DISCLOSURE -> {
                item { DisclosureContent() }
                item { Button(onClick = openAccessibilitySettings) { Text("Continue to Accessibility settings") } }
            }
            OnboardingStep.VERIFY -> {
                item { Text(if (accessibilityEnabled) "Protection is ready" else "Accessibility access is still off", style = MaterialTheme.typography.headlineMedium) }
                item { Text(if (accessibilityEnabled) "Task Tunnel can now recognize supported surfaces on this device." else "You can continue using Task Tunnel and enable access later from Protection.") }
                if (!accessibilityEnabled) item { OutlinedButton(onClick = openAccessibilitySettings) { Text("Try Accessibility settings again") } }
                item { Button(onClick = advance) { Text(if (accessibilityEnabled) "Choose protection setup" else "Continue without access") } }
            }
            OnboardingStep.CONFIGURE -> {
                item { Text("Your protection setup", style = MaterialTheme.typography.headlineMedium) }
                item { Text("Instagram: Reply to messages or browse intentionally") }
                item { Text("YouTube: Search / watch something specific or browse intentionally") }
                item { HorizontalDivider() }
                item { DriftConfigurationSection(selectedDriftPackages, setDriftEnabled, setDriftAppEnabled) }
                item { Button(onClick = advance) { Text("Finish setup") } }
            }
        }
        if (progress.step != OnboardingStep.CONFIGURE) item { TextButton(onClick = completeLater) { Text("Set up later") } }
    }
}

@Composable
private fun DisclosureContent() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Why Task Tunnel needs Accessibility access", style = MaterialTheme.typography.headlineMedium)
        Text("What it can see", style = MaterialTheme.typography.titleMedium)
        Text("Enough of the visible interface in supported apps to identify Reels, Explore, Shorts, Messages, Search, and normal videos.")
        Text("Why", style = MaterialTheme.typography.titleMedium)
        Text("To determine whether you moved outside the purpose you declared and show a user-controlled intervention.")
        Text("What is stored", style = MaterialTheme.typography.titleMedium)
        Text("Only structured task, session, surface, intervention, and decision events needed for Task Tunnel and Attention.")
        Text("What is not stored", style = MaterialTheme.typography.titleMedium)
        Text("No screenshots, private message contents, accessibility text history, usernames as Attention history, raw accessibility trees, or raw fingerprints.")
        Text("Processing stays on this device for the MVP. There is no account or cloud backend.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Task Tunnel uses AccessibilityService for its digital-wellbeing protection feature.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AccessibilityDisclosureScreen(accessEnabled: Boolean, continueToSettings: () -> Unit, goBack: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { OutlinedButton(onClick = goBack) { Text("Back") } }
        item { DisclosureContent() }
        item { Text(if (accessEnabled) "Accessibility access is currently on." else "Accessibility access is currently off.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Button(onClick = continueToSettings) { Text("Continue to Accessibility settings") } }
    }
}

@Composable
private fun SettingsScreen(
    appVersion: String,
    historyAvailable: Boolean,
    clearHistory: () -> Unit,
    openDiagnostics: () -> Unit,
    openDisclosure: () -> Unit,
    openDeveloper: () -> Unit,
    goBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var historyCleared by remember { mutableStateOf(false) }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear Attention history?") },
        text = { Text("This permanently removes the structured Attention history stored on this device.") },
        confirmButton = { Button(onClick = { clearHistory(); confirmClear = false; historyCleared = true }) { Text("Clear history") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
    LazyColumn(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { OutlinedButton(onClick = goBack) { Text("Back to Attention") } }
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Privacy and data", style = MaterialTheme.typography.titleLarge) }
        item { Text("Attention history is stored on this device. It contains structured events, not screenshots or private message contents.") }
        item {
            OutlinedButton(onClick = { confirmClear = true }, enabled = historyAvailable) { Text("Clear Attention history") }
            if (historyCleared) Text("Attention history cleared.", style = MaterialTheme.typography.bodySmall)
            if (!historyAvailable) Text("History is currently unavailable. Reopen Task Tunnel and try again.", style = MaterialTheme.typography.bodySmall)
        }
        item { TextButton(onClick = openDisclosure) { Text("Privacy and Accessibility explanation") } }
        item { HorizontalDivider() }
        item { Text("Help and about", style = MaterialTheme.typography.titleLarge) }
        item { OutlinedButton(onClick = openDiagnostics) { Text("Protection diagnostics") } }
        item { Text("Task Tunnel $appVersion") }
        item { Text("Local-first Android MVP with no account or cloud backend.", style = MaterialTheme.typography.bodySmall) }
        if (DeveloperDiagnosticsGate.isAvailable(BuildConfig.DEBUG)) {
            item { HorizontalDivider() }
            item { TextButton(onClick = openDeveloper) { Text("Developer options") } }
        }
    }
}

@Composable
private fun DiagnosticsScreen(report: DiagnosticReport, goBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var copied by remember(report) { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedButton(onClick = goBack) { Text("Back") } }
        item { Text("Protection diagnostics", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Technical status only. This never includes app content, usernames, screenshots, detector internals, or Attention history.") }
        item { Text(report.asText(), style = MaterialTheme.typography.bodySmall) }
        item {
            Button(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Task Tunnel diagnostics", report.asText()))
                copied = true
            }) { Text(if (copied) "Diagnostics copied" else "Copy diagnostics") }
        }
    }
}

@Composable
private fun DeveloperScreen(
    runtime: AccessibilityState,
    enabledInSettings: Boolean,
    openSettings: () -> Unit,
    openInspector: () -> Unit,
    goBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedButton(onClick = goBack) { Text("Back to Settings") } }
        item { Text("Task Tunnel — developer diagnostics", style = MaterialTheme.typography.headlineSmall) }
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
        item { Button(onClick = openInspector, enabled = runtime.connected) { Text("Open sanitized inspector") } }
        item { Button(onClick = { AccessibilityRuntime.requestTestOverlay() }, enabled = runtime.connected && !runtime.overlayPending) { Text(if (runtime.overlayPending) "Overlay scheduled…" else "Show test overlay in 3 seconds") } }
        item { Text("Recent foreground transitions", style = MaterialTheme.typography.titleMedium) }
        if (runtime.packageHistory.isEmpty()) item { Text("None observed") }
        items(runtime.packageHistory) { Text("${it.packageName} — ${formatTime(it.observedAtMillis)}") }
    }
}

@Composable
private fun FingerprintCopyAction(label: String, fingerprint: String?) {
    val context = LocalContext.current
    var copied by remember(fingerprint) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(enabled = fingerprint != null, onClick = {
            fingerprint ?: return@Button
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Sanitized diagnostic", fingerprint)); copied = true
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
                Text("Confidence: ${(observed.detection.confidence * 100).toInt()}% evidence strength")
                Text("Package: ${observed.packageName.ifEmpty { "Unavailable" }}")
                Text("Captured: ${formatTime(observed.capturedAtMillis)}")
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

private fun formatTime(timeMillis: Long): String = DateFormat.getTimeInstance().format(Date(timeMillis))
private fun formatEpisodeRange(episode: AttentionEpisode): String {
    val start = formatTime(episode.startedAtMillis); val end = formatTime(episode.endedAtMillis)
    return if (start == end) start else "$start – $end"
}
