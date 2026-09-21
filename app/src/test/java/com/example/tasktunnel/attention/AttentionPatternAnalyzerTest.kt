package com.example.tasktunnel.attention

import com.example.tasktunnel.tunnel.DetectedSurface
import com.example.tasktunnel.tunnel.TunnelTask
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionPatternAnalyzerTest {
    private val zone = TimeZone.getTimeZone("America/Los_Angeles")
    private val day = GregorianCalendar(zone).apply {
        set(2026, GregorianCalendar.SEPTEMBER, 21, 12, 0, 0)
        set(GregorianCalendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun commonDetourRequiresFiveEventsAndClearLeader() {
        val events = (0 until 3).map { intervention(it * 10L, "t$it", DetectedSurface.INSTAGRAM_REELS, it + 1) } +
            (3 until 5).map { intervention(it * 10L, "t$it", DetectedSurface.INSTAGRAM_EXPLORE, it + 1) }

        val pattern = patterns(events).single { it.type == AttentionPatternType.COMMON_DETOUR_SURFACE }

        assertEquals(DetectedSurface.INSTAGRAM_REELS, pattern.surface)
        assertEquals(3, pattern.evidenceCount)
        assertEquals(5, pattern.sampleSize)
        assertTrue(patterns(events.take(4)).none { it.type == AttentionPatternType.COMMON_DETOUR_SURFACE })
    }

    @Test
    fun recoveryPatternRequiresClearReturnTendency() {
        val events = (0 until 5).flatMap { index ->
            val timestamp = index * 20L
            buildList {
                add(intervention(timestamp, "t$index", DetectedSurface.INSTAGRAM_REELS, index + 1))
                if (index < 3) add(decision(timestamp + 1, "t$index", index + 20L))
            }
        }

        val pattern = patterns(events).single { it.type == AttentionPatternType.RECOVERY_AFTER_SURFACE }

        assertEquals(3, pattern.evidenceCount)
        assertEquals(5, pattern.sampleSize)
    }

    @Test
    fun recurringDriftPathRequiresThreeIdenticalOrderedEpisodes() {
        val repeated = (0 until 3).map { drift(it * 100L, "d$it", listOf(AttentionApp.INSTAGRAM, AttentionApp.YOUTUBE, AttentionApp.REDDIT)) }
        val different = drift(400, "different", listOf(AttentionApp.INSTAGRAM, AttentionApp.REDDIT, AttentionApp.YOUTUBE))

        val pattern = patterns(repeated + different).single { it.type == AttentionPatternType.RECURRING_DRIFT_PATH }

        assertEquals(3, pattern.evidenceCount)
        assertEquals(listOf(AttentionApp.INSTAGRAM, AttentionApp.YOUTUBE, AttentionApp.REDDIT), pattern.sequence)
    }

    @Test
    fun timeOfDayPatternUsesLocalTimeAndClearMajority() {
        val evening = listOf(18, 18, 19)
            .mapIndexed { index, hour -> driftAtHour(index, "e$index", hour) }
        val morning = listOf(8, 9).mapIndexed { index, hour -> driftAtHour(index + 3, "m$index", hour) }

        val pattern = patterns(evening + morning).single { it.type == AttentionPatternType.TIME_OF_DAY_DRIFT }

        assertEquals(DriftTimeBucket.EVENING, pattern.timeBucket)
        assertEquals(3, pattern.evidenceCount)
        assertTrue(patterns(evening.take(2) + morning.take(2)).none { it.type == AttentionPatternType.TIME_OF_DAY_DRIFT })
    }

    @Test
    fun patternsUseOnlyTheLastThirtyDaysAndTeaserPrefersRecentPattern() {
        val old = intervention(day - 31L * 24 * 60 * 60 * 1_000, "old", DetectedSurface.INSTAGRAM_REELS, 100)
        val todayEvents = (0 until 5).flatMap { index ->
            val timestamp = day + index * 20L
            listOf(
                intervention(timestamp, "today$index", DetectedSurface.INSTAGRAM_REELS, index + 1),
                decision(timestamp + 1, "today$index", index + 20L),
            )
        }
        val review = AttentionReview.from(listOf(old) + todayEvents, day + 11 * 60 * 60 * 1_000, zone)

        assertTrue(review.patterns.none { it.latestEvidenceMillis == old.timestampMillis })
        assertTrue(review.teaser?.headline?.contains("Reels") == true)
    }

    private fun patterns(events: List<AttentionEvent>) = AttentionPatternAnalyzer.analyze(
        events.map(::today),
        day + 24L * 60 * 60 * 1_000 - 1,
        zone,
    )

    private fun today(event: AttentionEvent) = event.copy(timestampMillis = day + event.timestampMillis)

    private fun intervention(time: Long, tunnelId: String, surface: DetectedSurface, id: Int) = AttentionEvent(
        id = id.toLong(),
        timestampMillis = time,
        type = AttentionEventType.INTERVENTION,
        subtype = AttentionSubtype.SURFACE_INTERVENTION,
        app = AttentionApp.INSTAGRAM,
        surface = surface,
        task = TunnelTask.INSTAGRAM_MESSAGES,
        tunnelId = tunnelId,
    )

    private fun decision(time: Long, tunnelId: String, id: Long) = AttentionEvent(
        id = id,
        timestampMillis = time,
        type = AttentionEventType.DECISION,
        subtype = AttentionSubtype.RETURN,
        surface = DetectedSurface.INSTAGRAM_REELS,
        decision = AttentionDecision.RETURN,
        tunnelId = tunnelId,
    )

    private fun drift(time: Long, id: String, apps: List<AttentionApp>) = AttentionEvent(
        timestampMillis = time,
        type = AttentionEventType.INTERVENTION,
        subtype = AttentionSubtype.DRIFT_CHECK_IN,
        driftEpisodeId = id,
        relatedApps = apps,
    )

    private fun driftAtHour(index: Int, id: String, hour: Int): AttentionEvent {
        val timestamp = GregorianCalendar(zone).apply {
            timeInMillis = day
            set(GregorianCalendar.HOUR_OF_DAY, hour)
            add(GregorianCalendar.MINUTE, index)
        }.timeInMillis
        return drift(timestamp - day, id, listOf(AttentionApp.INSTAGRAM, AttentionApp.YOUTUBE))
    }
}