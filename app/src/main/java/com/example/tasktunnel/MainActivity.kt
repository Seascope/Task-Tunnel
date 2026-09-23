package com.example.tasktunnel

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tasktunnel.accessibility.AccessibilityRuntime
import com.example.tasktunnel.attention.AttentionViewModel
import com.example.tasktunnel.drift.DriftAppCatalog
import com.example.tasktunnel.drift.DriftPoolPreferences
import com.example.tasktunnel.onboarding.OnboardingFlow
import com.example.tasktunnel.onboarding.OnboardingPreferences
import com.example.tasktunnel.onboarding.OnboardingProgress
import com.example.tasktunnel.onboarding.OnboardingStep
import com.example.tasktunnel.notification.TunnelNotificationPreferences
import com.example.tasktunnel.notification.TunnelNotificationController
import com.example.tasktunnel.protection.DeveloperDiagnosticsGate
import com.example.tasktunnel.protection.ProtectionHealth
import com.example.tasktunnel.protection.ProtectionLevel
import com.example.tasktunnel.protection.ProtectionMasterPreferences
import com.example.tasktunnel.protection.ProtectionSnapshotFactory
import com.example.tasktunnel.ui.AccessibilityDisclosureScreen
import com.example.tasktunnel.ui.AttentionScreen
import com.example.tasktunnel.ui.DeveloperScreen
import com.example.tasktunnel.ui.DiagnosticsScreen
import com.example.tasktunnel.ui.EpisodeDetailScreen
import com.example.tasktunnel.ui.InspectorScreen
import com.example.tasktunnel.ui.OnboardingScreen
import com.example.tasktunnel.ui.PrimaryDestination
import com.example.tasktunnel.ui.ProtectionScreen
import com.example.tasktunnel.ui.ReviewScreen
import com.example.tasktunnel.ui.SecondaryScaffold
import com.example.tasktunnel.ui.SettingsScreen
import com.example.tasktunnel.ui.TaskTunnelScaffold
import com.example.tasktunnel.ui.home.HomeScreen
import com.example.tasktunnel.ui.home.HomeViewModel
import com.example.tasktunnel.ui.theme.TaskTunnelTheme
import com.example.tasktunnel.tunnel.IntentionalCheckInPreferences

class MainActivity : ComponentActivity() {
    private var serviceEnabled by mutableStateOf(false)
    private var lifecycleRefresh by mutableIntStateOf(0)
    private var notificationControlsEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshAccessibilityState()
        refreshNotificationState()
        setContent {
            TaskTunnelTheme {
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) {
                    refreshNotificationState()
                    TunnelNotificationPreferences.markPermissionOffered(this@MainActivity)
                }
                val runtime by AccessibilityRuntime.state.collectAsState()
                val attentionViewModel: AttentionViewModel = viewModel()
                val attention by attentionViewModel.uiState.collectAsState()
                val homeViewModel: HomeViewModel = viewModel()
                val home by homeViewModel.uiState.collectAsState()
                LaunchedEffect(lifecycleRefresh) { homeViewModel.refresh() }
                var destination by remember { mutableStateOf(MainDestination.HOME) }
                var lastPrimary by remember { mutableStateOf(MainDestination.HOME) }
                var selectedEpisodeId by remember { mutableStateOf<String?>(null) }
                var disclosureReturn by remember { mutableStateOf(MainDestination.PROTECTION) }
                var onboarding by remember { mutableStateOf(OnboardingPreferences.load(this@MainActivity)) }
                var selectedDriftPackages by remember { mutableStateOf(DriftPoolPreferences.load(this@MainActivity)) }
                val availableDriftApps = remember(lifecycleRefresh) {
                    DriftAppCatalog.installedLaunchableApps(this@MainActivity)
                }
                var intentionalCheckInsEnabled by remember { mutableStateOf(IntentionalCheckInPreferences.load(this@MainActivity)) }
                var protectionEnabled by remember { mutableStateOf(ProtectionMasterPreferences.load(this@MainActivity)) }
                var showNotificationOffer by remember { mutableStateOf(false) }
                val notificationPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                LaunchedEffect(onboarding.completed, notificationPermissionGranted, protectionEnabled) {
                    // Only offer the runtime permission here. A user-disabled app/channel setting is
                    // an explicit system preference and should be repaired from Settings, not nagged
                    // by the first-run permission dialog.
                    showNotificationOffer = protectionEnabled &&
                        onboarding.completed &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !notificationPermissionGranted &&
                        !TunnelNotificationPreferences.wasPermissionOffered(this@MainActivity)
                }
                if (showNotificationOffer) {
                    AlertDialog(
                        onDismissRequest = {
                            TunnelNotificationPreferences.markPermissionOffered(this@MainActivity)
                            showNotificationOffer = false
                        },
                        title = { Text("Control active tunnels from notifications") },
                        text = { Text("Continue check-ins, change purpose, or end a tunnel directly from the notification shade.") },
                        confirmButton = {
                            TextButton(onClick = {
                                TunnelNotificationPreferences.markPermissionOffered(this@MainActivity)
                                showNotificationOffer = false
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }) { Text("Allow notifications") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                TunnelNotificationPreferences.markPermissionOffered(this@MainActivity)
                                showNotificationOffer = false
                            }) { Text("Not now") }
                        },
                    )
                }

