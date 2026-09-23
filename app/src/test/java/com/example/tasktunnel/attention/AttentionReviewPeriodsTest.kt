package com.example.tasktunnel.attention

import org.junit.Assert.assertEquals
import org.junit.Test

class AttentionReviewPeriodsTest {
    private val start = 1_000_000L
    private val period = CalendarPeriod(start, start + 60_000L)
    private val driftApps = listOf(AttentionApp.INSTAGRAM, AttentionApp.REDDIT, AttentionApp.YOUTUBE)

    @Test
    fun driftSummaryCountsCheckInRatherThanLaterDecision() {
        val events = listOf(
            drift(start + 1, "d1", AttentionSubtype.DRIFT_CHECK_IN, driftApps),
            drift(start + 2, "d1", AttentionSubtype.KEEP_GOING, driftApps),
        )

        assertEquals(1, ReviewPeriodSummary.from(events, period).driftEpisodes)
    }

    @Test
    fun incompleteLegacyDriftDoesNotInflateSummary() {
        val events = listOf(
            drift(start + 1, "legacy", AttentionSubtype.DRIFT_CHECK_IN, listOf(AttentionApp.INSTAGRAM)),
        )

        assertEquals(0, ReviewPeriodSummary.from(events, period).driftEpisodes)
    }

    private fun drift(
        time: Long,
        id: String,
        subtype: AttentionSubtype,
        apps: List<AttentionApp>,
    ) = AttentionEvent(
        timestampMillis = time,
        type = if (subtype == AttentionSubtype.DRIFT_CHECK_IN) AttentionEventType.INTERVENTION else AttentionEventType.DECISION,
        subtype = subtype,
        driftEpisodeId = id,
        relatedApps = apps,
    )
}
