package com.example.tasktunnel.attention

import com.example.tasktunnel.tunnel.DetectedSurface
import com.example.tasktunnel.tunnel.TunnelTask
import java.util.Calendar
import java.util.TimeZone

private const val PATTERN_HISTORY_DAYS = 30
private const val TEASER_RECENCY_DAYS = 7
private const val MIN_SURFACE_OBSERVATIONS = 5
private const val MIN_DRIFT_PATH_OBSERVATIONS = 3
private const val CLEAR_TENDENCY_NUMERATOR = 3
private const val CLEAR_TENDENCY_DENOMINATOR = 5

enum class AttentionPatternType {
    COMMON_DETOUR_SURFACE,
    RECOVERY_AFTER_SURFACE,
    RECURRING_DRIFT_PATH,
    TIME_OF_DAY_DRIFT,
}

enum class DriftTimeBucket(val label: String) {
    MORNING("morning"),
    AFTERNOON("afternoon"),
    EVENING("evening"),
    LATE_NIGHT("late night"),
}

data class AttentionPattern(
    val type: AttentionPatternType,
    val app: AttentionApp? = null,
    val task: TunnelTask? = null,
    val surface: DetectedSurface? = null,
    val sequence: List<AttentionApp> = emptyList(),
    val timeBucket: DriftTimeBucket? = null,
    val evidenceCount: Int,
    val sampleSize: Int,
    val latestEvidenceMillis: Long,
)

data class ReviewTeaser(
    val headline: String,
    val supportingText: String,
)

data class AttentionReview(
    val recap: DailyAttentionRecap,
    val patterns: List<AttentionPattern>,
    val teaser: ReviewTeaser?,
) {
    val hasMeaningfulData: Boolean
        get() = recap.hasMeaningfulData || patterns.isNotEmpty()

    companion object {
        fun from(
            events: List<AttentionEvent>,
            nowMillis: Long,
            timeZone: TimeZone = TimeZone.getDefault(),
        ): AttentionReview {
            val recap = DailyAttentionRecap.from(events, nowMillis, timeZone)
            val patterns = AttentionPatternAnalyzer.analyze(events, nowMillis, timeZone)
            return AttentionReview(recap, patterns, ReviewTeaserFactory.create(recap, patterns, nowMillis, timeZone))
        }
    }
}

object AttentionPatternAnalyzer {
    fun analyze(
        events: List<AttentionEvent>,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): List<AttentionPattern> {
        val window = PatternWindow.from(nowMillis, timeZone)
        val recentEvents = events.filter { it.timestampMillis in window.startMillis..nowMillis }
        val recentEpisodes = AttentionEpisodeGrouper.group(recentEvents)
        val patterns = buildList {
            addAll(commonDetourPatterns(recentEvents))
            addAll(recoveryPatterns(recentEvents))
            addAll(recurringDriftPatterns(recentEpisodes))
            addAll(timeOfDayPatterns(recentEpisodes, timeZone))
        }
        return patterns
            .sortedWith(compareByDescending<AttentionPattern> { it.evidenceCount * 100L + it.sampleSize }
                .thenByDescending { it.latestEvidenceMillis })
            .take(3)
    }

    private fun commonDetourPatterns(events: List<AttentionEvent>): List<AttentionPattern> {
        data class Context(val app: AttentionApp, val task: TunnelTask?)
        data class Entry(val context: Context, val surface: DetectedSurface, val event: AttentionEvent)
        return events
            .interventions()
            .mapNotNull { event ->
                val app = event.app ?: return@mapNotNull null
                val surface = event.surface ?: return@mapNotNull null
                Entry(Context(app, event.task), surface, event)
            }
            .groupBy { it.context }
            .mapNotNull { (context, entries) ->
                val total = entries.size
                if (total < MIN_SURFACE_OBSERVATIONS) return@mapNotNull null
                val bySurface = entries.groupBy { it.surface }
                val ordered = bySurface.entries.sortedWith(
                    compareByDescending<Map.Entry<DetectedSurface, List<Entry>>> { it.value.size }
                        .thenByDescending { group -> group.value.maxOf { it.event.timestampMillis } },
                )
                val leader = ordered.firstOrNull() ?: return@mapNotNull null
                if (ordered.size > 1 && leader.value.size == ordered[1].value.size) return@mapNotNull null
                if (leader.value.size * 5 < total * CLEAR_TENDENCY_NUMERATOR) return@mapNotNull null
                AttentionPattern(
                    type = AttentionPatternType.COMMON_DETOUR_SURFACE,
                    app = context.app,
                    task = context.task,
                    surface = leader.key,
                    evidenceCount = leader.value.size,
                    sampleSize = total,
                    latestEvidenceMillis = leader.value.maxOf { it.event.timestampMillis },
                )
            }
    }

