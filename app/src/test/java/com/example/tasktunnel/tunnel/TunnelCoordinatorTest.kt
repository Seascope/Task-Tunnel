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
    fun allowAnywaySuppressesRepeatedSurfaceUntilAConfidentTransition() {
        val coordinator = activeInstagramMessagesCoordinator()
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 10)
        assertTrue(coordinator.state.prompt is TunnelPrompt.Intervention)

        coordinator.allowAnyway()
        assertTrue(coordinator.state.activeSession?.overrideOccurred == true)
        coordinator.observeSurface(DetectedSurface.UNKNOWN, 11)
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 12)
        assertNull(coordinator.state.prompt)

        coordinator.observeSurface(DetectedSurface.INSTAGRAM_MESSAGES, 13)
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 14)
        assertTrue(coordinator.state.prompt is TunnelPrompt.Intervention)
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

    private fun activeInstagramMessagesCoordinator(): TunnelCoordinator = coordinator().apply {
        observeForeground(SupportedApp.INSTAGRAM.packageName, 0)
        startSession(TunnelTask.INSTAGRAM_MESSAGES, 1)
    }

    private fun coordinator() = TunnelCoordinator(idFactory = { "session-1" })
}
