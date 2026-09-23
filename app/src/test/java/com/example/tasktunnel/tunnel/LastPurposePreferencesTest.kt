package com.example.tasktunnel.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastPurposePreferencesTest {
    @Test
    fun parseStoredTask_acceptsPurposeForRequestedApp() {
        assertEquals(
            TunnelTask.INSTAGRAM_MESSAGES,
            LastPurposePreferences.parseStoredTask("INSTAGRAM_MESSAGES", SupportedApp.INSTAGRAM),
        )
    }

    @Test
    fun parseStoredTask_ignoresPurposeBelongingToAnotherApp() {
        assertNull(
            LastPurposePreferences.parseStoredTask("YOUTUBE_SHORTS", SupportedApp.INSTAGRAM),
        )
    }

    @Test
    fun parseStoredTask_ignoresUnknownOrMissingValue() {
        assertNull(LastPurposePreferences.parseStoredTask("REMOVED_PURPOSE", SupportedApp.YOUTUBE))
        assertNull(LastPurposePreferences.parseStoredTask(null, SupportedApp.YOUTUBE))
    }

    @Test
    fun parseStoredDuration_acceptsFiniteDuration() {
        assertEquals(
            10 * 60_000L,
            LastPurposePreferences.parseStoredDuration(10 * 60_000L)?.durationMillis,
        )
    }

    @Test
    fun parseStoredDuration_acceptsNoLimitSentinel() {
        assertEquals(
            null,
            LastPurposePreferences.parseStoredDuration(-1L)?.durationMillis,
        )
    }

    @Test
    fun parseStoredDuration_ignoresMissingOrInvalidValues() {
        assertNull(LastPurposePreferences.parseStoredDuration(null))
        assertNull(LastPurposePreferences.parseStoredDuration(0L))
        assertNull(LastPurposePreferences.parseStoredDuration(-2L))
        assertNull(LastPurposePreferences.parseStoredDuration(Long.MIN_VALUE))
    }
}
