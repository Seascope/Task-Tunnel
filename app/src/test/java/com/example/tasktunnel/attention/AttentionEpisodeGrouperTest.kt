package com.example.tasktunnel.attention

import com.example.tasktunnel.tunnel.TunnelTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionEpisodeGrouperTest {
    @Test
    fun sameTunnelIdGroupsIntoOneChronologicalTaskEpisode() {
        val events = listOf(
            event(300, AttentionEventType.DECISION, AttentionSubtype.RETURN, tunnelId = "t1"),
            event(100, AttentionEventType.INTENT, AttentionSubtype.PURPOSE_SELECTED, tunnelId = "t1"),
            event(200, AttentionEventType.TRANSITION, AttentionSubtype.SURFACE_ENTERED, tunnelId = "t1"),
        )

        val episodes = AttentionEpisodeGrouper.group(events)

        assertEquals(1, episodes.size)
        assertEquals(AttentionEpisodeType.TASK_TUNNEL, episodes.single().type)
        assertEquals(listOf(100L, 200L, 300L), episodes.single().events.map { it.timestampMillis })
    }

    @Test
    fun driftEventsWithSameEpisodeIdGroupTogetherAndPreserveAppSequence() {
        val events = listOf(
            event(
                100,
                AttentionEventType.TRANSITION,
                AttentionSubtype.DRIFT_SEQUENCE,
                driftId = "d1",
                apps = listOf(AttentionApp.INSTAGRAM, AttentionApp.REDDIT, AttentionApp.YOUTUBE),
            ),
            event(200, AttentionEventType.INTERVENTION, AttentionSubtype.DRIFT_CHECK_IN, driftId = "d1"),
        )

        val episode = AttentionEpisodeGrouper.group(events).single()

        assertEquals(AttentionEpisodeType.DRIFT, episode.type)
        assertEquals(listOf(AttentionApp.INSTAGRAM, AttentionApp.REDDIT, AttentionApp.YOUTUBE), episode.involvedApps)
    }

    @Test
    fun unrelatedTunnelAndDriftEpisodesRemainSeparate() {
        val events = listOf(
            event(100, AttentionEventType.INTENT, AttentionSubtype.PURPOSE_SELECTED, tunnelId = "t1"),
            event(200, AttentionEventType.INTENT, AttentionSubtype.PURPOSE_SELECTED, tunnelId = "t2"),
            event(300, AttentionEventType.INTERVENTION, AttentionSubtype.DRIFT_CHECK_IN, driftId = "d1"),
        )

        assertEquals(3, AttentionEpisodeGrouper.group(events).size)
    }

    @Test
    fun driftWeeklyMetricCountsDistinctRecentEpisodesAndEmptyHistoryIsZero() {
        val now = 10L * 24 * 60 * 60 * 1_000
        val recent = now - 1_000
        val old = now - 8L * 24 * 60 * 60 * 1_000
        val events = listOf(
            event(recent, AttentionEventType.INTERVENTION, AttentionSubtype.DRIFT_CHECK_IN, driftId = "d1"),
            event(recent + 1, AttentionEventType.INTERVENTION, AttentionSubtype.DRIFT_CHECK_IN, driftId = "d1"),
            event(recent + 2, AttentionEventType.INTERVENTION, AttentionSubtype.DRIFT_CHECK_IN, driftId = "d2"),
            event(old, AttentionEventType.INTERVENTION, AttentionSubtype.DRIFT_CHECK_IN, driftId = "old"),
        )

        assertEquals(2, AttentionMetrics.from(events, now).driftEpisodesLastSevenDays)
        assertEquals(0, AttentionMetrics.from(emptyList(), now).driftEpisodesLastSevenDays)
        assertTrue(AttentionEpisodeGrouper.group(emptyList()).isEmpty())
    }

    private fun event(
        timestamp: Long,
        type: AttentionEventType,
        subtype: AttentionSubtype,
        tunnelId: String? = null,
        driftId: String? = null,
        apps: List<AttentionApp> = emptyList(),
    ) = AttentionEvent(
        timestampMillis = timestamp,
        type = type,
        subtype = subtype,
        app = AttentionApp.INSTAGRAM,
        task = tunnelId?.let { TunnelTask.INSTAGRAM_MESSAGES },
        tunnelId = tunnelId,
        driftEpisodeId = driftId,
        relatedApps = apps,
    )
}

