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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tasktunnel.BuildConfig
import com.example.tasktunnel.accessibility.AccessibilityState
import com.example.tasktunnel.attention.AttentionEpisode
import com.example.tasktunnel.attention.AttentionEpisodeType
import com.example.tasktunnel.attention.AttentionReview
import com.example.tasktunnel.attention.AttentionUiState
import com.example.tasktunnel.attention.AttentionStoryItem
import com.example.tasktunnel.attention.ReviewPeriodSummary
import com.example.tasktunnel.attention.ReviewInsight
import com.example.tasktunnel.attention.ReviewInsightFactory
import com.example.tasktunnel.attention.ReviewInsightType
import com.example.tasktunnel.attention.SevenDayReview
import com.example.tasktunnel.attention.taskLabel
import com.example.tasktunnel.attention.episodeStory
import com.example.tasktunnel.drift.KnownDriftApp
import com.example.tasktunnel.drift.DriftAppCatalog
import com.example.tasktunnel.onboarding.OnboardingProgress
import com.example.tasktunnel.onboarding.OnboardingStep
import com.example.tasktunnel.onboarding.SetupChecklistState
import com.example.tasktunnel.protection.AppCompatibility
import com.example.tasktunnel.protection.DeveloperDiagnosticsGate
import com.example.tasktunnel.protection.DiagnosticReport
import com.example.tasktunnel.protection.InstalledAppStatus
import com.example.tasktunnel.protection.ProtectionHealth
import com.example.tasktunnel.protection.ProtectionLevel
import com.example.tasktunnel.protection.ProtectionSnapshot
import com.example.tasktunnel.ui.theme.BrandBlueContainer
import com.example.tasktunnel.ui.theme.HealthyGreen
import com.example.tasktunnel.ui.theme.LimitedAmber
import com.example.tasktunnel.ui.theme.SurfaceRaised
import com.example.tasktunnel.tunnel.TunnelSession
import com.example.tasktunnel.ui.theme.TaskTunnelTokens
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun AttentionScreen(
    uiState: AttentionUiState,
    runtime: AccessibilityState,
    health: ProtectionHealth,
    openEpisode: (AttentionEpisode) -> Unit,
    reviewProtection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeSession = runtime.tunnelState.activeSession
    val activeEpisodeId = activeSession?.id?.let { "tunnel:$it" }
    val activeDayStart = activeSession?.startedAtMillis?.let(::startOfLocalDay)
    val episodesByDay = uiState.episodes.groupBy { startOfLocalDay(it.startedAtMillis) }
    val dayStarts = (episodesByDay.keys + listOfNotNull(activeDayStart)).distinct().sortedDescending()
    val activeHasEpisode = activeEpisodeId != null && uiState.episodes.any { it.id == activeEpisodeId }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TaskTunnelTokens.ScreenHorizontalPadding,
            top = 8.dp,
            end = TaskTunnelTokens.ScreenHorizontalPadding,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (health.level != ProtectionLevel.ACTIVE) {
            item {
                ProtectionRepairRow(health, reviewProtection)
            }
        }

        when {
            !uiState.historyAvailable -> item {
                ErrorNotice(
                    "Activity history is unavailable",
                    "Task Tunnel can still protect you. Reopen the app and try again.",
                )
            }
            dayStarts.isEmpty() -> item {
                EmptyState(
                    "Nothing here yet",
                    "Recent Task Tunnels and Drift check-ins will appear here.",
                )
            }
            else -> {
                items(dayStarts, key = { it }) { dayStart ->
                    val episodes = episodesByDay[dayStart].orEmpty()
                    val fallbackActiveSession = activeSession?.takeIf {
                        activeDayStart == dayStart && !activeHasEpisode
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(attentionDayLabel(dayStart))
                        AttentionDayCard(
                            episodes = episodes,
                            activeSession = fallbackActiveSession,
                            activeEpisodeId = activeEpisodeId,
                            openEpisode = openEpisode,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttentionDayCard(
    episodes: List<AttentionEpisode>,
    activeSession: TunnelSession?,
    activeEpisodeId: String?,
    openEpisode: (AttentionEpisode) -> Unit,
) {
    val rowCount = episodes.size + if (activeSession != null) 1 else 0
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TaskTunnelTokens.CardRadius))
            .background(SurfaceRaised),
    ) {
        var renderedRows = 0
        if (activeSession != null) {
            ActiveSessionRow(activeSession)
            renderedRows += 1
            if (renderedRows < rowCount) AttentionRowDivider()
        }
        episodes.forEachIndexed { index, episode ->
            if (episode.type == AttentionEpisodeType.DRIFT) {
                DriftEpisodeRow(episode = episode, onClick = { openEpisode(episode) })
            } else {
                EpisodeRow(
                    episode = episode,
                    onClick = { openEpisode(episode) },
                    isLive = episode.id == activeEpisodeId,
                )
            }
            renderedRows += 1
            if (renderedRows < rowCount) AttentionRowDivider()
        }
    }
}

@Composable
private fun AttentionRowDivider() {
    RowDivider(modifier = Modifier.padding(horizontal = 14.dp), inset = true)
}

@Composable
private fun ActiveSessionRow(session: TunnelSession) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppIcon(session.app.packageName, session.app.displayName, Modifier.size(TaskTunnelTokens.AppIconSize))
        Spacer(Modifier.width(TaskTunnelTokens.IconTextGap))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    session.app.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    formatTime(session.startedAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${taskLabel(session.task)} · Live",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
fun ReviewScreen(
    review: AttentionReview,
    sevenDayReview: SevenDayReview,
    historyAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val insights = remember(review.patterns) { ReviewInsightFactory.create(review.patterns) }
    if (!historyAvailable || (!review.hasMeaningfulData && !sevenDayReview.hasMeaningfulHistory)) {
        EmptyState(
            title = "Nothing to review yet",
            body = "Useful patterns will appear after Task Tunnel has enough recent history.",
            modifier = modifier.padding(horizontal = TaskTunnelTokens.ScreenHorizontalPadding),
        )
        return
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = TaskTunnelTokens.ScreenHorizontalPadding,
                vertical = 14.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            "Patterns from recent use",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (insights.isEmpty()) {
            ReviewNoPatternCard()
        } else {
            ReviewInsightsCard(insights)
        }

        if (sevenDayReview.hasMeaningfulHistory) {
            WeeklySummaryDisclosure(sevenDayReview.summary)
        }
    }
}

@Composable
private fun ReviewInsightsCard(insights: List<ReviewInsight>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TaskTunnelTokens.CardRadius))
            .background(SurfaceRaised)
            .padding(horizontal = 16.dp),
    ) {
        insights.forEachIndexed { index, insight ->
            if (index > 0) RowDivider(inset = true)
            ReviewInsightRow(insight, Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun ReviewInsightRow(insight: ReviewInsight, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        ReviewInsightMarker(insight)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(insight.headline, style = MaterialTheme.typography.titleMedium)
            if (insight.type == ReviewInsightType.DRIFT_PATH && insight.packageSequence.isNotEmpty()) {
                Text(
                    insight.packageSequence.joinToString(" → ") { DriftAppCatalog.labelFor(context, it) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                insight.supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            insight.detailText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReviewInsightMarker(insight: ReviewInsight) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (insight.type == ReviewInsightType.DETOUR && insight.app != null) {
            AppIcon(insight.app.packageName, insight.app.displayName, Modifier.size(28.dp))
        } else {
            TaskTunnelIcon(
                TaskTunnelIconKind.DRIFT,
                Modifier.size(30.dp),
                MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ReviewNoPatternCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TaskTunnelTokens.CardRadius))
            .background(SurfaceRaised)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Nothing stands out yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Keep using Task Tunnel normally. Patterns will appear when behavior repeats enough to be genuinely useful.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeeklySummaryDisclosure(summary: ReviewPeriodSummary) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TaskTunnelTokens.CardRadius))
            .background(SurfaceRaised)
            .clickable { expanded = !expanded }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
                Text(
                    weeklySummaryLead(summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (expanded) "Hide" else "Details",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (expanded) {
            RowDivider()
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                WeeklyCountRow("Intentional sessions", summary.intentionalSessions)
                WeeklyCountRow("Detours", summary.detours)
                if (summary.detours > 0) {
                    WeeklyCountRow("Returned", summary.returned)
                    WeeklyCountRow("Continued", summary.continued)
                    WeeklyCountRow("Ended", summary.ended)
                }
                WeeklyCountRow("Drift check-ins", summary.driftEpisodes)
            }
        }
    }
}

@Composable
private fun WeeklyCountRow(label: String, count: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(count.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun weeklySummaryLead(summary: ReviewPeriodSummary): String = when {
    summary.intentionalSessions > 0 -> "${summary.intentionalSessions} intentional ${pluralize(summary.intentionalSessions, "session")}"
    summary.driftEpisodes > 0 -> "${summary.driftEpisodes} Drift ${pluralize(summary.driftEpisodes, "check-in")}"
    else -> "Recent activity"
}

private fun pluralize(count: Int, singular: String): String = if (count == 1) singular else "${singular}s"

@Composable
fun EpisodeDetailScreen(episode: AttentionEpisode?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = TaskTunnelTokens.ScreenHorizontalPadding,
            vertical = 14.dp,
        ),
    ) {
        if (episode == null) {
            item { EmptyState("Episode unavailable", "This episode is no longer in recent history.") }
        } else {
            item {
                EpisodeDetailHeader(episode)
                Spacer(Modifier.height(28.dp))
                SectionHeader("What happened", Modifier.padding(bottom = 10.dp))
            }
            item {
                EpisodeStoryCard(episodeStory(episode))
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EpisodeDetailHeader(episode: AttentionEpisode) {
    val context = LocalContext.current
    when (episode.type) {
        AttentionEpisodeType.TASK_TUNNEL -> {
            val app = episode.app
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app != null) {
                    AppIcon(app.packageName, app.displayName, Modifier.size(46.dp))
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        app?.displayName ?: "Task Tunnel",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        taskLabel(episode.task),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "${formatEpisodeRange(episode)} · ${formatEpisodeDuration(episode)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        AttentionEpisodeType.DRIFT -> {
            val appPath = episode.involvedPackages
                .joinToString(" → ") { packageName -> DriftAppCatalog.labelFor(context, packageName) }
                .ifBlank { episode.involvedApps.joinToString(" → ") { it.displayName } }
            Text(
                "Drift check-in",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                appPath,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (episode.involvedPackages.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    episode.involvedPackages.take(3).forEach { packageName ->
                        AppIdentity(
                            packageName = packageName,
                            displayName = DriftAppCatalog.labelFor(context, packageName),
                            showName = false,
                            iconSize = 30.dp,
                        )
                    }
                }
            }
            Text(
                formatEpisodeRange(episode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun EpisodeStoryCard(items: List<AttentionStoryItem>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TaskTunnelTokens.CardRadius))
            .background(SurfaceRaised),
    ) {
        if (items.isEmpty()) {
            Text(
                "No interruptions were recorded.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        items.forEachIndexed { index, item ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    formatTime(item.timestampMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (index < items.lastIndex) {
                RowDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
fun ProtectionScreen(
    snapshot: ProtectionSnapshot,
    protectionEnabled: Boolean,
    setupChecklist: SetupChecklistState,
    openSupportedApp: (String) -> Unit,
    setProtectionEnabled: (Boolean) -> Unit,
    selectedDriftPackages: Set<String>,
    availableDriftApps: List<KnownDriftApp>,
    intentionalCheckInsEnabled: Boolean,
    setIntentionalCheckInsEnabled: (Boolean) -> Unit,
    setDriftEnabled: (Boolean) -> Boolean,
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MasterProtectionCard(
                enabled = protectionEnabled,
                accessibilityEnabled = snapshot.diagnosticReport.accessibilityEnabled,
                setEnabled = setProtectionEnabled,
            )
        }
        if (!setupChecklist.complete) {
            item {
                SetupChecklistCard(
                    state = setupChecklist,
                    protectionEnabled = protectionEnabled,
                    supportedApps = snapshot.apps.take(3),
                    enableAccessibility = repairAccessibility,
                    openSupportedApp = openSupportedApp,
                )
            }
        }
        if (protectionEnabled && snapshot.health.level != ProtectionLevel.ACTIVE) {
            item {
                ProtectionNeedsAttentionCard(
                    health = snapshot.health,
                    repairAccessibility = repairAccessibility,
                    openTroubleshooting = openDiagnostics,
                )
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            SectionHeader("Works with", Modifier.padding(bottom = TaskTunnelTokens.SectionHeaderBottomGap))
            SupportedAppsCard(snapshot.apps.take(3))
        }
        item {
            FeatureToggleCard(
                title = "Check-ins",
                description = "Ask again during longer sessions so an intentional browse doesn't quietly turn into autopilot.",
                checked = intentionalCheckInsEnabled,
                enabled = protectionEnabled,
                onCheckedChange = setIntentionalCheckInsEnabled,
            )
        }
        item {
            DriftConfigurationSection(
                selectedPackages = selectedDriftPackages,
                availableApps = availableDriftApps,
                controlsEnabled = protectionEnabled,
                setDriftEnabled = setDriftEnabled,
                setAppEnabled = setDriftAppEnabled,
            )
        }
    }
}

@Composable
private fun ProtectionNeedsAttentionCard(
    health: ProtectionHealth,
    repairAccessibility: () -> Unit,
    openTroubleshooting: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TaskTunnelTokens.CardRadius))
            .background(LimitedAmber.copy(alpha = 0.10f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Task Tunnel needs a quick fix", style = MaterialTheme.typography.titleMedium)
        Text(
            "Task Tunnel may miss moments when you drift until Android access is restored.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = repairAccessibility,
                shape = RoundedCornerShape(TaskTunnelTokens.ActionRadius),
            ) { Text("Fix now") }
            TextButton(onClick = openTroubleshooting) { Text("Troubleshoot") }
        }
        if (health.backgroundConcern) {
            Text(
                "If this keeps happening after access is on, Troubleshooting has the Android-specific checks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeatureToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TaskTunnelTokens.CardRadius))
            .background(SurfaceRaised)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SupportedAppsCard(apps: List<InstalledAppStatus>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TaskTunnelTokens.CardRadius))
            .background(SurfaceRaised)
            .padding(horizontal = 10.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        apps.forEach { app ->
            val status = when (app.compatibility) {
                AppCompatibility.NOT_INSTALLED -> "Not installed"
                AppCompatibility.KNOWN_COMPATIBILITY_PROBLEM -> "Needs attention"
                AppCompatibility.VERIFIED, AppCompatibility.VERSION_NOT_RECORDED -> "Ready"
            }
            val statusColor = when (app.compatibility) {
                AppCompatibility.NOT_INSTALLED -> MaterialTheme.colorScheme.onSurfaceVariant
                AppCompatibility.KNOWN_COMPATIBILITY_PROBLEM -> LimitedAmber
                AppCompatibility.VERIFIED, AppCompatibility.VERSION_NOT_RECORDED -> HealthyGreen
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppIcon(app.packageName, app.displayName, Modifier.size(40.dp))
                Text(app.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, style = MaterialTheme.typography.bodySmall, color = statusColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SetupChecklistCard(
    state: SetupChecklistState,
    protectionEnabled: Boolean,
    supportedApps: List<InstalledAppStatus>,
    enableAccessibility: () -> Unit,
    openSupportedApp: (String) -> Unit,
) {
    val firstInstalledApp = supportedApps.firstOrNull { it.versionName != null }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TaskTunnelTokens.CardRadius))
            .background(SurfaceRaised)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Finish setting up", style = MaterialTheme.typography.titleMedium)
            Text(
                "Task Tunnel can stay out of your way once these basics are done.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SetupChecklistRow(
            complete = state.supportedAppInstalled,
            title = "Supported app available",
            detail = if (state.supportedAppInstalled) {
                "Instagram, YouTube, or TikTok is ready to use."
            } else {
                "Install Instagram, YouTube, or TikTok to use Task Tunnel intentions."
            },
        )
        SetupChecklistRow(
            complete = state.accessibilityEnabled,
            title = "Android permission ready",
            detail = if (state.accessibilityEnabled) {
                "Task Tunnel can step in when you move away from your intention."
            } else {
                "Turn this on so Purpose Gate and gentle reminders can appear."
            },
        )
        SetupChecklistRow(
            complete = state.firstTunnelStarted,
            title = "Try your first intention",
            detail = if (state.firstTunnelStarted) {
                "Your first Task Tunnel has started successfully."
            } else {
                "Open a supported app and choose what you came to do."
            },
        )
        when {
            !protectionEnabled -> Text(
                "Turn on Task Tunnel above before trying an intention.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            !state.accessibilityEnabled -> Button(
                onClick = enableAccessibility,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(TaskTunnelTokens.ActionRadius),
            ) { Text("Enable permission") }
            !state.firstTunnelStarted && firstInstalledApp != null -> Button(
                onClick = { openSupportedApp(firstInstalledApp.packageName) },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(TaskTunnelTokens.ActionRadius),
            ) { Text("Try Task Tunnel") }
        }
    }
}

@Composable
private fun SetupChecklistRow(complete: Boolean, title: String, detail: String) {
    val markerColor = if (complete) HealthyGreen else MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Canvas(Modifier.padding(top = 5.dp).size(10.dp)) {
            drawCircle(markerColor)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MasterProtectionCard(
    enabled: Boolean,
    accessibilityEnabled: Boolean,
    setEnabled: (Boolean) -> Unit,
) {
    val statusColor = when {
        !enabled -> LimitedAmber
        !accessibilityEnabled -> LimitedAmber
        else -> HealthyGreen
    }
    val statusText = when {
        !enabled -> "Paused"
        !accessibilityEnabled -> "Needs setup"
        else -> "Active"
    }
    val motifColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TaskTunnelTokens.CardRadius))
            .background(SurfaceRaised)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawLine(
                    color = motifColor.copy(alpha = 0.10f),
                    start = Offset(size.width * 0.03f, size.height * 0.12f),
                    end = Offset(size.width * 0.47f, size.height * 0.94f),
                    strokeWidth = stroke,
                )
                drawLine(
                    color = motifColor.copy(alpha = 0.10f),
                    start = Offset(size.width * 0.97f, size.height * 0.12f),
                    end = Offset(size.width * 0.53f, size.height * 0.94f),
                    strokeWidth = stroke,
                )
                drawCircle(
                    color = motifColor.copy(alpha = 0.14f),
                    radius = 3.dp.toPx(),
                    center = Offset(size.width * 0.5f, size.height * 0.91f),
                )
            },
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (enabled) "Task Tunnel is active" else "Task Tunnel is paused",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        when {
                            !enabled -> "Your settings are saved. Turn it back on whenever you want help staying intentional."
                            !accessibilityEnabled -> "One Android permission needs attention before Task Tunnel can step in."
                            else -> "It’ll ask why you opened a supported app and step in if you wander away."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(statusColor.copy(alpha = 0.13f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(statusText, style = MaterialTheme.typography.labelMedium, color = statusColor)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (enabled) {
                    Button(
                        onClick = { setEnabled(false) },
                        shape = RoundedCornerShape(TaskTunnelTokens.ActionRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandBlueContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) { Text("Turn off") }
                } else {
                    Button(
                        onClick = { setEnabled(true) },
                        shape = RoundedCornerShape(TaskTunnelTokens.ActionRadius),
                    ) { Text("Turn on") }
                }
            }
        }
    }
}

@Composable
fun DriftConfigurationSection(
    selectedPackages: Set<String>,
    availableApps: List<KnownDriftApp>,
    setDriftEnabled: (Boolean) -> Boolean,
    setAppEnabled: (String, Boolean) -> Unit,
    controlsEnabled: Boolean = true,
) {
    var showAppPicker by remember { mutableStateOf(false) }
    val enabled = selectedPackages.isNotEmpty()
    val availablePackages = availableApps.mapTo(hashSetOf()) { it.packageName }
    val selectedApps = availableApps.filter { it.packageName in selectedPackages }
    val hiddenSelectionCount = selectedPackages.count { it !in availablePackages }

    if (showAppPicker) {
        DriftAppPickerDialog(
            apps = availableApps,
            selectedPackages = selectedPackages,
            setAppEnabled = setAppEnabled,
            dismiss = { showAppPicker = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TaskTunnelTokens.CardRadius))
            .background(SurfaceRaised)
            .alpha(if (controlsEnabled) 1f else 0.5f)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Drift", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Catch yourself bouncing quickly between apps without meaning to.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = { requested ->
                    if (requested && !setDriftEnabled(true)) {
                        showAppPicker = true
                    } else if (!requested) {
                        setDriftEnabled(false)
                    }
                },
                enabled = controlsEnabled,
            )
        }
        if (enabled) {
            val count = selectedApps.size + hiddenSelectionCount
            val visibleSummary = when {
                selectedApps.isEmpty() && hiddenSelectionCount > 0 -> "$hiddenSelectionCount unavailable ${pluralize(hiddenSelectionCount, "app")} selected"
                selectedApps.isEmpty() -> "Choose which apps can form a Drift path"
                selectedApps.size <= 3 && hiddenSelectionCount == 0 -> selectedApps.joinToString(" · ") { it.displayName }
                else -> "$count ${pluralize(count, "app")} included"
            }
            RowDivider()
            SettingsRow(
                title = "Apps included",
                subtitle = visibleSummary,
                trailingText = count.toString(),
                showChevron = controlsEnabled,
                enabled = controlsEnabled,
                onClick = if (controlsEnabled) ({ showAppPicker = true }) else null,
            )
        }
    }
}

@Composable
private fun DriftAppPickerDialog(
    apps: List<KnownDriftApp>,
    selectedPackages: Set<String>,
    setAppEnabled: (String, Boolean) -> Unit,
    dismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val filteredApps = if (normalizedQuery.isEmpty()) {
        apps
    } else {
        apps.filter {
            it.displayName.contains(normalizedQuery, ignoreCase = true) ||
                it.packageName.contains(normalizedQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Apps included in Drift") },
        text = {
            Column {
                Text(
                    "Choose the apps that can be part of a Drift path.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                    singleLine = true,
                    placeholder = { Text("Search apps") },
                    shape = RoundedCornerShape(TaskTunnelTokens.ActionRadius),
                )
                if (filteredApps.isEmpty()) {
                    Text(
                        "No matching apps found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val selected = app.packageName in selectedPackages
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .clickable { setAppEnabled(app.packageName, !selected) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(app.packageName, app.displayName, Modifier.size(34.dp))
                                Spacer(Modifier.width(TaskTunnelTokens.IconTextGap))
                                Text(
                                    app.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { setAppEnabled(app.packageName, it) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
    )
}

@Composable
fun OnboardingScreen(
    progress: OnboardingProgress,
    accessibilityEnabled: Boolean,
    supportedApps: List<InstalledAppStatus>,
    firstTunnelStarted: Boolean,
    selectedDriftPackages: Set<String>,
    availableDriftApps: List<KnownDriftApp>,
    setDriftEnabled: (Boolean) -> Boolean,
    setDriftAppEnabled: (String, Boolean) -> Unit,
    advance: () -> Unit,
    openAccessibilitySettings: () -> Unit,
    openSupportedApp: (String) -> Unit,
    completeLater: () -> Unit,
) {
    val installedSupportedApps = supportedApps.filter { it.versionName != null }
    val stepLabel = if (progress.step == OnboardingStep.READY) "Ready" else "${progress.step.ordinal + 1} of 5"
    val primaryLabel = when (progress.step) {
        OnboardingStep.VALUE -> "Get started"
        OnboardingStep.APPS -> "Continue"
        OnboardingStep.ACCESSIBILITY -> if (accessibilityEnabled) "Agree & continue" else "Agree & open settings"
        OnboardingStep.DRIFT -> "Continue"
        OnboardingStep.TRY -> null
        OnboardingStep.READY -> "Start using Task Tunnel"
    }
    val primaryAction: (() -> Unit)? = when (progress.step) {
        OnboardingStep.ACCESSIBILITY -> if (accessibilityEnabled) advance else openAccessibilitySettings
        OnboardingStep.TRY -> null
        else -> advance
    }
    val secondaryLabel = when (progress.step) {
        OnboardingStep.VALUE,
        OnboardingStep.APPS,
        -> "Set up later"
        OnboardingStep.ACCESSIBILITY -> if (accessibilityEnabled) null else "Not now"
        OnboardingStep.DRIFT -> if (selectedDriftPackages.isEmpty()) "Skip for now" else null
        OnboardingStep.TRY -> "Try later"
        OnboardingStep.READY -> null
    }
    val secondaryAction: (() -> Unit)? = when (progress.step) {
        OnboardingStep.DRIFT -> advance
        OnboardingStep.VALUE,
        OnboardingStep.APPS,
        OnboardingStep.ACCESSIBILITY,
        OnboardingStep.TRY,
        -> completeLater
        OnboardingStep.READY -> null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (primaryAction != null || secondaryAction != null) {
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding()
                        .padding(horizontal = TaskTunnelTokens.ScreenHorizontalPadding, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (primaryAction != null && primaryLabel != null) {
                        Button(
                            onClick = primaryAction,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(TaskTunnelTokens.ActionRadius),
                        ) { Text(primaryLabel) }
                    }
                    if (secondaryAction != null && secondaryLabel != null) {
                        TextButton(onClick = secondaryAction, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(secondaryLabel)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()).padding(horizontal = TaskTunnelTokens.ScreenHorizontalPadding, vertical = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Task Tunnel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(stepLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(32.dp))
            when (progress.step) {
                OnboardingStep.VALUE -> ValueOnboarding()
                OnboardingStep.APPS -> SupportedAppsOnboarding(supportedApps)
                OnboardingStep.ACCESSIBILITY -> AccessibilityOnboarding(accessibilityEnabled)
                OnboardingStep.DRIFT -> DriftOnboarding(
                    selected = selectedDriftPackages,
                    availableApps = availableDriftApps,
                    setEnabled = setDriftEnabled,
                    setApp = setDriftAppEnabled,
                )
                OnboardingStep.TRY -> TryOnboarding(
                    accessibilityEnabled = accessibilityEnabled,
                    installedApps = installedSupportedApps,
                    firstTunnelStarted = firstTunnelStarted,
                    openAccessibilitySettings = openAccessibilitySettings,
                    openSupportedApp = openSupportedApp,
                )
                OnboardingStep.READY -> ReadyOnboarding(
                    accessibilityEnabled = accessibilityEnabled,
                    supportedAppInstalled = installedSupportedApps.isNotEmpty(),
                    firstTunnelStarted = firstTunnelStarted,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ValueOnboarding() {
    Text("Use distracting apps with a reason", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Text(
        "Task Tunnel asks what you came to do, then stays quiet until your attention moves somewhere that no longer fits.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp),
    )
    Spacer(Modifier.height(30.dp))
    OnboardingPoint("1", "Choose a purpose", "Reply, search, watch, post, or browse intentionally.")
    OnboardingPoint("2", "Use the real app", "Nothing is replaced or locked behind a fake interface.")
    OnboardingPoint("3", "Decide when you drift", "Return, continue consciously, or end the Tunnel.")
}

@Composable
private fun SupportedAppsOnboarding(apps: List<InstalledAppStatus>) {
    Text("Built around the apps you already use", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Text(
        "Task Tunnel currently understands intentions inside Instagram, YouTube, and TikTok. You don't need to configure each one separately.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 22.dp),
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(TaskTunnelTokens.CardRadius)).background(SurfaceRaised),
    ) {
        apps.forEachIndexed { index, app ->
            SupportedAppSetupRow(app)
            if (index < apps.lastIndex) RowDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
    if (apps.none { it.versionName != null }) {
        Text(
            "Install at least one supported app before trying Task Tunnel. You can finish the rest of setup now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun SupportedAppSetupRow(app: InstalledAppStatus) {
    val installed = app.versionName != null
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, app.displayName, Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(app.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                if (installed) "Ready for Task Tunnel" else "Not installed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccessibilityOnboarding(accessibilityEnabled: Boolean) {
    Text("Let Task Tunnel notice where you are", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Text(
        "Task Tunnel uses Android Accessibility to access app activity and on-screen interface information so it can recognize broad places like Messages, Reels, Search or Shorts and step in when your use no longer matches the purpose you chose.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 24.dp),
    )
    SetupFact("Used only for Task Tunnel", "Accessibility information is processed on this device to recognize supported app surfaces, route you back when you choose to return, and detect Drift across apps you selected.")
    SetupFact("Not sent or stored as content", "Task Tunnel does not send or store message contents, searches, titles, screenshots, usernames, or raw accessibility trees.")
    SetupFact("When unsure, it stays out of the way", "If Task Tunnel can't confidently tell where you are, it leaves you alone.")
    Spacer(Modifier.height(20.dp))
    val statusColor = if (accessibilityEnabled) HealthyGreen else LimitedAmber
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(9.dp)) { drawCircle(statusColor) }
        Spacer(Modifier.width(9.dp))
        Text(
            if (accessibilityEnabled) "Permission is ready" else "Permission still needs to be enabled",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SetupFact(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DriftOnboarding(
    selected: Set<String>,
    availableApps: List<KnownDriftApp>,
    setEnabled: (Boolean) -> Boolean,
    setApp: (String, Boolean) -> Unit,
) {
    Text("Catch app-hopping too", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Text(
        "Drift is optional. It notices quick switching such as Reddit → Instagram → YouTube and gives you a moment to notice the pattern.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 22.dp),
    )
    DriftConfigurationSection(selected, availableApps, setEnabled, setApp)
    Text(
        "For apps used only in Drift, Task Tunnel only notices which app is in front. It does not read what is on that app's screen.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun TryOnboarding(
    accessibilityEnabled: Boolean,
    installedApps: List<InstalledAppStatus>,
    firstTunnelStarted: Boolean,
    openAccessibilitySettings: () -> Unit,
    openSupportedApp: (String) -> Unit,
) {
    Text("Try it once", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Text(
        "Open a supported app, choose what you came to do when Purpose Gate appears, then come back here. That's the whole interaction.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 24.dp),
    )
    when {
        firstTunnelStarted -> {
            SetupFact("It worked", "Your first Task Tunnel started successfully.")
        }
        !accessibilityEnabled -> {
            Text(
                "The Android permission above needs to be on before Purpose Gate can appear.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = openAccessibilitySettings,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(50.dp),
                shape = RoundedCornerShape(TaskTunnelTokens.ActionRadius),
            ) { Text("Enable permission") }
        }
        installedApps.isEmpty() -> {
            Text(
                "No supported app is installed yet. Install Instagram, YouTube, or TikTok, then return here to try it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {
            installedApps.forEachIndexed { index, app ->
                val content: @Composable () -> Unit = {
                    AppIcon(app.packageName, app.displayName, Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Open ${app.displayName}")
                }
                if (index == 0) {
                    Button(
                        onClick = { openSupportedApp(app.packageName) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(TaskTunnelTokens.ActionRadius),
                        content = { content() },
                    )
                } else {
                    OutlinedButton(
                        onClick = { openSupportedApp(app.packageName) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(50.dp),
                        shape = RoundedCornerShape(TaskTunnelTokens.ActionRadius),
                        content = { content() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadyOnboarding(
    accessibilityEnabled: Boolean,
    supportedAppInstalled: Boolean,
    firstTunnelStarted: Boolean,
) {
    Text("You're ready", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Text(
        "Task Tunnel is set up. Open a supported app normally and Purpose Gate will appear when protection is active.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 26.dp),
    )
    OnboardingStatusRow(supportedAppInstalled, "Supported app ready")
    OnboardingStatusRow(accessibilityEnabled, "Android permission ready")
    OnboardingStatusRow(firstTunnelStarted, "First intention tried")
}

@Composable
private fun OnboardingStatusRow(complete: Boolean, label: String) {
    val color = if (complete) HealthyGreen else LimitedAmber
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Spacer(Modifier.width(11.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun OnboardingPoint(index: String, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(index, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DisclosureContent() {
    Text("How Task Tunnel works on your phone", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Text(
        "Task Tunnel uses Android Accessibility to access app activity and on-screen interface information needed to recognize broad places inside supported apps and step in only when it needs to.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 18.dp),
    )
    DisclosureItem("What it notices", "In Instagram, YouTube and TikTok, Task Tunnel recognizes broad places such as Messages, Reels, Search, videos, Shorts and Inbox. Apps used only for Drift contribute only which app is currently in front.")
    DisclosureItem("Why", "This lets Task Tunnel notice when you move away from the purpose you chose, and when you rapidly hop between selected apps.")
    DisclosureItem("What stays private", "Task Tunnel does not keep screenshots, message contents, searches, video titles, usernames, raw screen text, or raw accessibility trees.")
    DisclosureItem("What is saved", "Your chosen purposes, Drift app choices, meaningful transitions, prompts and the choices you make are stored locally so Attention and Review can work.")
    DisclosureItem("Where your data lives", "Protection and activity processing stay on this device. Task Tunnel has no account, cloud sync, analytics upload, or remote detector service.")
}

@Composable
private fun DisclosureItem(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AccessibilityDisclosureScreen(accessEnabled: Boolean, continueToSettings: () -> Unit, decline: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TaskTunnelTokens.ScreenHorizontalPadding, vertical = 14.dp),
    ) {
        item { DisclosureContent() }
        item {
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Canvas(Modifier.size(8.dp)) {
                    drawCircle(if (accessEnabled) HealthyGreen else LimitedAmber)
                }
                Text(
                    if (accessEnabled) "Android permission is ready" else "Android permission is off",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "By tapping Agree, you consent to Task Tunnel using Android Accessibility for the purposes described above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
            Button(
                onClick = continueToSettings,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp),
                shape = RoundedCornerShape(TaskTunnelTokens.ActionRadius),
            ) { Text(if (accessEnabled) "Agree & continue" else "Agree & open Android settings") }
            TextButton(
                onClick = decline,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) { Text("Not now") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun SettingsScreen(
    appVersion: String,
    historyAvailable: Boolean,
    notificationControlsEnabled: Boolean,
    configureNotificationControls: () -> Unit,
    clearHistory: () -> Unit,
    openDiagnostics: () -> Unit,
    openDisclosure: () -> Unit,
    openDeveloper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear activity history?") },
            text = { Text("This permanently deletes your past Task Tunnel activity from this phone. Your protection settings stay the same.") },
            confirmButton = {
                TextButton(onClick = { clearHistory(); confirmClear = false }) { Text("Clear history", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TaskTunnelTokens.ScreenHorizontalPadding, vertical = 12.dp),
    ) {
        item {
            SectionHeader("Everyday", Modifier.padding(bottom = TaskTunnelTokens.SectionHeaderBottomGap))
            SettingsRow(
                title = "Notifications",
                subtitle = if (notificationControlsEnabled) {
                    "Continue, change purpose or end an active Task Tunnel from your notification shade"
                } else {
                    "Off — turn these on if you want controls outside the app"
                },
                leading = { TaskTunnelIcon(TaskTunnelIconKind.ATTENTION, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
                showChevron = true,
                onClick = configureNotificationControls,
            )
            RowDivider(inset = true)
            SettingsRow(
                title = "Privacy",
                subtitle = "See what Task Tunnel notices and what always stays private",
                leading = { TaskTunnelIcon(TaskTunnelIconKind.INFO, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
                showChevron = true,
                onClick = openDisclosure,
            )
            Spacer(Modifier.height(TaskTunnelTokens.MajorSectionGap))
            SectionHeader("Your data", Modifier.padding(bottom = TaskTunnelTokens.SectionHeaderBottomGap))
            SettingsRow(
                title = "Clear activity history",
                subtitle = if (historyAvailable) {
                    "Delete past Attention and usage activity from this phone"
                } else {
                    "Activity history is temporarily unavailable. Reopen Task Tunnel and try again."
                },
                leading = { TaskTunnelIcon(TaskTunnelIconKind.DELETE, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
                enabled = historyAvailable,
                onClick = { confirmClear = true },
            )
            Spacer(Modifier.height(TaskTunnelTokens.MajorSectionGap))
            SectionHeader("Advanced", Modifier.padding(bottom = TaskTunnelTokens.SectionHeaderBottomGap))
            SettingsRow(
                title = if (showAdvanced) "Hide advanced options" else "Advanced options",
                subtitle = "Troubleshooting and technical tools",
                leading = { TaskTunnelIcon(TaskTunnelIconKind.SETTINGS, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
                showChevron = true,
                onClick = { showAdvanced = !showAdvanced },
            )
            if (showAdvanced) {
                RowDivider(inset = true)
                SettingsRow(
                    title = "Troubleshooting",
                    subtitle = "Check Task Tunnel's Android access and copy a safe status report",
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
            }
            Spacer(Modifier.height(TaskTunnelTokens.MajorSectionGap))
            SectionHeader("About", Modifier.padding(bottom = TaskTunnelTokens.SectionHeaderBottomGap))
            SettingsRow("Task Tunnel", "Core activity stays on this device", trailingText = appVersion)
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
            Text("Troubleshooting", style = MaterialTheme.typography.titleLarge)
            Text(
                "This status report helps diagnose Android setup problems. It never includes your app content, usernames, screenshots or activity history.",
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
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp).height(50.dp),
                shape = RoundedCornerShape(TaskTunnelTokens.ActionRadius),
            ) { Text(if (copied) "Status copied" else "Copy status report") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun startOfLocalDay(timeMillis: Long): Long = Calendar.getInstance().apply {
    this.timeInMillis = timeMillis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun attentionDayLabel(dayStartMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val todayStart = startOfLocalDay(nowMillis)
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = todayStart
        add(Calendar.DAY_OF_YEAR, -1)
    }.timeInMillis
    return when (dayStartMillis) {
        todayStart -> "Today"
        yesterday -> "Yesterday"
        else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(java.util.Date(dayStartMillis))
    }
}
