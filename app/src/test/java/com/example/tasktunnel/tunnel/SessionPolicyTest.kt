package com.example.tasktunnel.tunnel

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionPolicyTest {
    @Test
    fun instagramMessages_allowsSearchAsCompatibleIntentAndFailsOpenUnknown() {
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_MESSAGES, DetectedSurface.INSTAGRAM_MESSAGES))
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_MESSAGES, DetectedSurface.INSTAGRAM_EXPLORE))
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_MESSAGES, DetectedSurface.INSTAGRAM_OTHER))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_MESSAGES, DetectedSurface.INSTAGRAM_HOME))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_MESSAGES, DetectedSurface.INSTAGRAM_REELS))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_MESSAGES, DetectedSurface.INSTAGRAM_PROFILE))
        assertEquals(PolicyDecision.UNKNOWN_FAIL_OPEN, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_MESSAGES, DetectedSurface.UNKNOWN))
    }

    @Test
    fun instagramSearch_allowsMessagesAsCompatibleIntentButBlocksFeeds() {
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_SEARCH, DetectedSurface.INSTAGRAM_MESSAGES))
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_SEARCH, DetectedSurface.INSTAGRAM_EXPLORE))
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_SEARCH, DetectedSurface.INSTAGRAM_PROFILE))
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_SEARCH, DetectedSurface.INSTAGRAM_OTHER))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_SEARCH, DetectedSurface.INSTAGRAM_HOME))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_SEARCH, DetectedSurface.INSTAGRAM_REELS))
    }

    @Test
    fun instagramPost_allowsCreateFlowButBlocksBrowsingSurfaces() {
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_POST, DetectedSurface.INSTAGRAM_CREATE))
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_POST, DetectedSurface.INSTAGRAM_OTHER))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_POST, DetectedSurface.INSTAGRAM_HOME))
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_POST, DetectedSurface.INSTAGRAM_PROFILE))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_POST, DetectedSurface.INSTAGRAM_EXPLORE))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.INSTAGRAM_POST, DetectedSurface.INSTAGRAM_REELS))
    }

    @Test
    fun youtubeSearchWatch_allowsSearchVideoAndNeutralDetailsButBlocksFeeds() {
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SEARCH_WATCH, DetectedSurface.YOUTUBE_SEARCH))
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SEARCH_WATCH, DetectedSurface.YOUTUBE_VIDEO))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SEARCH_WATCH, DetectedSurface.YOUTUBE_OTHER))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SEARCH_WATCH, DetectedSurface.YOUTUBE_HOME))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SEARCH_WATCH, DetectedSurface.YOUTUBE_SHORTS))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SEARCH_WATCH, DetectedSurface.YOUTUBE_SUBSCRIPTIONS))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SEARCH_WATCH, DetectedSurface.YOUTUBE_YOU))
        assertEquals(PolicyDecision.UNKNOWN_FAIL_OPEN, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SEARCH_WATCH, DetectedSurface.UNKNOWN))
    }

    @Test
    fun youtubeSubscriptions_allowsSubscribedOrUnknownCreatorContentButBlocksExplicitUnsubscribedContent() {
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SUBSCRIPTIONS, DetectedSurface.YOUTUBE_SUBSCRIPTIONS))
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SUBSCRIPTIONS, DetectedSurface.YOUTUBE_VIDEO))
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SUBSCRIPTIONS, DetectedSurface.YOUTUBE_SHORTS))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SUBSCRIPTIONS, DetectedSurface.YOUTUBE_UNSUBSCRIBED_VIDEO))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SUBSCRIPTIONS, DetectedSurface.YOUTUBE_UNSUBSCRIBED_SHORTS))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SUBSCRIPTIONS, DetectedSurface.YOUTUBE_OTHER))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SUBSCRIPTIONS, DetectedSurface.YOUTUBE_HOME))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SUBSCRIPTIONS, DetectedSurface.YOUTUBE_SEARCH))
        assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SUBSCRIPTIONS, DetectedSurface.YOUTUBE_YOU))
    }

    @Test
    fun youtubeShorts_intentionallyAllowsOnlyShortsAmongKnownSurfaces() {
        assertEquals(PolicyDecision.ALLOW, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SHORTS, DetectedSurface.YOUTUBE_SHORTS))
        listOf(
            DetectedSurface.YOUTUBE_HOME,
            DetectedSurface.YOUTUBE_SEARCH,
            DetectedSurface.YOUTUBE_VIDEO,
            DetectedSurface.YOUTUBE_SUBSCRIPTIONS,
            DetectedSurface.YOUTUBE_YOU,
            DetectedSurface.YOUTUBE_OTHER,
        ).forEach {
            assertEquals(PolicyDecision.INTERVENE, SessionPolicy.evaluate(TunnelTask.YOUTUBE_SHORTS, it))
        }
    }

    @Test
    fun tiktokSearchWatch_intervenesOnProfileButAllowsSearchAndInbox() {
        assertEquals(
            PolicyDecision.INTERVENE,
            SessionPolicy.evaluate(TunnelTask.TIKTOK_SEARCH_WATCH, DetectedSurface.TIKTOK_PROFILE),
        )
        assertEquals(
            PolicyDecision.ALLOW,
            SessionPolicy.evaluate(TunnelTask.TIKTOK_SEARCH_WATCH, DetectedSurface.TIKTOK_SEARCH),
        )
        assertEquals(
            PolicyDecision.ALLOW,
            SessionPolicy.evaluate(TunnelTask.TIKTOK_SEARCH_WATCH, DetectedSurface.TIKTOK_INBOX),
        )
    }

    @Test
    fun intentionalBrowse_allowsSelectedAppSurfaces() {
        val instagram = listOf(
            DetectedSurface.INSTAGRAM_MESSAGES,
            DetectedSurface.INSTAGRAM_EXPLORE,
            DetectedSurface.INSTAGRAM_REELS,
            DetectedSurface.INSTAGRAM_HOME,
            DetectedSurface.INSTAGRAM_PROFILE,
            DetectedSurface.INSTAGRAM_CREATE,
            DetectedSurface.INSTAGRAM_OTHER,
        )
        val youtube = listOf(
            DetectedSurface.YOUTUBE_SEARCH,
            DetectedSurface.YOUTUBE_VIDEO,
            DetectedSurface.YOUTUBE_SHORTS,
            DetectedSurface.YOUTUBE_HOME,
            DetectedSurface.YOUTUBE_SUBSCRIPTIONS,
            DetectedSurface.YOUTUBE_YOU,
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
