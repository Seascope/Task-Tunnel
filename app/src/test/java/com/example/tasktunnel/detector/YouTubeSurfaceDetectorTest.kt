package com.example.tasktunnel.detector

import com.example.tasktunnel.accessibility.SanitizedNode
import com.example.tasktunnel.accessibility.UiChromeRole
import com.example.tasktunnel.accessibility.YouTubeSubscriptionState
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeSurfaceDetectorTest {
    @Test fun shortsPairIsStrong() = assertDetection(
        YouTubeSurface.YOUTUBE_SHORTS,
        0.95,
        node("com.google.android.youtube:id/reel_recycler"),
        node("reel_player_page_container"),
    )

    @Test fun shortsPairRemainsStrongWithProgressBar() = assertDetection(
        YouTubeSurface.YOUTUBE_SHORTS,
        0.95,
        node("reel_recycler"),
        node("reel_player_page_container"),
        node("reel_progress_bar"),
    )

    @Test fun eachShortsIdAloneFailsOpen() {
        assertDetection(YouTubeSurface.UNKNOWN, 0.0, node("reel_recycler"))
        assertDetection(YouTubeSurface.UNKNOWN, 0.0, node("reel_player_page_container"))
    }

    @Test fun timeBarAloneFailsOpen() = assertDetection(YouTubeSurface.UNKNOWN, 0.0, node("reel_time_bar"))

    @Test fun pivotBarAloneIsOther() = assertDetection(YouTubeSurface.YOUTUBE_OTHER, 0.6, node("pivot_bar"))

    @Test fun genericPlaybackAndShellIdsAreOtherNotShorts() = assertDetection(
        YouTubeSurface.YOUTUBE_OTHER,
        0.6,
        node("reel_time_bar"),
        node("pivot_bar"),
        node("toolbar"),
    )

    @Test fun homeLikeShellIsOther() = assertDetection(
        YouTubeSurface.YOUTUBE_OTHER,
        0.6,
        node("pivot_bar"),
        node("toolbar"),
    )

    @Test fun physicalSearchSignalsRemainSearch() = assertDetection(
        YouTubeSurface.YOUTUBE_SEARCH,
        0.8,
        node("search_query"),
        node(editable = true, visibleToUser = true),
    )

    @Test fun invisibleStaleSearchIdsDoNotOverrideVisibleSubscriptionsTab() = assertDetection(
        YouTubeSurface.YOUTUBE_SUBSCRIPTIONS,
        0.85,
        node("search_query", visibleToUser = false),
        node("search_results", visibleToUser = false),
        node(chromeRole = UiChromeRole.YOUTUBE_SUBSCRIPTIONS, selected = true),
    )

    @Test fun invisibleStaleShortsTreeDoesNotOverrideVisibleHomeTab() = assertDetection(
        YouTubeSurface.YOUTUBE_HOME,
        0.85,
        node("reel_recycler", visibleToUser = false),
        node("reel_player_page_container", visibleToUser = false),
        node(chromeRole = UiChromeRole.YOUTUBE_HOME, selected = true),
    )

    @Test fun invisibleStaleLegacyVideoTreeDoesNotOverrideVisibleSubscriptionsTab() = assertDetection(
        YouTubeSurface.YOUTUBE_SUBSCRIPTIONS,
        0.85,
        node("watch_player", visibleToUser = false),
        node("player_view", visibleToUser = false),
        node(chromeRole = UiChromeRole.YOUTUBE_SUBSCRIPTIONS, selected = true),
    )

    @Test fun longVideoSignalsRemainVideo() = assertDetection(
        YouTubeSurface.YOUTUBE_VIDEO,
        0.8,
        node("watch_player"),
        node(className = "android.widget.SeekBar", visibleToUser = true),
    )

    @Test fun conflictingSpecializedEvidenceFailsOpen() = assertDetection(
        YouTubeSurface.UNKNOWN,
        0.0,
        node("reel_recycler"), node("reel_player_page_container"), node("watch_player"), node("player_view"),
    )

    @Test fun normalizedIdMustBeAnExactSegment() = assertDetection(
        YouTubeSurface.UNKNOWN,
        0.0,
        node("com.google.android.youtube:id/not_reel_recycler"),
        node("com.google.android.youtube:id/not_reel_player_page_container"),
    )


    @Test fun selectedHomeChromeRoleIsHome() = assertDetection(
        YouTubeSurface.YOUTUBE_HOME,
        0.85,
        node(chromeRole = UiChromeRole.YOUTUBE_HOME, selected = true),
    )

    @Test fun selectedSubscriptionsChromeRoleIsSubscriptions() = assertDetection(
        YouTubeSurface.YOUTUBE_SUBSCRIPTIONS,
        0.85,
        node(chromeRole = UiChromeRole.YOUTUBE_SUBSCRIPTIONS, selected = true),
    )

    @Test fun selectedYouChromeRoleIsYou() = assertDetection(
        YouTubeSurface.YOUTUBE_YOU,
        0.85,
        node(chromeRole = UiChromeRole.YOUTUBE_YOU, selected = true),
    )

    @Test fun videoEvidenceWinsOverSelectedHomeChromeRole() = assertDetection(
        YouTubeSurface.YOUTUBE_VIDEO,
        0.8,
        node("watch_player"),
        node(className = "android.widget.SeekBar", visibleToUser = true),
        node(chromeRole = UiChromeRole.YOUTUBE_HOME, selected = true),
    )


    @Test fun fullWatchPlayerWinsOverSelectedSubscriptionsTab() = assertDetection(
        YouTubeSurface.YOUTUBE_VIDEO,
        0.8,
        node("watch_player", visibleHeightFraction = 0.31),
        node(chromeRole = UiChromeRole.YOUTUBE_SUBSCRIPTIONS, selected = true),
    )

    @Test fun miniPlayerDoesNotOverrideSelectedSubscriptionsTab() = assertDetection(
        YouTubeSurface.YOUTUBE_SUBSCRIPTIONS,
        0.85,
        node("watch_player", visibleHeightFraction = 0.09),
        node(chromeRole = UiChromeRole.YOUTUBE_SUBSCRIPTIONS, selected = true),
    )

    @Test fun expandedPlayerControlsWinOverSelectedSubscriptionsTab() = assertDetection(
        YouTubeSurface.YOUTUBE_VIDEO,
        0.8,
        node("watch_player"),
        node("player_collapse_button"),
        node(chromeRole = UiChromeRole.YOUTUBE_SUBSCRIPTIONS, selected = true),
    )



    @Test fun modernWatchPageWithoutLegacyWatchPlayerIsVideo() = assertDetection(
        YouTubeSurface.YOUTUBE_VIDEO,
        0.86,
        node("next_gen_watch_layout_no_player_fragment_container"),
        node("watch_panel"),
        node("watch_list"),
        node("watch_while_time_bar_view"),
    )

    @Test fun modernWatchPageCarriesNotSubscribedCreatorState() {
        val result = YouTubeSurfaceDetector.detect(
            YouTubeSurfaceDetector.YOUTUBE_PACKAGE,
            listOf(
                node("next_gen_watch_layout_no_player_fragment_container"),
                node("watch_panel"),
                node("watch_list"),
                node("watch_while_time_bar_view"),
                node(youtubeSubscriptionState = YouTubeSubscriptionState.NOT_SUBSCRIBED, visibleTopFraction = 0.55),
            ),
        )
        assertEquals(YouTubeSurface.YOUTUBE_VIDEO, result.surface)
        assertEquals(YouTubeSubscriptionState.NOT_SUBSCRIBED, result.creatorSubscriptionState)
    }

    @Test fun modernMiniPlayerSkeletonDoesNotOverrideSelectedSubscriptionsTab() = assertDetection(
        YouTubeSurface.YOUTUBE_SUBSCRIPTIONS,
        0.85,
        node("next_gen_watch_layout_no_player_fragment_container"),
        node("watch_while_time_bar_view"),
        node(chromeRole = UiChromeRole.YOUTUBE_SUBSCRIPTIONS, selected = true),
    )

    @Test fun modernWatchPlayerControlsAreVideoEvenWithoutLegacyWatchPlayer() = assertDetection(
        YouTubeSurface.YOUTUBE_VIDEO,
        0.8,
        node("next_gen_watch_layout_no_player_fragment_container"),
        node("player_collapse_button"),
    )

    @Test fun searchContextWithBackWinsOverStickySubscriptionsTab() {
        val result = YouTubeSurfaceDetector.detect(
            YouTubeSurfaceDetector.YOUTUBE_PACKAGE,
            listOf(
                node(chromeRole = UiChromeRole.YOUTUBE_SUBSCRIPTIONS, selected = true),
                node(chromeRole = UiChromeRole.YOUTUBE_BACK),
            ),
            searchContextActive = true,
        )
        assertEquals(YouTubeSurface.YOUTUBE_SEARCH, result.surface)
        assertEquals(0.82, result.confidence, 0.0)
    }

    @Test fun stickySubscriptionsWithoutBackEndsSearchContextClassification() {
        val result = YouTubeSurfaceDetector.detect(
            YouTubeSurfaceDetector.YOUTUBE_PACKAGE,
            listOf(node(chromeRole = UiChromeRole.YOUTUBE_SUBSCRIPTIONS, selected = true)),
            searchContextActive = true,
        )
        assertEquals(YouTubeSurface.YOUTUBE_SUBSCRIPTIONS, result.surface)
    }

    @Test fun videoStillWinsOverSearchContextAndStickySubscriptionsTab() {
        val result = YouTubeSurfaceDetector.detect(
            YouTubeSurfaceDetector.YOUTUBE_PACKAGE,
            listOf(
                node("watch_player", visibleHeightFraction = 0.31),
                node(chromeRole = UiChromeRole.YOUTUBE_SUBSCRIPTIONS, selected = true),
                node(chromeRole = UiChromeRole.YOUTUBE_BACK),
            ),
            searchContextActive = true,
        )
        assertEquals(YouTubeSurface.YOUTUBE_VIDEO, result.surface)
    }

    @Test fun conflictingSelectedChromeRolesFailOpen() = assertDetection(
        YouTubeSurface.UNKNOWN,
        0.0,
        node(chromeRole = UiChromeRole.YOUTUBE_HOME, selected = true),
        node(chromeRole = UiChromeRole.YOUTUBE_SUBSCRIPTIONS, selected = true),
    )


    @Test fun videoCarriesExplicitSubscribedState() {
        val result = YouTubeSurfaceDetector.detect(
            YouTubeSurfaceDetector.YOUTUBE_PACKAGE,
            listOf(
                node("watch_player", visibleHeightFraction = 0.31),
                node(youtubeSubscriptionState = YouTubeSubscriptionState.SUBSCRIBED),
            ),
        )
        assertEquals(YouTubeSurface.YOUTUBE_VIDEO, result.surface)
        assertEquals(YouTubeSubscriptionState.SUBSCRIBED, result.creatorSubscriptionState)
    }

    @Test fun shortsCarryExplicitNotSubscribedState() {
        val result = YouTubeSurfaceDetector.detect(
            YouTubeSurfaceDetector.YOUTUBE_PACKAGE,
            listOf(
                node("reel_recycler"),
                node("reel_player_page_container"),
                node(youtubeSubscriptionState = YouTubeSubscriptionState.NOT_SUBSCRIBED),
            ),
        )
        assertEquals(YouTubeSurface.YOUTUBE_SHORTS, result.surface)
        assertEquals(YouTubeSubscriptionState.NOT_SUBSCRIBED, result.creatorSubscriptionState)
    }

    @Test fun visibleSubscriptionStateWinsOverConflictingOffscreenState() {
        val result = YouTubeSurfaceDetector.detect(
            YouTubeSurfaceDetector.YOUTUBE_PACKAGE,
            listOf(
                node("watch_player", visibleHeightFraction = 0.31),
                node(youtubeSubscriptionState = YouTubeSubscriptionState.SUBSCRIBED, visibleToUser = true),
                node(youtubeSubscriptionState = YouTubeSubscriptionState.NOT_SUBSCRIBED, visibleToUser = false),
            ),
        )
        assertEquals(YouTubeSubscriptionState.SUBSCRIBED, result.creatorSubscriptionState)
    }

    @Test fun hiddenSubscriptionStateAloneFailsOpenToUnknownRelation() {
        val result = YouTubeSurfaceDetector.detect(
            YouTubeSurfaceDetector.YOUTUBE_PACKAGE,
            listOf(
                node("watch_player", visibleHeightFraction = 0.31),
                node(
                    youtubeSubscriptionState = YouTubeSubscriptionState.NOT_SUBSCRIBED,
                    visibleToUser = false,
                ),
            ),
        )
        assertEquals(YouTubeSurface.YOUTUBE_VIDEO, result.surface)
        assertEquals(null, result.creatorSubscriptionState)
    }

    @Test fun conflictingSubscriptionStateFailsOpenToUnknownRelation() {
        val result = YouTubeSurfaceDetector.detect(
            YouTubeSurfaceDetector.YOUTUBE_PACKAGE,
            listOf(
                node("watch_player", visibleHeightFraction = 0.31),
                node(youtubeSubscriptionState = YouTubeSubscriptionState.SUBSCRIBED),
                node(youtubeSubscriptionState = YouTubeSubscriptionState.NOT_SUBSCRIBED),
            ),
        )
        assertEquals(YouTubeSurface.YOUTUBE_VIDEO, result.surface)
        assertEquals(null, result.creatorSubscriptionState)
    }


    @Test fun currentCreatorStateWinsOverConflictingRecommendationBelowWatchPlayer() {
        val result = YouTubeSurfaceDetector.detect(
            YouTubeSurfaceDetector.YOUTUBE_PACKAGE,
            listOf(
                node("watch_player", visibleTopFraction = 0.00, visibleHeightFraction = 0.30),
                node(
                    youtubeSubscriptionState = YouTubeSubscriptionState.NOT_SUBSCRIBED,
                    visibleTopFraction = 0.36,
                ),
                node(
                    youtubeSubscriptionState = YouTubeSubscriptionState.SUBSCRIBED,
                    visibleTopFraction = 0.78,
                ),
            ),
        )
        assertEquals(YouTubeSurface.YOUTUBE_VIDEO, result.surface)
        assertEquals(YouTubeSubscriptionState.NOT_SUBSCRIBED, result.creatorSubscriptionState)
    }

    @Test fun currentSubscribedCreatorWinsOverUnsubscribedRecommendationBelowWatchPlayer() {
        val result = YouTubeSurfaceDetector.detect(
            YouTubeSurfaceDetector.YOUTUBE_PACKAGE,
            listOf(
                node("watch_player", visibleTopFraction = 0.00, visibleHeightFraction = 0.30),
                node(
                    youtubeSubscriptionState = YouTubeSubscriptionState.SUBSCRIBED,
                    visibleTopFraction = 0.37,
                ),
                node(
                    youtubeSubscriptionState = YouTubeSubscriptionState.NOT_SUBSCRIBED,
                    visibleTopFraction = 0.81,
                ),
            ),
        )
        assertEquals(YouTubeSubscriptionState.SUBSCRIBED, result.creatorSubscriptionState)
    }

    @Test fun currentCreatorBelowOldFallbackCutoffIsStillDetected() {
        val result = YouTubeSurfaceDetector.detect(
            YouTubeSurfaceDetector.YOUTUBE_PACKAGE,
            listOf(
                node("watch_player", visibleHeightFraction = 0.31),
                node(
                    youtubeSubscriptionState = YouTubeSubscriptionState.NOT_SUBSCRIBED,
                    visibleTopFraction = 0.79,
                ),
                node(
                    youtubeSubscriptionState = YouTubeSubscriptionState.SUBSCRIBED,
                    visibleTopFraction = 0.93,
                ),
            ),
        )
        assertEquals(YouTubeSubscriptionState.NOT_SUBSCRIBED, result.creatorSubscriptionState)
    }

    @Test fun conflictingStatesAtSameCreatorPositionFailOpenDuringTransition() {
        val result = YouTubeSurfaceDetector.detect(
            YouTubeSurfaceDetector.YOUTUBE_PACKAGE,
            listOf(
                node("watch_player", visibleTopFraction = 0.00, visibleHeightFraction = 0.30),
                node(
                    youtubeSubscriptionState = YouTubeSubscriptionState.SUBSCRIBED,
                    visibleTopFraction = 0.36,
                ),
                node(
                    youtubeSubscriptionState = YouTubeSubscriptionState.NOT_SUBSCRIBED,
                    visibleTopFraction = 0.37,
                ),
            ),
        )
        assertEquals(null, result.creatorSubscriptionState)
    }

    @Test fun nonYouTubePackageFailsOpen() {
        val result = YouTubeSurfaceDetector.detect(
            "com.instagram.android",
            listOf(node("reel_recycler"), node("reel_player_page_container")),
        )
        assertEquals(YouTubeSurface.UNKNOWN, result.surface)
        assertEquals(0.0, result.confidence, 0.0)
    }

    private fun assertDetection(expected: YouTubeSurface, expectedConfidence: Double, vararg nodes: SanitizedNode) {
        val result = YouTubeSurfaceDetector.detect(YouTubeSurfaceDetector.YOUTUBE_PACKAGE, nodes.toList())
        assertEquals(expected, result.surface)
        assertEquals(expectedConfidence, result.confidence, 0.0)
    }

    private fun node(
        id: String? = null,
        className: String = "android.view.View",
        editable: Boolean = false,
        visibleToUser: Boolean = true,
        selected: Boolean = false,
        chromeRole: UiChromeRole? = null,
        youtubeSubscriptionState: YouTubeSubscriptionState? = null,
        visibleTopFraction: Double? = null,
        visibleHeightFraction: Double? = null,
    ) = SanitizedNode(
        depth = 0, className = className, resourceId = id, childCount = 0,
        clickable = false, scrollable = false, editable = editable, enabled = true, visibleToUser = visibleToUser,
        selected = selected, chromeRole = chromeRole, youtubeSubscriptionState = youtubeSubscriptionState,
        visibleTopFraction = visibleTopFraction, visibleHeightFraction = visibleHeightFraction,
    )
}
