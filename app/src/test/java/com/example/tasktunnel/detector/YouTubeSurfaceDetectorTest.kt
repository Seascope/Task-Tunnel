package com.example.tasktunnel.detector

import com.example.tasktunnel.accessibility.SanitizedNode
import com.example.tasktunnel.accessibility.UiChromeRole
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

    @Test fun conflictingSelectedChromeRolesFailOpen() = assertDetection(
        YouTubeSurface.UNKNOWN,
        0.0,
        node(chromeRole = UiChromeRole.YOUTUBE_HOME, selected = true),
        node(chromeRole = UiChromeRole.YOUTUBE_SUBSCRIPTIONS, selected = true),
    )

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
    ) = SanitizedNode(
        depth = 0, className = className, resourceId = id, childCount = 0,
        clickable = false, scrollable = false, editable = editable, enabled = true, visibleToUser = visibleToUser,
        selected = selected, chromeRole = chromeRole,
    )
}
