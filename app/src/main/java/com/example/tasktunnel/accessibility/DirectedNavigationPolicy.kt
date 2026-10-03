package com.example.tasktunnel.accessibility

import com.example.tasktunnel.detector.InstagramSurface
import com.example.tasktunnel.detector.TikTokSurface
import com.example.tasktunnel.detector.YouTubeSurface
import com.example.tasktunnel.tunnel.TunnelTask

/**
 * Content-only decisions used by AccessibilityService directed routing.
 *
 * Starting a tunnel can intentionally accept a broader surface (for example a YouTube video for
 * Search/Watch). A user pressing "Return to Search", however, asked for the concrete named
 * destination. Keep those semantics separate so an ACTION_CLICK=true/no-op cannot be treated as a
 * successful jump merely because the old surface was still allowed by the purpose.
 */
internal object DirectedNavigationPolicy {
    fun instagramDestinationReached(task: TunnelTask, surface: InstagramSurface?): Boolean = when (task) {
        TunnelTask.INSTAGRAM_MESSAGES -> surface == InstagramSurface.INSTAGRAM_MESSAGES
        TunnelTask.INSTAGRAM_SEARCH -> surface == InstagramSurface.INSTAGRAM_EXPLORE
        TunnelTask.INSTAGRAM_POST -> surface == InstagramSurface.INSTAGRAM_CREATE
        else -> false
    }

    fun youtubeDestinationReached(
        task: TunnelTask,
        surface: YouTubeSurface?,
        hasBackNavigationChrome: Boolean,
    ): Boolean = when (task) {
        TunnelTask.YOUTUBE_SEARCH_WATCH -> surface == YouTubeSurface.YOUTUBE_SEARCH
        TunnelTask.YOUTUBE_SHORTS -> surface == YouTubeSurface.YOUTUBE_SHORTS
        TunnelTask.YOUTUBE_SUBSCRIPTIONS ->
            surface == YouTubeSurface.YOUTUBE_SUBSCRIPTIONS && !hasBackNavigationChrome
        else -> false
    }

    fun youtubeMayUnwindWithBack(surface: YouTubeSurface?): Boolean = when (surface) {
        YouTubeSurface.YOUTUBE_SEARCH,
        YouTubeSurface.YOUTUBE_VIDEO,
        -> true
        else -> false
    }

    fun tikTokDestinationReached(task: TunnelTask, surface: TikTokSurface?): Boolean = when (task) {
        TunnelTask.TIKTOK_SEARCH_WATCH -> surface == TikTokSurface.TIKTOK_SEARCH
        TunnelTask.TIKTOK_INBOX -> surface == TikTokSurface.TIKTOK_INBOX
        else -> false
    }

    /** Never press Back from a known top-level TikTok destination; that can background the app. */
    fun tikTokMayUnwindWithBack(surface: TikTokSurface?, mainShellVisible: Boolean): Boolean {
        if (mainShellVisible) return false
        return when (surface) {
            TikTokSurface.TIKTOK_FEED,
            TikTokSurface.TIKTOK_FRIENDS,
            TikTokSurface.TIKTOK_INBOX,
            TikTokSurface.TIKTOK_PROFILE,
            -> false
            TikTokSurface.TIKTOK_SEARCH,
            TikTokSurface.TIKTOK_OTHER,
            TikTokSurface.UNKNOWN,
            null,
            -> true
        }
    }
}
