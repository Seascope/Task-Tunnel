package com.example.tasktunnel.detector

import com.example.tasktunnel.accessibility.YouTubeSubscriptionState

/**
 * Keeps creator-subscription state tied to the long-form video that was resolved before the user
 * scrolled into the rest of the watch page.
 *
 * YouTube can render unrelated Subscribe buttons in comments/community/recommendation content
 * below the video. Once the long-form watch page is scrolled, those controls must not replace the
 * already-resolved creator relationship. If the creator relationship was not resolved before the
 * scroll, fail open instead of guessing from a later Subscribe button.
 */
internal class YouTubeWatchSubscriptionGuard {
    private var resolvedLongFormState: YouTubeSubscriptionState? = null
    private var longFormContentScrolled = false

    fun reset() {
        resolvedLongFormState = null
        longFormContentScrolled = false
    }

    fun onLongFormScrolled() {
        longFormContentScrolled = true
    }

    fun stabilize(detection: YouTubeDetection): YouTubeDetection {
        return when (detection.surface) {
            YouTubeSurface.YOUTUBE_VIDEO -> {
                if (longFormContentScrolled) {
                    detection.copy(creatorSubscriptionState = resolvedLongFormState)
                } else {
                    detection.creatorSubscriptionState?.let { resolvedLongFormState = it }
                    detection
                }
            }

            // UNKNOWN can be a short-lived capture while the same watch page is settling.
            YouTubeSurface.UNKNOWN -> detection

            // Any confidently different YouTube surface ends the long-form watch context.
            else -> {
                reset()
                detection
            }
        }
    }
}
