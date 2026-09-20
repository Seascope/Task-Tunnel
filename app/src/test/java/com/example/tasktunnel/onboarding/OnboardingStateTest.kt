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
    fun disclosureContinuesToVerification() {
        val progress = OnboardingFlow.next(OnboardingProgress(step = OnboardingStep.DISCLOSURE))
        assertEquals(OnboardingStep.VERIFY, progress.step)
    }

    @Test
    fun permissionDeclineCanContinueToConfiguration() {
        val progress = OnboardingFlow.next(OnboardingProgress(step = OnboardingStep.VERIFY))
        assertEquals(OnboardingStep.CONFIGURE, progress.step)
    }

    @Test
    fun completionPreventsOnboardingReentry() {
        val progress = OnboardingFlow.next(OnboardingProgress(step = OnboardingStep.CONFIGURE))
        assertTrue(progress.completed)
    }

    @Test
    fun setupLaterLeavesAppUsable() {
        assertTrue(OnboardingFlow.completeLater().completed)
    }
}