                val saveOnboarding: (OnboardingProgress) -> Unit = { progress ->
                    onboarding = progress
                    OnboardingPreferences.save(this@MainActivity, progress)
                }
                val setDriftAppEnabled: (String, Boolean) -> Unit = { packageName, enabled ->
                    selectedDriftPackages = if (enabled) selectedDriftPackages + packageName else selectedDriftPackages - packageName
                    DriftPoolPreferences.save(this@MainActivity, selectedDriftPackages)
                    AccessibilityRuntime.setDriftPool(selectedDriftPackages)
                }
                val setDriftEnabled: (Boolean) -> Boolean = { enabled ->
                    selectedDriftPackages = if (enabled) {
                        DriftPoolPreferences.restoreSelection(this@MainActivity)
                    } else {
                        emptySet()
                    }
                    DriftPoolPreferences.save(this@MainActivity, selectedDriftPackages)
                    AccessibilityRuntime.setDriftPool(selectedDriftPackages)
                    !enabled || selectedDriftPackages.isNotEmpty()
                }
                val setProtectionEnabled: (Boolean) -> Unit = { enabled ->
                    protectionEnabled = enabled
                    ProtectionMasterPreferences.save(this@MainActivity, enabled)
                    if (!enabled) showNotificationOffer = false
                    AccessibilityRuntime.setProtectionEnabled(enabled)
                    homeViewModel.refresh()
                }
                val openAccessibilitySettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                val snapshot = remember(
                    serviceEnabled,
                    runtime.connected,
                    runtime.lastHeartbeatMillis,
                    selectedDriftPackages,
                    attention.historyAvailable,
                    lifecycleRefresh,
                ) {
                    ProtectionSnapshotFactory.create(
                        this@MainActivity,
                        serviceEnabled,
                        runtime,
                        selectedDriftPackages,
                        attention.historyAvailable,
                    )
                }

                val displayHealth = if (protectionEnabled) {
                    snapshot.health
                } else {
                    ProtectionHealth(
                        level = ProtectionLevel.OFF,
                        title = "Protection paused",
                        summary = "Task Tunnel services are turned off. Your settings are preserved.",
                    )
                }

