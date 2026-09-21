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

enum class TunnelStatus { ACTIVE, EXPIRED }

data class TunnelSession(
    val id: String,
    val app: SupportedApp,
    val task: TunnelTask,
    val startedAtMillis: Long,
    val intendedDurationMillis: Long? = null,
    val status: TunnelStatus = TunnelStatus.ACTIVE,
    val overrideOccurred: Boolean = false,
) {
    val expiresAtMillis: Long?
        get() = intendedDurationMillis?.let { startedAtMillis + it }
}

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

enum class IntentionCheckInKind { DETOUR_RENEWAL, OPEN_ENDED_BROWSE }

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
    data class SessionExpired(
        val sessionId: String,
        val app: SupportedApp,
        val task: TunnelTask,
    ) : TunnelPrompt
    data class IntentionCheckIn(
        val sessionId: String,
        val task: TunnelTask,
        val kind: IntentionCheckInKind,
        val surface: DetectedSurface? = null,
        val shownCount: Int,
    ) : TunnelPrompt
}

data class ScopedOverride(
    val sessionId: String,
    val surface: DetectedSurface,
    val expiresAtMillis: Long,
)

data class ReturnCooldown(
    val sessionId: String,
    val surface: DetectedSurface,
    val untilMillis: Long,
)

data class IntentionCheckInState(
    val sessionId: String,
    val kind: IntentionCheckInKind,
    val surface: DetectedSurface? = null,
    val shownCount: Int = 0,
    val nextCheckAtMillis: Long? = null,
    val suppressed: Boolean = false,
)

data class TunnelRuntimeState(
    val foregroundPackage: String? = null,
    val activeSession: TunnelSession? = null,
    val prompt: TunnelPrompt? = null,
    val overrideScope: ScopedOverride? = null,
    val returnCooldown: ReturnCooldown? = null,
    val checkIn: IntentionCheckInState? = null,
    val currentSurface: DetectedSurface? = null,
    val leftProtectedAppAtMillis: Long? = null,
)

