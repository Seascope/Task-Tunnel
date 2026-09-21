package com.example.tasktunnel.attention

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

private const val SEVEN_DAYS = 7
private const val MIN_TREND_DETOURS = 5
private const val MIN_TREND_SESSIONS = 5
private const val MIN_TREND_DRIFT_EPISODES = 3
private const val MIN_ABSOLUTE_CHANGE = 2
private const val MIN_RELATIVE_CHANGE = 0.15
private const val MIN_RETURN_POINT_CHANGE = 0.15

data class CalendarPeriod(val startMillis: Long, val endMillis: Long) {
    fun contains(timestampMillis: Long): Boolean = timestampMillis in startMillis until endMillis

    companion object {
        fun currentDay(nowMillis: Long, timeZone: TimeZone): CalendarPeriod {
            val start = Calendar.getInstance(timeZone).apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val end = start.clone() as Calendar
            end.add(Calendar.DAY_OF_YEAR, 1)
            return CalendarPeriod(start.timeInMillis, end.timeInMillis)
        }

        fun currentSevenDays(nowMillis: Long, timeZone: TimeZone): CalendarPeriod {
            val today = currentDay(nowMillis, timeZone)
            val start = Calendar.getInstance(timeZone).apply {
                timeInMillis = today.startMillis
                add(Calendar.DAY_OF_YEAR, -(SEVEN_DAYS - 1))
            }
            return CalendarPeriod(start.timeInMillis, nowMillis + 1)
        }

        fun previousSevenDays(nowMillis: Long, timeZone: TimeZone): CalendarPeriod {
            val current = currentSevenDays(nowMillis, timeZone)
            val start = Calendar.getInstance(timeZone).apply {
                timeInMillis = current.startMillis
                add(Calendar.DAY_OF_YEAR, -SEVEN_DAYS)
            }
            return CalendarPeriod(start.timeInMillis, current.startMillis)
        }
    }
}

enum class ReviewPeriod { TODAY, SEVEN_DAYS }

data class ReviewPeriodSummary(
    val intentionalSessions: Int,
    val detours: Int,
    val returned: Int,
    val continued: Int,
    val ended: Int,
    val driftEpisodes: Int,
) {
    val hasMeaningfulHistory: Boolean
        get() = intentionalSessions > 0 || detours > 0 || driftEpisodes > 0

    companion object {
        fun from(events: List<AttentionEvent>, period: CalendarPeriod): ReviewPeriodSummary {
            val periodEvents = events.filter { period.contains(it.timestampMillis) }
            val interventions = periodEvents.asSequence()
                .filter { it.subtype == AttentionSubtype.SURFACE_INTERVENTION }
                .distinctBy(::semanticEventKey)
                .toList()
            val interventionTunnelIds = interventions.mapNotNull(AttentionEvent::tunnelId).toSet()
            val decisions = periodEvents.asSequence()
                .filter { it.type == AttentionEventType.DECISION && it.tunnelId in interventionTunnelIds }
                .distinctBy(::semanticEventKey)
                .toList()
            return ReviewPeriodSummary(
                intentionalSessions = periodEvents.asSequence()
                    .filter { it.type == AttentionEventType.INTENT && it.subtype == AttentionSubtype.PURPOSE_SELECTED }
                    .mapNotNull(AttentionEvent::tunnelId)
                    .distinct()
                    .count(),
                detours = interventions.size,
                returned = decisions.count { it.subtype == AttentionSubtype.RETURN },
                continued = decisions.count { it.subtype == AttentionSubtype.ALLOW_ANYWAY },
                ended = decisions.count { it.subtype == AttentionSubtype.END_TUNNEL },
                driftEpisodes = periodEvents.asSequence()
                    .mapNotNull(AttentionEvent::driftEpisodeId)
                    .distinct()
                    .count(),
            )
        }
    }
}

data class ReviewDaySummary(
    val startMillis: Long,
    val label: String,
    val detours: Int,
    val returned: Int,
    val hasHistory: Boolean,
)

enum class ReviewTrendType {
    RETURN_BEHAVIOR,
    DETOUR_FREQUENCY,
    DRIFT_FREQUENCY,
    SESSION_ACTIVITY,
    STEADY,
}

data class ReviewTrend(
    val type: ReviewTrendType,
    val headline: String,
    val supportingText: String,
)

