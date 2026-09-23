package com.example.tasktunnel.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackSenderTest {
    private val report = FeedbackStatusReport(
        appVersion = "1.0",
        androidVersion = "16 (API 36)",
        accessibilityEnabled = true,
        protectionEnabled = true,
        driftEnabled = false,
        notificationControlsEnabled = true,
    )

    @Test
    fun fieldsWithoutStatusContainOnlyFeedbackFields() {
        val draft = FeedbackDraft(
            category = FeedbackCategory.CONFUSING,
            note = "  I could not tell what Drift meant.  ",
            includeStatusReport = false,
        )

        val fields = with(FeedbackSender) { draft.fields(report) }

        assertEquals("I could not tell what Drift meant.", fields["message"])
        assertEquals(FeedbackCategory.CONFUSING.label, fields["category"])
        assertTrue(fields.containsKey("subject"))
        assertFalse(fields.containsKey("android_version"))
        assertFalse(fields.containsKey("accessibility"))
    }

    @Test
    fun fieldsWithStatusContainOnlySafeStatusFields() {
        val draft = FeedbackDraft(
            category = FeedbackCategory.WRONG_INTERRUPTION,
            note = "It interrupted me in Messages.",
            includeStatusReport = true,
        )

        val fields = with(FeedbackSender) { draft.fields(report) }

        assertEquals("1.0", fields["app_version"])
        assertEquals("on", fields["accessibility"])
        assertEquals("off", fields["drift"])
        assertFalse(fields.containsKey("activity_history"))
        assertFalse(fields.containsKey("accessibility_text"))
    }
}
