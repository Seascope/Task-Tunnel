package com.example.tasktunnel.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class PrimaryDestination(val label: String) {
    HOME("Home"),
    ATTENTION("Attention"),
    REVIEW("Review"),
    PROTECTION("Protection"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskTunnelScaffold(
    destination: PrimaryDestination,
    onNavigate: (PrimaryDestination) -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(destination.label, style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        TaskTunnelIcon(TaskTunnelIconKind.SETTINGS, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    PrimaryDestination.entries.forEach { item ->
                        val selected = item == destination
                        NavigationBarItem(
                            selected = selected,
                            onClick = { onNavigate(item) },
                            icon = {
                                TaskTunnelIcon(
                                    when (item) {
                                        PrimaryDestination.HOME -> TaskTunnelIconKind.HOME
                                        PrimaryDestination.ATTENTION -> TaskTunnelIconKind.ATTENTION
                                        PrimaryDestination.REVIEW -> TaskTunnelIconKind.REVIEW
                                        PrimaryDestination.PROTECTION -> TaskTunnelIconKind.PROTECTION
                                    },
                                    Modifier.size(23.dp),
                                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        content(Modifier.padding(innerPadding).consumeWindowInsets(innerPadding))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecondaryScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        TaskTunnelIcon(TaskTunnelIconKind.BACK, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        content(Modifier.padding(innerPadding).consumeWindowInsets(innerPadding))
    }
}
