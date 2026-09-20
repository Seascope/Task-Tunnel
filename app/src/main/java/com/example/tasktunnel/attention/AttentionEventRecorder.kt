package com.example.tasktunnel.attention

import android.content.Context
import com.example.tasktunnel.drift.DriftEpisode
import com.example.tasktunnel.tunnel.DetectedSurface
import com.example.tasktunnel.tunnel.PolicyDecision
import com.example.tasktunnel.tunnel.SessionPolicy
import com.example.tasktunnel.tunnel.TunnelPrompt
import com.example.tasktunnel.tunnel.TunnelSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Pure semantic filtering. It never receives accessibility nodes or detector diagnostics. */
class AttentionEventSemantics {
    private val lastSurfaceByTunnel = mutableMapOf<String, DetectedSurface>()
    private val shownInterventions = mutableSetOf<String>()
    private val shownDriftEpisodes = mutableSetOf<String>()

    fun purposeSelected(session: TunnelSession, nowMillis: Long) = AttentionEvent(
        timestampMillis = nowMillis,
        type = AttentionEventType.INTENT,
        subtype = AttentionSubtype.PURPOSE_SELECTED,
        app = AttentionApp.fromSupported(session.app),
        task = session.task,
        tunnelId = session.id,
    )

    fun surfaceObserved(session: TunnelSession, surface: DetectedSurface, nowMillis: Long): AttentionEvent? {
        if (surface !in meaningfulSurfaces || lastSurfaceByTunnel[session.id] == surface) return null
        val previous = lastSurfaceByTunnel[session.id]
        lastSurfaceByTunnel[session.id] = surface
        return AttentionEvent(
            timestampMillis = nowMillis,
            type = AttentionEventType.TRANSITION,
            subtype = if (
                previous != null &&
                SessionPolicy.evaluate(session.task, previous) == PolicyDecision.INTERVENE &&
                SessionPolicy.evaluate(session.task, surface) == PolicyDecision.ALLOW
            ) AttentionSubtype.SURFACE_RETURNED else AttentionSubtype.SURFACE_ENTERED,
            app = AttentionApp.fromSupported(session.app),
            surface = surface,
            task = session.task,
            tunnelId = session.id,
        )
    }

    fun tunnelPromptShown(
        prompt: TunnelPrompt,
        session: TunnelSession?,
        nowMillis: Long,
    ): AttentionEvent? {
        val event = when (prompt) {
            is TunnelPrompt.Intervention -> AttentionEvent(
                timestampMillis = nowMillis,
                type = AttentionEventType.INTERVENTION,
                subtype = AttentionSubtype.SURFACE_INTERVENTION,
                app = session?.app?.let(AttentionApp::fromSupported),
                surface = prompt.surface,
                task = prompt.task,
                tunnelId = prompt.sessionId,
            )
            is TunnelPrompt.SessionExpired -> AttentionEvent(
                timestampMillis = nowMillis,
                type = AttentionEventType.INTERVENTION,
                subtype = AttentionSubtype.SESSION_EXPIRED,
                app = AttentionApp.fromSupported(prompt.app),
                task = prompt.task,
                tunnelId = prompt.sessionId,
            )
            is TunnelPrompt.PurposeGate -> return null
        }
        val key = "${event.tunnelId}:${event.subtype}:${event.surface}"
        return event.takeIf { shownInterventions.add(key) }
    }

    fun tunnelDecision(
        session: TunnelSession,
        subtype: AttentionSubtype,
        decision: AttentionDecision,
        nowMillis: Long,
        surface: DetectedSurface? = null,
    ): AttentionEvent {
        if (subtype == AttentionSubtype.RETURN || subtype == AttentionSubtype.ALLOW_ANYWAY) {
            shownInterventions.remove("${session.id}:${AttentionSubtype.SURFACE_INTERVENTION}:$surface")
        }
        return AttentionEvent(
            timestampMillis = nowMillis,
            type = AttentionEventType.DECISION,
            subtype = subtype,
            app = AttentionApp.fromSupported(session.app),
            surface = surface,
            task = session.task,
            tunnelId = session.id,
            decision = decision,
        )
    }

