package com.example.tasktunnel.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tasktunnel.protection.ProtectionHealth
import com.example.tasktunnel.protection.ProtectionLevel
import com.example.tasktunnel.ui.AppIcon
import com.example.tasktunnel.ui.theme.TaskTunnelTokens

private val ReelsVideo = Color(0xFF83A9CF)
private val MessagesShorts = Color(0xFFABA2C6)
private val ExploreSearch = Color(0xFFB7AD91)
private val Other = Color(0xFF737C85)
private val Unclassified = Color(0xFF444C54)
private val BarRemainder = Color(0xFF59616A)

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    health: ProtectionHealth,
    refresh: () -> Unit,
    turnProtectionOn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        HomeUiState.Loading -> HomeLoading(modifier)
        HomeUiState.Unavailable -> HomeUnavailable(refresh, modifier)
        is HomeUiState.Loaded -> HomeLoaded(uiState.presentation, health, turnProtectionOn, modifier)
    }
}

@Composable
private fun HomeLoading(modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = TaskTunnelTokens.ScreenHorizontalPadding, vertical = 20.dp)) {
        Text("Today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("Loading today’s activity…", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HomeUnavailable(refresh: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = TaskTunnelTokens.ScreenHorizontalPadding, vertical = 20.dp)) {
        Text("Today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("Today’s activity is unavailable", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("Try again in a moment. Protection can still run while activity history is unavailable.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = refresh, modifier = Modifier.padding(top = 8.dp)) { Text("Try again") }
    }
}

@Composable
private fun HomeLoaded(
    presentation: HomePresentation,
    health: ProtectionHealth,
    turnProtectionOn: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TaskTunnelTokens.ScreenHorizontalPadding, vertical = 8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(
                    "${formatHomeDuration(presentation.totalTrackedTodayMillis)} tracked",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (presentation.apps.isEmpty()) {
                Text("Nothing tracked yet.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Use Instagram, YouTube, or TikTok with Protection active to see where your time went inside each app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (health.level == ProtectionLevel.OFF) {
                    Spacer(Modifier.height(8.dp))
                    HomeProtectionNotice(health, turnProtectionOn)
                } else if (health.level == ProtectionLevel.LIMITED) {
                    Spacer(Modifier.height(8.dp))
                    HomeProtectionNotice(health, turnProtectionOn)
                }
            } else {
                if (health.level != ProtectionLevel.ACTIVE) {
                    Spacer(Modifier.height(8.dp))
                    HomeProtectionNotice(health, turnProtectionOn)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (presentation.apps.isNotEmpty()) {
            presentation.apps.forEachIndexed { index, app ->
                item { AppUsageSection(app) }
                if (index < presentation.apps.lastIndex) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
            item {
                val trackingAvailabilityNote = presentation.trackingAvailabilityNote
                if (trackingAvailabilityNote != null) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        trackingAvailabilityNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeProtectionNotice(health: ProtectionHealth, turnProtectionOn: () -> Unit) {
    val isOff = health.level == ProtectionLevel.OFF
    val statusColor = if (isOff) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) {
            drawCircle(statusColor)
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(if (isOff) "Protection off" else health.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (isOff) "Tracking is paused" else health.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isOff) {
            TextButton(
                onClick = turnProtectionOn,
            ) { Text("Turn on") }
        }
    }
}

@Composable
private fun AppUsageSection(app: HomeAppUsage) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.app.packageName, app.app.displayName, Modifier.size(38.dp))
            Spacer(Modifier.width(12.dp))
            Text(app.app.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(formatHomeDuration(app.totalTrackedDurationMillis), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        if (!app.shouldDeemphasizeComposition) {
            HomeCompositionBar(app)
            Spacer(Modifier.height(8.dp))
        }
        app.surfaceRows.forEach { surface -> HomeSurfaceRow(surface) }
        if (app.shouldDeemphasizeComposition || app.showMeaningfulUnclassifiedNote) {
            Text(
                if (app.shouldDeemphasizeComposition) {
                    "Only part of this activity could be categorized."
                } else {
                    "Some ${app.app.displayName} activity couldn’t be categorized."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun HomeCompositionBar(app: HomeAppUsage) {
    Canvas(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).semantics { invisibleToUser() },
    ) {
        var start = 0f
        val total = app.totalTrackedDurationMillis.toFloat().coerceAtLeast(1f)
        app.surfaceRows.forEach { surface ->
            val fraction = surface.durationMillis / total
            val color = if (fraction < 0.01f) BarRemainder else surfaceColor(surface.kind)
            val width = size.width * fraction
            if (width > 0f) drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(start, 0f), size = androidx.compose.ui.geometry.Size(width, size.height))
            start += width
        }
    }
}

@Composable
private fun HomeSurfaceRow(surface: HomeSurfaceUsage) {
    val subdued = surface.kind == HomeSurfaceKind.OTHER || surface.kind == HomeSurfaceKind.UNCLASSIFIED
    val textColor = if (subdued) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().heightIn(min = 31.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(5.dp).semantics { invisibleToUser() }) { drawCircle(surfaceColor(surface.kind)) }
        Spacer(Modifier.width(9.dp))
        Text(surface.kind.displayLabel, style = MaterialTheme.typography.bodyMedium, color = textColor, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            formatHomeDuration(surface.durationMillis),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = textColor,
            textAlign = TextAlign.End,
        )
    }
}

private fun surfaceColor(kind: HomeSurfaceKind): Color = when (kind) {
    HomeSurfaceKind.HOME, HomeSurfaceKind.FEED, HomeSurfaceKind.FRIENDS -> ReelsVideo
    HomeSurfaceKind.INBOX, HomeSurfaceKind.PROFILE, HomeSurfaceKind.CREATE -> MessagesShorts
    HomeSurfaceKind.REELS, HomeSurfaceKind.VIDEO -> ReelsVideo
    HomeSurfaceKind.MESSAGES, HomeSurfaceKind.SHORTS -> MessagesShorts
    HomeSurfaceKind.EXPLORE, HomeSurfaceKind.SEARCH, HomeSurfaceKind.SUBSCRIPTIONS -> ExploreSearch
    HomeSurfaceKind.YOU, HomeSurfaceKind.OTHER -> Other
    HomeSurfaceKind.UNCLASSIFIED -> Unclassified
}
