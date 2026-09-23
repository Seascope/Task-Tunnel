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

        coordinator.observeSurface(DetectedSurface.INSTAGRAM_HOME, 11)

        assertTrue(coordinator.state.prompt is TunnelPrompt.Intervention)
        assertEquals(
            DetectedSurface.INSTAGRAM_HOME,
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
    fun openEndedBrowseCheckInPausesDuringQuickAppSwitch() {
        val coordinator = TunnelCoordinator(
            idFactory = { "browse-session" },
            quickReturnGraceMillis = 500,
            browseCheckInIntervalMillis = 100,
        )
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 0)
        assertTrue(coordinator.startSession(TunnelTask.INSTAGRAM_BROWSE, 1))
        assertEquals(101L, coordinator.state.checkIn?.nextCheckAtMillis)

        coordinator.observeForeground("com.example.other", 50)
        coordinator.advanceTime(150)

        assertNull(coordinator.state.prompt)
        assertEquals(550L, coordinator.nextDeadlineMillis())
        assertEquals(101L, coordinator.state.checkIn?.nextCheckAtMillis)

        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 151)

        assertNull(coordinator.state.prompt)
        assertEquals(202L, coordinator.state.checkIn?.nextCheckAtMillis)
        coordinator.advanceTime(202)
        assertTrue(coordinator.state.prompt is TunnelPrompt.IntentionCheckIn)
    }

    @Test
    fun dueCheckInDoesNotReplacePurposeChangePicker() {
        val coordinator = TunnelCoordinator(
            idFactory = { "browse-session" },
            browseCheckInIntervalMillis = 100,
        )
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 0)
        assertTrue(coordinator.startSession(TunnelTask.INSTAGRAM_BROWSE, 1))
        assertTrue(coordinator.requestPurposeChange("browse-session", 90))

        coordinator.advanceTime(150)

        assertTrue(coordinator.state.prompt is TunnelPrompt.PurposeGate)
        assertEquals(101L, coordinator.state.checkIn?.nextCheckAtMillis)
        assertNull(coordinator.nextDeadlineMillis())

        coordinator.dismissPurposeGate()
        coordinator.advanceTime(150)
        assertTrue(coordinator.state.prompt is TunnelPrompt.IntentionCheckIn)
    }

    @Test
    fun disablingCheckInsDropsOldReminderAndReenableStartsFreshCadence() {
        val coordinator = TunnelCoordinator(
            idFactory = { "browse-session" },
            browseCheckInIntervalMillis = 100,
        )
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 0)
        assertTrue(coordinator.startSession(TunnelTask.INSTAGRAM_BROWSE, 1))

        coordinator.setCheckInsEnabled(false)
        assertNull(coordinator.state.checkIn)
        coordinator.advanceTime(500)
        assertNull(coordinator.state.prompt)

        coordinator.setCheckInsEnabled(true)
        coordinator.advanceTime(500)
        assertEquals(600L, coordinator.state.checkIn?.nextCheckAtMillis)
        assertNull(coordinator.state.prompt)
        coordinator.advanceTime(600)
        assertTrue(coordinator.state.prompt is TunnelPrompt.IntentionCheckIn)
    }

    @Test
    fun visibleBrowseCheckInSurvivesQuickAppSwitchWithoutConsumingAnotherSlot() {
        val coordinator = TunnelCoordinator(
            idFactory = { "browse-session" },
            quickReturnGraceMillis = 500,
            browseCheckInIntervalMillis = 100,
        )
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 0)
        assertTrue(coordinator.startSession(TunnelTask.INSTAGRAM_BROWSE, 1))

        coordinator.advanceTime(101)
        assertEquals(1, (coordinator.state.prompt as TunnelPrompt.IntentionCheckIn).shownCount)

        coordinator.observeForeground("com.example.other", 110)
        assertNull(coordinator.state.prompt)
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 130)

        val restored = coordinator.state.prompt as TunnelPrompt.IntentionCheckIn
        assertEquals(IntentionCheckInKind.OPEN_ENDED_BROWSE, restored.kind)
        assertEquals(1, restored.shownCount)
    }

    @Test
    fun detourRenewalSurvivesVisitingAllowedSurface() {
        val coordinator = TunnelCoordinator(
            idFactory = { "session-1" },
            allowAnywayDurationMillis = 100,
            detourRenewalExtensionMillis = 200,
        )
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 0)
        assertTrue(coordinator.startSession(TunnelTask.INSTAGRAM_MESSAGES, 1))
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 10)
        coordinator.allowAnyway(10)

        coordinator.observeSurface(DetectedSurface.INSTAGRAM_MESSAGES, 50)
        assertEquals(DetectedSurface.INSTAGRAM_REELS, coordinator.state.checkIn?.surface)
        coordinator.advanceTime(110)
        assertNull(coordinator.state.prompt)

        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 120)

        val prompt = coordinator.state.prompt as TunnelPrompt.IntentionCheckIn
        assertEquals(IntentionCheckInKind.DETOUR_RENEWAL, prompt.kind)
        assertEquals(DetectedSurface.INSTAGRAM_REELS, prompt.surface)
    }

    @Test
    fun detourRenewalPausesDuringQuickExternalAppSwitch() {
        val coordinator = TunnelCoordinator(
            idFactory = { "session-1" },
            allowAnywayDurationMillis = 100,
            quickReturnGraceMillis = 500,
            detourRenewalExtensionMillis = 200,
        )
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 0)
        assertTrue(coordinator.startSession(TunnelTask.INSTAGRAM_MESSAGES, 1))
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 10)
        coordinator.allowAnyway(10)

        coordinator.observeForeground("com.example.other", 50)
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 80)
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 80)

        assertEquals(140L, coordinator.state.checkIn?.nextCheckAtMillis)
        assertEquals(140L, coordinator.state.overrideScope?.expiresAtMillis)
        coordinator.advanceTime(139)
        assertNull(coordinator.state.prompt)
        coordinator.advanceTime(140)
        assertTrue(coordinator.state.prompt is TunnelPrompt.IntentionCheckIn)
    }

    @Test
    fun maxDetourCheckInsEndWithFiniteAllowanceThenRestoreIntervention() {
        val coordinator = TunnelCoordinator(
            idFactory = { "session-1" },
            allowAnywayDurationMillis = 100,
            detourRenewalExtensionMillis = 200,
        )
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 0)
        assertTrue(coordinator.startSession(TunnelTask.INSTAGRAM_MESSAGES, 1))
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 10)
        coordinator.allowAnyway(10)

        coordinator.advanceTime(110)
        coordinator.continueCheckIn(110)
        assertEquals(310L, coordinator.state.overrideScope?.expiresAtMillis)

        coordinator.advanceTime(310)
        coordinator.continueCheckIn(310)
        assertTrue(coordinator.state.checkIn?.suppressed == true)
        assertNull(coordinator.state.checkIn?.nextCheckAtMillis)
        assertEquals(510L, coordinator.state.overrideScope?.expiresAtMillis)

        coordinator.advanceTime(509)
        assertNull(coordinator.state.prompt)
        coordinator.advanceTime(510)
        assertTrue(coordinator.state.prompt is TunnelPrompt.Intervention)
    }

    @Test
    fun suppressedDetourCheckInsStaySuppressedAfterAnotherAllowance() {
        val coordinator = TunnelCoordinator(
            idFactory = { "session-1" },
            allowAnywayDurationMillis = 100,
            detourRenewalExtensionMillis = 200,
        )
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 0)
        assertTrue(coordinator.startSession(TunnelTask.INSTAGRAM_MESSAGES, 1))
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 10)
        coordinator.allowAnyway(10)

        coordinator.advanceTime(110)
        coordinator.continueCheckIn(110)
        coordinator.advanceTime(310)
        coordinator.continueCheckIn(310)
        coordinator.advanceTime(510)
        assertTrue(coordinator.state.prompt is TunnelPrompt.Intervention)

        coordinator.allowAnyway(510)
        assertTrue(coordinator.state.checkIn?.suppressed == true)
        assertNull(coordinator.state.checkIn?.nextCheckAtMillis)
        assertEquals(810L, coordinator.state.overrideScope?.expiresAtMillis)

        coordinator.advanceTime(810)
        assertTrue(coordinator.state.prompt is TunnelPrompt.Intervention)
    }

    @Test
    fun returningFromDetourCheckInCanSuppressStaleSurfaceDuringDirectedReturn() {
        val coordinator = TunnelCoordinator(
            idFactory = { "session-1" },
            allowAnywayDurationMillis = 100,
            detourRenewalExtensionMillis = 200,
        )
        coordinator.observeForeground(SupportedApp.INSTAGRAM.packageName, 0)
        assertTrue(coordinator.startSession(TunnelTask.INSTAGRAM_MESSAGES, 1))
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 10)
        coordinator.allowAnyway(10)
        coordinator.advanceTime(110)

        assertTrue(coordinator.returnFromCheckIn(110, addReturnCooldown = true))
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 111)
        assertNull(coordinator.state.prompt)
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 2_111)
        assertTrue(coordinator.state.prompt is TunnelPrompt.Intervention)
    }

    @Test
    fun allowanceExpiryReintervenesWithoutAnotherAccessibilityEvent() {
        val coordinator = activeInstagram(duration = null)
        coordinator.setCheckInsEnabled(false)
        coordinator.observeSurface(DetectedSurface.INSTAGRAM_REELS, 10)
        coordinator.allowAnyway(10)

        coordinator.advanceTime(110)

        val prompt = coordinator.state.prompt as TunnelPrompt.Intervention
        assertEquals(DetectedSurface.INSTAGRAM_REELS, prompt.surface)
        assertNull(coordinator.state.overrideScope)
    }

    @Test
    fun expiredReplacementPickerCannotStartGhostPurpose() {
        val coordinator = activeInstagram(duration = 100)
        assertTrue(coordinator.requestPurposeChange("session-1", 50))

        assertFalse(
            coordinator.startSession(
                TunnelTask.INSTAGRAM_SEARCH,
                nowMillis = 101,
                intendedDurationMillis = 50,
            ),
        )

        assertEquals(TunnelTask.INSTAGRAM_MESSAGES, coordinator.state.activeSession?.task)
        assertEquals(TunnelStatus.EXPIRED, coordinator.state.activeSession?.status)
        assertTrue(coordinator.state.prompt is TunnelPrompt.SessionExpired)
    }

    @Test
    fun changingAwayFromSubscriptionsNormalizesCreatorSpecificCachedSurface() {
        var id = 0
        val coordinator = TunnelCoordinator(idFactory = { "session-${++id}" })
        coordinator.observeForeground(SupportedApp.YOUTUBE.packageName, 0)
        assertTrue(coordinator.startSession(TunnelTask.YOUTUBE_SUBSCRIPTIONS, 1))
        coordinator.observeSurface(DetectedSurface.YOUTUBE_UNSUBSCRIBED_VIDEO, 10)
        assertTrue(coordinator.state.prompt is TunnelPrompt.Intervention)
        assertTrue(coordinator.requestPurposeChange("session-1", 20))

        assertTrue(coordinator.startSession(TunnelTask.YOUTUBE_SEARCH_WATCH, 20))

        assertEquals(DetectedSurface.YOUTUBE_VIDEO, coordinator.state.currentSurface)
        assertEquals(TunnelTask.YOUTUBE_SEARCH_WATCH, coordinator.state.activeSession?.task)
        assertNull(coordinator.state.prompt)
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
