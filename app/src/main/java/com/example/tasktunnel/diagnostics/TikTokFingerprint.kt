package com.example.tasktunnel.diagnostics

import com.example.tasktunnel.accessibility.TreeSnapshot
import com.example.tasktunnel.detector.TikTokDetection
import com.example.tasktunnel.detector.TikTokSurfaceDetector

/** A bounded, content-free TikTok evidence summary with no surface classification. */
object TikTokFingerprint {
    const val TIKTOK_PACKAGE = TikTokSurfaceDetector.TIKTOK_PACKAGE

    fun format(
        snapshot: TreeSnapshot,
        versionName: String?,
        versionCode: Long?,
        detection: TikTokDetection? = null,
    ): String = buildString {
        appendLine("Task Tunnel sanitized TikTok fingerprint")
        appendLine("package=${snapshot.packageName}")
        appendLine("versionName=${versionName ?: "unknown"} versionCode=${versionCode ?: "unknown"}")
        appendLine("surface=${detection?.surface?.name ?: "UNCLASSIFIED"} confidence=${detection?.confidence ?: "n/a"}")
        detection?.strongestSignals?.forEach { appendLine("reason=$it") }
        append(SanitizedFingerprint.format(
            snapshot = snapshot,
            appLabel = "TikTok",
            classification = detection?.surface?.name ?: "UNCLASSIFIED",
            confidence = detection?.confidence?.toString() ?: "n/a",
            classificationLabel = "evidence",
        ).substringAfter("capturedAtMillis=${snapshot.capturedAtMillis}\n"))
    }
}