    private fun recoveryPatterns(events: List<AttentionEvent>): List<AttentionPattern> {
        data class Context(val app: AttentionApp, val task: TunnelTask?, val surface: DetectedSurface)
        val interventions = events.interventions()
        val decisions = events
            .filter { it.type == AttentionEventType.DECISION }
            .distinctBy(::eventKey)
            .sortedBy(AttentionEvent::timestampMillis)
        return interventions
            .mapNotNull { event ->
                val app = event.app ?: return@mapNotNull null
                val surface = event.surface ?: return@mapNotNull null
                Context(app, event.task, surface) to event
            }
            .groupBy { it.first }
            .mapNotNull { (context, entries) ->
                if (entries.size < MIN_SURFACE_OBSERVATIONS) return@mapNotNull null
                val returned = entries.count { (_, intervention) ->
                    val nextIntervention = interventions
                        .filter { it.tunnelId == intervention.tunnelId && it.timestampMillis > intervention.timestampMillis }
                        .minOfOrNull(AttentionEvent::timestampMillis)
                    decisions.any { decision ->
                        decision.tunnelId == intervention.tunnelId &&
                            decision.timestampMillis >= intervention.timestampMillis &&
                            (nextIntervention == null || decision.timestampMillis < nextIntervention) &&
                            decision.subtype == AttentionSubtype.RETURN &&
                            decision.surface == context.surface
                    }
                }
                if (returned * CLEAR_TENDENCY_DENOMINATOR < entries.size * CLEAR_TENDENCY_NUMERATOR) {
                    return@mapNotNull null
                }
                AttentionPattern(
                    type = AttentionPatternType.RECOVERY_AFTER_SURFACE,
                    app = context.app,
                    task = context.task,
                    surface = context.surface,
                    evidenceCount = returned,
                    sampleSize = entries.size,
                    latestEvidenceMillis = entries.maxOf { it.second.timestampMillis },
                )
            }
    }

    private fun recurringDriftPatterns(episodes: List<AttentionEpisode>): List<AttentionPattern> = episodes
        .asSequence()
        .filter { it.type == AttentionEpisodeType.DRIFT && it.involvedApps.size >= 2 }
        .groupBy { it.involvedApps }
        .mapNotNull { (sequence, matching) ->
            if (matching.size < MIN_DRIFT_PATH_OBSERVATIONS) return@mapNotNull null
            AttentionPattern(
                type = AttentionPatternType.RECURRING_DRIFT_PATH,
                sequence = sequence,
                evidenceCount = matching.size,
                sampleSize = matching.size,
                latestEvidenceMillis = matching.maxOf(AttentionEpisode::startedAtMillis),
            )
        }

    private fun timeOfDayPatterns(episodes: List<AttentionEpisode>, timeZone: TimeZone): List<AttentionPattern> {
        val driftEpisodes = episodes.filter { it.type == AttentionEpisodeType.DRIFT }
        if (driftEpisodes.size < MIN_SURFACE_OBSERVATIONS) return emptyList()
        val buckets = driftEpisodes.groupBy { bucketFor(it.startedAtMillis, timeZone) }
        val ordered = buckets.entries.sortedWith(
            compareByDescending<Map.Entry<DriftTimeBucket, List<AttentionEpisode>>> { it.value.size }
                .thenByDescending { entry -> entry.value.maxOf(AttentionEpisode::startedAtMillis) },
        )
        val leader = ordered.firstOrNull() ?: return emptyList()
        if (ordered.size > 1 && leader.value.size == ordered[1].value.size) return emptyList()
        if (leader.value.size * CLEAR_TENDENCY_DENOMINATOR < driftEpisodes.size * CLEAR_TENDENCY_NUMERATOR) return emptyList()
        return listOf(
            AttentionPattern(
                type = AttentionPatternType.TIME_OF_DAY_DRIFT,
                timeBucket = leader.key,
                evidenceCount = leader.value.size,
                sampleSize = driftEpisodes.size,
                latestEvidenceMillis = leader.value.maxOf(AttentionEpisode::startedAtMillis),
            ),
        )
    }