/** Pure, in-memory Task Tunnel state machine. The AccessibilityService owns one instance per service run. */
class TunnelCoordinator(
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val returnCooldownMillis: Long = DEFAULT_RETURN_COOLDOWN_MILLIS,
    private val allowAnywayDurationMillis: Long = DEFAULT_ALLOW_ANYWAY_DURATION_MILLIS,
    private val quickReturnGraceMillis: Long = DEFAULT_QUICK_RETURN_GRACE_MILLIS,
    private val detourRenewalExtensionMillis: Long = DEFAULT_DETOUR_RENEWAL_EXTENSION_MILLIS,
    private val browseCheckInIntervalMillis: Long = DEFAULT_BROWSE_CHECK_IN_INTERVAL_MILLIS,
) {
    var state: TunnelRuntimeState = TunnelRuntimeState()
        private set
    private var checkInsEnabled = true

    fun setCheckInsEnabled(enabled: Boolean) {
        checkInsEnabled = enabled
        if (!enabled && state.prompt is TunnelPrompt.IntentionCheckIn) {
            state = state.copy(prompt = null, checkIn = null)
        }
    }

    fun observeForeground(packageName: String, nowMillis: Long) {
        val previousPackage = state.foregroundPackage
        if (previousPackage == packageName) {
            advanceTime(nowMillis)
            return
        }

        val app = SupportedApp.fromPackage(packageName)
        val session = state.activeSession
        when {
            session == null -> state = state.copy(
                foregroundPackage = packageName,
                prompt = app?.let(TunnelPrompt::PurposeGate),
                overrideScope = null,
                returnCooldown = null,
                checkIn = null,
                currentSurface = null,
                leftProtectedAppAtMillis = null,
            )
            session.status == TunnelStatus.EXPIRED -> state = state.copy(
                foregroundPackage = packageName,
                activeSession = null,
                prompt = app?.takeIf { it != session.app }?.let(TunnelPrompt::PurposeGate),
                overrideScope = null,
                returnCooldown = null,
                checkIn = null,
                currentSurface = null,
                leftProtectedAppAtMillis = null,
            )
            app == session.app -> {
                val leftAt = state.leftProtectedAppAtMillis
                val expiredWhileAway = leftAt != null && session.isExpiredAt(nowMillis)
                val graceElapsed = leftAt != null && nowMillis - leftAt >= quickReturnGraceMillis
                state = if (expiredWhileAway || graceElapsed) {
                    state.copy(
                        foregroundPackage = packageName,
                        activeSession = null,
                        prompt = TunnelPrompt.PurposeGate(app),
                        overrideScope = null,
                        returnCooldown = null,
                        leftProtectedAppAtMillis = null,
                    )
                } else {
                    state.copy(
                        foregroundPackage = packageName,
                        prompt = null,
                        overrideScope = state.overrideScope?.takeIf {
                            it.sessionId == session.id && nowMillis < it.expiresAtMillis
                        },
                        returnCooldown = state.returnCooldown?.takeIf {
                            it.sessionId == session.id && nowMillis < it.untilMillis
                        },
                        checkIn = state.checkIn?.takeIf { it.sessionId == session.id },
                        currentSurface = state.currentSurface,
                        leftProtectedAppAtMillis = null,
                    )
                }
            }
            else -> state = state.copy(
                foregroundPackage = packageName,
                prompt = app?.let(TunnelPrompt::PurposeGate),
                overrideScope = state.overrideScope?.takeIf {
                    it.sessionId == session.id && nowMillis < it.expiresAtMillis
                },
                returnCooldown = null,
                checkIn = null,
                currentSurface = null,
                leftProtectedAppAtMillis = state.leftProtectedAppAtMillis ?: nowMillis,
            )
        }
        advanceTime(nowMillis)
    }

    fun dismissPurposeGate() {
        if (state.prompt is TunnelPrompt.PurposeGate) state = state.copy(prompt = null)
    }

    fun requestPurposeGate(app: SupportedApp): Boolean {
        if (state.activeSession != null || state.foregroundPackage != app.packageName) return false
        state = state.copy(prompt = TunnelPrompt.PurposeGate(app))
        return true
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
                intendedDurationMillis = intendedDurationMillis?.takeIf { it > 0L },
            ),
            prompt = null,
            overrideScope = null,
            returnCooldown = null,
            checkIn = null,
            currentSurface = null,
            leftProtectedAppAtMillis = null,
        )
        val session = state.activeSession
        if (session != null && checkInsEnabled && task.isOpenEndedBrowse && intendedDurationMillis == null) {
            state = state.copy(
                checkIn = IntentionCheckInState(
                    sessionId = session.id,
                    kind = IntentionCheckInKind.OPEN_ENDED_BROWSE,
                    nextCheckAtMillis = nowMillis + browseCheckInIntervalMillis,
                ),
            )
        }
    }

    fun observeSurface(surface: DetectedSurface, nowMillis: Long) {
        val activeCheckIn = state.checkIn
        if (activeCheckIn?.kind == IntentionCheckInKind.DETOUR_RENEWAL && activeCheckIn.surface != surface) {
            state = state.copy(checkIn = null, overrideScope = null)
        }
        advanceTime(nowMillis)
        if (state.prompt is TunnelPrompt.IntentionCheckIn) return
        val session = state.activeSession?.takeIf { it.status == TunnelStatus.ACTIVE } ?: return
        if (session.app.packageName != state.foregroundPackage) return

        val checkIn = state.checkIn?.takeIf { it.sessionId == session.id }
        val clearedCheckIn = checkIn?.takeIf {
            it.kind != IntentionCheckInKind.DETOUR_RENEWAL || it.surface == surface
        }

        val override = state.overrideScope?.takeIf {
            it.sessionId == session.id && nowMillis < it.expiresAtMillis
        }
        val cooldown = state.returnCooldown?.takeIf {
            it.sessionId == session.id && it.surface == surface && nowMillis < it.untilMillis
        }
        val decision = SessionPolicy.evaluate(session.task, surface)
        val prompt = when {
            decision != PolicyDecision.INTERVENE -> null
            override?.surface == surface -> null
            cooldown != null -> null
            else -> TunnelPrompt.Intervention(session.id, session.task, surface)
        }
        state = state.copy(
            prompt = prompt,
            overrideScope = override,
            returnCooldown = cooldown,
            checkIn = clearedCheckIn,
            currentSurface = surface,
        )
    }

    fun allowAnyway(nowMillis: Long) {
        val intervention = state.prompt as? TunnelPrompt.Intervention ?: return
        val session = state.activeSession?.takeIf { it.id == intervention.sessionId } ?: return
        state = state.copy(
            activeSession = session.copy(overrideOccurred = true),
            prompt = null,
            overrideScope = ScopedOverride(
                sessionId = session.id,
                surface = intervention.surface,
                expiresAtMillis = nowMillis + allowAnywayDurationMillis,
            ),
            checkIn = if (checkInsEnabled) {
                IntentionCheckInState(
                    sessionId = session.id,
                    kind = IntentionCheckInKind.DETOUR_RENEWAL,
                    surface = intervention.surface,
                    nextCheckAtMillis = nowMillis + allowAnywayDurationMillis,
                )
            } else null,
            currentSurface = intervention.surface,
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

    fun returnFromCheckIn(nowMillis: Long): Boolean {
        val checkIn = state.prompt as? TunnelPrompt.IntentionCheckIn ?: return false
        state = state.copy(
            prompt = null,
            checkIn = null,
            returnCooldown = checkIn.surface?.let {
                ReturnCooldown(checkIn.sessionId, it, nowMillis + returnCooldownMillis)
            },
        )
        return true
    }

    fun continueCheckIn(nowMillis: Long) {
        val prompt = state.prompt as? TunnelPrompt.IntentionCheckIn ?: return
        val session = state.activeSession?.takeIf { it.id == prompt.sessionId } ?: return
        val checkIn = state.checkIn?.takeIf { it.sessionId == session.id } ?: return
        val nextCount = prompt.shownCount
        val suppress = nextCount >= MAX_CHECK_INS
        val nextCheck = when {
            suppress -> null
            checkIn.kind == IntentionCheckInKind.DETOUR_RENEWAL -> nowMillis + detourRenewalExtensionMillis
            else -> nowMillis + browseCheckInIntervalMillis
        }
        state = state.copy(
            prompt = null,
            checkIn = checkIn.copy(shownCount = nextCount, nextCheckAtMillis = nextCheck, suppressed = suppress),
            overrideScope = if (checkIn.kind == IntentionCheckInKind.DETOUR_RENEWAL && checkIn.surface != null) {
                ScopedOverride(session.id, checkIn.surface, nextCheck ?: Long.MAX_VALUE)
            } else state.overrideScope,
        )
    }

    fun continueExpiredSession(nowMillis: Long) {
        val expired = state.prompt as? TunnelPrompt.SessionExpired ?: return
        val session = state.activeSession?.takeIf {
            it.id == expired.sessionId && it.status == TunnelStatus.EXPIRED
        } ?: return
        state = state.copy(
            activeSession = session.copy(
                startedAtMillis = nowMillis,
                status = TunnelStatus.ACTIVE,
            ),
            prompt = null,
            overrideScope = state.overrideScope?.takeIf { nowMillis < it.expiresAtMillis },
            returnCooldown = null,
            leftProtectedAppAtMillis = null,
        )
    }

    fun chooseAnotherPurpose() {
        val expired = state.prompt as? TunnelPrompt.SessionExpired
        val checkIn = state.prompt as? TunnelPrompt.IntentionCheckIn
        val app = expired?.app ?: state.activeSession?.takeIf { it.id == checkIn?.sessionId }?.app ?: return
        state = state.copy(
            activeSession = null,
            prompt = TunnelPrompt.PurposeGate(app),
            overrideScope = null,
            returnCooldown = null,
            checkIn = null,
            currentSurface = null,
            leftProtectedAppAtMillis = null,
        )
    }

    fun endSession() {
        if (state.activeSession == null && state.prompt !is TunnelPrompt.Intervention && state.prompt !is TunnelPrompt.IntentionCheckIn) return
        state = state.copy(
            activeSession = null,
            prompt = null,
            overrideScope = null,
            returnCooldown = null,
            checkIn = null,
            currentSurface = null,
            leftProtectedAppAtMillis = null,
        )
    }

    fun advanceTime(nowMillis: Long) {
        val session = state.activeSession ?: return
        val override = state.overrideScope?.takeIf {
            it.sessionId == session.id && nowMillis < it.expiresAtMillis
        }
        val cooldown = state.returnCooldown?.takeIf {
            it.sessionId == session.id && nowMillis < it.untilMillis
        }
        if (session.status == TunnelStatus.EXPIRED) {
            state = state.copy(overrideScope = override, returnCooldown = cooldown)
            return
        }

        val leftAt = state.leftProtectedAppAtMillis
        if (leftAt != null) {
            val expiredInBackground = session.isExpiredAt(nowMillis)
            val graceElapsed = nowMillis - leftAt >= quickReturnGraceMillis
            if (expiredInBackground || graceElapsed) {
                val currentGate = (state.prompt as? TunnelPrompt.PurposeGate)?.takeIf {
                    it.app.packageName == state.foregroundPackage
                }
                state = state.copy(
                    activeSession = null,
                    prompt = currentGate,
                    overrideScope = null,
                    returnCooldown = null,
                    leftProtectedAppAtMillis = null,
                )
                return
            }
        } else if (session.isExpiredAt(nowMillis)) {
            state = state.copy(
                activeSession = session.copy(status = TunnelStatus.EXPIRED),
                prompt = TunnelPrompt.SessionExpired(session.id, session.app, session.task),
                overrideScope = null,
                returnCooldown = null,
            )
            return
        }
        if (checkInsEnabled) {
            val checkIn = state.checkIn?.takeIf { it.sessionId == session.id && !it.suppressed }
            if (checkIn?.nextCheckAtMillis != null && nowMillis >= checkIn.nextCheckAtMillis &&
                (checkIn.kind == IntentionCheckInKind.OPEN_ENDED_BROWSE || state.currentSurface == checkIn.surface)
            ) {
                state = state.copy(
                    prompt = TunnelPrompt.IntentionCheckIn(session.id, session.task, checkIn.kind, checkIn.surface, checkIn.shownCount + 1),
                    checkIn = checkIn.copy(shownCount = checkIn.shownCount + 1, nextCheckAtMillis = null),
                    overrideScope = null,
                )
                return
            }
        }
        state = state.copy(overrideScope = override, returnCooldown = cooldown)
    }

    fun nextDeadlineMillis(): Long? {
        val session = state.activeSession ?: return null
        val deadlines = buildList {
            state.overrideScope?.takeIf { it.sessionId == session.id }?.let { add(it.expiresAtMillis) }
            if (session.status == TunnelStatus.ACTIVE) {
                session.expiresAtMillis?.let(::add)
                state.leftProtectedAppAtMillis?.let { add(it + quickReturnGraceMillis) }
                if (checkInsEnabled) {
                    state.checkIn?.takeIf { !it.suppressed && it.sessionId == session.id }
                        ?.nextCheckAtMillis?.let(::add)
                }
            }
        }
        return deadlines.minOrNull()
    }

    private fun TunnelSession.isExpiredAt(nowMillis: Long): Boolean =
        expiresAtMillis?.let { nowMillis >= it } == true

    companion object {
        const val DEFAULT_RETURN_COOLDOWN_MILLIS = 2_000L
        const val DEFAULT_ALLOW_ANYWAY_DURATION_MILLIS = 5 * 60_000L
        const val DEFAULT_QUICK_RETURN_GRACE_MILLIS = 60_000L
        const val DEFAULT_DETOUR_RENEWAL_EXTENSION_MILLIS = 10 * 60_000L
        const val DEFAULT_BROWSE_CHECK_IN_INTERVAL_MILLIS = 15 * 60_000L
        const val MAX_CHECK_INS = 2
    }
}

private val TunnelTask.isOpenEndedBrowse: Boolean
    get() = this == TunnelTask.INSTAGRAM_BROWSE || this == TunnelTask.YOUTUBE_BROWSE
