package com.example.tasktunnel.attention

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
        .map { (key, grouped) ->
            val ordered = grouped.sortedWith(compareBy(AttentionEvent::timestampMillis, AttentionEvent::id))
            val type = if (ordered.any { it.tunnelId != null }) {
                AttentionEpisodeType.TASK_TUNNEL
            } else {
                AttentionEpisodeType.DRIFT
            }
            AttentionEpisode(
                id = key,
                type = type,
                startedAtMillis = ordered.first().timestampMillis,
                endedAtMillis = ordered.last().timestampMillis,
                app = ordered.firstNotNullOfOrNull { it.app },
                task = ordered.firstNotNullOfOrNull { it.task },
                involvedApps = ordered.flatMap { it.relatedApps }.distinct(),
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
                .filter { it.subtype == AttentionSubtype.DRIFT_CHECK_IN && it.timestampMillis >= cutoff }
                .mapNotNull(AttentionEvent::driftEpisodeId)
                .distinct()
                .count()
            return AttentionMetrics(driftCount)
        }
    }
}