    private fun bucketFor(timestampMillis: Long, timeZone: TimeZone): DriftTimeBucket {
        val hour = Calendar.getInstance(timeZone).apply { timeInMillis = timestampMillis }.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> DriftTimeBucket.MORNING
            in 12..16 -> DriftTimeBucket.AFTERNOON
            in 17..21 -> DriftTimeBucket.EVENING
            else -> DriftTimeBucket.LATE_NIGHT
        }
    }

    private fun List<AttentionEvent>.interventions() = asSequence()
        .filter { it.subtype == AttentionSubtype.SURFACE_INTERVENTION }
        .distinctBy(::eventKey)
        .toList()

    private fun eventKey(event: AttentionEvent): String =
        if (event.id != 0L) "id:${event.id}" else listOf(
            event.timestampMillis,
            event.type,
            event.subtype,
            event.tunnelId,
            event.driftEpisodeId,
            event.surface,
            event.decision,
        ).joinToString("|")
}

private data class PatternWindow(val startMillis: Long) {
    companion object {
        fun from(nowMillis: Long, timeZone: TimeZone): PatternWindow {
            val start = Calendar.getInstance(timeZone).apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -(PATTERN_HISTORY_DAYS - 1))
            }
            return PatternWindow(start.timeInMillis)
        }
    }
}

private object ReviewTeaserFactory {
    fun create(
        recap: DailyAttentionRecap,
        patterns: List<AttentionPattern>,
        nowMillis: Long,
        timeZone: TimeZone,
    ): ReviewTeaser? {
        if (!recap.hasMeaningfulData) return null
        val recentCutoff = nowMillis - TEASER_RECENCY_DAYS * 24L * 60 * 60 * 1_000
        val recentPattern = patterns.firstOrNull { it.latestEvidenceMillis >= recentCutoff }
        if (recentPattern != null && recap.detoursInterrupted > 0) {
            return ReviewTeaser(
                headline = patternHeadline(recentPattern),
                supportingText = patternSupportingText(recentPattern),
            )
        }
        return when {
            recap.detoursInterrupted > 0 && recap.recoveredIntentions > 0 -> ReviewTeaser(
                headline = if (recap.detoursInterrupted == 1 && recap.recoveredIntentions == 1) {
                    "You returned after today's only detour."
                } else {
                    "You returned after ${recap.recoveredIntentions} of ${recap.detoursInterrupted} detours today."
                },
                supportingText = "${recap.driftEpisodes} Drift ${if (recap.driftEpisodes == 1) "episode" else "episodes"}",
            )
            recap.detoursInterrupted == 0 -> ReviewTeaser(
                headline = "No detours interrupted today.",
                supportingText = "${recap.intentionalSessions} intentional ${if (recap.intentionalSessions == 1) "session" else "sessions"}",
            )
            recap.driftEpisodes > 0 -> ReviewTeaser(
                headline = "${recap.driftEpisodes} Drift ${if (recap.driftEpisodes == 1) "episode" else "episodes"} today.",
                supportingText = "Your local attention history is ready to review.",
            )
            else -> ReviewTeaser(
                headline = "${recap.intentionalSessions} intentional ${if (recap.intentionalSessions == 1) "session" else "sessions"} today.",
                supportingText = "Your local attention history is ready to review.",
            )
        }
    }

}

fun patternHeadline(pattern: AttentionPattern): String = when (pattern.type) {
    AttentionPatternType.COMMON_DETOUR_SURFACE -> "${surfaceLabel(pattern.surface)} is your most common ${pattern.app?.displayName ?: "app"} detour."
    AttentionPatternType.RECOVERY_AFTER_SURFACE -> "You usually return after ${surfaceLabel(pattern.surface)}."
    AttentionPatternType.RECURRING_DRIFT_PATH -> "${pattern.sequence.joinToString(" → ") { it.displayName }} is a recurring Drift path."
    AttentionPatternType.TIME_OF_DAY_DRIFT -> "Most of your Drift episodes happen in the ${pattern.timeBucket?.label}."
}

fun patternSupportingText(pattern: AttentionPattern): String = when (pattern.type) {
    AttentionPatternType.COMMON_DETOUR_SURFACE -> "${pattern.evidenceCount} of ${pattern.sampleSize} recent interventions involved ${surfaceLabel(pattern.surface)}."
    AttentionPatternType.RECOVERY_AFTER_SURFACE -> "You returned to ${taskLabel(pattern.task)} after ${pattern.evidenceCount} of your last ${pattern.sampleSize} ${surfaceLabel(pattern.surface)} detours."
    AttentionPatternType.RECURRING_DRIFT_PATH -> "It appeared in ${pattern.evidenceCount} recent Drift episodes."
    AttentionPatternType.TIME_OF_DAY_DRIFT -> "${pattern.evidenceCount} of your last ${pattern.sampleSize} Drift episodes started in the ${pattern.timeBucket?.label}."
}

private val DailyAttentionRecap.hasMeaningfulData: Boolean
    get() = intentionalSessions > 0 || detoursInterrupted > 0 || driftEpisodes > 0
