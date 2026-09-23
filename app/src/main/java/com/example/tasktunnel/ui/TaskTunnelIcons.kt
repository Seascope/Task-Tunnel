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
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

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
    DRIFT,
}

@Composable
fun TaskTunnelIcon(
    kind: TaskTunnelIconKind,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    if (kind == TaskTunnelIconKind.DRIFT) {
        DriftIcon(modifier = modifier, tint = tint)
        return
    }
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
        TaskTunnelIconKind.DRIFT -> Icons.Outlined.AccessTime
    }
    Icon(imageVector = imageVector, contentDescription = null, modifier = modifier, tint = tint)
}

@Composable
private fun DriftIcon(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    val resolvedTint = if (tint == Color.Unspecified) LocalContentColor.current else tint
    Canvas(modifier = modifier) {
        val unit = size.minDimension
        val trackStroke = unit * 0.065f
        val bodyStroke = unit * 0.075f
        val trackColor = resolvedTint.copy(alpha = 0.58f)

        val leftTrack = Path().apply {
            moveTo(size.width * 0.13f, size.height * 0.84f)
            cubicTo(
                size.width * 0.31f, size.height * 0.69f,
                size.width * 0.42f, size.height * 0.74f,
                size.width * 0.54f, size.height * 0.62f,
            )
        }
        val rightTrack = Path().apply {
            moveTo(size.width * 0.27f, size.height * 0.93f)
            cubicTo(
                size.width * 0.43f, size.height * 0.81f,
                size.width * 0.50f, size.height * 0.82f,
                size.width * 0.60f, size.height * 0.68f,
            )
        }
        drawPath(leftTrack, color = trackColor, style = Stroke(width = trackStroke, cap = StrokeCap.Round))
        drawPath(rightTrack, color = trackColor, style = Stroke(width = trackStroke, cap = StrokeCap.Round))

        val carCenter = Offset(size.width * 0.68f, size.height * 0.40f)
        rotate(degrees = 34f, pivot = carCenter) {
            val body = Path().apply {
                moveTo(size.width * 0.62f, size.height * 0.18f)
                lineTo(size.width * 0.74f, size.height * 0.18f)
                lineTo(size.width * 0.79f, size.height * 0.28f)
                lineTo(size.width * 0.81f, size.height * 0.70f)
                lineTo(size.width * 0.75f, size.height * 0.82f)
                lineTo(size.width * 0.61f, size.height * 0.82f)
                lineTo(size.width * 0.56f, size.height * 0.70f)
                lineTo(size.width * 0.57f, size.height * 0.29f)
                close()
            }
            drawPath(
                path = body,
                color = resolvedTint,
                style = Stroke(width = bodyStroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawLine(
                color = resolvedTint,
                start = Offset(size.width * 0.60f, size.height * 0.35f),
                end = Offset(size.width * 0.77f, size.height * 0.35f),
                strokeWidth = bodyStroke * 0.72f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = resolvedTint,
                start = Offset(size.width * 0.60f, size.height * 0.62f),
                end = Offset(size.width * 0.77f, size.height * 0.62f),
                strokeWidth = bodyStroke * 0.72f,
                cap = StrokeCap.Round,
            )
            listOf(
                Triple(0.54f, 0.31f, 0.43f),
                Triple(0.54f, 0.60f, 0.72f),
                Triple(0.83f, 0.31f, 0.43f),
                Triple(0.83f, 0.60f, 0.72f),
            ).forEach { (x, yStart, yEnd) ->
                drawLine(
                    color = resolvedTint,
                    start = Offset(size.width * x, size.height * yStart),
                    end = Offset(size.width * x, size.height * yEnd),
                    strokeWidth = bodyStroke * 0.82f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

