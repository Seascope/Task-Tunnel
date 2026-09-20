package com.example.tasktunnel.tunnel

import java.util.UUID

enum class SupportedApp(val packageName: String, val displayName: String) {
    INSTAGRAM("com.instagram.android", "Instagram"),
    YOUTUBE("com.google.android.youtube", "YouTube"),
    ;

    companion object {
        fun fromPackage(packageName: String?): SupportedApp? = entries.firstOrNull {
            it.packageName == packageName
        }
    }
}

enum class TunnelTask(val app: SupportedApp) {
    INSTAGRAM_MESSAGES(SupportedApp.INSTAGRAM),
    INSTAGRAM_BROWSE(SupportedApp.INSTAGRAM),
    YOUTUBE_SEARCH_WATCH(SupportedApp.YOUTUBE),
    YOUTUBE_BROWSE(SupportedApp.YOUTUBE),
}

enum class TunnelStatus { ACTIVE }

data class TunnelSession(
    val id: String,
    val app: SupportedApp,
    val task: TunnelTask,
    val startedAtMillis: Long,
    val intendedDurationMillis: Long? = null,
    val status: TunnelStatus = TunnelStatus.ACTIVE,
    val overrideOccurred: Boolean = false,
)

enum class DetectedSurface {
    INSTAGRAM_MESSAGES,
    INSTAGRAM_EXPLORE,
    INSTAGRAM_REELS,
    INSTAGRAM_HOME,
    INSTAGRAM_OTHER,
    YOUTUBE_SEARCH,
    YOUTUBE_VIDEO,
    YOUTUBE_SHORTS,
    YOUTUBE_OTHER,
    UNKNOWN,
}

enum class PolicyDecision { ALLOW, INTERVENE, UNKNOWN_FAIL_OPEN }

object SessionPolicy {
    fun evaluate(task: TunnelTask, surface: DetectedSurface): PolicyDecision {
        if (surface == DetectedSurface.UNKNOWN) return PolicyDecision.UNKNOWN_FAIL_OPEN
        return when (task) {
            TunnelTask.INSTAGRAM_MESSAGES -> when (surface) {
                DetectedSurface.INSTAGRAM_REELS,
                DetectedSurface.INSTAGRAM_EXPLORE
                -> PolicyDecision.INTERVENE
                in instagramSurfaces -> PolicyDecision.ALLOW
                else -> PolicyDecision.UNKNOWN_FAIL_OPEN
            }
            TunnelTask.YOUTUBE_SEARCH_WATCH -> when (surface) {
                DetectedSurface.YOUTUBE_SHORTS -> PolicyDecision.INTERVENE
                in youtubeSurfaces -> PolicyDecision.ALLOW
                else -> PolicyDecision.UNKNOWN_FAIL_OPEN
            }
            TunnelTask.INSTAGRAM_BROWSE -> {
                if (surface in instagramSurfaces) PolicyDecision.ALLOW else PolicyDecision.UNKNOWN_FAIL_OPEN
            }
            TunnelTask.YOUTUBE_BROWSE -> {
                if (surface in youtubeSurfaces) PolicyDecision.ALLOW else PolicyDecision.UNKNOWN_FAIL_OPEN
            }
        }
    }

    private val instagramSurfaces = setOf(
        DetectedSurface.INSTAGRAM_MESSAGES,
        DetectedSurface.INSTAGRAM_EXPLORE,
        DetectedSurface.INSTAGRAM_REELS,
        DetectedSurface.INSTAGRAM_HOME,
        DetectedSurface.INSTAGRAM_OTHER,
    )
    private val youtubeSurfaces = setOf(
        DetectedSurface.YOUTUBE_SEARCH,
        DetectedSurface.YOUTUBE_VIDEO,
        DetectedSurface.YOUTUBE_SHORTS,
        DetectedSurface.YOUTUBE_OTHER,
    )
}

sealed interface TunnelPrompt {
    data class PurposeGate(val app: SupportedApp) : TunnelPrompt
    data class Intervention(
        val sessionId: String,
        val task: TunnelTask,
        val surface: DetectedSurface,
    ) : TunnelPrompt
}

