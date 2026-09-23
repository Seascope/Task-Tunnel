package com.example.tasktunnel.detector

import com.example.tasktunnel.accessibility.SanitizedNode
import org.junit.Assert.assertEquals
import org.junit.Test

class TikTokSurfaceDetectorTest {
    @Test
    fun searchEvidenceWinsOverStillSelectedFeedTab() {
        assertDetection(
            TikTokSurface.TIKTOK_SEARCH,
            node("hu0", editable = true),
            node("omq", selected = true, clickable = true),
            node("omy"),
        )
    }

    @Test
    fun selectedNavigationTabsMapToSemanticSurfaces() {
        mapOf(
            "omq" to TikTokSurface.TIKTOK_FEED,
            "omp" to TikTokSurface.TIKTOK_FRIENDS,
            "omr" to TikTokSurface.TIKTOK_INBOX,
            "oms" to TikTokSurface.TIKTOK_PROFILE,
        ).forEach { (id, expected) ->
            assertDetection(expected, node(id, selected = true, clickable = true), node("omy"))
        }
    }

    @Test
    fun conflictingSelectedTabsFailOpen() {
        assertDetection(
            TikTokSurface.UNKNOWN,
            node("omq", selected = true, clickable = true),
            node("omr", selected = true, clickable = true),
            node("omy"),
        )
    }

    @Test
    fun navigationWithoutRecognizedSelectionIsOther() {
        assertDetection(TikTokSurface.TIKTOK_OTHER, node("omy"))
    }

    @Test
    fun genericFeedIdsAloneDoNotGuessFeed() {
        assertDetection(TikTokSurface.UNKNOWN, node("ewa"), node("bql"), node("ep7"))
    }

    @Test
    fun nonTikTokPackageFailsOpen() {
        val result = TikTokSurfaceDetector.detect(
            "com.instagram.android",
            listOf(node("omq", selected = true, clickable = true)),
        )
        assertEquals(TikTokSurface.UNKNOWN, result.surface)
        assertEquals(0.0, result.confidence, 0.0)
    }

    private fun assertDetection(expected: TikTokSurface, vararg nodes: SanitizedNode) {
        assertEquals(expected, TikTokSurfaceDetector.detect(TikTokSurfaceDetector.TIKTOK_PACKAGE, nodes.toList()).surface)
    }

    private fun node(
        id: String,
        visibleToUser: Boolean = true,
        selected: Boolean = false,
        clickable: Boolean = false,
        editable: Boolean = false,
        scrollable: Boolean = false,
    ) = SanitizedNode(
        depth = 0,
        className = "android.view.View",
        resourceId = id,
        childCount = 0,
        clickable = clickable,
        scrollable = scrollable,
        editable = editable,
        enabled = true,
        visibleToUser = visibleToUser,
        selected = selected,
    )
}
