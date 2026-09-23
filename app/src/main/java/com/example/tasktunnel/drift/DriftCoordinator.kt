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

    fun observeForeground(packageName: String?, nowMillis: Long) {
        if (packageName.isNullOrBlank()) return
        foregroundPackage = packageName
        detector.observeForeground(packageName, nowMillis)
    }

    fun checkInCandidate(higherPriorityPromptVisible: Boolean): DriftEpisode? {
        if (higherPriorityPromptVisible) return null
        if (foregroundPackage !in detector.selectedPackages) return null
        return detector.pendingCheckIn
    }

    fun isSelected(packageName: String?): Boolean = packageName in detector.selectedPackages

    fun markCheckInShown(episodeId: String) = detector.markCheckInShown(episodeId)

    fun rearmShownCheckIn() = detector.rearmShownCheckIn()

    /**
     * A shown accessibility overlay can disappear because the user leaves the selected Drift
     * pool or a higher-priority Tunnel interaction takes over. Treat that as an implicit
     * dismissal so a shown-but-unresolved episode cannot permanently suppress future check-ins.
     * No Attention decision is recorded because the user did not press a Drift action.
     */
    fun dismissShownCheckIn(nowMillis: Long) {
        val shownEpisode = detector.state.episode?.takeIf { it.checkInShown && !it.acknowledged } ?: return
        detector.acknowledge(shownEpisode.id, nowMillis)
    }

    fun keepGoing(episodeId: String, nowMillis: Long) = detector.acknowledge(episodeId, nowMillis)

    fun setAnIntention(episodeId: String, nowMillis: Long): SupportedApp? {
        detector.acknowledge(episodeId, nowMillis)
        return SupportedApp.fromPackage(foregroundPackage)
    }

    fun updateSelectedPackages(packages: Set<String>) {
        val normalized = packages.toSet()
        if (normalized == detector.selectedPackages) return
        detector.replaceSelectedPackages(normalized)
    }

    fun clear() {
        foregroundPackage = null
        detector.clear()
    }
}
