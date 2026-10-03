package com.example.tasktunnel.accessibility

import com.example.tasktunnel.detector.InstagramSurface
import com.example.tasktunnel.detector.TikTokSurface
import com.example.tasktunnel.detector.YouTubeSurface
import com.example.tasktunnel.tunnel.TunnelTask
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectedNavigationPolicyTest {
    @Test fun instagramReturnRequiresTheNamedDestination() {
        assertTrue(DirectedNavigationPolicy.instagramDestinationReached(TunnelTask.INSTAGRAM_SEARCH, InstagramSurface.INSTAGRAM_EXPLORE))
        assertFalse(DirectedNavigationPolicy.instagramDestinationReached(TunnelTask.INSTAGRAM_SEARCH, InstagramSurface.INSTAGRAM_REELS))
        assertTrue(DirectedNavigationPolicy.instagramDestinationReached(TunnelTask.INSTAGRAM_MESSAGES, InstagramSurface.INSTAGRAM_MESSAGES))
    }

    @Test fun youtubeSearchReturnDoesNotAcceptAnOldVideo() {
        assertTrue(DirectedNavigationPolicy.youtubeDestinationReached(TunnelTask.YOUTUBE_SEARCH_WATCH, YouTubeSurface.YOUTUBE_SEARCH, false))
        assertFalse(DirectedNavigationPolicy.youtubeDestinationReached(TunnelTask.YOUTUBE_SEARCH_WATCH, YouTubeSurface.YOUTUBE_VIDEO, false))
    }

    @Test fun youtubeSubscriptionsRequiresTheActualFeedRoot() {
        assertTrue(DirectedNavigationPolicy.youtubeDestinationReached(TunnelTask.YOUTUBE_SUBSCRIPTIONS, YouTubeSurface.YOUTUBE_SUBSCRIPTIONS, false))
        assertFalse(DirectedNavigationPolicy.youtubeDestinationReached(TunnelTask.YOUTUBE_SUBSCRIPTIONS, YouTubeSurface.YOUTUBE_SUBSCRIPTIONS, true))
    }

    @Test fun youtubeBackRecoveryOnlyUnwindsNestedSearchOrWatch() {
        assertTrue(DirectedNavigationPolicy.youtubeMayUnwindWithBack(YouTubeSurface.YOUTUBE_VIDEO))
        assertTrue(DirectedNavigationPolicy.youtubeMayUnwindWithBack(YouTubeSurface.YOUTUBE_SEARCH))
        assertFalse(DirectedNavigationPolicy.youtubeMayUnwindWithBack(YouTubeSurface.YOUTUBE_HOME))
        assertFalse(DirectedNavigationPolicy.youtubeMayUnwindWithBack(YouTubeSurface.YOUTUBE_SHORTS))
    }

    @Test fun tikTokNeverBacksOutOfKnownMainShell() {
        assertFalse(DirectedNavigationPolicy.tikTokMayUnwindWithBack(TikTokSurface.TIKTOK_FEED, true))
        assertFalse(DirectedNavigationPolicy.tikTokMayUnwindWithBack(TikTokSurface.TIKTOK_PROFILE, false))
        assertTrue(DirectedNavigationPolicy.tikTokMayUnwindWithBack(TikTokSurface.TIKTOK_SEARCH, false))
    }
}
