package com.example.tasktunnel.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateTest {
    @Test
    fun firstRunStartsWithValueBeforePermission() {
        val progress = OnboardingProgress()
        assertFalse(progress.completed)
        assertEquals(OnboardingStep.VALUE, progress.step)
    }

    @Test
    fun flowMovesThroughRealSetupStages() {
        var progress = OnboardingProgress()
        progress = OnboardingFlow.next(progress)
        assertEquals(OnboardingStep.APPS, progress.step)
        progress = OnboardingFlow.next(progress)
        assertEquals(OnboardingStep.ACCESSIBILITY, progress.step)
        progress = OnboardingFlow.next(progress)
        assertEquals(OnboardingStep.DRIFT, progress.step)
        progress = OnboardingFlow.next(progress)
        assertEquals(OnboardingStep.TRY, progress.step)
        progress = OnboardingFlow.next(progress)
        assertEquals(OnboardingStep.READY, progress.step)
        progress = OnboardingFlow.next(progress)
        assertTrue(progress.completed)
    }

    @Test
    fun setupLaterLeavesAppUsable() {
        assertTrue(OnboardingFlow.completeLater().completed)
    }

    @Test
    fun checklistOnlyCompletesWhenCoreSetupActuallyWorks() {
        assertFalse(SetupChecklistState(true, true, false).complete)
        assertFalse(SetupChecklistState(true, false, true).complete)
        assertFalse(SetupChecklistState(false, true, true).complete)
        assertTrue(SetupChecklistState(true, true, true).complete)
    }
}