    fun driftCheckInShown(episode: DriftEpisode, nowMillis: Long): List<AttentionEvent> {
        if (!shownDriftEpisodes.add(episode.id)) return emptyList()
        val apps = episode.involvedPackages.mapNotNull(AttentionApp::fromPackage).distinct()
        return listOf(
            AttentionEvent(
                timestampMillis = episode.startedAtMillis,
                type = AttentionEventType.TRANSITION,
                subtype = AttentionSubtype.DRIFT_SEQUENCE,
                driftEpisodeId = episode.id,
                relatedApps = apps,
            ),
            AttentionEvent(
                timestampMillis = nowMillis,
                type = AttentionEventType.INTERVENTION,
                subtype = AttentionSubtype.DRIFT_CHECK_IN,
                app = episode.involvedPackages.lastOrNull()?.let(AttentionApp::fromPackage),
                driftEpisodeId = episode.id,
                relatedApps = apps,
            ),
        )
    }

    fun driftDecision(
        episode: DriftEpisode,
        subtype: AttentionSubtype,
        decision: AttentionDecision,
        foregroundPackage: String?,
        nowMillis: Long,
    ) = AttentionEvent(
        timestampMillis = nowMillis,
        type = AttentionEventType.DECISION,
        subtype = subtype,
        app = AttentionApp.fromPackage(foregroundPackage),
        driftEpisodeId = episode.id,
        decision = decision,
        relatedApps = episode.involvedPackages.mapNotNull(AttentionApp::fromPackage).distinct(),
    )

    companion object {
        private val meaningfulSurfaces = setOf(
            DetectedSurface.INSTAGRAM_MESSAGES,
            DetectedSurface.INSTAGRAM_EXPLORE,
            DetectedSurface.INSTAGRAM_REELS,
            DetectedSurface.YOUTUBE_SEARCH,
            DetectedSurface.YOUTUBE_VIDEO,
            DetectedSurface.YOUTUBE_SHORTS,
        )
    }
}

class AttentionEventRecorder(
    private val store: AttentionEventStore,
    private val scope: CoroutineScope,
    private val semantics: AttentionEventSemantics = AttentionEventSemantics(),
) {
    fun purposeSelected(session: TunnelSession, nowMillis: Long) = enqueue(semantics.purposeSelected(session, nowMillis))

    fun surfaceObserved(session: TunnelSession, surface: DetectedSurface, nowMillis: Long) =
        semantics.surfaceObserved(session, surface, nowMillis)?.let(::enqueue)

    fun tunnelPromptShown(prompt: TunnelPrompt, session: TunnelSession?, nowMillis: Long) =
        semantics.tunnelPromptShown(prompt, session, nowMillis)?.let(::enqueue)

    fun tunnelDecision(
        session: TunnelSession,
        subtype: AttentionSubtype,
        decision: AttentionDecision,
        nowMillis: Long,
        surface: DetectedSurface? = null,
    ) = enqueue(semantics.tunnelDecision(session, subtype, decision, nowMillis, surface))

    fun driftCheckInShown(episode: DriftEpisode, nowMillis: Long) =
        semantics.driftCheckInShown(episode, nowMillis).forEach(::enqueue)

    fun driftDecision(
        episode: DriftEpisode,
        subtype: AttentionSubtype,
        decision: AttentionDecision,
        foregroundPackage: String?,
        nowMillis: Long,
    ) = enqueue(semantics.driftDecision(episode, subtype, decision, foregroundPackage, nowMillis))

    private fun enqueue(event: AttentionEvent) {
        scope.launch { store.record(event) }
    }
}

object AttentionHistory {
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    fun repository(context: Context): AttentionEventRepository =
        AttentionEventRepository(AttentionDatabase.getInstance(context).attentionEventDao())

    fun recorder(context: Context): AttentionEventRecorder =
        AttentionEventRecorder(repository(context), writeScope)
}
