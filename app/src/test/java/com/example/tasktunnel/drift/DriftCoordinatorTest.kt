package com.example.tasktunnel.drift

import com.example.tasktunnel.tunnel.SupportedApp
import com.example.tasktunnel.tunnel.TunnelCoordinator
import com.example.tasktunnel.tunnel.TunnelPrompt
import com.example.tasktunnel.tunnel.TunnelTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriftCoordinatorTest {
    @Test
    fun setIntentionOnInstagramRoutesToInstagramPurposeGate() {
        val drift = coordinator()
        triggerEndingOn(drift, INSTAGRAM)
        val episode = requireNotNull(drift.checkInCandidate(false))
        val app = drift.setAnIntention(episode.id, 20)
        val tunnel = TunnelCoordinator(idFactory = { "tunnel" }).apply {
            observeForeground(INSTAGRAM, 20)
            dismissPurposeGate()
        }

        assertEquals(SupportedApp.INSTAGRAM, app)
        assertTrue(tunnel.requestPurposeGate(requireNotNull(app)))
        assertEquals(TunnelPrompt.PurposeGate(SupportedApp.INSTAGRAM), tunnel.state.prompt)
    }

    @Test
    fun setIntentionOnYouTubeRoutesToYouTubePurposeGate() {
        val drift = coordinator()
        triggerEndingOn(drift, YOUTUBE)
        val episode = requireNotNull(drift.checkInCandidate(false))

        assertEquals(SupportedApp.YOUTUBE, drift.setAnIntention(episode.id, 20))
    }

    @Test
    fun setIntentionOnUnsupportedSelectedAppCreatesNoGenericTunnel() {
        val drift = coordinator()
        triggerEndingOn(drift, REDDIT)
        val episode = requireNotNull(drift.checkInCandidate(false))

        assertNull(drift.setAnIntention(episode.id, 20))
        assertNull(drift.state.episode)
    }


    @Test
    fun dismissedShownCheckInRearmsFromCurrentAppInsteadOfGettingStuck() {
        val drift = coordinator()
        triggerEndingOn(drift, REDDIT)
        val episode = requireNotNull(drift.checkInCandidate(false))
        drift.markCheckInShown(episode.id)

        drift.dismissShownCheckIn(30)

        assertNull(drift.state.episode)
        assertEquals(listOf(REDDIT), drift.state.transitions.map { it.packageName })
    }

    @Test
    fun foregroundObservationsAreNotClearedByTunnelLifecycle() {
        val drift = coordinator()

        drift.observeForeground(INSTAGRAM, 0)
        drift.observeForeground(REDDIT, 10)
        drift.observeForeground(YOUTUBE, 20)

        assertTrue(drift.state.episode != null)
        assertTrue(drift.checkInCandidate(false) != null)
    }

    @Test
    fun higherPriorityTunnelPromptPreventsOverlayStacking() {
        val drift = coordinator()
        triggerEndingOn(drift, REDDIT)

        assertNull(drift.checkInCandidate(higherPriorityPromptVisible = true))
        assertTrue(drift.checkInCandidate(higherPriorityPromptVisible = false) != null)
    }

    @Test
    fun unrelatedCurrentAppDoesNotReceiveDriftCheckIn() {
        val drift = coordinator()
        triggerEndingOn(drift, REDDIT)

        drift.observeForeground(CHROME, 21)

        assertNull(drift.checkInCandidate(false))
    }

    @Test
    fun freshCoordinatorModelsServiceRestartByClearingTransientState() {
        val beforeRestart = coordinator()
        triggerEndingOn(beforeRestart, REDDIT)

        val afterRestart = coordinator()

        assertNull(afterRestart.state.episode)
        assertNull(afterRestart.checkInCandidate(false))
    }

    @Test
    fun startingTunnelDoesNotErasePendingDriftHistory() {
        val drift = coordinator()
        triggerEndingOn(drift, INSTAGRAM)
        val tunnel = TunnelCoordinator(idFactory = { "tunnel" }).apply {
            observeForeground(INSTAGRAM, 20)
            startSession(TunnelTask.INSTAGRAM_MESSAGES, 21)
        }

        drift.observeForeground(INSTAGRAM, 21)

        assertTrue(drift.state.episode != null)
        assertTrue(tunnel.state.activeSession != null)
    }

    @Test
    fun driftSetIntentionCanReplaceAnAwayTunnelWithCurrentAppPurposeGate() {
        val tunnel = TunnelCoordinator(idFactory = { "tunnel" }).apply {
            observeForeground(INSTAGRAM, 0)
            startSession(TunnelTask.INSTAGRAM_MESSAGES, 1)
            observeForeground(YOUTUBE, 10)
            dismissPurposeGate()
        }

        assertTrue(tunnel.state.activeSession != null)
        assertTrue(tunnel.requestPurposeGate(SupportedApp.YOUTUBE))
        assertNull(tunnel.state.activeSession)
        assertEquals(TunnelPrompt.PurposeGate(SupportedApp.YOUTUBE), tunnel.state.prompt)
    }

    @Test
    fun intentionInOneAppDoesNotEraseRecentDriftHistoryBeforeNextApp() {
        val drift = coordinator()

        drift.observeForeground(REDDIT, 0)
        drift.observeForeground(INSTAGRAM, 10)
        // Starting an intentional Instagram session is deliberately not represented as a Drift
        // reset. If the user closes it quickly and opens YouTube, the recent hopping still counts.
        drift.observeForeground(YOUTUBE, 20)

        assertEquals(listOf(REDDIT, INSTAGRAM, YOUTUBE), drift.checkInCandidate(false)?.involvedPackages)
    }

    private fun triggerEndingOn(coordinator: DriftCoordinator, last: String) {
        val firstTwo = SELECTED.filterNot { it == last }.take(2)
        coordinator.observeForeground(firstTwo[0], 0)
        coordinator.observeForeground(firstTwo[1], 10)
        coordinator.observeForeground(last, 20)
    }

    private fun coordinator() = DriftCoordinator(
        selectedPackages = SELECTED.toSet(),
        policy = DriftPolicy(
            distinctAppThreshold = 3,
            rollingWindowMillis = 60,
            quietResetMillis = 60,
        ),
        idFactory = { "episode-1" },
    )

    companion object {
        private const val INSTAGRAM = "com.instagram.android"
        private const val YOUTUBE = "com.google.android.youtube"
        private const val REDDIT = "com.reddit.frontpage"
        private const val CHROME = "com.android.chrome"
        private val SELECTED = listOf(INSTAGRAM, YOUTUBE, REDDIT)
    }
}
