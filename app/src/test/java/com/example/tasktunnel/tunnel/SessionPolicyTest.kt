package com.example.tasktunnel.tunnel

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionPolicyTest {
    @Test
    fun instagramMessages_allowsMessagesAndFailsOpenUnknown() {
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_MESSAGES, DetectedSurface.INSTAGRAM_MESSAGES))
        assertEquals(PolicyDecision.UNKNOWN_FAIL_OPEN, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_MESSAGES, DetectedSurface.UNKNOWN))
    }

    @Test
    fun instagramMessages_intervenesOnReelsAndExplore() {
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_MESSAGES, DetectedSurface.INSTAGRAM_REELS))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_MESSAGES, DetectedSurface.INSTAGRAM_EXPLORE))
    }

    @Test
    fun youtubeSearchWatch_allowsSearchAndVideoAndFailsOpenUnknown() {
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SEARCH_WATCH, DetectedSurface.YOUTUBE_SEARCH))
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SEARCH_WATCH, DetectedSurface.YOUTUBE_VIDEO))
        assertEquals(PolicyDecision.UNKNOWN_FAIL_OPEN, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SEARCH_WATCH, DetectedSurface.UNKNOWN))
    }

    @Test
    fun youtubeSearchWatch_intervenesOnShorts() {
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SEARCH_WATCH, DetectedSurface.YOUTUBE_SHORTS))
    }

    @Test
    fun intentionalBrowse_allowsSelectedAppSurfaces() {
        val instagram = listOf(
            DetectedSurface.INSTAGRAM_MESSAGES,
            DetectedSurface.INSTAGRAM_EXPLORE,
            DetectedSurface.INSTAGRAM_REELS,
            DetectedSurface.INSTAGRAM_HOME,
            DetectedSurface.INSTAGRAM_OTHER,
        )
        val youtube = listOf(
            DetectedSurface.YOUTUBE_SEARCH,
            DetectedSurface.YOUTUBE_VIDEO,
            DetectedSurface.YOUTUBE_SHORTS,
            DetectedSurface.YOUTUBE_OTHER,
        )
        instagram.forEach {
            assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_BROWSE, it))
        }
        youtube.forEach {
            assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.YOUTUBE_BROWSE, it))
        }
    }
}
