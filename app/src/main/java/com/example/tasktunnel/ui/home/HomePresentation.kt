package com.example.tasktunnel.ui.home

import com.example.tasktunnel.attention.AttentionApp
import com.example.tasktunnel.tunnel.TunnelTask

/** Presentation-only data for the production Today home surface. */
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Loaded(
        val presentation: HomePresentation,
    ) : HomeUiState

    data object Unavailable : HomeUiState
}

data class HomePresentation(
    val totalTrackedTodayMillis: Long,
    val apps: List<HomeAppUsage>,
    val trackingAvailabilityNote: String? = null,
    /** Reserved for a future task-context summary; usage summaries do not provide it yet. */
    val taskContextAggregate: HomeTaskContextAggregate? = null,
)

data class HomeAppUsage(
    val app: AttentionApp,
    val totalTrackedDurationMillis: Long,
    val surfaceRows: List<HomeSurfaceUsage>,
    val coverage: Double,
    val shouldDeemphasizeComposition: Boolean,
    val showMeaningfulUnclassifiedNote: Boolean,
)

data class HomeSurfaceUsage(
    val kind: HomeSurfaceKind,
    val durationMillis: Long,
)

enum class HomeSurfaceKind(val displayLabel: String) {
    REELS("Reels"),
    MESSAGES("Messages"),
    EXPLORE("Explore"),
    VIDEO("Video"),
    SHORTS("Shorts"),
    SEARCH("Search"),
    OTHER("Other"),
    UNCLASSIFIED("Unclassified"),
}

/** Typed placeholder so a later summary API can add task context without changing the screen contract. */
data class HomeTaskContextAggregate(
    val entries: List<HomeTaskContextUsage>,
)

data class HomeTaskContextUsage(
    val app: AttentionApp,
    val task: TunnelTask,
    val durationMillis: Long,
)

fun formatHomeDuration(durationMillis: Long): String {
    val totalMinutes = (durationMillis.coerceAtLeast(0L) / MILLIS_PER_MINUTE).toInt()
    if (totalMinutes < 1) return "<1 min"
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours == 0) "$totalMinutes min" else if (minutes == 0) "${hours}h" else "${hours}h ${minutes}m"
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60
