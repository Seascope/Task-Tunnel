package com.example.tasktunnel.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelCoordinatorM31Test {
    @Test
    fun allowanceAppliesOnlyToSameSurfaceUntilItsExpiry() {
        val coordinator = activeInstagram(duration = null)
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 10)
        coordinator.allowAnyway(10)

        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 109)
        assertNull(coordinator.state.prompt)

        coordinator.observeSurface(DetectedSurface.INSTAGRAM_MESSAGES, 105)
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 109)
        assertNull(coordinator.state.prompt)

        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 110)
        assertTrue(coordinator.state.prompt is TunnelPrompt.Intervention)
    }

    @Test
    fun differentBlockedSurfaceDoesNotInheritAllowance() {
        val coordinator = activeInstagram(duration = null)
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 10)
        coordinator.allowAnyway(10)

        coordinator.observeSurface(DetectedSurface.INSTAGRAM_EXPLORE, 11)

        assertTrue(coordinator.state.prompt is TunnelPrompt.Intervention)
        assertEquals(
            DetectedSurface.INSTAGRAM_EXPLORE,
            (coordinator.state.prompt as TunnelPrompt.Intervention).surface,
        )
    }

    @Test
    fun allowanceBelongsToCurrentTunnelAndEndClearsIt() {
        var id = 0
        val coordinator = coordinator(idFactory = { "session-${++id}" })
        startInstagram(coordinator, duration = null)
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 10)
        coordinator.allowAnyway(10)
        val firstSessionId = coordinator.state.activeSession?.id

        coordinator.endSession()
        assertNull(coordinator.state.overrideScope)
        coordinator.observeForeground("com.example.other", 11)
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 12)
        coordinator.startSession(TunnelTask.INSTAGRAM_MESSAGES, 13)
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 14)

        assertFalse(firstSessionId == coordinator.state.activeSession?.id)
        assertTrue(coordinator.state.prompt is TunnelPrompt.Intervention)
    }

    @Test
    fun quickReturnKeepsSameTunnelWithoutPurposeGate() {
        val coordinator = activeInstagram(duration = null)
        val session = coordinator.state.activeSession

        coordinator.observeForeground("com.example.other", 10)
        assertNull(coordinator.state.prompt)
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 69)

        assertSame(session, coordinator.state.activeSession)
        assertFalse(coordinator.state.prompt is TunnelPrompt.PurposeGate)
    }

    @Test
    fun returnAfterGraceStalesTunnelAndShowsFreshGate() {
        val coordinator = activeInstagram(duration = null)
        coordinator.observeForeground("com.example.other", 10)

        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 70)

        assertNull(coordinator.state.activeSession)
        assertTrue(coordinator.state.prompt is TunnelPrompt.PurposeGate)
    }

    @Test
    fun explicitEndAndFreshCoordinatorHaveNoGraceSession() {
        val coordinator = activeInstagram(duration = null)
        coordinator.observeForeground("com.example.other", 10)
        coordinator.endSession()
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 11)

        assertNull(coordinator.state.activeSession)
        assertTrue(coordinator.state.prompt is TunnelPrompt.PurposeGate)
        assertNull(coordinator().state.activeSession)
    }

    @Test
    fun unrelatedAppNeverReceivesTunnelOverlay() {
        val coordinator = activeInstagram(duration = null)

        coordinator.observeForeground("com.example.other", 10)
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 11)

        assertNull(coordinator.state.prompt)
    }

    @Test
    fun noLimitSessionDoesNotExpire() {
        val coordinator = activeInstagram(duration = null)

        coordinator.advanceTime(Long.MAX_VALUE)

        assertEquals(TunnelStatus.ACTIVE, coordinator.state.activeSession?.status)
        assertNull(coordinator.state.prompt)
    }

    @Test
    fun foregroundExpiryOffersNeutralRedecision() {
        val coordinator = activeInstagram(duration = 100)

        coordinator.advanceTime(101)

        assertEquals(TunnelStatus.EXPIRED, coordinator.state.activeSession?.status)
        assertTrue(coordinator.state.prompt is TunnelPrompt.SessionExpired)
    }

    @Test
    fun finishEndsExpiredTunnel() {
        val coordinator = expiredInstagram()

        coordinator.endSession()

        assertNull(coordinator.state.activeSession)
        assertNull(coordinator.state.prompt)
    }

    @Test
    fun continueRestartsWindowWithoutImmediateExpiryLoop() {
        val coordinator = expiredInstagram()

        coordinator.continueExpiredSession(101)
        coordinator.advanceTime(101)

        assertEquals(TunnelStatus.ACTIVE, coordinator.state.activeSession?.status)
        assertEquals(201L, coordinator.state.activeSession?.expiresAtMillis)
        assertNull(coordinator.state.prompt)
    }

    @Test
    fun chooseAnotherPurposeReturnsToGate() {
        val coordinator = expiredInstagram()

        coordinator.chooseAnotherPurpose()

        assertNull(coordinator.state.activeSession)
        assertEquals(
            TunnelPrompt.PurposeGate(SupportedApp.INSTAGRAM),
            coordinator.state.prompt,
        )
    }

    @Test
    fun backgroundExpiryEndsSilentlyThenLaterOpenGetsFreshGate() {
        val coordinator = activeInstagram(duration = 100)
        coordinator.observeForeground("com.example.other", 50)

        coordinator.advanceTime(101)

        assertNull(coordinator.state.activeSession)
        assertNull(coordinator.state.prompt)
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 102)
        assertTrue(coordinator.state.prompt is TunnelPrompt.PurposeGate)
    }

    @Test
    fun nextDeadlineUsesAllowanceGraceAndSessionWindow() {
        val coordinator = activeInstagram(duration = 1_000)
        assertEquals(1_001L, coordinator.nextDeadlineMillis())

        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 10)
        coordinator.allowAnyway(10)
        assertEquals(110L, coordinator.nextDeadlineMillis())

        coordinator.observeForeground("com.example.other", 20)
        assertEquals(80L, coordinator.nextDeadlineMillis())
    }

    private fun expiredInstagram(): TunnelCoordinator = activeInstagram(duration = 100).apply {
        advanceTime(101)
    }

    private fun activeInstagram(duration: Long?): TunnelCoordinator = coordinator().apply {
        startInstagram(this, duration)
    }

    private fun startInstagram(coordinator: TunnelCoordinator, duration: Long?) {
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 0)
        coordinator.startSession(TunnelTask.INSTAGRAM_MESSAGES, 1, duration)
    }

    private fun coordinator(
        idFactory: () -> String = { "session-1" },
    ) = TunnelCoordinator(
        idFactory = idFactory,
        allowAnywayDurationMillis = 100,
        quickReturnGraceMillis = 60,
    )
}
