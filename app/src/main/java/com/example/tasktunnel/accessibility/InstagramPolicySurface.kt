package com.example.tasktunnel.accessibility

import com.example.tasktunnel.detector.InstagramDetection
import com.example.tasktunnel.detector.InstagramSurface
import com.example.tasktunnel.tunnel.DetectedSurface
import com.example.tasktunnel.tunnel.TunnelTask

/**
 * Keeps policy context separate from the surface recorded for Review/usage.
 *
 * Instagram uses the same Reels viewer both for the dedicated Reels tab and for a Reel opened
 * from Search/Explore. During a Search tunnel, the latter is a legitimate search result and must
 * remain watchable, while entering the dedicated Reels tab is still a detour.
 */
internal fun InstagramDetection.toPolicyTunnelSurface(task: TunnelTask?): DetectedSurface {
    val base = surface.toTunnelSurface()
    if (task == TunnelTask.INSTAGRAM_SEARCH &&
        surface == InstagramSurface.INSTAGRAM_REELS &&
        "active:clips_tab" !in strongestSignals
    ) {
        // INSTAGRAM_OTHER is deliberately compatible with Search policy. The actual REELS surface
        // is still recorded separately before policy evaluation, so Review/usage stays accurate.
        return DetectedSurface.INSTAGRAM_OTHER
    }
    return base
}

private fun InstagramSurface.toTunnelSurface(): DetectedSurface = when (this) {
    InstagramSurface.INSTAGRAM_MESSAGES -> DetectedSurface.INSTAGRAM_MESSAGES
    InstagramSurface.INSTAGRAM_EXPLORE -> DetectedSurface.INSTAGRAM_EXPLORE
    InstagramSurface.INSTAGRAM_REELS -> DetectedSurface.INSTAGRAM_REELS
    InstagramSurface.INSTAGRAM_HOME -> DetectedSurface.INSTAGRAM_HOME
    InstagramSurface.INSTAGRAM_PROFILE -> DetectedSurface.INSTAGRAM_PROFILE
    InstagramSurface.INSTAGRAM_CREATE -> DetectedSurface.INSTAGRAM_CREATE
    InstagramSurface.INSTAGRAM_OTHER -> DetectedSurface.INSTAGRAM_OTHER
    InstagramSurface.UNKNOWN -> DetectedSurface.UNKNOWN
}
