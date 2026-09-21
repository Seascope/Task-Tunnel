package com.example.tasktunnel.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tasktunnel.BuildConfig
import com.example.tasktunnel.accessibility.AccessibilityState
import com.example.tasktunnel.attention.AttentionEpisode
import com.example.tasktunnel.attention.AttentionEpisodeType
import com.example.tasktunnel.attention.AttentionEvent
import com.example.tasktunnel.attention.AttentionEventType
import com.example.tasktunnel.attention.AttentionPattern
import com.example.tasktunnel.attention.AttentionPatternType
import com.example.tasktunnel.attention.AttentionReview
import com.example.tasktunnel.attention.AttentionUiState
import com.example.tasktunnel.attention.DailyAttentionRecap
import com.example.tasktunnel.attention.episodeSubtitle
import com.example.tasktunnel.attention.episodeTitle
import com.example.tasktunnel.attention.eventDescription
import com.example.tasktunnel.attention.patternHeadline
import com.example.tasktunnel.attention.patternSupportingText
import com.example.tasktunnel.attention.surfaceLabel
import com.example.tasktunnel.attention.taskLabel
import com.example.tasktunnel.drift.DriftAppCatalog
import com.example.tasktunnel.onboarding.OnboardingProgress
import com.example.tasktunnel.onboarding.OnboardingStep
import com.example.tasktunnel.protection.AppCompatibility
import com.example.tasktunnel.protection.DeveloperDiagnosticsGate
import com.example.tasktunnel.protection.DiagnosticReport
import com.example.tasktunnel.protection.InstalledAppStatus
import com.example.tasktunnel.protection.ProtectionHealth
import com.example.tasktunnel.protection.ProtectionLevel
import com.example.tasktunnel.protection.ProtectionSnapshot
import com.example.tasktunnel.ui.theme.HealthyGreen
import com.example.tasktunnel.ui.theme.LimitedAmber
import com.example.tasktunnel.ui.theme.SurfaceGraphite
import com.example.tasktunnel.ui.theme.TaskTunnelTokens
import java.util.Calendar

@Composable
fun AttentionScreen(
    uiState: AttentionUiState,
    runtime: AccessibilityState,
    health: ProtectionHealth,
    openEpisode: (AttentionEpisode) -> Unit,
    reviewProtection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = uiState.episodes.filter { isToday(it.startedAtMillis) }
    val earlier = uiState.episodes.filterNot { isToday(it.startedAtMillis) }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = TaskTunnelTokens.ScreenHorizontalPadding, top = 8.dp, end = TaskTunnelTokens.ScreenHorizontalPadding, bottom = 28.dp),
    ) {
        item {
            if (health.level != ProtectionLevel.ACTIVE) {
                ProtectionRepairRow(health, reviewProtection)
                Spacer(Modifier.height(6.dp))
            }
        }
        runtime.tunnelState.activeSession?.let { session ->
            item {
                ActiveTunnelNotice(session.app.packageName, session.app.displayName, taskLabel(session.task))
                Spacer(Modifier.height(TaskTunnelTokens.MajorSectionGap))
            }
        }
        item { SectionHeader("Today", Modifier.padding(bottom = TaskTunnelTokens.SectionHeaderBottomGap)) }
        when {
            !uiState.historyAvailable -> item { ErrorNotice("Attention history is unavailable", "Protection can still run. Reopen Task Tunnel and check diagnostics if this continues.") }
            uiState.episodes.isEmpty() -> item { EmptyState("No episodes yet", "Intentions, meaningful transitions, Drift check-ins, and your choices will appear here. History stays on this device.") }
            today.isEmpty() -> item { EmptyState("Nothing recorded today", "Recent episodes are available below.") }
            else -> episodeItems(today, openEpisode)
        }
        if (earlier.isNotEmpty()) {
            item {
                Spacer(Modifier.height(TaskTunnelTokens.MajorSectionGap))
                SectionHeader("Earlier", Modifier.padding(bottom = TaskTunnelTokens.SectionHeaderBottomGap))
            }
            episodeItems(earlier, openEpisode)
        }
    }
}

