package com.example.tasktunnel.detector

import com.example.tasktunnel.accessibility.YouTubeSubscriptionState
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeWatchSubscriptionGuardTest {
    @Test fun communityPostSubscribeDoesNotReplaceResolvedSubscribedCreatorAfterScroll() {
        val guard = YouTubeWatchSubscriptionGuard()
        guard.stabilize(video(YouTubeSubscriptionState.SUBSCRIBED))
        guard.onLongFormScrolled()

        val result = guard.stabilize(video(YouTubeSubscriptionState.NOT_SUBSCRIBED))

        assertEquals(YouTubeSubscriptionState.SUBSCRIBED, result.creatorSubscriptionState)
    }

    @Test fun scrolledWatchPageWithoutResolvedCreatorFailsOpen() {
        val guard = YouTubeWatchSubscriptionGuard()
        guard.onLongFormScrolled()

        val result = guard.stabilize(video(YouTubeSubscriptionState.NOT_SUBSCRIBED))

        assertEquals(null, result.creatorSubscriptionState)
    }

    @Test fun resetAllowsNextVideoToResolveItsOwnCreator() {
        val guard = YouTubeWatchSubscriptionGuard()
        guard.stabilize(video(YouTubeSubscriptionState.SUBSCRIBED))
        guard.onLongFormScrolled()
        guard.reset()

        val result = guard.stabilize(video(YouTubeSubscriptionState.NOT_SUBSCRIBED))

        assertEquals(YouTubeSubscriptionState.NOT_SUBSCRIBED, result.creatorSubscriptionState)
    }

    @Test fun leavingLongFormWatchClearsPinnedCreatorState() {
        val guard = YouTubeWatchSubscriptionGuard()
        guard.stabilize(video(YouTubeSubscriptionState.SUBSCRIBED))
        guard.onLongFormScrolled()
        guard.stabilize(
            YouTubeDetection(
                surface = YouTubeSurface.YOUTUBE_SUBSCRIPTIONS,
                confidence = 0.85,
                strongestSignals = emptyList(),
            ),
        )

        val result = guard.stabilize(video(YouTubeSubscriptionState.NOT_SUBSCRIBED))

        assertEquals(YouTubeSubscriptionState.NOT_SUBSCRIBED, result.creatorSubscriptionState)
    }

    private fun video(state: YouTubeSubscriptionState?) = YouTubeDetection(
        surface = YouTubeSurface.YOUTUBE_VIDEO,
        confidence = 0.9,
        strongestSignals = emptyList(),
        creatorSubscriptionState = state,
    )
}
