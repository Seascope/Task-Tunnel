package com.example.tasktunnel.detector

import com.example.tasktunnel.accessibility.SanitizedNode
import com.example.tasktunnel.accessibility.UiChromeRole
import java.util.Locale

enum class YouTubeSurface {
    YOUTUBE_SHORTS,
    YOUTUBE_VIDEO,
    YOUTUBE_SEARCH,
    YOUTUBE_HOME,
    YOUTUBE_SUBSCRIPTIONS,
    YOUTUBE_YOU,
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
 * inspector captures. Raw text and content descriptions are never retained. The sanitizer may
 * derive one fixed navigation-role enum (Home/Shorts/Subscriptions/You) from exact YouTube chrome
 * labels so top-level feeds can be distinguished without storing arbitrary accessibility content.
 * Conflicting specialized evidence fails open to UNKNOWN.
 */
object YouTubeSurfaceDetector {
    const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    private const val MAX_SIGNALS = 5

    private val shortsIds = setOf(
        "reel_recycler", "reel_player_page_container",
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
        val selectedChromeRoles = nodes.asSequence()
            .filter { it.visibleToUser && it.selected }
            .mapNotNull { it.chromeRole }
            .toSet()
        val hasPagerStructure = nodes.any {
            it.scrollable && it.childCount > 0 &&
                (it.className?.endsWith("ViewPager2") == true || it.className?.endsWith("RecyclerView") == true)
        }
        val hasEditableStructure = nodes.any { it.editable && it.visibleToUser }
        val hasSeekBarStructure = nodes.any { it.className?.endsWith("SeekBar") == true && it.visibleToUser }

        // This pair is the only verified Shorts signature. Generic playback and progress IDs are
        // deliberately not evidence because they also occur outside Shorts.
        val shortsStrong = shortsMatches.size == shortsIds.size
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
            0.95,
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
        if (selectedChromeRoles.size > 1) {
            return unknown(selectedChromeRoles.map { "nav:${it.name.lowercase(Locale.ROOT)}" })
        }
        selectedChromeRoles.singleOrNull()?.let { role ->
            val surface = when (role) {
                UiChromeRole.YOUTUBE_HOME -> YouTubeSurface.YOUTUBE_HOME
                UiChromeRole.YOUTUBE_SHORTS -> YouTubeSurface.YOUTUBE_SHORTS
                UiChromeRole.YOUTUBE_SUBSCRIPTIONS -> YouTubeSurface.YOUTUBE_SUBSCRIPTIONS
                UiChromeRole.YOUTUBE_YOU -> YouTubeSurface.YOUTUBE_YOU
            }
            return result(surface, 0.85, listOf("nav:${role.name.lowercase(Locale.ROOT)}"))
        }
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
