package com.example.tasktunnel.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelCoordinatorTest {
    @Test
    fun purposeGateAppearsOncePerForegroundLaunch() {
        val coordinator = coordinator()
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 0)
        assertTrue(coordinator.state.prompt is TunnelPrompt.PurposeGate)

        coordinator.dismissPurposeGate()
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 1)
        assertNull(coordinator.state.prompt)

        coordinator.observeForeground("com.example.other", 2)
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 3)
        assertTrue(coordinator.state.prompt is TunnelPrompt.PurposeGate)
    }

    @Test
    fun allowAnywaySuppressesRepeatedSurfaceForTemporaryAllowance() {
        val coordinator = activeInstagramMessagesCoordinator()
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 10)
        assertTrue(coordinator.state.prompt is TunnelPrompt.Intervention)

        coordinator.allowAnyway(10)
        assertTrue(coordinator.state.activeSession?.overrideOccurred == true)
        coordinator.observeSurface(DetectedSurface.UNKNOWN, 11)
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 12)
        assertNull(coordinator.state.prompt)

        coordinator.observeSurface(DetectedSurface.INSTAGRAM_MESSAGES, 13)
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 14)
        assertNull(coordinator.state.prompt)
    }

    @Test
    fun endTunnelClearsSessionAndDoesNotImmediatelyReopenGate() {
        val coordinator = activeInstagramMessagesCoordinator()
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 10)
        coordinator.endSession()

        assertNull(coordinator.state.activeSession)
        assertNull(coordinator.state.prompt)
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 11)
        assertNull(coordinator.state.prompt)
    }

    @Test
    fun returnClearsInterventionAndSuppressesStaleEventsBriefly() {
        val coordinator = activeInstagramMessagesCoordinator()
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 10)
        assertTrue(coordinator.returnFromIntervention(10))
        assertNull(coordinator.state.prompt)

        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 11)
        assertNull(coordinator.state.prompt)
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 2_011)
        assertTrue(coordinator.state.prompt is TunnelPrompt.Intervention)
    }

    @Test
    fun activeTunnelIsReusedAfterLeavingAndReturning() {
        val coordinator = activeInstagramMessagesCoordinator()
        val session = coordinator.state.activeSession
        coordinator.observeForeground("com.example.other", 10)
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 20)

        assertEquals(session, coordinator.state.activeSession)
        assertFalse(coordinator.state.prompt is TunnelPrompt.PurposeGate)
    }


    @Test
    fun choosingSamePurposeAgainClearsTemporaryDetourState() {
        val coordinator = activeInstagramMessagesCoordinator()
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 10)
        coordinator.allowAnyway(10)

        assertTrue(coordinator.state.overrideScope != null)
        assertTrue(coordinator.state.detourAllow != null)
        assertTrue(coordinator.requestPurposeChange("session-1", 20))

        val gate = coordinator.state.prompt as TunnelPrompt.PurposeGate
        coordinator.startSession(
            TunnelTask.INSTAGRAM_MESSAGES,
            nowMillis = 20,
            intendedDurationMillis = gate.preservedDurationMillis,
            evaluateCurrentSurface = false,
        )

        assertEquals(TunnelTask.INSTAGRAM_MESSAGES, coordinator.state.activeSession?.task)
        assertNull(coordinator.state.overrideScope)
        assertNull(coordinator.state.detourAllow)
        assertNull(coordinator.state.prompt)
    }

    @Test
    fun purposeChangeKeepsOldSessionUntilUserChoosesAndPreservesRemainingTime() {
        val coordinator = coordinator().apply {
            observeForeground(SupportedApp.INSTAGRAM.packageName, 0)
            startSession(TunnelTask.INSTAGRAM_MESSAGES, 1_000, intendedDurationMillis = 10 * 60_000L)
        }
        val oldSession = coordinator.state.activeSession

        assertTrue(coordinator.requestPurposeChange("session-1", 4 * 60_000L))
        val gate = coordinator.state.prompt as TunnelPrompt.PurposeGate
        assertEquals(oldSession, coordinator.state.activeSession)
        assertEquals(oldSession?.id, gate.replacingSessionId)
        assertEquals(361_000L, gate.preservedDurationMillis)

        coordinator.startSession(
            TunnelTask.INSTAGRAM_SEARCH,
            nowMillis = 4 * 60_000L,
            intendedDurationMillis = gate.preservedDurationMillis,
            evaluateCurrentSurface = false,
        )
        assertEquals(TunnelTask.INSTAGRAM_SEARCH, coordinator.state.activeSession?.task)
        assertEquals(361_000L, coordinator.state.activeSession?.intendedDurationMillis)
    }

    @Test
    fun dismissingPurposeChangeKeepsOriginalTunnel() {
        val coordinator = activeInstagramMessagesCoordinator()
        val original = coordinator.state.activeSession
        assertTrue(coordinator.requestPurposeChange("session-1", 10))

        coordinator.dismissPurposeGate()

        assertEquals(original, coordinator.state.activeSession)
        assertNull(coordinator.state.prompt)
    }

    @Test
    fun notificationCanReplaceTimeLimitWithoutChangingPurpose() {
        val coordinator = activeInstagramMessagesCoordinator()
        assertTrue(coordinator.updateTimeLimit("session-1", 5_000, 5 * 60_000L))

        assertEquals(TunnelTask.INSTAGRAM_MESSAGES, coordinator.state.activeSession?.task)
        assertEquals(5_000L, coordinator.state.activeSession?.startedAtMillis)
        assertEquals(5 * 60_000L, coordinator.state.activeSession?.intendedDurationMillis)
    }

    private fun activeInstagramMessagesCoordinator(): TunnelCoordinator = coordinator().apply {
        observeForeground(SupportedApp.INSTAGRAM.packageName, 0)
        startSession(TunnelTask.INSTAGRAM_MESSAGES, 1)
    }

    private fun coordinator() = TunnelCoordinator(idFactory = { "session-1" })
}
