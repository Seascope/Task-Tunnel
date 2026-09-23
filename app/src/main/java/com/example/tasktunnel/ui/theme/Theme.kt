package com.example.tasktunnel.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TaskTunnelColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color(0xFF061A32),
    primaryContainer = BrandBlueContainer,
    onPrimaryContainer = Color(0xFFD9E8FF),
    secondary = TextSecondary,
    onSecondary = Canvas,
    tertiary = HealthyGreen,
    error = ErrorRed,
    background = Canvas,
    onBackground = TextPrimary,
    surface = Canvas,
    onSurface = TextPrimary,
    surfaceDim = Canvas,
    surfaceBright = SurfaceHigh,
    surfaceContainerLowest = Canvas,
    surfaceContainerLow = SurfaceGraphite,
    surfaceContainer = SurfaceGraphite,
    surfaceContainerHigh = SurfaceRaised,
    surfaceContainerHighest = SurfaceHigh,
    surfaceVariant = SurfaceGraphite,
    onSurfaceVariant = TextSecondary,
    outline = Divider,
    outlineVariant = Divider,
    scrim = Color.Black,
)

private val TaskTunnelShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun TaskTunnelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TaskTunnelColors,
        typography = TaskTunnelTypography,
        shapes = TaskTunnelShapes,
        content = content,
    )
}