data class ScopedOverride(val sessionId: String, val surface: DetectedSurface)

data class ReturnCooldown(
    val sessionId: String,
    val surface: DetectedSurface,
    val untilMillis: Long,
)

data class TunnelRuntimeState(
    val foregroundPackage: String? = null,
    val activeSession: TunnelSession? = null,
    val prompt: TunnelPrompt? = null,
    val overrideScope: ScopedOverride? = null,
    val returnCooldown: ReturnCooldown? = null,
)

/** Pure, in-memory M3 state machine. The AccessibilityService owns one instance per service run. */
class TunnelCoordinator(
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val returnCooldownMillis: Long = DEFAULT_RETURN_COOLDOWN_MILLIS,
) {
    var state: TunnelRuntimeState = TunnelRuntimeState()
        private set

    fun observeForeground(packageName: String, nowMillis: Long) {
        val previousPackage = state.foregroundPackage
        if (previousPackage == packageName) return

        val app = SupportedApp.fromPackage(packageName)
        val applicableSession = state.activeSession?.takeIf { it.app == app }
        state = state.copy(
            foregroundPackage = packageName,
            prompt = if (app != null && applicableSession == null) TunnelPrompt.PurposeGate(app) else null,
            overrideScope = state.overrideScope?.takeIf { applicableSession?.id == it.sessionId },
            returnCooldown = state.returnCooldown?.takeIf {
                applicableSession?.id == it.sessionId && nowMillis < it.untilMillis
            },
        )
    }

    fun dismissPurposeGate() {
        if (state.prompt is TunnelPrompt.PurposeGate) state = state.copy(prompt = null)
    }

    fun startSession(task: TunnelTask, nowMillis: Long, intendedDurationMillis: Long? = null) {
        val gate = state.prompt as? TunnelPrompt.PurposeGate ?: return
        if (gate.app != task.app) return
        state = state.copy(
            activeSession = TunnelSession(
                id = idFactory(),
                app = task.app,
                task = task,
                startedAtMillis = nowMillis,
                intendedDurationMillis = intendedDurationMillis,
            ),
            prompt = null,
            overrideScope = null,
            returnCooldown = null,
        )
    }

    fun observeSurface(surface: DetectedSurface, nowMillis: Long) {
        val session = state.activeSession ?: return
        if (session.app.packageName != state.foregroundPackage) return

        val existingOverride = state.overrideScope
        val override = existingOverride?.takeIf {
            it.sessionId == session.id && (surface == it.surface || surface == DetectedSurface.UNKNOWN)
        }
        val cooldown = state.returnCooldown?.takeIf {
            it.sessionId == session.id && it.surface == surface && nowMillis < it.untilMillis
        }
        val decision = SessionPolicy.evaluate(session.task, surface)
        val prompt = when {
            decision != PolicyDecision.INTERVENE -> null
            override != null -> null
            cooldown != null -> null
            else -> TunnelPrompt.Intervention(session.id, session.task, surface)
        }
        state = state.copy(prompt = prompt, overrideScope = override, returnCooldown = cooldown)
    }

    fun allowAnyway() {
        val intervention = state.prompt as? TunnelPrompt.Intervention ?: return
        val session = state.activeSession?.takeIf { it.id == intervention.sessionId } ?: return
        state = state.copy(
            activeSession = session.copy(overrideOccurred = true),
            prompt = null,
            overrideScope = ScopedOverride(session.id, intervention.surface),
            returnCooldown = null,
        )
    }

    fun returnFromIntervention(nowMillis: Long): Boolean {
        val intervention = state.prompt as? TunnelPrompt.Intervention ?: return false
        state = state.copy(
            prompt = null,
            returnCooldown = ReturnCooldown(
                intervention.sessionId,
                intervention.surface,
                nowMillis + returnCooldownMillis,
            ),
        )
        return true
    }

    fun endSession() {
        if (state.activeSession == null && state.prompt !is TunnelPrompt.Intervention) return
        state = state.copy(
            activeSession = null,
            prompt = null,
            overrideScope = null,
            returnCooldown = null,
        )
    }

    companion object {
        const val DEFAULT_RETURN_COOLDOWN_MILLIS = 2_000L
    }
}
