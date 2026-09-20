package com.example.tasktunnel.detector

import com.example.tasktunnel.accessibility.SanitizedNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeSurfaceDetectorTest {
    @Test fun strongShortsSignals() = assertSurface(
        YouTubeSurface.YOUTUBE_SHORTS,
        node("com.google.android.youtube:id/shorts_player"),
        node("reel_player_page"),
    )

    @Test fun normalVideoSignals() = assertSurface(
        YouTubeSurface.YOUTUBE_VIDEO,
        node("watch_player"), node("player_view"),
    )

    @Test fun videoIdWithVisibleSeekBarStructure() = assertSurface(
        YouTubeSurface.YOUTUBE_VIDEO,
        node("watch_player"), node(className = "android.widget.SeekBar", visibleToUser = true),
    )

    @Test fun searchSignals() = assertSurface(
        YouTubeSurface.YOUTUBE_SEARCH,
        node("search_edit_text"), node("search_results"),
    )

    @Test fun searchIdWithVisibleEditableStructure() = assertSurface(
        YouTubeSurface.YOUTUBE_SEARCH,
        node("search_query"), node(editable = true, visibleToUser = true),
    )

    @Test fun conflictingSpecializedEvidenceFailsOpen() = assertSurface(
        YouTubeSurface.UNKNOWN,
        node("shorts_player"), node("reel_player_page"), node("watch_player"), node("player_view"),
    )

    @Test fun oneWeakShortsTokenFailsOpen() = assertSurface(YouTubeSurface.UNKNOWN, node("reel_player_page"))

    @Test fun nonYouTubePackageFailsOpen() {
        val result = YouTubeSurfaceDetector.detect("com.instagram.android", listOf(node("shorts_player"), node("reel_player_page")))
        assertEquals(YouTubeSurface.UNKNOWN, result.surface)
        assertEquals(0.0, result.confidence, 0.0)
    }

    @Test fun knownGenericShellIsOther() = assertSurface(YouTubeSurface.YOUTUBE_OTHER, node("bottom_bar"))

    private fun assertSurface(expected: YouTubeSurface, vararg nodes: SanitizedNode) {
        val result = YouTubeSurfaceDetector.detect(YouTubeSurfaceDetector.YOUTUBE_PACKAGE, nodes.toList())
        assertEquals(expected, result.surface)
        if (expected == YouTubeSurface.UNKNOWN) assertEquals(0.0, result.confidence, 0.0)
        else assertTrue(result.confidence > 0.0)
    }

    private fun node(
        id: String? = null,
        className: String = "android.view.View",
        editable: Boolean = false,
        visibleToUser: Boolean = true,
    ) = SanitizedNode(
        depth = 0, className = className, resourceId = id, childCount = 0,
        clickable = false, scrollable = false, editable = editable, enabled = true, visibleToUser = visibleToUser,
    )
}
