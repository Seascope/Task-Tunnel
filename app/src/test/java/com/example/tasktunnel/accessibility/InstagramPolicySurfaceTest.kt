package com.example.tasktunnel.accessibility

import com.example.tasktunnel.detector.InstagramDetection
import com.example.tasktunnel.detector.InstagramSurface
import com.example.tasktunnel.tunnel.DetectedSurface
import com.example.tasktunnel.tunnel.TunnelTask
import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramPolicySurfaceTest {
    @Test
    fun search_allowsReelViewerOpenedOutsideDedicatedReelsTab() {
        val detection = InstagramDetection(
            surface = InstagramSurface.INSTAGRAM_REELS,
            confidence = 0.95,
            strongestSignals = listOf(
                "active:clips_expanded_touch_view",
                "id:clips_viewer_view_pager",
                "id:clips_single_media_component",
            ),
        )

        assertEquals(
            DetectedSurface.INSTAGRAM_OTHER,
            detection.toPolicyTunnelSurface(TunnelTask.INSTAGRAM_SEARCH),
        )
    }

    @Test
    fun search_stillBlocksDedicatedReelsTab() {
        val detection = InstagramDetection(
            surface = InstagramSurface.INSTAGRAM_REELS,
            confidence = 0.98,
            strongestSignals = listOf(
                "active:clips_expanded_touch_view",
                "active:clips_tab",
                "id:clips_media_component",
            ),
        )

        assertEquals(
            DetectedSurface.INSTAGRAM_REELS,
            detection.toPolicyTunnelSurface(TunnelTask.INSTAGRAM_SEARCH),
        )
    }

    @Test
    fun nonSearchPurposesKeepNormalReelsPolicy() {
        val detection = InstagramDetection(
            surface = InstagramSurface.INSTAGRAM_REELS,
            confidence = 0.95,
            strongestSignals = listOf("active:clips_expanded_touch_view"),
        )

        assertEquals(
            DetectedSurface.INSTAGRAM_REELS,
            detection.toPolicyTunnelSurface(TunnelTask.INSTAGRAM_MESSAGES),
        )
    }
}