                if (!onboarding.completed) {
                    OnboardingScreen(
                        progress = onboarding,
                        accessibilityEnabled = serviceEnabled,
                        selectedDriftPackages = selectedDriftPackages,
                        availableDriftApps = availableDriftApps,
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
                    BackHandler(
                        enabled = destination != MainDestination.ATTENTION &&
                            destination != MainDestination.REVIEW &&
                            destination != MainDestination.PROTECTION &&
                            destination != MainDestination.HOME,
                    ) {
                        destination = when (destination) {
                            MainDestination.EPISODE -> MainDestination.ATTENTION
                            MainDestination.REVIEW -> MainDestination.REVIEW
                            MainDestination.SETTINGS -> lastPrimary
                            MainDestination.DIAGNOSTICS -> MainDestination.SETTINGS
                            MainDestination.DISCLOSURE -> disclosureReturn
                            MainDestination.DEVELOPER -> MainDestination.SETTINGS
                            MainDestination.INSPECTOR -> MainDestination.DEVELOPER
                            MainDestination.HOME,
                            MainDestination.ATTENTION,
                            MainDestination.PROTECTION,
                            -> destination
                        }
                    }
                    when (destination) {
                        MainDestination.HOME,
                        MainDestination.ATTENTION,
                        MainDestination.REVIEW,
                        MainDestination.PROTECTION,
                        -> {
                            val primary = when (destination) {
                                MainDestination.HOME -> PrimaryDestination.HOME
                                MainDestination.ATTENTION -> PrimaryDestination.ATTENTION
                                MainDestination.REVIEW -> PrimaryDestination.REVIEW
                                MainDestination.PROTECTION -> PrimaryDestination.PROTECTION
                                else -> PrimaryDestination.HOME
                            }
                            TaskTunnelScaffold(
                                destination = primary,
                                onNavigate = {
                                    destination = when (it) {
                                        PrimaryDestination.HOME -> MainDestination.HOME
                                        PrimaryDestination.ATTENTION -> MainDestination.ATTENTION
                                        PrimaryDestination.REVIEW -> MainDestination.REVIEW
                                        PrimaryDestination.PROTECTION -> MainDestination.PROTECTION
                                    }
                                    lastPrimary = destination
                                },
                                onOpenSettings = { destination = MainDestination.SETTINGS },
                            ) { contentModifier ->
                                if (destination == MainDestination.HOME) {
                                    HomeScreen(
                                        uiState = home,
                                        health = displayHealth,
                                        refresh = homeViewModel::refresh,
                                        turnProtectionOn = {
                                            if (!protectionEnabled) {
                                                setProtectionEnabled(true)
                                                if (!serviceEnabled) {
                                                    disclosureReturn = MainDestination.HOME
                                                    destination = MainDestination.DISCLOSURE
                                                }
                                            } else {
                                                disclosureReturn = MainDestination.HOME
                                                destination = MainDestination.DISCLOSURE
                                            }
                                        },
                                        modifier = contentModifier,
                                    )
                                } else if (destination == MainDestination.ATTENTION) {
                                    AttentionScreen(
                                        uiState = attention,
                                        runtime = runtime,
                                        health = displayHealth,
                                        openEpisode = { selectedEpisodeId = it.id; destination = MainDestination.EPISODE },
                                        reviewProtection = {
                                            destination = MainDestination.PROTECTION
                                            lastPrimary = MainDestination.PROTECTION
                                        },
                                        modifier = contentModifier,
                                    )
                                } else if (destination == MainDestination.REVIEW) {
                                    ReviewScreen(attention.review, attention.sevenDayReview, attention.historyAvailable, contentModifier)
                                } else {
                                    ProtectionScreen(
                                        snapshot = snapshot,
                                        protectionEnabled = protectionEnabled,
                                        setProtectionEnabled = setProtectionEnabled,
                                        selectedDriftPackages = selectedDriftPackages,
                                        availableDriftApps = availableDriftApps,
                                        intentionalCheckInsEnabled = intentionalCheckInsEnabled,
                                        setIntentionalCheckInsEnabled = {
                                            intentionalCheckInsEnabled = it
                                            IntentionalCheckInPreferences.save(this@MainActivity, it)
                                            AccessibilityRuntime.setIntentionalCheckInsEnabled(it)
                                        },
                                        setDriftEnabled = setDriftEnabled,
                                        setDriftAppEnabled = setDriftAppEnabled,
                                        repairAccessibility = {
                                            disclosureReturn = MainDestination.PROTECTION
                                            destination = MainDestination.DISCLOSURE
                                        },
                                        openDiagnostics = { destination = MainDestination.DIAGNOSTICS },
                                        modifier = contentModifier,
                                    )
                                }
                            }
                        }
                        MainDestination.EPISODE -> SecondaryScaffold("Episode", { destination = MainDestination.ATTENTION }) {
                            EpisodeDetailScreen(attention.episodes.firstOrNull { episode -> episode.id == selectedEpisodeId }, it)
                        }
                        MainDestination.SETTINGS -> SecondaryScaffold("Settings", { destination = lastPrimary }) {
                            SettingsScreen(
                                appVersion = BuildConfig.VERSION_NAME,
                                historyAvailable = attention.historyAvailable,
                                notificationControlsEnabled = notificationControlsEnabled,
                                configureNotificationControls = {
                                    val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                                    val appNotificationsEnabled = permissionGranted &&
                                        NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()
                                    if (
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        !permissionGranted
                                    ) {
                                        // The "offered" bit only suppresses the unsolicited first-run
                                        // prompt. An explicit tap in Settings should always be allowed
                                        // to request notification permission again.
                                        TunnelNotificationPreferences.markPermissionOffered(this@MainActivity)
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appNotificationsEnabled) {
                                        TunnelNotificationController(this@MainActivity).ensureChannel()
                                        startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                            putExtra(Settings.EXTRA_CHANNEL_ID, TunnelNotificationController.CHANNEL_ID)
                                        })
                                    } else {
                                        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                        })
                                    }
                                },
                                clearHistory = {
                                    AccessibilityRuntime.onAttentionHistoryCleared()
                                    attentionViewModel.clearHistory()
                                },
                                openDiagnostics = { destination = MainDestination.DIAGNOSTICS },
                                openDisclosure = {
                                    disclosureReturn = MainDestination.SETTINGS
                                    destination = MainDestination.DISCLOSURE
                                },
                                openDeveloper = { destination = MainDestination.DEVELOPER },
                                modifier = it,
                            )
                        }
                        MainDestination.DIAGNOSTICS -> SecondaryScaffold("Protection diagnostics", { destination = MainDestination.SETTINGS }) {
                            DiagnosticsScreen(snapshot.diagnosticReport, it)
                        }
                        MainDestination.DISCLOSURE -> SecondaryScaffold("Privacy & Accessibility", { destination = disclosureReturn }) {
                            AccessibilityDisclosureScreen(
                                accessEnabled = serviceEnabled,
                                continueToSettings = { destination = disclosureReturn; openAccessibilitySettings() },
                                modifier = it,
                            )
                        }
                        MainDestination.DEVELOPER -> if (DeveloperDiagnosticsGate.isAvailable(BuildConfig.DEBUG)) {
                            SecondaryScaffold("Developer options", { destination = MainDestination.SETTINGS }) {
                                DeveloperScreen(
                                    runtime = runtime,
                                    enabledInSettings = serviceEnabled,
                                    openSettings = openAccessibilitySettings,
                                    openInspector = { destination = MainDestination.INSPECTOR },
                                    modifier = it,
                                )
                            }
                        } else {
                            SecondaryScaffold("Protection diagnostics", { destination = MainDestination.SETTINGS }) {
                                DiagnosticsScreen(snapshot.diagnosticReport, it)
                            }
                        }
                        MainDestination.INSPECTOR -> if (DeveloperDiagnosticsGate.isAvailable(BuildConfig.DEBUG)) {
                            SecondaryScaffold("Sanitized inspector", { destination = MainDestination.DEVELOPER }) {
                                InspectorScreen(runtime, it)
                            }
                        } else {
                            SecondaryScaffold("Protection diagnostics", { destination = MainDestination.SETTINGS }) {
                                DiagnosticsScreen(snapshot.diagnosticReport, it)
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
        refreshNotificationState()
        lifecycleRefresh += 1
    }

    private fun refreshNotificationState() {
        notificationControlsEnabled = TunnelNotificationPreferences.controlsEnabled(this)
    }

    private fun refreshAccessibilityState() {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        serviceEnabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName && it.resolveInfo.serviceInfo.name.endsWith("TaskTunnelAccessibilityService") }
    }
}

private enum class MainDestination {
    HOME,
    ATTENTION,
    REVIEW,
    EPISODE,
    PROTECTION,
    SETTINGS,
    DIAGNOSTICS,
    DISCLOSURE,
    DEVELOPER,
    INSPECTOR,
}
