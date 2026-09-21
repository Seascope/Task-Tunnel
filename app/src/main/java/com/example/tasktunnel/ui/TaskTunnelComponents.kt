package com.example.tasktunnel.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import com.example.tasktunnel.attention.AttentionEpisodeType
import com.example.tasktunnel.attention.AttentionEventType
import com.example.tasktunnel.attention.AttentionSubtype
import com.example.tasktunnel.attention.episodeSubtitle
import com.example.tasktunnel.attention.episodeTitle
import com.example.tasktunnel.attention.surfaceLabel
import com.example.tasktunnel.protection.ProtectionHealth
import com.example.tasktunnel.protection.ProtectionLevel
import com.example.tasktunnel.ui.theme.HealthyGreen
import com.example.tasktunnel.ui.theme.InstagramFallback
import com.example.tasktunnel.ui.theme.LimitedAmber
import com.example.tasktunnel.ui.theme.RedditFallback
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
        style = MaterialTheme.typography.titleSmall,
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
    showChevron: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier.fillMaxWidth().then(clickModifier).defaultMinSize(minHeight = TaskTunnelTokens.MinimumTouchTarget)
            .padding(vertical = TaskTunnelTokens.RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.width(42.dp), contentAlignment = Alignment.CenterStart) { leading() }
            Spacer(Modifier.width(TaskTunnelTokens.IconTextGap - 6.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TaskTunnelTokens.SecondaryTextGap)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        if (trailingText != null) {
            Spacer(Modifier.width(12.dp))
            Text(trailingText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showChevron) {
            Spacer(Modifier.width(8.dp))
            TaskTunnelIcon(TaskTunnelIconKind.CHEVRON, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
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
fun EpisodeRow(episode: AttentionEpisode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val app = episode.app
    val packageName = app?.packageName.orEmpty()
    val duration = formatDuration(episode)
    Column(modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = TaskTunnelTokens.RowVerticalPadding)) {
        Row(verticalAlignment = Alignment.Top) {
            if (app != null) AppIcon(packageName, app.displayName, Modifier.size(TaskTunnelTokens.AppIconSize))
            else Box(Modifier.size(TaskTunnelTokens.AppIconSize).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
            Spacer(Modifier.width(TaskTunnelTokens.IconTextGap))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TaskTunnelTokens.SecondaryTextGap)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(episodeTitle(episode), style = MaterialTheme.typography.titleMedium)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatEpisodeRange(episode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(duration, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(episodeSubtitle(episode), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                episodeOutcome(episode)?.let { outcome ->
                    Text(outcome, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun DriftEpisodeRow(episode: AttentionEpisode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = TaskTunnelTokens.RowVerticalPadding)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                TaskTunnelIcon(TaskTunnelIconKind.ATTENTION, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(TaskTunnelTokens.IconTextGap))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TaskTunnelTokens.SecondaryTextGap)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Drift episode", style = MaterialTheme.typography.titleMedium)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatEpisodeRange(episode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDuration(episode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(episodeSubtitle(episode), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                episodeOutcome(episode)?.let { outcome ->
                    Text(outcome, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

private fun episodeOutcome(episode: AttentionEpisode): String? {
    val intervention = episode.events.lastOrNull { it.type == AttentionEventType.INTERVENTION }
    val decision = episode.events.lastOrNull { it.type == AttentionEventType.DECISION }
    if (intervention == null && decision == null) return null
    val interruptedSurface = intervention?.surface?.let(::surfaceLabel)
    return when (decision?.subtype) {
        AttentionSubtype.RETURN -> {
            val returned = episode.events.lastOrNull { it.subtype == AttentionSubtype.SURFACE_RETURNED }?.surface?.let(::surfaceLabel)
            listOfNotNull(interruptedSurface, returned?.let { "Returned to $it" }).joinToString(" → ")
        }
        AttentionSubtype.ALLOW_ANYWAY -> listOfNotNull(interruptedSurface, "Allowed for 5 min").joinToString(" · ")
        AttentionSubtype.END_TUNNEL,
        AttentionSubtype.EXPIRY_FINISH,
        -> "Session ended after intended use"
        AttentionSubtype.EXPIRY_CONTINUE -> "Continued for another session"
        AttentionSubtype.EXPIRY_CHOOSE_ANOTHER -> "Chose another purpose"
        AttentionSubtype.KEEP_GOING -> "Kept going after the check-in"
        AttentionSubtype.SET_INTENTION -> "Set an intention after the check-in"
        else -> when (intervention?.subtype) {
            AttentionSubtype.DRIFT_CHECK_IN -> "A gentle check-in was shown"
            AttentionSubtype.SURFACE_INTERVENTION -> interruptedSurface?.let { "$it needed a decision" }
            else -> null
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

val AttentionApp.packageName: String
    get() = when (this) {
        AttentionApp.INSTAGRAM -> "com.instagram.android"
        AttentionApp.YOUTUBE -> "com.google.android.youtube"
        AttentionApp.REDDIT -> "com.reddit.frontpage"
    }

fun formatTime(timeMillis: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timeMillis))

fun formatEpisodeRange(episode: AttentionEpisode): String {
    val start = formatTime(episode.startedAtMillis)
    val end = formatTime(episode.endedAtMillis)
    return if (start == end) start else "$start – $end"
}

private fun formatDuration(episode: AttentionEpisode): String {
    val minutes = max(1L, (episode.endedAtMillis - episode.startedAtMillis + 59_999L) / 60_000L)
    return "$minutes min"
}
