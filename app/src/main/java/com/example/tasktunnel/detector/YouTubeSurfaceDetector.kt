package com.example.tasktunnel.detector

import com.example.tasktunnel.accessibility.SanitizedNode
import java.util.Locale

enum class YouTubeSurface {
    YOUTUBE_SHORTS,
    YOUTUBE_VIDEO,
    YOUTUBE_SEARCH,
    YOUTUBE_OTHER,
    UNKNOWN,
}

data class YouTubeDetection(
    val surface: YouTubeSurface,
    /** Confidence is detector evidence strength, from 0.0 to 1.0; it is not a probability. */
    val confidence: Double,
    val strongestSignals: List<String>,
)

/**
 * Deterministic classifier over bounded, content-free accessibility fingerprints.
 *
 * Candidate IDs are deliberately explicit and must be verified/tuned using physical-device
 * inspector captures. Text and content descriptions are never detector inputs. Conflicting
 * specialized evidence fails open to UNKNOWN.
 */
object YouTubeSurfaceDetector {
    const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    private const val MAX_SIGNALS = 5

    private val shortsIds = setOf(
        "reel_watch_player", "shorts_player", "reel_player_page", "shorts_video_cell",
    )
    private val videoIds = setOf(
        "watch_player", "player_view", "player_fragment_container", "video_player",
    )
    private val searchIds = setOf(
        "search_edit_text", "search_query", "search_results", "search_results_list",
    )
    private val shellIds = setOf(
        "bottom_bar", "pivot_bar", "tabs", "app_bar", "toolbar",
    )

    fun detect(packageName: String, nodes: List<SanitizedNode>): YouTubeDetection {
        if (packageName != YOUTUBE_PACKAGE) return unknown()

        val ids = nodes.mapNotNull { it.resourceId?.normalizedId() }.toSet()
        val shortsMatches = shortsIds.intersect(ids).sorted()
        val videoMatches = videoIds.intersect(ids).sorted()
        val searchMatches = searchIds.intersect(ids).sorted()
        val shellMatches = shellIds.intersect(ids).sorted()
        val hasPagerStructure = nodes.any {
            it.scrollable && it.childCount > 0 &&
                (it.className?.endsWith("ViewPager2") == true || it.className?.endsWith("RecyclerView") == true)
        }
        val hasEditableStructure = nodes.any { it.editable && it.visibleToUser }
        val hasSeekBarStructure = nodes.any { it.className?.endsWith("SeekBar") == true && it.visibleToUser }

        // The sanitized snapshot has no parent identity/selection/orientation, so a generic pager
        // cannot independently corroborate an ID. Require two Shorts-specific IDs for now.
        val shortsStrong = shortsMatches.size >= 2
        val videoStrong = videoMatches.size >= 2 || (videoMatches.isNotEmpty() && hasSeekBarStructure)
        val searchStrong = searchMatches.size >= 2 || (searchMatches.isNotEmpty() && hasEditableStructure)
        val specializedCount = listOf(shortsStrong, videoStrong, searchStrong).count { it }

        if (specializedCount > 1) {
            return result(
                YouTubeSurface.UNKNOWN,
                0.0,
                signalList(shortsMatches, videoMatches, searchMatches, hasPagerStructure, hasEditableStructure, hasSeekBarStructure),
            )
        }
        if (shortsStrong) return result(
            YouTubeSurface.YOUTUBE_SHORTS,
            if (shortsMatches.size >= 2) 0.95 else 0.85,
            signalList(shortsMatches, emptyList(), emptyList(), hasPagerStructure, false, false),
        )
        if (videoStrong) return result(
            YouTubeSurface.YOUTUBE_VIDEO,
            if (videoMatches.size >= 2) 0.9 else 0.8,
            signalList(emptyList(), videoMatches, emptyList(), false, false, hasSeekBarStructure),
        )
        if (searchStrong) return result(
            YouTubeSurface.YOUTUBE_SEARCH,
            if (searchMatches.size >= 2) 0.9 else 0.8,
            signalList(emptyList(), emptyList(), searchMatches, false, hasEditableStructure, false),
        )
        if (shellMatches.isNotEmpty() && shortsMatches.isEmpty() && videoMatches.isEmpty() && searchMatches.isEmpty()) {
            return result(YouTubeSurface.YOUTUBE_OTHER, 0.6, shellMatches.map { "id:$it" })
        }
        return unknown(signalList(shortsMatches, videoMatches, searchMatches, hasPagerStructure, hasEditableStructure, hasSeekBarStructure))
    }

    private fun String.normalizedId(): String = substringAfterLast('/').lowercase(Locale.ROOT)

    private fun signalList(
        shorts: List<String>,
        video: List<String>,
        search: List<String>,
        pager: Boolean,
        editable: Boolean,
        seekBar: Boolean,
    ): List<String> = buildList {
        shorts.forEach { add("shorts-id:$it") }
        video.forEach { add("video-id:$it") }
        search.forEach { add("search-id:$it") }
        if (pager) add("structure:scrollable-pager")
        if (editable) add("structure:visible-editable")
        if (seekBar) add("structure:visible-seekbar")
    }.take(MAX_SIGNALS)

    private fun result(surface: YouTubeSurface, confidence: Double, signals: List<String>) =
        YouTubeDetection(surface, confidence, signals.take(MAX_SIGNALS))

    private fun unknown(signals: List<String> = emptyList()) =
        YouTubeDetection(YouTubeSurface.UNKNOWN, 0.0, signals.take(MAX_SIGNALS))
}