@Composable
private fun ProtectionRepairRow(health: ProtectionHealth, onRepair: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProtectionStatusLine(health, showSummary = false, modifier = Modifier.weight(1f))
        TextButton(onClick = onRepair, modifier = Modifier.height(40.dp)) {
            Text("Fix")
        }
    }
}

@Composable
fun ReviewScreen(review: AttentionReview, historyAvailable: Boolean, modifier: Modifier = Modifier) {
    if (!historyAvailable || !review.hasMeaningfulData) {
        Column(modifier.padding(TaskTunnelTokens.ScreenHorizontalPadding, 18.dp)) {
            Text("Nothing to review yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Your daily recap will appear after Task Tunnel records some intentional sessions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        return
    }

    Column(
        modifier = modifier.padding(
            horizontal = TaskTunnelTokens.ScreenHorizontalPadding,
            vertical = 14.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("Today")
        ReviewTodayHero(review.recap)
        if (review.patterns.isNotEmpty()) {
            SectionHeader("Patterns")
            review.patterns.forEachIndexed { index, pattern ->
                if (index > 0) RowDivider()
                PatternRow(pattern, Modifier.padding(vertical = 10.dp))
            }
        } else {
            SectionHeader("Patterns")
            Text(
                "A little more history is needed before patterns become useful.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ReviewTodayHero(recap: DailyAttentionRecap) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceGraphite).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (recap.detoursInterrupted > 0) {
            Text("${recap.recoveredIntentions} / ${recap.detoursInterrupted}", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text("detours ended with a return\nto your intention", style = MaterialTheme.typography.titleMedium)
        } else {
            Text(recap.intentionalSessions.toString(), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text("intentional ${pluralize(recap.intentionalSessions, "session")} today", style = MaterialTheme.typography.titleMedium)
            Text("No detours interrupted today.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${recap.intentionalSessions} sessions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("·", color = MaterialTheme.colorScheme.outline)
            Text("${recap.driftEpisodes} Drift ${pluralize(recap.driftEpisodes, "episode")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (recap.detoursInterrupted > 0) {
            Text("${recap.recoveredIntentions} returned · ${recap.consciousDetours} continued · ${recap.endedTunnels} ended", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PatternRow(pattern: AttentionPattern, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        PatternMarker(pattern)
        Spacer(Modifier.width(11.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(patternHeadline(pattern), style = MaterialTheme.typography.titleMedium)
            Text(patternSupportingText(pattern), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PatternMarker(pattern: AttentionPattern) {
    Column(Modifier.width(46.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (pattern.app != null && pattern.type in setOf(AttentionPatternType.COMMON_DETOUR_SURFACE, AttentionPatternType.RECOVERY_AFTER_SURFACE)) {
            AppIcon(pattern.app.packageName, pattern.app.displayName, Modifier.size(25.dp))
        } else {
            TaskTunnelIcon(TaskTunnelIconKind.ATTENTION, Modifier.size(23.dp), MaterialTheme.colorScheme.primary)
        }
        Text(patternLabel(pattern), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun patternLabel(pattern: AttentionPattern): String = when (pattern.type) {
    AttentionPatternType.COMMON_DETOUR_SURFACE -> surfaceLabel(pattern.surface).uppercase()
    AttentionPatternType.RECOVERY_AFTER_SURFACE -> "RETURNING"
    AttentionPatternType.RECURRING_DRIFT_PATH -> "DRIFT PATH"
    AttentionPatternType.TIME_OF_DAY_DRIFT -> "DRIFT TIMING"
}

private fun pluralize(count: Int, singular: String): String = if (count == 1) singular else "${singular}s"

private fun androidx.compose.foundation.lazy.LazyListScope.episodeItems(
    episodes: List<AttentionEpisode>,
    openEpisode: (AttentionEpisode) -> Unit,
) {
    items(episodes, key = { it.id }) { episode ->
        if (episode.type == AttentionEpisodeType.DRIFT) DriftEpisodeRow(episode, { openEpisode(episode) })
        else EpisodeRow(episode, { openEpisode(episode) })
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ActiveTunnelNotice(packageName: String, appName: String, task: String) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName, appName, Modifier.size(34.dp))
        Spacer(Modifier.width(TaskTunnelTokens.IconTextGap))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Active Tunnel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("$appName · $task", style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
    }
}

@Composable
fun EpisodeDetailScreen(episode: AttentionEpisode?, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TaskTunnelTokens.ScreenHorizontalPadding, vertical = 14.dp),
    ) {
        if (episode == null) {
            item { EmptyState("Episode unavailable", "This episode is no longer in recent history.") }
        } else {
            item {
                Text(formatEpisodeRange(episode), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(episodeTitle(episode), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
                Text(episodeSubtitle(episode), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                if (episode.type == AttentionEpisodeType.DRIFT && episode.involvedApps.isNotEmpty()) {
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        episode.involvedApps.forEach { app -> AppIdentity(app.packageName, app.displayName, showName = false, iconSize = 30.dp) }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            item { EpisodePath(episode.events) }
            item {
                Text("This history stays on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp, bottom = 20.dp))
            }
        }
    }
}

@Composable
fun EpisodePath(events: List<AttentionEvent>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        events.forEachIndexed { index, event ->
            val previousTimestamp = events.getOrNull(index - 1)?.let { formatTime(it.timestampMillis) }
            EpisodePathNode(event, formatTime(event.timestampMillis) != previousTimestamp, index == 0, index == events.lastIndex)
        }
    }
}

@Composable
private fun EpisodePathNode(event: AttentionEvent, showTimestamp: Boolean, first: Boolean, last: Boolean) {
    val lineColor = MaterialTheme.colorScheme.outline
    val nodeColor = when (event.type) {
        AttentionEventType.INTENT -> MaterialTheme.colorScheme.primary
        AttentionEventType.TRANSITION -> MaterialTheme.colorScheme.onSurfaceVariant
        AttentionEventType.INTERVENTION -> LimitedAmber
        AttentionEventType.DECISION -> HealthyGreen
    }
    val canvasColor = MaterialTheme.colorScheme.background
    Row(Modifier.fillMaxWidth().heightIn(min = 42.dp)) {
        Column(Modifier.width(64.dp).padding(top = 2.dp), horizontalAlignment = Alignment.End) {
            if (showTimestamp) Text(formatTime(event.timestampMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.width(30.dp).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2f
                if (!first) drawLine(lineColor, Offset(x, 0f), Offset(x, 12.dp.toPx()), 1.dp.toPx())
                if (!last) drawLine(lineColor, Offset(x, 24.dp.toPx()), Offset(x, size.height), 1.dp.toPx())
                drawCircle(nodeColor, 5.dp.toPx(), Offset(x, 18.dp.toPx()))
                drawCircle(canvasColor, 2.dp.toPx(), Offset(x, 18.dp.toPx()))
            }
        }
        Column(Modifier.weight(1f).padding(top = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(event.type.timelineLabel(), style = MaterialTheme.typography.labelSmall, color = nodeColor)
            Text(eventDescription(event), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (event.relatedApps.isNotEmpty() && event.app == null) {
                Text(event.relatedApps.joinToString(" → ") { it.displayName }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun AttentionEventType.timelineLabel(): String = when (this) {
    AttentionEventType.INTENT -> "INTENT"
    AttentionEventType.TRANSITION -> "TRANSITION"
    AttentionEventType.INTERVENTION -> "INTERVENTION"
    AttentionEventType.DECISION -> "DECISION"
}

@Composable
fun ProtectionScreen(
    snapshot: ProtectionSnapshot,
    selectedDriftPackages: Set<String>,
    intentionalCheckInsEnabled: Boolean,
    setIntentionalCheckInsEnabled: (Boolean) -> Unit,
    setDriftEnabled: (Boolean) -> Unit,
    setDriftAppEnabled: (String, Boolean) -> Unit,
    repairAccessibility: () -> Unit,
    openDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TaskTunnelTokens.ScreenHorizontalPadding,
            top = 8.dp,
            end = TaskTunnelTokens.ScreenHorizontalPadding,
            bottom = 32.dp,
        ),
    ) {
        item {
            ProtectionStatusLine(snapshot.health, Modifier.fillMaxWidth(), showSummary = true)
            if (snapshot.health.level != ProtectionLevel.ACTIVE) {
                Button(
                    onClick = repairAccessibility,
                    modifier = Modifier.padding(top = 14.dp).height(48.dp),
                    shape = RoundedCornerShape(TaskTunnelTokens.ActionRadius),
                ) { Text("Turn protection on") }
                if (snapshot.health.backgroundConcern) {
                    Text(
                        "If access is already on, turn Task Tunnel off and on in Accessibility settings. Check background restrictions only if interruptions continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(TaskTunnelTokens.MajorSectionGap))
            SectionHeader("Protected apps", Modifier.padding(bottom = TaskTunnelTokens.SectionHeaderBottomGap))
        }
        snapshot.apps.take(2).forEachIndexed { index, app ->
            item {
                ProtectedAppRow(
                    app,
                    if (app.displayName == "Instagram") "Reply to messages\nBrowse intentionally" else "Search / watch something specific\nBrowse intentionally",
                )
                if (index == 0) RowDivider(inset = true)
            }
        }
        item {
            Spacer(Modifier.height(TaskTunnelTokens.MajorSectionGap))
            SectionHeader("Intentional check-ins", Modifier.padding(bottom = TaskTunnelTokens.SectionHeaderBottomGap))
            Row(
                Modifier.fillMaxWidth().padding(vertical = TaskTunnelTokens.RowVerticalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TaskTunnelTokens.SecondaryTextGap)) {
                    Text("Ask again after a while", style = MaterialTheme.typography.titleMedium)
                    Text("Ask again when a detour or open-ended browse has been running for a while.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = intentionalCheckInsEnabled, onCheckedChange = setIntentionalCheckInsEnabled)
            }
            Spacer(Modifier.height(TaskTunnelTokens.MajorSectionGap))
            SectionHeader("Drift Detection", Modifier.padding(bottom = TaskTunnelTokens.SectionHeaderBottomGap))
            DriftConfigurationSection(selectedDriftPackages, setDriftEnabled, setDriftAppEnabled)
            Spacer(Modifier.height(TaskTunnelTokens.MajorSectionGap))
            SectionHeader("System", Modifier.padding(bottom = TaskTunnelTokens.SectionHeaderBottomGap))
            SettingsRow(
                title = "Protection health",
                subtitle = snapshot.health.summary,
                trailingText = when (snapshot.health.level) {
                    ProtectionLevel.ACTIVE -> "Good"
                    ProtectionLevel.LIMITED -> "Limited"
                    ProtectionLevel.OFF -> "Off"
                },
                showChevron = true,
                onClick = openDiagnostics,
            )
            RowDivider()
            SettingsRow(
                title = "Accessibility access",
                subtitle = if (snapshot.diagnosticReport.accessibilityEnabled) "Task Tunnel can inspect supported app surfaces." else "Required for interventions in supported apps.",
                trailingText = if (snapshot.diagnosticReport.accessibilityEnabled) "On" else "Off",
                showChevron = !snapshot.diagnosticReport.accessibilityEnabled,
                onClick = if (snapshot.diagnosticReport.accessibilityEnabled) openDiagnostics else repairAccessibility,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProtectedAppRow(app: InstalledAppStatus, intentions: String) {
    val status = when (app.compatibility) {
        AppCompatibility.NOT_INSTALLED -> "Not installed"
        AppCompatibility.KNOWN_COMPATIBILITY_PROBLEM -> "Check health"
        AppCompatibility.VERIFIED -> "Verified"
        AppCompatibility.VERSION_NOT_RECORDED -> null
    }
    SettingsRow(
        title = app.displayName,
        subtitle = intentions,
        leading = { AppIcon(app.packageName, app.displayName, Modifier.size(TaskTunnelTokens.AppIconSize)) },
        trailingText = status,
    )
}

@Composable
fun DriftConfigurationSection(
    selectedPackages: Set<String>,
    setDriftEnabled: (Boolean) -> Unit,
    setAppEnabled: (String, Boolean) -> Unit,
) {
    val enabled = selectedPackages.isNotEmpty()
    Row(
        Modifier.fillMaxWidth().padding(vertical = TaskTunnelTokens.RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TaskTunnelTokens.SecondaryTextGap)) {
            Text("Notice rapid app switching", style = MaterialTheme.typography.titleMedium)
            Text("Optional. Checks for quick movement between selected apps.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = enabled, onCheckedChange = setDriftEnabled)
    }
    if (enabled) {
        DriftAppCatalog.apps.forEachIndexed { index, app ->
            if (index > 0) RowDivider(inset = true)
            Row(
                Modifier.fillMaxWidth().clickable { setAppEnabled(app.packageName, app.packageName !in selectedPackages) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app.packageName, app.displayName, Modifier.size(32.dp))
                Spacer(Modifier.width(TaskTunnelTokens.IconTextGap))
                Text(app.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = app.packageName in selectedPackages, onCheckedChange = { setAppEnabled(app.packageName, it) })
            }
        }
    }
}

@Composable
fun OnboardingScreen(
    progress: OnboardingProgress,
    accessibilityEnabled: Boolean,
    selectedDriftPackages: Set<String>,
    setDriftEnabled: (Boolean) -> Unit,
    setDriftAppEnabled: (String, Boolean) -> Unit,
    advance: () -> Unit,
    openAccessibilitySettings: () -> Unit,
    completeLater: () -> Unit,
) {
    val stepNumber = progress.step.ordinal + 1
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding()
                    .padding(horizontal = TaskTunnelTokens.ScreenHorizontalPadding, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    onClick = when (progress.step) {
                        OnboardingStep.DISCLOSURE -> openAccessibilitySettings
                        else -> advance
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(
                        when (progress.step) {
                            OnboardingStep.VALUE -> "See how it works"
                            OnboardingStep.HOW_IT_WORKS -> "Privacy and Accessibility"
                            OnboardingStep.DISCLOSURE -> "Continue to Accessibility settings"
                            OnboardingStep.VERIFY -> if (accessibilityEnabled) "Choose protection setup" else "Continue without access"
                            OnboardingStep.CONFIGURE -> "Finish setup"
                        },
                    )
                }
                if (progress.step != OnboardingStep.CONFIGURE) {
                    TextButton(onClick = completeLater, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Set up later") }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()).padding(horizontal = TaskTunnelTokens.ScreenHorizontalPadding, vertical = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TASK TUNNEL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("$stepNumber of 5", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(38.dp))
            when (progress.step) {
                OnboardingStep.VALUE -> ValueOnboarding()
                OnboardingStep.HOW_IT_WORKS -> HowItWorksOnboarding()
                OnboardingStep.DISCLOSURE -> DisclosureContent()
                OnboardingStep.VERIFY -> VerifyOnboarding(accessibilityEnabled, openAccessibilitySettings)
                OnboardingStep.CONFIGURE -> ConfigureOnboarding(selectedDriftPackages, setDriftEnabled, setDriftAppEnabled)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ValueOnboarding() {
    Text("Keep the reason you opened the app", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Text(
        "Use Instagram and YouTube for what you intended. Task Tunnel adds a calm pause when you move somewhere else.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp),
    )
    Spacer(Modifier.height(34.dp))
    OnboardingPoint("01", "Choose a purpose", "Reply, search, watch, or browse intentionally.")
    OnboardingPoint("02", "Stay in the native app", "Task Tunnel disappears while you stay with that purpose.")
    OnboardingPoint("03", "Make the next choice", "Return, allow the detour, or end the Tunnel.")
}

@Composable
private fun HowItWorksOnboarding() {
    Text("A quiet layer over apps you already use", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Text(
        "Task Tunnel watches only supported app surfaces while protection is active. It does not replace Instagram or YouTube.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 30.dp),
    )
    OnboardingPoint("01", "Choose a purpose", "Choose what you came to do.")
    OnboardingPoint("02", "Use the native app", "Task Tunnel stays quiet while the purpose still fits.")
    OnboardingPoint("03", "Choose what happens next", "If you leave that purpose, Task Tunnel offers a decision.")
    Text("Uncertain screens always fail open. Intentional browsing is a first-class choice.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp))
}

@Composable
private fun OnboardingPoint(index: String, title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                index,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(42.dp).padding(top = 2.dp),
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 42.dp, top = 3.dp),
        )
    }
}

@Composable
private fun VerifyOnboarding(accessibilityEnabled: Boolean, openSettings: () -> Unit) {
    Text(if (accessibilityEnabled) "Protection is ready" else "Accessibility access is still off", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Text(
        if (accessibilityEnabled) "Task Tunnel can now recognize supported surfaces on this device." else "You can continue using Task Tunnel and enable access later from Protection.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp),
    )
    if (!accessibilityEnabled) TextButton(onClick = openSettings, modifier = Modifier.padding(top = 14.dp)) { Text("Try Accessibility settings again") }
}

@Composable
private fun ConfigureOnboarding(selected: Set<String>, setEnabled: (Boolean) -> Unit, setApp: (String, Boolean) -> Unit) {
    Text("Your protection setup", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Text("Task Tunnel protects these intentions.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 24.dp))
    SettingsRow("Instagram", "Reply to messages\nBrowse intentionally", leading = { AppIcon("com.instagram.android", "Instagram", Modifier.size(38.dp)) })
    RowDivider(inset = true)
    SettingsRow("YouTube", "Search / watch something specific\nBrowse intentionally", leading = { AppIcon("com.google.android.youtube", "YouTube", Modifier.size(38.dp)) })
    Spacer(Modifier.height(TaskTunnelTokens.MajorSectionGap))
    SectionHeader("Optional Drift Detection", Modifier.padding(bottom = 6.dp))
    DriftConfigurationSection(selected, setEnabled, setApp)
}

@Composable
fun DisclosureContent() {
    Text("Why Accessibility access is needed", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Text(
        "Task Tunnel uses Android Accessibility access for its digital-wellbeing protection feature.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 18.dp),
    )
    DisclosureItem("What it can see", "Enough of the visible interface in supported apps to identify Messages, Reels, Explore, Search, normal videos, and Shorts.")
    DisclosureItem("Why", "To tell when you move outside the purpose you declared and offer a choice.")
    DisclosureItem("What is stored", "Your chosen purposes, meaningful app transitions, Task Tunnel prompts, and the choices you make.")
    DisclosureItem("What is not stored", "No screenshots, private message contents, accessibility text history, usernames in Attention history, raw accessibility trees, or raw fingerprints.")
    DisclosureItem("Where processing happens", "Everything is processed on this device. No account or cloud connection is required.")
}

@Composable
private fun DisclosureItem(title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        TaskTunnelIcon(TaskTunnelIconKind.INFO, Modifier.padding(top = 2.dp).size(17.dp), MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AccessibilityDisclosureScreen(accessEnabled: Boolean, continueToSettings: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TaskTunnelTokens.ScreenHorizontalPadding, vertical = 14.dp),
    ) {
        item { DisclosureContent() }
        item {
            Text(
                if (accessEnabled) "Accessibility access is currently on." else "Accessibility access is currently off.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (accessEnabled) HealthyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
            Button(
                onClick = continueToSettings,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp),
                shape = RoundedCornerShape(TaskTunnelTokens.ActionRadius),
            ) { Text("Continue to Accessibility settings") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun SettingsScreen(
    appVersion: String,
    historyAvailable: Boolean,
    clearHistory: () -> Unit,
    openDiagnostics: () -> Unit,
    openDisclosure: () -> Unit,
    openDeveloper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var historyCleared by remember { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear Attention history?") },
            text = { Text("This permanently removes your Attention history from this device.") },
            confirmButton = {
                TextButton(onClick = { clearHistory(); confirmClear = false; historyCleared = true }) { Text("Clear history", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TaskTunnelTokens.ScreenHorizontalPadding, vertical = 12.dp),
    ) {
        item {
            SectionHeader("Privacy & data", Modifier.padding(bottom = TaskTunnelTokens.SectionHeaderBottomGap))
            SettingsRow(
                title = "Privacy and Accessibility",
                subtitle = "What Task Tunnel can see and what stays on this device",
                leading = { TaskTunnelIcon(TaskTunnelIconKind.INFO, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
                showChevron = true,
                onClick = openDisclosure,
            )
            RowDivider(inset = true)
            SettingsRow(
                title = "Clear Attention history",
                subtitle = when {
                    historyCleared -> "Attention history cleared."
                    historyAvailable -> "Remove Attention history from this device"
                    else -> "History is currently unavailable. Reopen Task Tunnel and try again."
                },
                leading = { TaskTunnelIcon(TaskTunnelIconKind.DELETE, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
                enabled = historyAvailable,
                onClick = { confirmClear = true },
            )
            Spacer(Modifier.height(TaskTunnelTokens.MajorSectionGap))
            SectionHeader("Help & system", Modifier.padding(bottom = TaskTunnelTokens.SectionHeaderBottomGap))
            SettingsRow(
                title = "Protection diagnostics",
                subtitle = "Copy sanitized device and service status",
                leading = { TaskTunnelIcon(TaskTunnelIconKind.COPY, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
                showChevron = true,
                onClick = openDiagnostics,
            )
            if (DeveloperDiagnosticsGate.isAvailable(BuildConfig.DEBUG)) {
                RowDivider(inset = true)
                SettingsRow(
                    title = "Developer options",
                    subtitle = "Debug build only",
                    leading = { TaskTunnelIcon(TaskTunnelIconKind.SETTINGS, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
                    showChevron = true,
                    onClick = openDeveloper,
                )
            }
            Spacer(Modifier.height(TaskTunnelTokens.MajorSectionGap))
            SectionHeader("About", Modifier.padding(bottom = TaskTunnelTokens.SectionHeaderBottomGap))
            SettingsRow("Task Tunnel", "Private by design · Data stays on this device", trailingText = appVersion)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun DiagnosticsScreen(report: DiagnosticReport, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var copied by remember(report) { mutableStateOf(false) }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TaskTunnelTokens.ScreenHorizontalPadding, vertical = 16.dp),
    ) {
        item {
            Text("Sanitized technical status", style = MaterialTheme.typography.titleLarge)
            Text(
                "This never includes app content, usernames, screenshots, detector internals, or Attention history.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
            )
        }
        items(report.asText().lineSequence().filter { it.isNotBlank() }.toList()) { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 5.dp))
        }
        item {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Task Tunnel diagnostics", report.asText()))
                    copied = true
                },
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp).height(52.dp),
            ) { Text(if (copied) "Diagnostics copied" else "Copy diagnostics") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun isToday(timeMillis: Long): Boolean {
    val now = Calendar.getInstance()
    val value = Calendar.getInstance().apply { timeInMillis = timeMillis }
    return now.get(Calendar.ERA) == value.get(Calendar.ERA) &&
        now.get(Calendar.YEAR) == value.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == value.get(Calendar.DAY_OF_YEAR)
}
