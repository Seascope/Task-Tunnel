package com.example.tasktunnel.drift

import com.example.tasktunnel.tunnel.SupportedApp

/** Coordinates soft Drift check-ins while leaving Task Tunnel policy ownership elsewhere. */
class DriftCoordinator(
    selectedPackages: Set<String>,
    policy: DriftPolicy = DriftPolicy(),
    idFactory: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    private val detector = DriftDetector(selectedPackages, policy, idFactory)
    var foregroundPackage: String? = null
        private set

    val state: DriftDetectorState
        get() = detector.state

    fun observeForeground(packageName: String?, nowMillis: Long, activeTunnel: Boolean) {
        if (packageName.isNullOrBlank()) return
        foregroundPackage = packageName
        if (activeTunnel) {
            detector.clear()
            return
        }
        detector.observeForeground(packageName, nowMillis)
    }

    fun checkInCandidate(higherPriorityPromptVisible: Boolean): DriftEpisode? {
        if (higherPriorityPromptVisible) return null
        if (foregroundPackage !in detector.selectedPackages) return null
        return detector.pendingCheckIn
    }

    fun isSelected(packageName: String?): Boolean = packageName in detector.selectedPackages

    fun markCheckInShown(episodeId: String) = detector.markCheckInShown(episodeId)

    fun keepGoing(episodeId: String) = detector.acknowledge(episodeId)

    fun setAnIntention(episodeId: String): SupportedApp? {
        detector.acknowledge(episodeId)
        return SupportedApp.fromPackage(foregroundPackage)
    }

    fun updateSelectedPackages(packages: Set<String>) = detector.replaceSelectedPackages(packages)

    fun clear() {
        foregroundPackage = null
        detector.clear()
    }
}