data class SevenDayReview(
    val summary: ReviewPeriodSummary,
    val days: List<ReviewDaySummary>,
    val trends: List<ReviewTrend>,
    val patterns: List<AttentionPattern>,
    val hasPreviousHistory: Boolean,
) {
    val hasMeaningfulHistory: Boolean
        get() = summary.hasMeaningfulHistory

    companion object {
        fun from(
            events: List<AttentionEvent>,
            nowMillis: Long,
            timeZone: TimeZone = TimeZone.getDefault(),
        ): SevenDayReview {
            val currentPeriod = CalendarPeriod.currentSevenDays(nowMillis, timeZone)
            val previousPeriod = CalendarPeriod.previousSevenDays(nowMillis, timeZone)
            val summary = ReviewPeriodSummary.from(events, currentPeriod)
            val previous = ReviewPeriodSummary.from(events, previousPeriod)
            val days = buildDays(events, currentPeriod, timeZone)
            val trends = ReviewTrendAnalyzer.analyze(summary, previous, events.any { previousPeriod.contains(it.timestampMillis) })
            val patterns = AttentionPatternAnalyzer.analyze(events, nowMillis, timeZone, historyDays = SEVEN_DAYS)
            return SevenDayReview(summary, days, trends, patterns, events.any { previousPeriod.contains(it.timestampMillis) })
        }

        private fun buildDays(
            events: List<AttentionEvent>,
            period: CalendarPeriod,
            timeZone: TimeZone,
        ): List<ReviewDaySummary> {
            val day = Calendar.getInstance(timeZone).apply { timeInMillis = period.startMillis }
            val formatter = SimpleDateFormat("EEE", Locale.US).apply { this.timeZone = timeZone }
            return (0 until SEVEN_DAYS).map {
                val start = day.timeInMillis
                val end = (day.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
                val summary = ReviewPeriodSummary.from(events, CalendarPeriod(start, end))
                val label = formatter.format(Date(start)).take(1)
                day.add(Calendar.DAY_OF_YEAR, 1)
                ReviewDaySummary(start, label, summary.detours, summary.returned, summary.hasMeaningfulHistory)
            }
        }
    }
}

object ReviewTrendAnalyzer {
    fun analyze(current: ReviewPeriodSummary, previous: ReviewPeriodSummary, previousHasHistory: Boolean): List<ReviewTrend> {
        if (!previousHasHistory) return emptyList()
        val candidates = buildList {
            returnTrend(current, previous)?.let(::add)
            detourTrend(current, previous)?.let(::add)
            driftTrend(current, previous)?.let(::add)
            sessionTrend(current, previous)?.let(::add)
        }
        return if (candidates.isEmpty()) listOf(
            ReviewTrend(
                ReviewTrendType.STEADY,
                "Recent behavior is fairly steady.",
                "Detours, returns, and Drift were similar to the previous 7 days.",
            ),
        ) else candidates.take(3)
    }

    private fun returnTrend(current: ReviewPeriodSummary, previous: ReviewPeriodSummary): ReviewTrend? {
        if (current.detours < MIN_TREND_DETOURS || previous.detours < MIN_TREND_DETOURS) return null
        val currentRate = current.returned.toDouble() / current.detours
        val previousRate = previous.returned.toDouble() / previous.detours
        if (abs(currentRate - previousRate) < MIN_RETURN_POINT_CHANGE) return null
        return if (currentRate > previousRate) {
            ReviewTrend(ReviewTrendType.RETURN_BEHAVIOR, "You returned more often after detours.", "${current.returned} of ${current.detours}, compared with ${previous.returned} of ${previous.detours} previously.")
        } else {
            ReviewTrend(ReviewTrendType.RETURN_BEHAVIOR, "You returned less often after detours.", "${current.returned} of ${current.detours}, compared with ${previous.returned} of ${previous.detours} previously.")
        }
    }

    private fun detourTrend(current: ReviewPeriodSummary, previous: ReviewPeriodSummary): ReviewTrend? {
        if (current.detours < MIN_TREND_DETOURS || previous.detours < MIN_TREND_DETOURS) return null
        val change = current.detours - previous.detours
        if (abs(change) < MIN_ABSOLUTE_CHANGE || abs(change).toDouble() / previous.detours < MIN_RELATIVE_CHANGE) return null
        return if (change < 0) ReviewTrend(ReviewTrendType.DETOUR_FREQUENCY, "Fewer detours than the previous 7 days.", "${current.detours} this period, down from ${previous.detours}.")
        else ReviewTrend(ReviewTrendType.DETOUR_FREQUENCY, "More detours than the previous 7 days.", "${current.detours} this period, up from ${previous.detours}.")
    }

    private fun driftTrend(current: ReviewPeriodSummary, previous: ReviewPeriodSummary): ReviewTrend? {
        if (current.driftEpisodes < MIN_TREND_DRIFT_EPISODES || previous.driftEpisodes < MIN_TREND_DRIFT_EPISODES) return null
        val change = current.driftEpisodes - previous.driftEpisodes
        if (abs(change) < MIN_ABSOLUTE_CHANGE || abs(change).toDouble() / previous.driftEpisodes < MIN_RELATIVE_CHANGE) return null
        return if (change < 0) ReviewTrend(ReviewTrendType.DRIFT_FREQUENCY, "Drift episodes were less frequent.", "${current.driftEpisodes} this period, compared with ${previous.driftEpisodes} previously.")
        else ReviewTrend(ReviewTrendType.DRIFT_FREQUENCY, "Drift episodes were more frequent.", "${current.driftEpisodes} this period, compared with ${previous.driftEpisodes} previously.")
    }

    private fun sessionTrend(current: ReviewPeriodSummary, previous: ReviewPeriodSummary): ReviewTrend? {
        if (current.intentionalSessions < MIN_TREND_SESSIONS || previous.intentionalSessions < MIN_TREND_SESSIONS) return null
        val change = current.intentionalSessions - previous.intentionalSessions
        if (abs(change) < MIN_ABSOLUTE_CHANGE || abs(change).toDouble() / previous.intentionalSessions < MIN_RELATIVE_CHANGE) return null
        return if (change > 0) ReviewTrend(ReviewTrendType.SESSION_ACTIVITY, "You used Task Tunnel more often this week.", "${current.intentionalSessions} intentional sessions, compared with ${previous.intentionalSessions} previously.")
        else ReviewTrend(ReviewTrendType.SESSION_ACTIVITY, "You used Task Tunnel less often this week.", "${current.intentionalSessions} intentional sessions, compared with ${previous.intentionalSessions} previously.")
    }
}

private fun semanticEventKey(event: AttentionEvent): String =
    if (event.id != 0L) "id:${event.id}" else listOf(event.timestampMillis, event.type, event.subtype, event.tunnelId, event.driftEpisodeId, event.surface, event.decision).joinToString("|")
