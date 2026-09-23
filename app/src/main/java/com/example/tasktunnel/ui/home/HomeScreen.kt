package com.example.tasktunnel.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tasktunnel.R
import com.example.tasktunnel.attention.AttentionApp
import com.example.tasktunnel.protection.ProtectionHealth
import com.example.tasktunnel.protection.ProtectionLevel
import com.example.tasktunnel.ui.AppIcon
import com.example.tasktunnel.ui.theme.TaskTunnelTokens

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
        Text("Today", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Text("Loading today’s activity…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeUnavailable(refresh: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = TaskTunnelTokens.ScreenHorizontalPadding, vertical = 20.dp)) {
        Text("Today", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Text("Today’s activity is unavailable", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("Try again in a moment. Task Tunnel can still protect your intentions in the meantime.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val apps = presentation.apps.sortedByDescending(HomeAppUsage::totalTrackedDurationMillis)
    val maxAppDuration = apps.maxOfOrNull(HomeAppUsage::totalTrackedDurationMillis) ?: 1L

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TaskTunnelTokens.ScreenHorizontalPadding, vertical = 12.dp),
    ) {
        item {
            Text("Today", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            if (apps.isEmpty()) {
                EmptyTodayState(health, turnProtectionOn)
            } else {
                TodayHero(presentation.totalTrackedTodayMillis)
                if (health.level != ProtectionLevel.ACTIVE) {
                    Spacer(Modifier.height(16.dp))
                    HomeProtectionNotice(health, turnProtectionOn)
                }
                Spacer(Modifier.height(30.dp))
                Text("Where your time went", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(18.dp))
            }
        }

        apps.forEachIndexed { index, app ->
            item {
                AppUsageSection(app = app, maxAppDurationMillis = maxAppDuration)
                if (index < apps.lastIndex) Spacer(Modifier.height(24.dp))
            }
        }

        item {
            val trackingAvailabilityNote = presentation.trackingAvailabilityNote
            if (trackingAvailabilityNote != null && apps.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    trackingAvailabilityNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TodayHero(totalTrackedTodayMillis: Long) {
    Text(
        formatHomeDuration(totalTrackedTodayMillis),
        style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(2.dp))
    Text(
        "across Instagram, YouTube & TikTok",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmptyTodayState(health: ProtectionHealth, turnProtectionOn: () -> Unit) {
    Text(
        "—",
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "No activity here yet",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EmptyStateAppIcon(AttentionApp.INSTAGRAM)
        EmptyStateAppIcon(AttentionApp.YOUTUBE)
        EmptyStateAppIcon(AttentionApp.TIKTOK)
    }
    Spacer(Modifier.height(12.dp))
    Text(
        if (health.level == ProtectionLevel.OFF) {
            "Turn Task Tunnel on to start seeing today’s activity."
        } else {
            "Your activity will appear here as you use these apps."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (health.level != ProtectionLevel.ACTIVE) {
        Spacer(Modifier.height(16.dp))
        HomeProtectionNotice(health, turnProtectionOn)
    }
}

@Composable
private fun EmptyStateAppIcon(app: AttentionApp) {
    AppIcon(app.packageName, app.displayName, Modifier.size(34.dp))
}

@Composable
private fun HomeProtectionNotice(health: ProtectionHealth, turnProtectionOn: () -> Unit) {
    val isOff = health.level == ProtectionLevel.OFF
    val statusColor = if (isOff) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TaskTunnelTokens.NoticeRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(8.dp)) { drawCircle(statusColor) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (isOff) "Task Tunnel is paused" else "Task Tunnel needs attention", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (isOff) "Turn it on to resume intentions and activity history" else "Task Tunnel may miss some moments until Android access is fixed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = turnProtectionOn) {
            Text(if (isOff) "Turn on" else "Fix now", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AppUsageSection(
    app: HomeAppUsage,
    maxAppDurationMillis: Long,
) {
    var expanded by remember(app.app) { mutableStateOf(false) }
    val primarySurface = app.primarySurface()
    val summary = when {
        app.shouldDeemphasizeComposition -> "Activity details are limited"
        primarySurface != null -> "Most time: ${primarySurface.kind.displayLabel} · ${formatHomeDuration(primarySurface.durationMillis)}"
        else -> "Tap for activity details"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) { expanded = !expanded }
            .padding(vertical = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.app.packageName, app.app.displayName, Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Text(app.app.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                formatHomeDuration(app.totalTrackedDurationMillis),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(11.dp))
        UsageBar(
            fraction = app.totalTrackedDurationMillis.toFloat() / maxAppDurationMillis.coerceAtLeast(1L).toFloat(),
            emphasized = true,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (expanded) "Hide" else "Details",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (expanded) {
            Spacer(Modifier.height(16.dp))
            SurfaceBreakdown(app)
        }
    }
}

@Composable
private fun SurfaceBreakdown(app: HomeAppUsage) {
    val rows = app.compactSurfaceRows()
    val maxSurfaceDuration = rows.maxOfOrNull(HomeSurfaceDisplayUsage::durationMillis) ?: 1L

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 52.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        rows.forEachIndexed { index, surface ->
            SurfaceBreakdownRow(
                surface = surface,
                maxSurfaceDurationMillis = maxSurfaceDuration,
                rank = index,
            )
        }
        if (app.shouldDeemphasizeComposition || app.showMeaningfulUnclassifiedNote) {
            Text(
                if (app.shouldDeemphasizeComposition) {
                    "Only part of this activity could be categorized."
                } else {
                    "Some ${app.app.displayName} activity couldn’t be categorized."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SurfaceBreakdownRow(
    surface: HomeSurfaceDisplayUsage,
    maxSurfaceDurationMillis: Long,
    rank: Int,
) {
    val textColor = if (surface.isOther) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SurfaceStatIcon(surface.kind, muted = surface.isOther)
        Spacer(Modifier.width(10.dp))
        Text(
            surface.label,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            formatHomeDuration(surface.durationMillis),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = textColor,
            textAlign = TextAlign.End,
        )
    }
    Spacer(Modifier.height(5.dp))
    UsageBar(
        fraction = surface.durationMillis.toFloat() / maxSurfaceDurationMillis.coerceAtLeast(1L).toFloat(),
        emphasized = !surface.isOther,
        alpha = when (rank) {
            0 -> 1f
            1 -> 0.72f
            2 -> 0.54f
            else -> 0.38f
        },
    )
}


@Composable
private fun SurfaceStatIcon(kind: HomeSurfaceKind, muted: Boolean) {
    val containerColor = if (muted) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }
    val iconColor = if (muted) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.primary
    }

    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(containerColor)
            .semantics { invisibleToUser() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(surfaceIconRes(kind)),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(17.dp),
        )
    }
}

private fun surfaceIconRes(kind: HomeSurfaceKind): Int = when (kind) {
    HomeSurfaceKind.HOME -> R.drawable.ic_surface_home
    HomeSurfaceKind.FEED -> R.drawable.ic_surface_feed
    HomeSurfaceKind.FRIENDS -> R.drawable.ic_surface_friends
    HomeSurfaceKind.INBOX,
    HomeSurfaceKind.MESSAGES -> R.drawable.ic_purpose_message
    HomeSurfaceKind.PROFILE,
    HomeSurfaceKind.YOU -> R.drawable.ic_surface_profile
    HomeSurfaceKind.CREATE -> R.drawable.ic_purpose_create
    HomeSurfaceKind.REELS,
    HomeSurfaceKind.SHORTS -> R.drawable.ic_purpose_shorts
    HomeSurfaceKind.EXPLORE -> R.drawable.ic_purpose_browse
    HomeSurfaceKind.VIDEO -> R.drawable.ic_surface_video
    HomeSurfaceKind.SEARCH -> R.drawable.ic_purpose_search
    HomeSurfaceKind.SUBSCRIPTIONS -> R.drawable.ic_purpose_subscriptions
    HomeSurfaceKind.OTHER,
    HomeSurfaceKind.UNCLASSIFIED -> R.drawable.ic_surface_other
}

@Composable
private fun UsageBar(
    fraction: Float,
    emphasized: Boolean,
    alpha: Float = 1f,
) {
    val clampedFraction = fraction.coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { invisibleToUser() },
    ) {
        Box(
            Modifier
                .fillMaxWidth(clampedFraction)
                .height(7.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(
                    if (emphasized) {
                        MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
                    },
                ),
        )
    }
}
