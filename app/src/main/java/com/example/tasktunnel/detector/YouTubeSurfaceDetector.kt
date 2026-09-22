package com.example.tasktunnel.detector

import com.example.tasktunnel.accessibility.SanitizedNode
import com.example.tasktunnel.accessibility.UiChromeRole
import com.example.tasktunnel.accessibility.YouTubeSubscriptionState
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
    /** Fixed semantic state only; channel identity and raw accessibility labels are never retained. */
    val creatorSubscriptionState: YouTubeSubscriptionState? = null,
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
    private val fullPlayerControlIds = setOf(
        "player_collapse_button", "controls_layout", "player_control_play_pause_replay_button",
    )
    // Current YouTube builds can expose a watch page without the legacy `watch_player` node.
    // These IDs were captured from the modern watch layout on-device. We require evidence from
    // all three structural groups below so a minimized player over a feed is not mistaken for
    // the full watch surface.
    private const val modernWatchContainerId = "next_gen_watch_layout_no_player_fragment_container"
    private val modernWatchContentIds = setOf("watch_panel", "watch_list")
    private val modernWatchPlaybackIds = setOf("watch_while_time_bar_view", "watch_while_time_bar_view_overlay")
    private val searchIds = setOf(
        "search_edit_text", "search_query", "search_results", "search_results_list",
    )
    private val shellIds = setOf(
        "bottom_bar", "pivot_bar", "tabs", "app_bar", "toolbar",
    )

    fun detect(
        packageName: String,
        nodes: List<SanitizedNode>,
        searchContextActive: Boolean = false,
    ): YouTubeDetection {
        if (packageName != YOUTUBE_PACKAGE) return unknown()

        val ids = nodes.mapNotNull { it.resourceId?.normalizedId() }.toSet()
        val visibleIds = nodes.asSequence()
            .filter { it.visibleToUser }
            .mapNotNull { it.resourceId?.normalizedId() }
            .toSet()
        val shortsMatches = shortsIds.intersect(ids).sorted()
        val videoMatches = videoIds.intersect(ids).sorted()
        val fullPlayerControlMatches = fullPlayerControlIds.intersect(visibleIds).sorted()
        val modernWatchContentMatches = modernWatchContentIds.intersect(visibleIds).sorted()
        // Playback chrome/time-bar nodes can remain structurally present while controls are
        // visually hidden, so their IDs may not be visibleToUser at the exact capture instant.
        // The watch-content anchor still must be visible, which keeps mini-player feeds out.
        val modernWatchPlaybackMatches = modernWatchPlaybackIds.intersect(ids).sorted()
        val hasModernWatchPage = modernWatchContainerId in visibleIds &&
            modernWatchContentMatches.isNotEmpty() &&
            (modernWatchPlaybackMatches.isNotEmpty() || fullPlayerControlMatches.isNotEmpty())
        val searchMatches = searchIds.intersect(ids).sorted()
        val shellMatches = shellIds.intersect(ids).sorted()
        val selectedChromeRoles = nodes.asSequence()
            .filter { it.visibleToUser && it.selected }
            .mapNotNull { it.chromeRole }
            .filter { it != UiChromeRole.YOUTUBE_BACK }
            .toSet()
        val visibleSubscriptionCandidates = nodes.asSequence()
            .filter { it.visibleToUser && it.youtubeSubscriptionState != null }
            .toList()
        val allSubscriptionStates = nodes.asSequence()
            .mapNotNull { it.youtubeSubscriptionState }
            .toSet()
        val hasBackNavigationChrome = nodes.any {
            it.visibleToUser && it.chromeRole == UiChromeRole.YOUTUBE_BACK
        }
        val hasPagerStructure = nodes.any {
            it.scrollable && it.childCount > 0 &&
                (it.className?.endsWith("ViewPager2") == true || it.className?.endsWith("RecyclerView") == true)
        }
        val hasEditableStructure = nodes.any { it.editable && it.visibleToUser }
        val hasSeekBarStructure = nodes.any { it.className?.endsWith("SeekBar") == true && it.visibleToUser }
        val hasLargeWatchPlayer = nodes.any { node ->
            node.visibleToUser &&
                node.resourceId?.normalizedId() == "watch_player" &&
                (node.visibleHeightFraction ?: 0.0) >= FULL_PLAYER_MIN_HEIGHT_FRACTION
        }
        val hasFullPlayerControls = fullPlayerControlMatches.isNotEmpty() &&
            ("watch_player" in videoMatches || modernWatchContainerId in visibleIds)

        // Content surfaces outrank the still-selected bottom tab. YouTube intentionally leaves the
        // source tab selected when a video is opened from Home/Subscriptions. A full-size
        // watch_player (or its expanded-player controls) therefore means Video even if a nav tab
        // remains selected. A small mini-player does not satisfy this evidence and the underlying
        // feed can still classify from its selected tab.
        val shortsStrong = shortsMatches.size == shortsIds.size
        val videoStrong = videoMatches.size >= 2 ||
            (videoMatches.isNotEmpty() && hasSeekBarStructure) ||
            hasLargeWatchPlayer ||
            hasFullPlayerControls ||
            hasModernWatchPage
        val creatorSubscriptionState = selectCurrentCreatorSubscriptionState(
            nodes = nodes,
            visibleCandidates = visibleSubscriptionCandidates,
            allStates = allSubscriptionStates,
            videoStrong = videoStrong,
            shortsStrong = shortsStrong,
        )
        val directSearchStrong = searchMatches.size >= 2 || (searchMatches.isNotEmpty() && hasEditableStructure)
        // YouTube keeps the originating bottom tab selected while Search is layered over it.
        // After Task Tunnel intentionally enters Search, the exact Back/up affordance is bounded,
        // content-free evidence that the user is still in that nested Search flow. This prevents
        // a sticky Subscriptions/Home selection from overwriting the actual Search surface.
        // Strong video/Shorts evidence still wins over this contextual hint.
        val contextualSearchStrong = searchContextActive && hasBackNavigationChrome &&
            !shortsStrong && !videoStrong
        val searchStrong = directSearchStrong || contextualSearchStrong
        val specializedCount = listOf(shortsStrong, videoStrong, directSearchStrong).count { it }

        if (specializedCount > 1) {
            return result(
                YouTubeSurface.UNKNOWN,
                0.0,
                signalList(
                    shortsMatches, videoMatches, searchMatches, hasPagerStructure, hasEditableStructure,
                    hasSeekBarStructure, hasLargeWatchPlayer, fullPlayerControlMatches,
                    modernWatchContentMatches, modernWatchPlaybackMatches, hasModernWatchPage,
                ),
            )
        }
        if (shortsStrong) return result(
            YouTubeSurface.YOUTUBE_SHORTS,
            0.95,
            subscriptionSignal(creatorSubscriptionState, visibleSubscriptionCandidates.size) +
                signalList(shortsMatches, emptyList(), emptyList(), hasPagerStructure, false, false),
            creatorSubscriptionState,
        )
        if (videoStrong) return result(
            YouTubeSurface.YOUTUBE_VIDEO,
            when {
                videoMatches.size >= 2 -> 0.9
                hasModernWatchPage -> 0.86
                else -> 0.8
            },
            subscriptionSignal(creatorSubscriptionState, visibleSubscriptionCandidates.size) +
                signalList(
                    emptyList(), videoMatches, emptyList(), false, false, hasSeekBarStructure,
                    hasLargeWatchPlayer, fullPlayerControlMatches,
                    modernWatchContentMatches, modernWatchPlaybackMatches, hasModernWatchPage,
                ),
            creatorSubscriptionState,
        )
        if (searchStrong) return result(
            YouTubeSurface.YOUTUBE_SEARCH,
            when {
                searchMatches.size >= 2 -> 0.9
                directSearchStrong -> 0.8
                else -> 0.82
            },
            signalList(
                emptyList(), emptyList(), searchMatches, false, hasEditableStructure, false,
                backNavigationChrome = hasBackNavigationChrome,
                searchContext = contextualSearchStrong,
            ),
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
                UiChromeRole.YOUTUBE_BACK -> null
            }
            if (surface != null) {
                return result(surface, 0.85, listOf("nav:${role.name.lowercase(Locale.ROOT)}"))
            }
        }
        if (shellMatches.isNotEmpty() && shortsMatches.isEmpty() && videoMatches.isEmpty() && searchMatches.isEmpty()) {
            return result(YouTubeSurface.YOUTUBE_OTHER, 0.6, shellMatches.map { "id:$it" })
        }
        return unknown(
            signalList(
                shortsMatches, videoMatches, searchMatches, hasPagerStructure, hasEditableStructure,
                hasSeekBarStructure, hasLargeWatchPlayer, fullPlayerControlMatches,
                modernWatchContentMatches, modernWatchPlaybackMatches, hasModernWatchPage,
            ),
        )
    }

    /**
     * YouTube can keep Subscribe/Subscribed controls from recommended content in the same
     * accessibility tree as the currently playing video. Treating the whole tree as one global
     * state therefore creates false conflicts and fails open. For normal watch pages, prefer the
     * subscription control spatially closest below the full-size player. For Shorts, prefer the
     * lowest visible control in the active page region. If the evidence is still ambiguous, fail
     * open to null rather than guessing.
     */
    private fun selectCurrentCreatorSubscriptionState(
        nodes: List<SanitizedNode>,
        visibleCandidates: List<SanitizedNode>,
        allStates: Set<YouTubeSubscriptionState>,
        videoStrong: Boolean,
        shortsStrong: Boolean,
    ): YouTubeSubscriptionState? {
        if (videoStrong) {
            val playerBottom = nodes.asSequence()
                .filter {
                    it.visibleToUser &&
                        it.resourceId?.normalizedId() == "watch_player" &&
                        it.visibleTopFraction != null &&
                        it.visibleHeightFraction != null &&
                        it.visibleHeightFraction >= FULL_PLAYER_MIN_HEIGHT_FRACTION
                }
                .map { (it.visibleTopFraction!! + it.visibleHeightFraction!!).coerceIn(0.0, 1.0) }
                .minOrNull()

            if (playerBottom != null) {
                val nearby = visibleCandidates.asSequence()
                    .filter { candidate ->
                        val top = candidate.visibleTopFraction ?: return@filter false
                        top >= playerBottom - CURRENT_CREATOR_ABOVE_PLAYER_TOLERANCE &&
                            top <= (playerBottom + CURRENT_CREATOR_MAX_DISTANCE_BELOW_PLAYER).coerceAtMost(0.92)
                    }
                    .sortedBy { candidate ->
                        val top = candidate.visibleTopFraction ?: 1.0
                        kotlin.math.abs(top - playerBottom)
                    }
                    .toList()
                chooseNearestUnambiguousState(nearby)?.let { return it }
            }

            // Some YouTube builds expose the current creator control without usable player
            // bounds, or place that row lower than older layouts did. The current creator's
            // subscription control is the uppermost visible subscription action on a watch page;
            // recommendation controls, when present, occur below it. Prefer that ordering instead
            // of imposing a fixed screen-height cutoff that can discard the real control.
            val topmostVisible = visibleCandidates.sortedBy { it.visibleTopFraction ?: 1.0 }
            chooseNearestUnambiguousState(topmostVisible)?.let { return it }
        }

        if (shortsStrong) {
            val shortCandidates = visibleCandidates
                .filter { candidate ->
                    val top = candidate.visibleTopFraction ?: return@filter false
                    top in SHORTS_CREATOR_MIN_TOP..SHORTS_CREATOR_MAX_TOP
                }
                .sortedByDescending { it.visibleTopFraction ?: 0.0 }
            chooseNearestUnambiguousState(shortCandidates)?.let { return it }
        }

        val visibleStates = visibleCandidates.mapNotNull { it.youtubeSubscriptionState }.toSet()
        return when {
            visibleStates.size == 1 -> visibleStates.single()
            visibleStates.size > 1 -> null
            else -> allStates.singleOrNull()
        }
    }

    private fun chooseNearestUnambiguousState(
        candidates: List<SanitizedNode>,
    ): YouTubeSubscriptionState? {
        val first = candidates.firstOrNull() ?: return null
        val firstState = first.youtubeSubscriptionState ?: return null
        val firstTop = first.visibleTopFraction

        // Parent/child accessibility wrappers can duplicate one control. Accept duplicates that
        // resolve to the same state. If an opposite state appears at effectively the same vertical
        // position, the page is still transitioning; fail open and let the settled re-capture win.
        if (firstTop != null) {
            val nearStates = candidates.asSequence()
                .takeWhile { candidate ->
                    val top = candidate.visibleTopFraction ?: return@takeWhile false
                    kotlin.math.abs(top - firstTop) <= SAME_CONTROL_VERTICAL_TOLERANCE
                }
                .mapNotNull { it.youtubeSubscriptionState }
                .toSet()
            if (nearStates.size > 1) return null
        }
        return firstState
    }

    private fun subscriptionSignal(
        state: YouTubeSubscriptionState?,
        visibleCandidateCount: Int,
    ): List<String> = listOf(
        "subscription:${state?.name?.lowercase(Locale.ROOT) ?: "unknown"}",
        "subscription-visible-candidates:$visibleCandidateCount",
    )

    private fun String.normalizedId(): String = substringAfterLast('/').lowercase(Locale.ROOT)

    private fun signalList(
        shorts: List<String>,
        video: List<String>,
        search: List<String>,
        pager: Boolean,
        editable: Boolean,
        seekBar: Boolean,
        largeWatchPlayer: Boolean = false,
        fullPlayerControls: List<String> = emptyList(),
        modernWatchContent: List<String> = emptyList(),
        modernWatchPlayback: List<String> = emptyList(),
        modernWatchPage: Boolean = false,
        backNavigationChrome: Boolean = false,
        searchContext: Boolean = false,
    ): List<String> = buildList {
        shorts.forEach { add("shorts-id:$it") }
        video.forEach { add("video-id:$it") }
        search.forEach { add("search-id:$it") }
        fullPlayerControls.forEach { add("player-control-id:$it") }
        modernWatchContent.forEach { add("modern-watch-content:$it") }
        modernWatchPlayback.forEach { add("modern-watch-playback:$it") }
        if (modernWatchPage) add("structure:modern-watch-page")
        if (pager) add("structure:scrollable-pager")
        if (editable) add("structure:visible-editable")
        if (seekBar) add("structure:visible-seekbar")
        if (largeWatchPlayer) add("structure:large-watch-player")
        if (backNavigationChrome) add("chrome:back")
        if (searchContext) add("context:search")
    }.take(MAX_SIGNALS)

    private fun result(
        surface: YouTubeSurface,
        confidence: Double,
        signals: List<String>,
        creatorSubscriptionState: YouTubeSubscriptionState? = null,
    ) = YouTubeDetection(surface, confidence, signals.take(MAX_SIGNALS), creatorSubscriptionState)

    private fun unknown(signals: List<String> = emptyList()) =
        YouTubeDetection(YouTubeSurface.UNKNOWN, 0.0, signals.take(MAX_SIGNALS))

    private const val FULL_PLAYER_MIN_HEIGHT_FRACTION = 0.18
    private const val CURRENT_CREATOR_ABOVE_PLAYER_TOLERANCE = 0.03
    private const val CURRENT_CREATOR_MAX_DISTANCE_BELOW_PLAYER = 0.38
    private const val SHORTS_CREATOR_MIN_TOP = 0.25
    private const val SHORTS_CREATOR_MAX_TOP = 0.90
    private const val SAME_CONTROL_VERTICAL_TOLERANCE = 0.025
}
