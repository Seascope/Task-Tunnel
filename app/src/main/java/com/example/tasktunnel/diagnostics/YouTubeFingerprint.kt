package com.example.tasktunnel.diagnostics

import com.example.tasktunnel.accessibility.TreeSnapshot
import com.example.tasktunnel.detector.YouTubeDetection
import java.util.Locale

/** A deterministic, bounded, content-free summary of one sanitized YouTube tree. */
object YouTubeFingerprint {
    fun format(snapshot: TreeSnapshot, detection: YouTubeDetection): String {
        val base = SanitizedFingerprint.format(
            snapshot = snapshot,
            appLabel = "YouTube",
            classification = detection.surface.name,
            confidence = "%.2f".format(Locale.ROOT, detection.confidence),
            classificationLabel = "detector",
        )
        val candidates = snapshot.nodes.asSequence()
            .filter { it.youtubeSubscriptionState != null }
            .map { node ->
                val top = node.visibleTopFraction?.let { "%.3f".format(Locale.ROOT, it) } ?: "?"
                val height = node.visibleHeightFraction?.let { "%.3f".format(Locale.ROOT, it) } ?: "?"
                "${node.youtubeSubscriptionState!!.name}@d${node.depth}/top=$top/h=$height/${node.className?.substringAfterLast('.') ?: "?"}"
            }
            .take(12)
            .toList()
        val state = detection.creatorSubscriptionState?.name ?: "UNKNOWN"
        val signals = detection.strongestSignals.joinToString(" | ").ifEmpty { "none" }
        val candidateText = candidates.joinToString(" | ").ifEmpty { "none" }
        return buildString {
            append(base)
            append("\ncreatorSubscriptionState=").append(state)
            append("\ndetectorSignals=").append(signals)
            append("\nsubscriptionCandidates(").append(candidates.size).append(")=").append(candidateText)
        }
    }
}
