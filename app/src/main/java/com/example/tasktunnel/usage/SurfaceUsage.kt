package com.example.tasktunnel.usage

import android.content.Context
import com.example.tasktunnel.attention.AttentionApp
import com.example.tasktunnel.attention.SurfaceUsageSegmentEntity
import com.example.tasktunnel.attention.SurfaceUsageSegmentDao
import com.example.tasktunnel.tunnel.DetectedSurface
import com.example.tasktunnel.tunnel.TunnelTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

private const val UNKNOWN_GRACE_MILLIS = 2_500L
const val SURFACE_USAGE_RETENTION_DAYS = 30

enum class SurfaceUsageClassification { CLASSIFIED, UNCLASSIFIED }

data class SurfaceUsageSegment(
    val id: Long = 0,
    val app: AttentionApp,
    val surface: DetectedSurface?,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val tunnelTask: TunnelTask?,
    val classification: SurfaceUsageClassification,
)

data class SurfaceUsageSummary(
    val app: AttentionApp,
    val totalTrackedDurationMillis: Long,
    val classifiedDurationMillis: Long,
    val unclassifiedDurationMillis: Long,
    val bySurface: Map<DetectedSurface?, Long>,
) {
    val coverage: Double
        get() = if (totalTrackedDurationMillis == 0L) 0.0 else classifiedDurationMillis.toDouble() / totalTrackedDurationMillis
}

data class SurfaceUsageInspectorState(
    val summaries: List<SurfaceUsageSummary> = emptyList(),
    val recentSegments: List<SurfaceUsageSegment> = emptyList(),
)

class SurfaceUsageRepository(private val dao: SurfaceUsageSegmentDao) {
    fun observeRecent(limit: Int = 50): Flow<List<SurfaceUsageSegment>> =
        dao.observeRecent(limit).map { it.map(SurfaceUsageSegmentEntity::toDomain) }

    suspend fun record(segment: SurfaceUsageSegment) = dao.insert(segment.toEntity())
    suspend fun deleteOlderThan(cutoffMillis: Long) = dao.deleteOlderThan(cutoffMillis)
    suspend fun clear() = dao.clear()

    suspend fun summary(app: AttentionApp, startMillis: Long, endMillis: Long): SurfaceUsageSummary {
        val rows = dao.getForRange(startMillis, endMillis).map(SurfaceUsageSegmentEntity::toDomain).filter { it.app == app }
        val bySurface = rows.groupBy { it.surface }.mapValues { (_, values) -> values.sumOf { durationWithin(it, startMillis, endMillis) } }
        val classified = rows.filter { it.classification == SurfaceUsageClassification.CLASSIFIED }.sumOf { durationWithin(it, startMillis, endMillis) }
        val total = rows.sumOf { durationWithin(it, startMillis, endMillis) }
        return SurfaceUsageSummary(app, total, classified, total - classified, bySurface)
    }

    suspend fun inspect(nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): SurfaceUsageInspectorState {
        val day = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = day.timeInMillis
        val end = (day.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        return SurfaceUsageInspectorState(
            summaries = AttentionApp.entries.filter {
                it == AttentionApp.INSTAGRAM || it == AttentionApp.YOUTUBE || it == AttentionApp.TIKTOK
            }
                .map { summary(it, start, end) },
            recentSegments = dao.getRecent(50).map(SurfaceUsageSegmentEntity::toDomain),
        )
    }

    private fun durationWithin(segment: SurfaceUsageSegment, start: Long, end: Long): Long =
        (minOf(segment.endedAtMillis, end) - maxOf(segment.startedAtMillis, start)).coerceAtLeast(0L)
}

class SurfaceUsageTracker(
    private val repository: SurfaceUsageRepository,
    private val scope: CoroutineScope,
) {
    private data class Active(val app: AttentionApp, val surface: DetectedSurface?, val task: TunnelTask?, val startedAt: Long, val classified: Boolean)
    private var active: Active? = null
    private var pendingUnknownSince: Long? = null
    private var interactive = true
    private var overlayVisible = false

    fun setInteractive(value: Boolean, nowMillis: Long) {
        interactive = value
        if (!value) close(nowMillis)
        else {
            close(nowMillis)
            pendingUnknownSince = null
        }
    }

    fun setOverlayVisible(value: Boolean, nowMillis: Long) {
        overlayVisible = value
        if (value) close(nowMillis)
        else {
            close(nowMillis)
            pendingUnknownSince = null
        }
    }

    fun foregroundChanged(packageName: String?, nowMillis: Long) {
        if (active != null && AttentionApp.fromPackage(packageName) != active?.app) close(nowMillis)
    }

    fun observe(packageName: String?, surface: DetectedSurface, task: TunnelTask?, nowMillis: Long) {
        val app = AttentionApp.fromPackage(packageName) ?: run { close(nowMillis); return }
        if (!interactive || overlayVisible) return
        if (surface == DetectedSurface.UNKNOWN) {
            if (active != null && pendingUnknownSince == null) pendingUnknownSince = nowMillis
            if (pendingUnknownSince != null && nowMillis - pendingUnknownSince!! > UNKNOWN_GRACE_MILLIS) {
                close(pendingUnknownSince!!)
                active = Active(app, null, task, pendingUnknownSince!!, false)
                pendingUnknownSince = null
            }
            return
        }
        pendingUnknownSince = null
        val classified = true
        val current = active
        if (current == null) active = Active(app, surface, task, nowMillis, classified)
        else if (current.app != app || current.surface != surface || current.task != task) {
            close(nowMillis)
            active = Active(app, surface, task, nowMillis, classified)
        }
    }

    fun close(nowMillis: Long) { active?.let { finish(it, nowMillis) }; active = null; pendingUnknownSince = null }

    private fun finish(value: Active, endMillis: Long) {
        if (endMillis <= value.startedAt) return
        scope.launch(Dispatchers.IO) {
            repository.record(SurfaceUsageSegment(0, value.app, value.surface, value.startedAt, endMillis, value.task, if (value.classified) SurfaceUsageClassification.CLASSIFIED else SurfaceUsageClassification.UNCLASSIFIED))
            repository.deleteOlderThan(endMillis - SURFACE_USAGE_RETENTION_DAYS * 24L * 60 * 60 * 1_000)
        }
    }
}

private fun SurfaceUsageSegment.toEntity() = SurfaceUsageSegmentEntity(0, app.name, surface?.name, startedAtMillis, endedAtMillis, tunnelTask?.name, classification.name)
private fun SurfaceUsageSegmentEntity.toDomain() = SurfaceUsageSegment(id, AttentionApp.valueOf(app), surface?.let(DetectedSurface::valueOf), startedAtMillis, endedAtMillis, tunnelTask?.let(TunnelTask::valueOf), SurfaceUsageClassification.valueOf(classification))
