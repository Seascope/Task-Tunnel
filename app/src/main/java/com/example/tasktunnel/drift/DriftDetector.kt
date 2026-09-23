package com.example.tasktunnel.drift

import java.util.UUID

data class DriftPolicy(
    val distinctAppThreshold: Int = DEFAULT_DISTINCT_APP_THRESHOLD,
    val rollingWindowMillis: Long = DEFAULT_ROLLING_WINDOW_MILLIS,
    val quietResetMillis: Long = DEFAULT_QUIET_RESET_MILLIS,
    val maxTransitions: Int = DEFAULT_MAX_TRANSITIONS,
) {
    init {
        require(distinctAppThreshold > 1)
        require(rollingWindowMillis > 0)
        require(quietResetMillis > 0)
        require(maxTransitions >= distinctAppThreshold)
    }

    companion object {
        const val DEFAULT_DISTINCT_APP_THRESHOLD = 3
        const val DEFAULT_ROLLING_WINDOW_MILLIS = 60_000L
        const val DEFAULT_QUIET_RESET_MILLIS = 60_000L
        const val DEFAULT_MAX_TRANSITIONS = 24
    }
}

data class DriftTransition(
    val packageName: String,
    val observedAtMillis: Long,
)

data class DriftEpisode(
    val id: String,
    val startedAtMillis: Long,
    val involvedPackages: List<String>,
    val latestTransitionAtMillis: Long,
    val checkInShown: Boolean = false,
    val acknowledged: Boolean = false,
)

data class DriftDetectorState(
    val transitions: List<DriftTransition> = emptyList(),
    val episode: DriftEpisode? = null,
    val lastObservedPackage: String? = null,
)

/** Pure, in-memory detector over foreground package transitions. */
class DriftDetector(
    selectedPackages: Set<String>,
    private val policy: DriftPolicy = DriftPolicy(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val ignoredPackages: Set<String> = DEFAULT_IGNORED_PACKAGES,
) {
    var selectedPackages: Set<String> = selectedPackages.toSet()
        private set

    var state: DriftDetectorState = DriftDetectorState()
        private set

    val pendingCheckIn: DriftEpisode?
        get() = state.episode?.takeIf { !it.checkInShown && !it.acknowledged }

    fun observeForeground(packageName: String?, nowMillis: Long) {
        if (packageName.isNullOrBlank() || packageName in ignoredPackages) return
        resetAfterQuietPeriod(nowMillis)

        val currentEpisode = state.episode

        val retained = state.transitions.filter {
            nowMillis - it.observedAtMillis <= policy.rollingWindowMillis
        }
        if (packageName == state.lastObservedPackage) {
            state = state.copy(
                transitions = retained,
                episode = currentEpisode,
            )
            return
        }

        if (packageName !in selectedPackages) {
            state = state.copy(
                transitions = retained,
                episode = currentEpisode,
                lastObservedPackage = packageName,
            )
            return
        }

        val transitions = (retained + DriftTransition(packageName, nowMillis))
            .takeLast(policy.maxTransitions)
        val existingEpisode = currentEpisode
        val episode = if (existingEpisode != null) {
            // Freeze the episode at the exact threshold-crossing sequence. If showing the
            // check-in is delayed by a higher-priority tunnel interaction, later app switches
            // belong to the unresolved context, not to the episode that originally triggered.
            existingEpisode
        } else {
            val distinctPackages = transitions.map { it.packageName }.distinct()
            if (distinctPackages.size >= policy.distinctAppThreshold) {
                DriftEpisode(
                    id = idFactory(),
                    startedAtMillis = transitions.first().observedAtMillis,
                    involvedPackages = distinctPackages,
                    latestTransitionAtMillis = nowMillis,
                )
            } else null
        }
        state = state.copy(
            transitions = transitions,
            episode = episode,
            lastObservedPackage = packageName,
        )
    }

    fun markCheckInShown(episodeId: String) {
        val episode = state.episode?.takeIf { it.id == episodeId } ?: return
        state = state.copy(episode = episode.copy(checkInShown = true))
    }

    fun acknowledge(episodeId: String, nowMillis: Long) {
        if (state.episode?.id != episodeId) return
        val baselinePackage = state.lastObservedPackage?.takeIf { it in selectedPackages }
        state = DriftDetectorState(
            transitions = baselinePackage?.let { listOf(DriftTransition(it, nowMillis)) }.orEmpty(),
            episode = null,
            lastObservedPackage = state.lastObservedPackage,
        )
    }

    fun replaceSelectedPackages(packages: Set<String>) {
        selectedPackages = packages.toSet()
        clear()
    }

    fun clear() {
        state = DriftDetectorState()
    }

    private fun resetAfterQuietPeriod(nowMillis: Long) {
        // Once a check-in is actually visible, keep its backing episode stable until the user
        // responds or the service explicitly dismisses it. Otherwise a quiet period can clear the
        // episode while the old overlay is still on screen, leaving stale buttons and allowing a
        // second episode to form behind that overlay.
        if (state.episode?.checkInShown == true) return
        val lastTransition = state.transitions.lastOrNull()?.observedAtMillis ?: return
        if (nowMillis - lastTransition >= policy.quietResetMillis) clear()
    }

    companion object {
        val DEFAULT_IGNORED_PACKAGES = setOf(
            "com.example.tasktunnel",
            "com.android.systemui",
        )
    }
}
