package com.example.tasktunnel.attention

import com.example.tasktunnel.tunnel.DetectedSurface
import com.example.tasktunnel.tunnel.TunnelTask

fun taskLabel(task: TunnelTask?): String = when (task) {
    TunnelTask.INSTAGRAM_MESSAGES -> "Reply to messages"
    TunnelTask.INSTAGRAM_SEARCH -> "Search / look something up"
    TunnelTask.INSTAGRAM_POST -> "Post something"
    TunnelTask.INSTAGRAM_BROWSE -> "Browse intentionally"
    TunnelTask.YOUTUBE_SEARCH_WATCH -> "Search / watch something specific"
    TunnelTask.YOUTUBE_SUBSCRIPTIONS -> "Check subscriptions"
    TunnelTask.YOUTUBE_SHORTS -> "Watch Shorts intentionally"
    TunnelTask.YOUTUBE_BROWSE -> "Browse intentionally"
    TunnelTask.TIKTOK_SEARCH_WATCH -> "Search / watch something specific"
    TunnelTask.TIKTOK_INBOX -> "Check Inbox"
    TunnelTask.TIKTOK_BROWSE -> "Browse intentionally"
    null -> "Intentional use"
}

fun surfaceLabel(surface: DetectedSurface?): String = when (surface) {
    DetectedSurface.INSTAGRAM_MESSAGES -> "Messages"
    DetectedSurface.INSTAGRAM_EXPLORE -> "Explore"
    DetectedSurface.INSTAGRAM_REELS -> "Reels"
    DetectedSurface.INSTAGRAM_HOME -> "Home"
    DetectedSurface.INSTAGRAM_PROFILE -> "Profile"
    DetectedSurface.INSTAGRAM_CREATE -> "Create"
    DetectedSurface.YOUTUBE_SEARCH -> "Search"
    DetectedSurface.YOUTUBE_VIDEO -> "Video"
    DetectedSurface.YOUTUBE_UNSUBSCRIBED_VIDEO -> "Video from a channel you don't subscribe to"
    DetectedSurface.YOUTUBE_SHORTS -> "Shorts"
    DetectedSurface.YOUTUBE_UNSUBSCRIBED_SHORTS -> "Short from a channel you don't subscribe to"
    DetectedSurface.YOUTUBE_HOME -> "Home"
    DetectedSurface.YOUTUBE_SUBSCRIPTIONS -> "Subscriptions"
    DetectedSurface.YOUTUBE_YOU -> "You"
    DetectedSurface.TIKTOK_FEED -> "For You"
    DetectedSurface.TIKTOK_FRIENDS -> "Friends"
    DetectedSurface.TIKTOK_SEARCH -> "Search"
    DetectedSurface.TIKTOK_INBOX -> "Inbox"
    DetectedSurface.TIKTOK_PROFILE -> "Profile"
    DetectedSurface.TIKTOK_OTHER -> "This screen"
    else -> "another screen"
}

