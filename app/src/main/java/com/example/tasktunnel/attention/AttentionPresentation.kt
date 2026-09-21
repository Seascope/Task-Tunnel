package com.example.tasktunnel.attention

import com.example.tasktunnel.tunnel.DetectedSurface
import com.example.tasktunnel.tunnel.TunnelTask

fun taskLabel(task: TunnelTask?): String = when (task) {
    TunnelTask.INSTAGRAM_MESSAGES -> "Reply to messages"
    TunnelTask.INSTAGRAM_BROWSE -> "Browse intentionally"
    TunnelTask.YOUTUBE_SEARCH_WATCH -> "Search / watch something specific"
    TunnelTask.YOUTUBE_BROWSE -> "Browse intentionally"
    null -> "Intentional use"
}

fun surfaceLabel(surface: DetectedSurface?): String = when (surface) {
    DetectedSurface.INSTAGRAM_MESSAGES -> "Messages"
    DetectedSurface.INSTAGRAM_EXPLORE -> "Explore"
    DetectedSurface.INSTAGRAM_REELS -> "Reels"
    DetectedSurface.INSTAGRAM_HOME -> "Home"
    DetectedSurface.YOUTUBE_SEARCH -> "Search"
    DetectedSurface.YOUTUBE_VIDEO -> "Video"
    DetectedSurface.YOUTUBE_SHORTS -> "Shorts"
    else -> "another screen"
}

fun eventDescription(event: AttentionEvent): String = when (event.subtype) {
    AttentionSubtype.PURPOSE_SELECTED -> when (event.task) {
        TunnelTask.INSTAGRAM_MESSAGES -> "Opened Instagram to reply to messages"
        TunnelTask.INSTAGRAM_BROWSE -> "Opened Instagram to browse intentionally"
        TunnelTask.YOUTUBE_SEARCH_WATCH -> "Opened YouTube to search for or watch something specific"
        TunnelTask.YOUTUBE_BROWSE -> "Opened YouTube to browse intentionally"
        null -> "Set an intention"
    }
    AttentionSubtype.SURFACE_ENTERED -> "Entered ${surfaceLabel(event.surface)}"
    AttentionSubtype.SURFACE_RETURNED -> "Returned to ${surfaceLabel(event.surface)}"
    AttentionSubtype.SURFACE_INTERVENTION ->
        "${surfaceLabel(event.surface)} was outside the current Task Tunnel"
    AttentionSubtype.SESSION_EXPIRED -> "The selected session time ended"
    AttentionSubtype.DRIFT_SEQUENCE -> event.relatedApps.joinToString(" → ") { it.displayName }
    AttentionSubtype.DRIFT_CHECK_IN -> "Task Tunnel offered a gentle Drift check-in"
    AttentionSubtype.RETURN -> "Chose Return"
    AttentionSubtype.ALLOW_ANYWAY -> "Chose Allow anyway"
    AttentionSubtype.END_TUNNEL -> "Ended the Task Tunnel"
    AttentionSubtype.KEEP_GOING -> "Chose Keep going"
    AttentionSubtype.SET_INTENTION -> "Chose Set an intention"
    AttentionSubtype.EXPIRY_FINISH -> "Chose Finish"
    AttentionSubtype.EXPIRY_CONTINUE -> "Chose Continue"
    AttentionSubtype.EXPIRY_CHOOSE_ANOTHER -> "Chose another purpose"
    AttentionSubtype.CHECK_IN_SHOWN -> "Intentional check-in"
    AttentionSubtype.CHECK_IN_RETURN -> "Returned to your intention"
    AttentionSubtype.CHECK_IN_CONTINUE -> "Chose to continue"
    AttentionSubtype.CHECK_IN_END -> "Ended the Task Tunnel"
    AttentionSubtype.CHECK_IN_CHOOSE_ANOTHER -> "Chose another purpose"
}

fun episodeTitle(episode: AttentionEpisode): String = when (episode.type) {
    AttentionEpisodeType.TASK_TUNNEL -> episode.app?.displayName ?: "Task Tunnel"
    AttentionEpisodeType.DRIFT -> "Drift episode"
}

fun episodeSubtitle(episode: AttentionEpisode): String = when (episode.type) {
    AttentionEpisodeType.TASK_TUNNEL -> taskLabel(episode.task)
    AttentionEpisodeType.DRIFT -> episode.involvedApps.joinToString(" → ") { it.displayName }
        .ifBlank { "App switching check-in" }
}
