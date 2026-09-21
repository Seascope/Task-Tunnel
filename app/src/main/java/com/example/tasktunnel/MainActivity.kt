package com.example.tasktunnel

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tasktunnel.accessibility.AccessibilityRuntime
import com.example.tasktunnel.attention.AttentionViewModel
import com.example.tasktunnel.drift.DriftAppCatalog
import com.example.tasktunnel.drift.DriftPoolPreferences
import com.example.tasktunnel.onboarding.OnboardingFlow
import com.example.tasktunnel.onboarding.OnboardingPreferences
import com.example.tasktunnel.onboarding.OnboardingProgress
import com.example.tasktunnel.onboarding.OnboardingStep
import com.example.tasktunnel.protection.DeveloperDiagnosticsGate
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
import com.example.tasktunnel.ui.theme.TaskTunnelTheme
import com.example.tasktunnel.tunnel.IntentionalCheckInPreferences

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
                var lastPrimary by remember { mutableStateOf(MainDestination.ATTENTION) }
                var selectedEpisodeId by remember { mutableStateOf<String?>(null) }
                var disclosureReturn by remember { mutableStateOf(MainDestination.PROTECTION) }
                var onboarding by remember { mutableStateOf(OnboardingPreferences.load(this@MainActivity)) }
                var selectedDriftPackages by remember { mutableStateOf(DriftPoolPreferences.load(this@MainActivity)) }
                var intentionalCheckInsEnabled by remember { mutableStateOf(IntentionalCheckInPreferences.load(this@MainActivity)) }

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
                    BackHandler(
                        enabled = destination != MainDestination.ATTENTION &&
                            destination != MainDestination.REVIEW &&
                            destination != MainDestination.PROTECTION,
                    ) {
                        destination = when (destination) {
                            MainDestination.EPISODE -> MainDestination.ATTENTION
                            MainDestination.REVIEW -> MainDestination.REVIEW
                            MainDestination.SETTINGS -> lastPrimary
                            MainDestination.DIAGNOSTICS -> MainDestination.SETTINGS
                            MainDestination.DISCLOSURE -> disclosureReturn
                            MainDestination.DEVELOPER -> MainDestination.SETTINGS
                            MainDestination.INSPECTOR -> MainDestination.DEVELOPER
                            MainDestination.ATTENTION,
                            MainDestination.PROTECTION,
                            -> destination
                        }
                    }
                    when (destination) {
                        MainDestination.ATTENTION,
                        MainDestination.REVIEW,
                        MainDestination.PROTECTION,
                        -> {
                            val primary = when (destination) {
                                MainDestination.ATTENTION -> PrimaryDestination.ATTENTION
                                MainDestination.REVIEW -> PrimaryDestination.REVIEW
                                MainDestination.PROTECTION -> PrimaryDestination.PROTECTION
                                else -> PrimaryDestination.ATTENTION
                            }
                            TaskTunnelScaffold(
                                destination = primary,
                                onNavigate = {
                                    destination = when (it) {
                                        PrimaryDestination.ATTENTION -> MainDestination.ATTENTION
                                        PrimaryDestination.REVIEW -> MainDestination.REVIEW
                                        PrimaryDestination.PROTECTION -> MainDestination.PROTECTION
                                    }
                                    lastPrimary = destination
                                },
                                onOpenSettings = { destination = MainDestination.SETTINGS },
                            ) { contentModifier ->
                                if (destination == MainDestination.ATTENTION) {
                                    AttentionScreen(
                                        uiState = attention,
                                        runtime = runtime,
                                        health = snapshot.health,
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
                                        selectedDriftPackages = selectedDriftPackages,
                                        intentionalCheckInsEnabled = intentionalCheckInsEnabled,
                                        setIntentionalCheckInsEnabled = {
                                            intentionalCheckInsEnabled = it
                                            IntentionalCheckInPreferences.save(this@MainActivity, it)
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
                                clearHistory = attentionViewModel::clearHistory,
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
        lifecycleRefresh += 1
    }

    private fun refreshAccessibilityState() {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        serviceEnabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName && it.resolveInfo.serviceInfo.name.endsWith("TaskTunnelAccessibilityService") }
    }
}

private enum class MainDestination {
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
