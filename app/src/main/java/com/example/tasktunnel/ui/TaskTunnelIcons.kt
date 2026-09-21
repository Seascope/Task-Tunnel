package com.example.tasktunnel.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

enum class TaskTunnelIconKind {
    HOME,
    ATTENTION,
    REVIEW,
    PROTECTION,
    SETTINGS,
    BACK,
    CHEVRON,
    DELETE,
    COPY,
    INFO,
    MESSAGE,
    BROWSE,
    TIMER,
}

@Composable
fun TaskTunnelIcon(
    kind: TaskTunnelIconKind,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val imageVector = when (kind) {
        TaskTunnelIconKind.HOME -> Icons.Outlined.Home
        TaskTunnelIconKind.ATTENTION -> Icons.Outlined.AccessTime
        TaskTunnelIconKind.REVIEW -> Icons.Outlined.Assessment
        TaskTunnelIconKind.PROTECTION -> Icons.Outlined.Shield
        TaskTunnelIconKind.SETTINGS -> Icons.Outlined.Settings
        TaskTunnelIconKind.BACK -> Icons.Outlined.ArrowBack
        TaskTunnelIconKind.CHEVRON -> Icons.Outlined.ChevronRight
        TaskTunnelIconKind.DELETE -> Icons.Outlined.DeleteOutline
        TaskTunnelIconKind.COPY -> Icons.Outlined.ContentCopy
        TaskTunnelIconKind.INFO -> Icons.Outlined.Info
        TaskTunnelIconKind.MESSAGE -> Icons.Outlined.ChatBubbleOutline
        TaskTunnelIconKind.BROWSE -> Icons.Outlined.Explore
        TaskTunnelIconKind.TIMER -> Icons.Outlined.Timer
    }
    Icon(imageVector = imageVector, contentDescription = null, modifier = modifier, tint = tint)
}
