package com.example.tasktunnel.attention

import com.example.tasktunnel.drift.DriftPolicy
import com.example.tasktunnel.tunnel.TunnelTask

enum class AttentionEpisodeType { TASK_TUNNEL, DRIFT }

data class AttentionEpisode(
    val id: String,
    val type: AttentionEpisodeType,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val app: AttentionApp?,
    val task: TunnelTask?,
    val involvedApps: List<AttentionApp>,
    val involvedPackages: List<String>,
    val events: List<AttentionEvent>,
)

object AttentionEpisodeGrouper {
    fun group(events: List<AttentionEvent>): List<AttentionEpisode> = events
        .groupBy { event ->
            when {
                event.tunnelId != null -> "tunnel:${event.tunnelId}"
                event.driftEpisodeId != null -> "drift:${event.driftEpisodeId}"
                else -> "event:${event.id}:${event.timestampMillis}"
            }
        }
        .mapNotNull { (key, grouped) ->
            val ordered = grouped.sortedWith(compareBy(AttentionEvent::timestampMillis, AttentionEvent::id))
            val type = if (ordered.any { it.tunnelId != null }) {
                AttentionEpisodeType.TASK_TUNNEL
            } else {
                AttentionEpisodeType.DRIFT
            }
            val involvedPackages = ordered.flatMap { event ->
                event.relatedPackages.ifEmpty { event.relatedApps.map(AttentionApp::packageName) }
            }.distinct()

            // DriftDetector only emits an episode after the configured distinct-app threshold is
            // reached. Older database rows could not persist arbitrary packages, so a historical
            // Drift episode may reconstruct with only one or two apps. Do not surface those
            // incomplete legacy rows as if they were real one/two-app Drift detections.
            if (type == AttentionEpisodeType.DRIFT &&
                involvedPackages.size < DriftPolicy.DEFAULT_DISTINCT_APP_THRESHOLD
            ) {
                return@mapNotNull null
            }

            AttentionEpisode(
                id = key,
                type = type,
                startedAtMillis = ordered.first().timestampMillis,
                endedAtMillis = ordered.last().timestampMillis,
                app = ordered.firstNotNullOfOrNull { it.app },
                task = ordered.firstNotNullOfOrNull { it.task },
                involvedApps = ordered.flatMap { it.relatedApps }.distinct(),
                involvedPackages = involvedPackages,
                events = ordered,
            )
        }
        .sortedByDescending(AttentionEpisode::endedAtMillis)
}

data class AttentionMetrics(val driftEpisodesLastSevenDays: Int) {
    companion object {
        private const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1_000

        fun from(events: List<AttentionEvent>, nowMillis: Long): AttentionMetrics {
            val cutoff = nowMillis - SEVEN_DAYS_MILLIS
            val driftCount = events.asSequence()
                .filter { it.timestampMillis >= cutoff && it.isValidDriftCheckIn() }
                .mapNotNull(AttentionEvent::driftEpisodeId)
                .distinct()
                .count()
            return AttentionMetrics(driftCount)
        }
    }
}

data class DailyAttentionRecap(
    val intentionalSessions: Int = 0,
    val detoursInterrupted: Int = 0,
    val recoveredIntentions: Int = 0,
    val consciousDetours: Int = 0,
    val endedTunnels: Int = 0,
    val driftEpisodes: Int = 0,
) {
    val recoveryInsight: String
        get() = when {
            detoursInterrupted == 0 -> "No detours interrupted today."
            recoveredIntentions == 1 && detoursInterrupted == 1 ->
                "Returned to your intention after the only detour today."
            else -> "$recoveredIntentions of $detoursInterrupted detours ended with a return to your intention."
        }

    companion object {
        fun from(
            events: List<AttentionEvent>,
            nowMillis: Long,
            timeZone: java.util.TimeZone = java.util.TimeZone.getDefault(),
        ): DailyAttentionRecap {
            val day = LocalCalendarDay.forInstant(nowMillis, timeZone)
            val today = events.filter { it.timestampMillis in day.startMillis until day.endMillis }
            val intentionalSessions = today.asSequence()
                .filter { it.type == AttentionEventType.INTENT && it.subtype == AttentionSubtype.PURPOSE_SELECTED }
                .mapNotNull(AttentionEvent::tunnelId)
                .distinct()
                .count()
            val interventions = today.asSequence()
                .filter { it.subtype == AttentionSubtype.SURFACE_INTERVENTION }
                .distinctBy(AttentionEvent::occurrenceKey)
                .toList()
            val decisions = today.asSequence()
                .filter { it.type == AttentionEventType.DECISION }
                .distinctBy(AttentionEvent::occurrenceKey)
                .toList()
            val interventionTunnelIds = interventions.mapNotNull(AttentionEvent::tunnelId).toSet()
            val interventionDecisions = decisions.filter { it.tunnelId in interventionTunnelIds }
            val driftEpisodes = today.asSequence()
                .filter(AttentionEvent::isValidDriftCheckIn)
                .mapNotNull(AttentionEvent::driftEpisodeId)
                .distinct()
                .count()
            return DailyAttentionRecap(
                intentionalSessions = intentionalSessions,
                detoursInterrupted = interventions.size,
                recoveredIntentions = interventionDecisions.count { it.subtype == AttentionSubtype.RETURN },
                consciousDetours = interventionDecisions.count { it.subtype == AttentionSubtype.ALLOW_ANYWAY },
                endedTunnels = interventionDecisions.count { it.subtype == AttentionSubtype.END_TUNNEL },
                driftEpisodes = driftEpisodes,
            )
        }
    }
}

data class LocalCalendarDay(val startMillis: Long, val endMillis: Long) {
    companion object {
        fun forInstant(nowMillis: Long, timeZone: java.util.TimeZone): LocalCalendarDay {
            val start = java.util.Calendar.getInstance(timeZone).apply {
                timeInMillis = nowMillis
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val end = start.clone() as java.util.Calendar
            end.add(java.util.Calendar.DAY_OF_YEAR, 1)
            return LocalCalendarDay(start.timeInMillis, end.timeInMillis)
        }
    }
}

private fun AttentionEvent.occurrenceKey(): String =
    if (id != 0L) "id:$id" else listOf(timestampMillis, type, subtype, tunnelId, driftEpisodeId, surface, decision).joinToString("|")

private fun AttentionEvent.isValidDriftCheckIn(): Boolean =
    subtype == AttentionSubtype.DRIFT_CHECK_IN &&
        driftEpisodeId != null &&
        relatedPackages.ifEmpty { relatedApps.map(AttentionApp::packageName) }
            .distinct()
            .size >= DriftPolicy.DEFAULT_DISTINCT_APP_THRESHOLD
