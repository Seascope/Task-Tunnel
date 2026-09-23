package com.example.tasktunnel.feedback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackDraftTest {
    private val report = FeedbackStatusReport(
        appVersion = "0.1",
        androidVersion = "16 (API 36)",
        accessibilityEnabled = true,
        protectionEnabled = true,
        driftEnabled = false,
        notificationControlsEnabled = true,
    )

    @Test
    fun bodyWithoutStatusDoesNotLeakStatusFields() {
        val body = FeedbackDraft(
            category = FeedbackCategory.CONFUSING,
            note = "The purpose picker was unclear.",
            includeStatusReport = false,
        ).body(report)

        assertTrue(body.contains("The purpose picker was unclear."))
        assertFalse(body.contains("Android:"))
        assertFalse(body.contains("Accessibility:"))
    }

    @Test
    fun bodyWithStatusIncludesOnlyExplicitStatusSection() {
        val body = FeedbackDraft(
            category = FeedbackCategory.WRONG_INTERRUPTION,
            note = "It interrupted me while I was replying.",
            includeStatusReport = true,
        ).body(report)

        assertTrue(body.contains("Task Tunnel status"))
        assertTrue(body.contains("Accessibility: on"))
        assertTrue(body.contains("Drift: off"))
        assertTrue(body.contains("No activity history or app content is included."))
    }
}
