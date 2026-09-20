package com.example.tasktunnel.diagnostics

import com.example.tasktunnel.accessibility.TreeSnapshot
import com.example.tasktunnel.detector.YouTubeDetection
import java.util.Locale

/** A deterministic, bounded, content-free summary of one sanitized YouTube tree. */
object YouTubeFingerprint {
    fun format(snapshot: TreeSnapshot, detection: YouTubeDetection) = SanitizedFingerprint.format(
        snapshot = snapshot,
        appLabel = "YouTube",
        classification = detection.surface.name,
        confidence = "%.2f".format(Locale.ROOT, detection.confidence),
        classificationLabel = "detector",
    )
}
