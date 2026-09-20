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
        val app = drift.setAnIntention(episode.id)
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

        assertEquals(SupportedApp.YOUTUBE, drift.setAnIntention(episode.id))
    }

    @Test
    fun setIntentionOnUnsupportedSelectedAppCreatesNoGenericTunnel() {
        val drift = coordinator()
        triggerEndingOn(drift, REDDIT)
        val episode = requireNotNull(drift.checkInCandidate(false))

        assertNull(drift.setAnIntention(episode.id))
        assertTrue(drift.state.episode?.acknowledged == true)
    }

    @Test
    fun activeTunnelAndQuickSwitchGraceSuppressDrift() {
        val drift = coordinator()

        drift.observeForeground(INSTAGRAM, 0, activeTunnel = true)
        drift.observeForeground(REDDIT, 10, activeTunnel = true)
        drift.observeForeground(YOUTUBE, 20, activeTunnel = true)

        assertNull(drift.state.episode)
        assertNull(drift.checkInCandidate(false))
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

        drift.observeForeground(CHROME, 21, activeTunnel = false)

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
    fun startingTunnelClearsPendingDriftAndKeepsTunnelPolicyIndependent() {
        val drift = coordinator()
        triggerEndingOn(drift, INSTAGRAM)
        val tunnel = TunnelCoordinator(idFactory = { "tunnel" }).apply {
            observeForeground(INSTAGRAM, 20)
            startSession(TunnelTask.INSTAGRAM_MESSAGES, 21)
        }

        drift.observeForeground(INSTAGRAM, 21, activeTunnel = tunnel.state.activeSession != null)

        assertNull(drift.state.episode)
        assertTrue(tunnel.state.activeSession != null)
    }

    private fun triggerEndingOn(coordinator: DriftCoordinator, last: String) {
        val firstTwo = SELECTED.filterNot { it == last }.take(2)
        coordinator.observeForeground(firstTwo[0], 0, activeTunnel = false)
        coordinator.observeForeground(firstTwo[1], 10, activeTunnel = false)
        coordinator.observeForeground(last, 20, activeTunnel = false)
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