fun eventDescription(event: AttentionEvent): String = when (event.subtype) {
    AttentionSubtype.PURPOSE_SELECTED -> when (event.task) {
        TunnelTask.INSTAGRAM_MESSAGES -> "Opened Instagram to reply to messages"
        TunnelTask.INSTAGRAM_SEARCH -> "Opened Instagram to look something up"
        TunnelTask.INSTAGRAM_POST -> "Opened Instagram to post something"
        TunnelTask.INSTAGRAM_BROWSE -> "Opened Instagram to browse intentionally"
        TunnelTask.YOUTUBE_SEARCH_WATCH -> "Opened YouTube to search for or watch something specific"
        TunnelTask.YOUTUBE_SUBSCRIPTIONS -> "Opened YouTube to check subscriptions"
        TunnelTask.YOUTUBE_SHORTS -> "Opened YouTube to watch Shorts intentionally"
        TunnelTask.YOUTUBE_BROWSE -> "Opened YouTube to browse intentionally"
        TunnelTask.TIKTOK_SEARCH_WATCH -> "Opened TikTok to search for or watch something specific"
        TunnelTask.TIKTOK_INBOX -> "Opened TikTok to check the Inbox"
        TunnelTask.TIKTOK_BROWSE -> "Opened TikTok to browse intentionally"
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

/** A single human-readable moment for the episode detail screen. */
data class AttentionStoryItem(
    val timestampMillis: Long,
    val text: String,
)

/**
 * Returns only the exceptional outcome worth surfacing in the recent-history list.
 * Normal intentional use deliberately returns null so quiet sessions stay visually quiet.
 */
fun episodeHighlight(episode: AttentionEpisode): String? {
    if (episode.type == AttentionEpisodeType.DRIFT) {
        val driftDecision = episode.events.lastOrNull {
            it.subtype == AttentionSubtype.KEEP_GOING || it.subtype == AttentionSubtype.SET_INTENTION
        }
        return when (driftDecision?.subtype) {
            AttentionSubtype.KEEP_GOING -> "Kept going after the check-in"
            AttentionSubtype.SET_INTENTION -> "Set an intention after the check-in"
            else -> null
        }
    }

    val detourDecision = episode.events.lastOrNull {
        it.subtype == AttentionSubtype.RETURN ||
            it.subtype == AttentionSubtype.ALLOW_ANYWAY ||
            it.subtype == AttentionSubtype.END_TUNNEL
    }
    if (detourDecision != null) {
        val interruptedSurface = detourDecision.surface?.let(::surfaceLabel)
            ?: episode.events.lastOrNull {
                it.timestampMillis <= detourDecision.timestampMillis &&
                    it.subtype == AttentionSubtype.SURFACE_INTERVENTION
            }?.surface?.let(::surfaceLabel)
        return when (detourDecision.subtype) {
            AttentionSubtype.RETURN -> interruptedSurface?.let { "Returned from $it" } ?: "Returned to your intention"
            AttentionSubtype.ALLOW_ANYWAY -> interruptedSurface?.let { "Continued on $it" } ?: "Continued after a detour"
            AttentionSubtype.END_TUNNEL -> interruptedSurface?.let { "Ended after $it" } ?: "Ended the Task Tunnel"
            else -> null
        }
    }

    val checkInDecision = episode.events.lastOrNull {
        it.subtype in setOf(
            AttentionSubtype.CHECK_IN_RETURN,
            AttentionSubtype.CHECK_IN_CONTINUE,
            AttentionSubtype.CHECK_IN_END,
            AttentionSubtype.CHECK_IN_CHOOSE_ANOTHER,
        )
    }
    if (checkInDecision != null) {
        return when (checkInDecision.subtype) {
            AttentionSubtype.CHECK_IN_RETURN -> "Returned at a check-in"
            AttentionSubtype.CHECK_IN_CONTINUE -> "Continued at a check-in"
            AttentionSubtype.CHECK_IN_END -> "Ended at a check-in"
            AttentionSubtype.CHECK_IN_CHOOSE_ANOTHER -> "Changed purpose at a check-in"
            else -> null
        }
    }

    val expiryDecision = episode.events.lastOrNull {
        it.subtype in setOf(
            AttentionSubtype.EXPIRY_FINISH,
            AttentionSubtype.EXPIRY_CONTINUE,
            AttentionSubtype.EXPIRY_CHOOSE_ANOTHER,
        )
    }
    if (expiryDecision != null) {
        return when (expiryDecision.subtype) {
            AttentionSubtype.EXPIRY_FINISH -> "Finished at the time limit"
            AttentionSubtype.EXPIRY_CONTINUE -> "Continued after the time limit"
            AttentionSubtype.EXPIRY_CHOOSE_ANOTHER -> "Changed purpose at the time limit"
            else -> null
        }
    }

    return episode.events.lastOrNull { it.subtype == AttentionSubtype.SURFACE_INTERVENTION }
        ?.surface
        ?.let(::surfaceLabel)
        ?.let { "$it needs a decision" }
}

/**
 * Turns the raw event stream into a short story. Routine surface transitions are omitted on
 * purpose; they remain stored for Review/analytics but do not belong in casual product UI.
 */
fun episodeStory(episode: AttentionEpisode): List<AttentionStoryItem> {
    val items = mutableListOf<AttentionStoryItem>()

    episode.events.forEach { event ->
        val text = when (event.subtype) {
            AttentionSubtype.PURPOSE_SELECTED -> "Started with ${taskLabel(event.task)}"
            AttentionSubtype.SURFACE_INTERVENTION -> "Opened ${surfaceLabel(event.surface)}"
            AttentionSubtype.SURFACE_RETURNED -> "Returned to ${surfaceLabel(event.surface)}"
            AttentionSubtype.SESSION_EXPIRED -> "Time limit ended"
            AttentionSubtype.DRIFT_SEQUENCE -> null
            AttentionSubtype.DRIFT_CHECK_IN -> "Task Tunnel checked in"
            AttentionSubtype.RETURN -> "You chose to go back"
            AttentionSubtype.ALLOW_ANYWAY -> "You chose to continue"
            AttentionSubtype.END_TUNNEL -> "You ended the Task Tunnel"
            AttentionSubtype.KEEP_GOING -> "You chose to keep going"
            AttentionSubtype.SET_INTENTION -> "You chose to set an intention"
            AttentionSubtype.EXPIRY_FINISH -> "You finished the session"
            AttentionSubtype.EXPIRY_CONTINUE -> "You continued for another session"
            AttentionSubtype.EXPIRY_CHOOSE_ANOTHER -> "You chose another purpose"
            AttentionSubtype.CHECK_IN_SHOWN -> "Task Tunnel checked in"
            AttentionSubtype.CHECK_IN_RETURN -> "You returned to your intention"
            AttentionSubtype.CHECK_IN_CONTINUE -> "You chose to continue"
            AttentionSubtype.CHECK_IN_END -> "You ended the Task Tunnel"
            AttentionSubtype.CHECK_IN_CHOOSE_ANOTHER -> "You chose another purpose"
            AttentionSubtype.SURFACE_ENTERED -> null
        }
        if (text != null && items.lastOrNull()?.text != text) {
            items += AttentionStoryItem(event.timestampMillis, text)
        }
    }

    return items
}
