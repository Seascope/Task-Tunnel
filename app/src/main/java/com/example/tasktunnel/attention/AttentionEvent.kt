package com.example.tasktunnel.attention

import com.example.tasktunnel.tunnel.DetectedSurface
import com.example.tasktunnel.tunnel.SupportedApp
import com.example.tasktunnel.tunnel.TunnelTask

enum class AttentionEventType { INTENT, TRANSITION, INTERVENTION, DECISION }

enum class AttentionApp(val displayName: String, val packageName: String) {
    INSTAGRAM("Instagram", SupportedApp.INSTAGRAM.packageName),
    YOUTUBE("YouTube", SupportedApp.YOUTUBE.packageName),
    TIKTOK("TikTok", SupportedApp.TIKTOK.packageName),
    REDDIT("Reddit", "com.reddit.frontpage"),
    ;

    companion object {
        fun fromPackage(packageName: String?): AttentionApp? =
            values().firstOrNull { it.packageName == packageName }

        fun fromSupported(app: SupportedApp): AttentionApp = when (app) {
            SupportedApp.INSTAGRAM -> INSTAGRAM
            SupportedApp.YOUTUBE -> YOUTUBE
            SupportedApp.TIKTOK -> TIKTOK
        }
    }
}

enum class AttentionSubtype {
    PURPOSE_SELECTED,
    SURFACE_ENTERED,
    SURFACE_RETURNED,
    SURFACE_INTERVENTION,
    SESSION_EXPIRED,
    DRIFT_SEQUENCE,
    DRIFT_CHECK_IN,
    RETURN,
    ALLOW_ANYWAY,
    END_TUNNEL,
    KEEP_GOING,
    SET_INTENTION,
    EXPIRY_FINISH,
    EXPIRY_CONTINUE,
    EXPIRY_CHOOSE_ANOTHER,
    CHECK_IN_SHOWN,
    CHECK_IN_RETURN,
    CHECK_IN_CONTINUE,
    CHECK_IN_END,
    CHECK_IN_CHOOSE_ANOTHER,
}

enum class AttentionDecision {
    RETURN,
    ALLOW_ANYWAY,
    END_TUNNEL,
    KEEP_GOING,
    SET_INTENTION,
    FINISH,
    CONTINUE,
    CHOOSE_ANOTHER_PURPOSE,
    CHECK_IN_RETURN,
    CHECK_IN_CONTINUE,
    CHECK_IN_END,
    CHECK_IN_CHOOSE_ANOTHER,
}

data class AttentionEvent(
    val id: Long = 0,
    val timestampMillis: Long,
    val type: AttentionEventType,
    val subtype: AttentionSubtype,
    val app: AttentionApp? = null,
    val surface: DetectedSurface? = null,
    val task: TunnelTask? = null,
    val tunnelId: String? = null,
    val driftEpisodeId: String? = null,
    val decision: AttentionDecision? = null,
    val relatedApps: List<AttentionApp> = emptyList(),
    /** Exact app packages for Drift history. Unlike [relatedApps], this is not limited to known apps. */
    val relatedPackages: List<String> = emptyList(),
)
