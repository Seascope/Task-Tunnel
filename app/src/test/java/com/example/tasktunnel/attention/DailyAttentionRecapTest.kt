package com.example.tasktunnel.attention

import com.example.tasktunnel.tunnel.DetectedSurface
import com.example.tasktunnel.tunnel.TunnelTask
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyAttentionRecapTest {
    private val zone = TimeZone.getTimeZone("America/Los_Angeles")
    private val today = GregorianCalendar(zone).apply {
        set(2026, GregorianCalendar.SEPTEMBER, 21, 12, 0, 0)
        set(GregorianCalendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun emptyDayHasZeroRecap() {
        assertEquals(DailyAttentionRecap(), DailyAttentionRecap.from(emptyList(), today, zone))
        assertEquals("No detours interrupted today.", DailyAttentionRecap().recoveryInsight)
    }

    @Test
    fun multipleTunnelStartsCountDistinctSessions() {
        val events = listOf(
            intent(today + 1, "t1"),
            intent(today + 2, "t1"),
            intent(today + 3, "t2"),
        )

        assertEquals(2, recap(events).intentionalSessions)
    }

    @Test
    fun interventionDecisionsCountTheirOutcomes() {
        val events = listOf(
            intervention(today + 10, "t1", DetectedSurface.INSTAGRAM_REELS, 11),
            decision(today + 12, AttentionSubtype.RETURN, AttentionDecision.RETURN, "t1", 11),
            intervention(today + 20, "t2", DetectedSurface.INSTAGRAM_EXPLORE, 21),
            decision(today + 22, AttentionSubtype.ALLOW_ANYWAY, AttentionDecision.ALLOW_ANYWAY, "t2", 21),
            intervention(today + 30, "t3", DetectedSurface.YOUTUBE_SHORTS, 31),
            decision(today + 32, AttentionSubtype.END_TUNNEL, AttentionDecision.END_TUNNEL, "t3", null),
        )

        assertEquals(DailyAttentionRecap(0, 3, 1, 1, 1, 0), recap(events))
        assertEquals("1 of 3 detours ended with a return to your intention.", recap(events).recoveryInsight)
    }

    @Test
    fun repeatedInterventionEventForSameOccurrenceDoesNotInflateDetours() {
        val first = intervention(today + 10, "t1", DetectedSurface.INSTAGRAM_REELS, 0)
        val repeated = first.copy()

        assertEquals(1, recap(listOf(first, repeated)).detoursInterrupted)
    }

    @Test
    fun repeatedEventsInsideDriftEpisodeCountOnce() {
        val events = listOf(
            drift(today + 1, "d1", AttentionSubtype.DRIFT_SEQUENCE),
            drift(today + 2, "d1", AttentionSubtype.DRIFT_CHECK_IN),
            drift(today + 3, "d1", AttentionSubtype.KEEP_GOING),
            drift(today + 4, "d2", AttentionSubtype.DRIFT_CHECK_IN),
        )

        assertEquals(2, recap(events).driftEpisodes)
    }

    @Test
    fun driftDecisionWithoutTodayCheckInDoesNotCreateTodayEpisode() {
        val events = listOf(
            drift(today + 1, "yesterday-drift", AttentionSubtype.KEEP_GOING),
        )

        assertEquals(0, recap(events).driftEpisodes)
    }

    @Test
    fun yesterdayEventsAreExcluded() {
        val yesterday = LocalCalendarDay.forInstant(today, zone).startMillis - 1
        val events = listOf(intent(yesterday, "old"), drift(yesterday, "old-drift", AttentionSubtype.DRIFT_CHECK_IN))

        assertEquals(DailyAttentionRecap(), recap(events))
    }

    @Test
    fun localCalendarBoundaryUsesMidnightInConfiguredZone() {
        val day = LocalCalendarDay.forInstant(today, zone)
        val events = listOf(intent(day.startMillis - 1, "before"), intent(day.startMillis, "at-start"))

        assertEquals(1, recap(events).intentionalSessions)
        assertTrue(day.endMillis > day.startMillis)
    }

    @Test
    fun adaptiveFrictionVariantsDoNotChangeRecapSemantics() {
        val direct = intervention(today + 10, "t1", DetectedSurface.INSTAGRAM_REELS, 11)
        val intentRecall = direct.copy(id = 2, timestampMillis = today + 12)
        val shortPause = direct.copy(id = 3, timestampMillis = today + 13)

        assertEquals(3, recap(listOf(direct, intentRecall, shortPause)).detoursInterrupted)
    }

    private fun recap(events: List<AttentionEvent>) = DailyAttentionRecap.from(events, today, zone)

    private fun intent(time: Long, tunnelId: String) = AttentionEvent(
        timestampMillis = time,
        type = AttentionEventType.INTENT,
        subtype = AttentionSubtype.PURPOSE_SELECTED,
        task = TunnelTask.INSTAGRAM_MESSAGES,
        tunnelId = tunnelId,
    )

    private fun intervention(time: Long, tunnelId: String, surface: DetectedSurface, id: Long) = AttentionEvent(
        id = id,
        timestampMillis = time,
        type = AttentionEventType.INTERVENTION,
        subtype = AttentionSubtype.SURFACE_INTERVENTION,
        surface = surface,
        tunnelId = tunnelId,
    )

    private fun decision(
        time: Long,
        subtype: AttentionSubtype,
        decision: AttentionDecision,
        tunnelId: String,
        id: Long?,
    ) = AttentionEvent(
        id = id ?: 0,
        timestampMillis = time,
        type = AttentionEventType.DECISION,
        subtype = subtype,
        decision = decision,
        tunnelId = tunnelId,
    )

    private fun drift(time: Long, driftId: String, subtype: AttentionSubtype) = AttentionEvent(
        timestampMillis = time,
        type = when (subtype) {
            AttentionSubtype.DRIFT_SEQUENCE -> AttentionEventType.TRANSITION
            AttentionSubtype.DRIFT_CHECK_IN -> AttentionEventType.INTERVENTION
            else -> AttentionEventType.DECISION
        },
        subtype = subtype,
        driftEpisodeId = driftId,
        relatedApps = listOf(AttentionApp.INSTAGRAM, AttentionApp.REDDIT, AttentionApp.YOUTUBE),
    )
}