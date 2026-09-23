package com.example.tasktunnel.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.tasktunnel.attention.AttentionApp
import com.example.tasktunnel.attention.AttentionEpisode
import com.example.tasktunnel.attention.episodeHighlight
import com.example.tasktunnel.attention.taskLabel
import com.example.tasktunnel.drift.DriftAppCatalog
import com.example.tasktunnel.protection.ProtectionHealth
import com.example.tasktunnel.protection.ProtectionLevel
import com.example.tasktunnel.ui.theme.HealthyGreen
import com.example.tasktunnel.ui.theme.InstagramFallback
import com.example.tasktunnel.ui.theme.LimitedAmber
import com.example.tasktunnel.ui.theme.RedditFallback
import com.example.tasktunnel.ui.theme.TikTokFallback
import com.example.tasktunnel.ui.theme.TaskTunnelTokens
import com.example.tasktunnel.ui.theme.YouTubeFallback
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier.semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun ProtectionStatusLine(health: ProtectionHealth, modifier: Modifier = Modifier, showSummary: Boolean = false) {
    val color = when (health.level) {
        ProtectionLevel.ACTIVE -> HealthyGreen
        ProtectionLevel.LIMITED -> LimitedAmber
        ProtectionLevel.OFF -> MaterialTheme.colorScheme.error
    }
    Row(modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.padding(top = 6.dp).size(8.dp)) { drawCircle(color) }
        Column(verticalArrangement = Arrangement.spacedBy(TaskTunnelTokens.SecondaryTextGap)) {
            Text(health.title, style = MaterialTheme.typography.titleMedium)
            if (showSummary) Text(health.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AppIdentity(
    packageName: String,
    displayName: String,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = TaskTunnelTokens.AppIconSize,
    showName: Boolean = true,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TaskTunnelTokens.IconTextGap)) {
        AppIcon(packageName, displayName, Modifier.size(iconSize))
        if (showName) Text(displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AppIcon(packageName: String, displayName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap(96, 96)
        }.getOrNull()?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier.clip(RoundedCornerShape(9.dp)))
    } else {
        val fallback = when (packageName) {
            "com.instagram.android" -> InstagramFallback
            "com.google.android.youtube" -> YouTubeFallback
            "com.zhiliaoapp.musically" -> TikTokFallback
            "com.reddit.frontpage" -> RedditFallback
            else -> MaterialTheme.colorScheme.primary
        }
        Box(modifier.clip(RoundedCornerShape(9.dp)).background(fallback), contentAlignment = Alignment.Center) {
            Text(displayName.take(1), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailingText: String? = null,
    subtitleMaxLines: Int = 2,
    showChevron: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier
            .fillMaxWidth()
            .then(clickModifier)
            .alpha(if (enabled) 1f else 0.5f)
            .defaultMinSize(minHeight = TaskTunnelTokens.MinimumTouchTarget)
            .padding(vertical = TaskTunnelTokens.RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.width(42.dp), contentAlignment = Alignment.CenterStart) { leading() }
            Spacer(Modifier.width(TaskTunnelTokens.IconTextGap - 6.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TaskTunnelTokens.SecondaryTextGap)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingText != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                trailingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showChevron) {
            Spacer(Modifier.width(8.dp))
            TaskTunnelIcon(TaskTunnelIconKind.CHEVRON, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RowDivider(modifier: Modifier = Modifier, inset: Boolean = false) {
    HorizontalDivider(
        modifier.padding(start = if (inset) 55.dp else 0.dp),
        thickness = TaskTunnelTokens.DividerThickness,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
fun EpisodeRow(
    episode: AttentionEpisode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLive: Boolean = false,
) {
    val app = episode.app
    val purpose = taskLabel(episode.task)
    Column(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (app != null) {
                AppIcon(app.packageName, app.displayName, Modifier.size(TaskTunnelTokens.AppIconSize))
            } else {
                Box(
                    Modifier
                        .size(TaskTunnelTokens.AppIconSize)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Spacer(Modifier.width(TaskTunnelTokens.IconTextGap))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app?.displayName ?: "Task Tunnel",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        formatTime(episode.startedAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    if (isLive) "$purpose · Live" else "$purpose · ${formatEpisodeDuration(episode)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                episodeHighlight(episode)?.let { highlight ->
                    Text(
                        highlight,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun DriftEpisodeRow(episode: AttentionEpisode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appPath = episode.involvedPackages
        .joinToString(" → ") { packageName -> DriftAppCatalog.labelFor(context, packageName) }
        .ifBlank { episode.involvedApps.joinToString(" → ") { it.displayName } }
    Column(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            DriftEpisodeIconStack(episode.involvedPackages, Modifier.size(40.dp))
            Spacer(Modifier.width(TaskTunnelTokens.IconTextGap))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Drift check-in",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        formatTime(episode.startedAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    appPath,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                episodeHighlight(episode)?.let { highlight ->
                    Text(
                        highlight,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DriftEpisodeIconStack(packages: List<String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val displayedPackages = packages.distinct().take(3)
    if (displayedPackages.isEmpty()) {
        Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            TaskTunnelIcon(TaskTunnelIconKind.ATTENTION, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
        }
        return
    }

    val iconSize = 18.dp
    val iconShape = RoundedCornerShape(7.dp)
    val surfaceBorder = MaterialTheme.colorScheme.surface
    val offsets = when (displayedPackages.size) {
        1 -> listOf(10.dp to 10.dp)
        2 -> listOf(4.dp to 10.dp, 18.dp to 10.dp)
        else -> listOf(1.dp to 13.dp, 10.dp to 2.dp, 19.dp to 13.dp)
    }

    Box(modifier) {
        displayedPackages.forEachIndexed { index, packageName ->
            val (x, y) = offsets[index]
            AppIcon(
                packageName = packageName,
                displayName = DriftAppCatalog.labelFor(context, packageName),
                modifier = Modifier
                    .size(iconSize)
                    .offset(x = x, y = y)
                    .border(1.dp, surfaceBorder, iconShape),
            )
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.Top) {
        TaskTunnelIcon(TaskTunnelIconKind.ATTENTION, Modifier.padding(top = 2.dp).size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ErrorNotice(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun formatTime(timeMillis: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timeMillis))

fun formatEpisodeRange(episode: AttentionEpisode): String {
    val start = formatTime(episode.startedAtMillis)
    val end = formatTime(episode.endedAtMillis)
    return if (start == end) start else "$start – $end"
}

fun formatEpisodeDuration(episode: AttentionEpisode): String {
    val minutes = max(1L, (episode.endedAtMillis - episode.startedAtMillis + 59_999L) / 60_000L)
    return "$minutes min"
}
