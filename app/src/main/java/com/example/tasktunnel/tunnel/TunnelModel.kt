package com.example.tasktunnel.tunnel

import java.util.UUID

enum class SupportedApp(val packageName: String, val displayName: String) {
    INSTAGRAM("com.instagram.android", "Instagram"),
    YOUTUBE("com.google.android.youtube", "YouTube"),
    TIKTOK("com.zhiliaoapp.musically", "TikTok"),
    ;

    companion object {
        fun fromPackage(packageName: String?): SupportedApp? = entries.firstOrNull {
            it.packageName == packageName
        }
    }
}

enum class TunnelTask(val app: SupportedApp) {
    INSTAGRAM_MESSAGES(SupportedApp.INSTAGRAM),
    INSTAGRAM_SEARCH(SupportedApp.INSTAGRAM),
    INSTAGRAM_POST(SupportedApp.INSTAGRAM),
    INSTAGRAM_BROWSE(SupportedApp.INSTAGRAM),
    YOUTUBE_SEARCH_WATCH(SupportedApp.YOUTUBE),
    YOUTUBE_SUBSCRIPTIONS(SupportedApp.YOUTUBE),
    YOUTUBE_SHORTS(SupportedApp.YOUTUBE),
    YOUTUBE_BROWSE(SupportedApp.YOUTUBE),
    TIKTOK_SEARCH_WATCH(SupportedApp.TIKTOK),
    TIKTOK_INBOX(SupportedApp.TIKTOK),
    TIKTOK_BROWSE(SupportedApp.TIKTOK),
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
    INSTAGRAM_PROFILE,
    INSTAGRAM_CREATE,
    INSTAGRAM_OTHER,
    YOUTUBE_SEARCH,
    YOUTUBE_VIDEO,
    YOUTUBE_UNSUBSCRIBED_VIDEO,
    YOUTUBE_SHORTS,
    YOUTUBE_UNSUBSCRIBED_SHORTS,
    YOUTUBE_HOME,
    YOUTUBE_SUBSCRIPTIONS,
    YOUTUBE_YOU,
    YOUTUBE_OTHER,
    TIKTOK_FEED,
    TIKTOK_FRIENDS,
    TIKTOK_SEARCH,
    TIKTOK_INBOX,
    TIKTOK_PROFILE,
    TIKTOK_OTHER,
    UNKNOWN,
}

enum class PolicyDecision { ALLOW, INTERVENE, UNKNOWN_FAIL_OPEN }

enum class IntentionCheckInKind { DETOUR_RENEWAL, OPEN_ENDED_BROWSE }

object SessionPolicy {
    fun evaluate(task: TunnelTask, surface: DetectedSurface): PolicyDecision {
        if (surface == DetectedSurface.UNKNOWN) return PolicyDecision.UNKNOWN_FAIL_OPEN
        return when (task) {
            TunnelTask.INSTAGRAM_MESSAGES -> when (surface) {
                DetectedSurface.INSTAGRAM_MESSAGES,
                DetectedSurface.INSTAGRAM_EXPLORE,
                DetectedSurface.INSTAGRAM_PROFILE,
                DetectedSurface.INSTAGRAM_OTHER,
                -> PolicyDecision.ALLOW
                in instagramSurfaces -> PolicyDecision.INTERVENE
                else -> PolicyDecision.UNKNOWN_FAIL_OPEN
            }
            TunnelTask.INSTAGRAM_SEARCH -> when (surface) {
                DetectedSurface.INSTAGRAM_MESSAGES,
                DetectedSurface.INSTAGRAM_EXPLORE,
                DetectedSurface.INSTAGRAM_PROFILE,
                DetectedSurface.INSTAGRAM_OTHER,
                -> PolicyDecision.ALLOW
                in instagramSurfaces -> PolicyDecision.INTERVENE
                else -> PolicyDecision.UNKNOWN_FAIL_OPEN
            }
            TunnelTask.INSTAGRAM_POST -> when (surface) {
                DetectedSurface.INSTAGRAM_CREATE,
                DetectedSurface.INSTAGRAM_PROFILE,
                DetectedSurface.INSTAGRAM_OTHER,
                -> PolicyDecision.ALLOW
                in instagramSurfaces -> PolicyDecision.INTERVENE
                else -> PolicyDecision.UNKNOWN_FAIL_OPEN
            }
            TunnelTask.YOUTUBE_SEARCH_WATCH -> when (surface) {
                DetectedSurface.YOUTUBE_SEARCH,
                DetectedSurface.YOUTUBE_VIDEO,
                -> PolicyDecision.ALLOW
                in youtubeSurfaces -> PolicyDecision.INTERVENE
                else -> PolicyDecision.UNKNOWN_FAIL_OPEN
            }
            TunnelTask.YOUTUBE_SUBSCRIPTIONS -> when (surface) {
                DetectedSurface.YOUTUBE_SUBSCRIPTIONS,
                DetectedSurface.YOUTUBE_VIDEO,
                DetectedSurface.YOUTUBE_SHORTS,
                -> PolicyDecision.ALLOW
                DetectedSurface.YOUTUBE_UNSUBSCRIBED_VIDEO,
                DetectedSurface.YOUTUBE_UNSUBSCRIBED_SHORTS,
                -> PolicyDecision.INTERVENE
                in youtubeSurfaces -> PolicyDecision.INTERVENE
                else -> PolicyDecision.UNKNOWN_FAIL_OPEN
            }
            TunnelTask.YOUTUBE_SHORTS -> when (surface) {
                DetectedSurface.YOUTUBE_SHORTS -> PolicyDecision.ALLOW
                in youtubeSurfaces -> PolicyDecision.INTERVENE
                else -> PolicyDecision.UNKNOWN_FAIL_OPEN
            }
            TunnelTask.TIKTOK_SEARCH_WATCH -> when (surface) {
                DetectedSurface.TIKTOK_FEED,
                DetectedSurface.TIKTOK_FRIENDS,
                DetectedSurface.TIKTOK_PROFILE,
                -> PolicyDecision.INTERVENE
                in tikTokSurfaces -> PolicyDecision.ALLOW
                else -> PolicyDecision.UNKNOWN_FAIL_OPEN
            }
            TunnelTask.TIKTOK_INBOX -> when (surface) {
                DetectedSurface.TIKTOK_FEED,
                DetectedSurface.TIKTOK_FRIENDS,
                -> PolicyDecision.INTERVENE
                in tikTokSurfaces -> PolicyDecision.ALLOW
                else -> PolicyDecision.UNKNOWN_FAIL_OPEN
            }
            TunnelTask.INSTAGRAM_BROWSE -> {
                if (surface in instagramSurfaces) PolicyDecision.ALLOW else PolicyDecision.UNKNOWN_FAIL_OPEN
            }
            TunnelTask.YOUTUBE_BROWSE -> {
                if (surface in youtubeSurfaces) PolicyDecision.ALLOW else PolicyDecision.UNKNOWN_FAIL_OPEN
            }
            TunnelTask.TIKTOK_BROWSE -> {
                if (surface in tikTokSurfaces) PolicyDecision.ALLOW else PolicyDecision.UNKNOWN_FAIL_OPEN
            }
        }
    }

    private val instagramSurfaces = setOf(
        DetectedSurface.INSTAGRAM_MESSAGES,
        DetectedSurface.INSTAGRAM_EXPLORE,
        DetectedSurface.INSTAGRAM_REELS,
        DetectedSurface.INSTAGRAM_HOME,
        DetectedSurface.INSTAGRAM_PROFILE,
        DetectedSurface.INSTAGRAM_CREATE,
        DetectedSurface.INSTAGRAM_OTHER,
    )
    private val youtubeSurfaces = setOf(
        DetectedSurface.YOUTUBE_SEARCH,
        DetectedSurface.YOUTUBE_VIDEO,
        DetectedSurface.YOUTUBE_UNSUBSCRIBED_VIDEO,
        DetectedSurface.YOUTUBE_SHORTS,
        DetectedSurface.YOUTUBE_UNSUBSCRIBED_SHORTS,
        DetectedSurface.YOUTUBE_HOME,
        DetectedSurface.YOUTUBE_SUBSCRIPTIONS,
        DetectedSurface.YOUTUBE_YOU,
        DetectedSurface.YOUTUBE_OTHER,
    )
    private val tikTokSurfaces = setOf(
        DetectedSurface.TIKTOK_FEED,
        DetectedSurface.TIKTOK_FRIENDS,
        DetectedSurface.TIKTOK_SEARCH,
        DetectedSurface.TIKTOK_INBOX,
        DetectedSurface.TIKTOK_PROFILE,
        DetectedSurface.TIKTOK_OTHER,
    )
}

sealed interface TunnelPrompt {
    data class PurposeGate(
        val app: SupportedApp,
        val replacingSessionId: String? = null,
        val preservedDurationMillis: Long? = null,
    ) : TunnelPrompt
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
    val consecutiveAllowCount: Int = 0,
)

data class DetourAllowState(
    val sessionId: String,
    val surface: DetectedSurface,
    val count: Int,
)

data class TunnelRuntimeState(
    val foregroundPackage: String? = null,
    val activeSession: TunnelSession? = null,
    val prompt: TunnelPrompt? = null,
    val overrideScope: ScopedOverride? = null,
    val returnCooldown: ReturnCooldown? = null,
    val checkIn: IntentionCheckInState? = null,
    val currentSurface: DetectedSurface? = null,
    val currentSurfaceObservedAtMillis: Long? = null,
    val detourAllow: DetourAllowState? = null,
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
    private var activeUsePausedAtMillis: Long? = null

    fun setCheckInsEnabled(enabled: Boolean) {
        checkInsEnabled = enabled
        if (!enabled) {
            state = state.copy(
                prompt = state.prompt.takeUnless { it is TunnelPrompt.IntentionCheckIn },
                checkIn = null,
            )
        }
    }

    /** Clears all live tunnel state without changing any persisted user preference. */
    fun reset() {
        activeUsePausedAtMillis = null
        state = TunnelRuntimeState()
    }

    /**
     * Pause active-use-only clocks while the device is not interactable (for example, while
     * keyguard owns the screen). The user's explicit tunnel duration remains wall-clock time,
     * but detour allowances and intentional check-in cadence must not burn behind the lock screen.
     */
    fun pauseActiveUse(nowMillis: Long) {
        if (activeUsePausedAtMillis != null) return
        val activeProtectedSession = state.activeSession?.let {
            it.status == TunnelStatus.ACTIVE && it.app.packageName == state.foregroundPackage
        } == true
        if (!activeProtectedSession) return
        activeUsePausedAtMillis = nowMillis
    }

    fun resumeActiveUse(nowMillis: Long) {
        val pausedAt = activeUsePausedAtMillis ?: return
        activeUsePausedAtMillis = null
        val session = state.activeSession?.takeIf { it.status == TunnelStatus.ACTIVE } ?: return
        if (session.app.packageName != state.foregroundPackage) return
        if (session.isExpiredAt(nowMillis)) {
            advanceTime(nowMillis)
            return
        }
        val pausedDuration = (nowMillis - pausedAt).coerceAtLeast(0L)
        if (pausedDuration == 0L) return
        state = state.copy(
            overrideScope = state.overrideScope?.takeIf { it.sessionId == session.id }?.let {
                it.copy(expiresAtMillis = it.expiresAtMillis + pausedDuration)
            },
            checkIn = state.checkIn?.takeIf { it.sessionId == session.id }?.let { checkIn ->
                checkIn.copy(
                    nextCheckAtMillis = checkIn.nextCheckAtMillis?.plus(pausedDuration),
                )
            },
        )
        advanceTime(nowMillis)
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
                currentSurfaceObservedAtMillis = null,
                detourAllow = null,
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
                currentSurfaceObservedAtMillis = null,
                detourAllow = null,
                leftProtectedAppAtMillis = null,
            )
            app == session.app -> {
                val leftAt = state.leftProtectedAppAtMillis
                val awayDuration = leftAt?.let { (nowMillis - it).coerceAtLeast(0L) } ?: 0L
                val expiredWhileAway = leftAt != null && session.isExpiredAt(nowMillis)
                val graceElapsed = leftAt != null && nowMillis - leftAt >= quickReturnGraceMillis
                state = if (expiredWhileAway || graceElapsed) {
                    state.copy(
                        foregroundPackage = packageName,
                        activeSession = null,
                        prompt = TunnelPrompt.PurposeGate(app),
                        overrideScope = null,
                        returnCooldown = null,
                        checkIn = null,
                        currentSurface = null,
                        currentSurfaceObservedAtMillis = null,
                        detourAllow = null,
                        leftProtectedAppAtMillis = null,
                    )
                } else {
                    state.copy(
                        foregroundPackage = packageName,
                        prompt = null,
                        overrideScope = state.overrideScope?.takeIf { it.sessionId == session.id }
                            ?.let { override ->
                                // Temporary detour allowances are active-use time, independent of
                                // whether intentional check-ins are enabled. Keep the allowance's
                                // remaining time intact across a quick switch away from the
                                // protected app instead of coupling the pause to check-in state.
                                if (leftAt != null) {
                                    override.copy(expiresAtMillis = override.expiresAtMillis + awayDuration)
                                } else override
                            }
                            ?.takeIf { nowMillis < it.expiresAtMillis },
                        returnCooldown = state.returnCooldown?.takeIf {
                            it.sessionId == session.id && nowMillis < it.untilMillis
                        },
                        checkIn = state.checkIn?.takeIf { it.sessionId == session.id }?.let { checkIn ->
                            if (leftAt != null && checkIn.nextCheckAtMillis != null) {
                                checkIn.copy(nextCheckAtMillis = checkIn.nextCheckAtMillis + awayDuration)
                            } else checkIn
                        },
                        currentSurface = state.currentSurface,
                        currentSurfaceObservedAtMillis = state.currentSurfaceObservedAtMillis,
                        detourAllow = state.detourAllow?.takeIf { it.sessionId == session.id && leftAt == null },
                        leftProtectedAppAtMillis = null,
                    )
                }
            }
            else -> {
                val rearmedCheckIn = rearmVisibleCheckIn(nowMillis)?.takeIf { it.sessionId == session.id }
                state = state.copy(
                    foregroundPackage = packageName,
                    prompt = app?.let(TunnelPrompt::PurposeGate),
                    overrideScope = state.overrideScope?.takeIf {
                        it.sessionId == session.id && nowMillis < it.expiresAtMillis
                    },
                    returnCooldown = null,
                    checkIn = rearmedCheckIn,
                    currentSurface = null,
                    currentSurfaceObservedAtMillis = null,
                    detourAllow = state.detourAllow?.takeIf { it.sessionId == session.id },
                    leftProtectedAppAtMillis = state.leftProtectedAppAtMillis ?: nowMillis,
                )
            }
        }
        advanceTime(nowMillis)
    }

    fun dismissPurposeGate() {
        val gate = state.prompt as? TunnelPrompt.PurposeGate ?: return
        state = state.copy(
            prompt = if (gate.replacingSessionId != null && state.activeSession?.status == TunnelStatus.EXPIRED) {
                state.activeSession?.let { TunnelPrompt.SessionExpired(it.id, it.app, it.task) }
            } else null,
        )
    }

    fun requestPurposeGate(app: SupportedApp): Boolean {
        if (state.foregroundPackage != app.packageName) return false
        val activeSession = state.activeSession
        if (activeSession?.app == app) return false
        state = state.copy(
            activeSession = null,
            prompt = TunnelPrompt.PurposeGate(app),
            overrideScope = null,
            returnCooldown = null,
            checkIn = null,
            currentSurface = null,
            currentSurfaceObservedAtMillis = null,
            detourAllow = null,
            leftProtectedAppAtMillis = null,
        )
        return true
    }


    fun requestPurposeChange(sessionId: String, nowMillis: Long): Boolean {
        advanceTime(nowMillis)
        val session = state.activeSession?.takeIf {
            it.id == sessionId && it.status == TunnelStatus.ACTIVE
        } ?: return false
        val remaining = session.expiresAtMillis?.let { (it - nowMillis).coerceAtLeast(0L) }
            ?.takeIf { it > 0L }
        state = state.copy(
            prompt = TunnelPrompt.PurposeGate(
                app = session.app,
                replacingSessionId = session.id,
                preservedDurationMillis = remaining,
            ),
        )
        return true
    }

    fun startSession(
        task: TunnelTask,
        nowMillis: Long,
        intendedDurationMillis: Long? = null,
        evaluateCurrentSurface: Boolean = true,
    ): Boolean {
        advanceTime(nowMillis)
        val gate = state.prompt as? TunnelPrompt.PurposeGate ?: return false
        if (gate.app != task.app) return false
        if (gate.replacingSessionId != null && state.activeSession?.let {
                it.id == gate.replacingSessionId && it.status == TunnelStatus.ACTIVE
            } != true
        ) return false
        val normalizedCurrentSurface = state.currentSurface.normalizeForTask(task)
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
            currentSurface = normalizedCurrentSurface,
            currentSurfaceObservedAtMillis = state.currentSurfaceObservedAtMillis,
            detourAllow = null,
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
        val currentSurface = state.currentSurface
        val observedAt = state.currentSurfaceObservedAtMillis
        if (evaluateCurrentSurface && currentSurface != null && observedAt != null &&
            state.foregroundPackage == task.app.packageName &&
            nowMillis - observedAt <= CURRENT_SURFACE_FRESHNESS_MILLIS
        ) {
            observeSurface(currentSurface, nowMillis)
        }
        return true
    }

    fun reevaluateCurrentSurface(nowMillis: Long) {
        val session = state.activeSession?.takeIf { it.status == TunnelStatus.ACTIVE } ?: return
        val currentSurface = state.currentSurface ?: return
        if (state.foregroundPackage != session.app.packageName) return
        observeSurface(currentSurface, nowMillis)
    }

    fun observeSurface(surface: DetectedSurface, nowMillis: Long) {
        if (surface != DetectedSurface.UNKNOWN) {
            state = state.copy(
                currentSurface = surface,
                currentSurfaceObservedAtMillis = nowMillis,
            )
        }
        val session = state.activeSession?.takeIf {
            it.status == TunnelStatus.ACTIVE && it.app.packageName == state.foregroundPackage
        }
        val visibleDetourCheckIn = (state.prompt as? TunnelPrompt.IntentionCheckIn)?.takeIf {
            it.kind == IntentionCheckInKind.DETOUR_RENEWAL &&
                it.sessionId == session?.id &&
                surface != DetectedSurface.UNKNOWN &&
                it.surface != surface
        }
        if (visibleDetourCheckIn != null) {
            state = state.copy(
                prompt = null,
                checkIn = rearmVisibleCheckIn(nowMillis),
            )
        }
        advanceTime(nowMillis)
        if (state.prompt is TunnelPrompt.PurposeGate || state.prompt is TunnelPrompt.IntentionCheckIn) return
        val activeSession = state.activeSession?.takeIf { it.status == TunnelStatus.ACTIVE } ?: return
        if (activeSession.app.packageName != state.foregroundPackage) return

        val checkIn = state.checkIn?.takeIf { it.sessionId == activeSession.id }

        val override = state.overrideScope?.takeIf {
            it.sessionId == activeSession.id && nowMillis < it.expiresAtMillis
        }
        val cooldown = state.returnCooldown?.takeIf {
            it.sessionId == activeSession.id && it.surface == surface && nowMillis < it.untilMillis
        }
        val decision = SessionPolicy.evaluate(activeSession.task, surface)
        val prompt = when {
            decision != PolicyDecision.INTERVENE -> null
            override?.surface == surface -> null
            cooldown != null -> null
            else -> TunnelPrompt.Intervention(activeSession.id, activeSession.task, surface)
        }
        state = state.copy(
            prompt = prompt,
            overrideScope = override,
            returnCooldown = cooldown,
            checkIn = checkIn,
            currentSurface = surface.takeIf { it != DetectedSurface.UNKNOWN } ?: state.currentSurface,
            currentSurfaceObservedAtMillis = surface.takeIf { it != DetectedSurface.UNKNOWN }?.let { nowMillis }
                ?: state.currentSurfaceObservedAtMillis,
            detourAllow = state.detourAllow?.takeIf { it.sessionId == activeSession.id },
        )
    }

    fun allowAnyway(nowMillis: Long) {
        val intervention = state.prompt as? TunnelPrompt.Intervention ?: return
        val session = state.activeSession?.takeIf { it.id == intervention.sessionId } ?: return
        val previousDetourCheckIn = state.checkIn?.takeIf {
            it.sessionId == session.id &&
                it.kind == IntentionCheckInKind.DETOUR_RENEWAL &&
                it.surface == intervention.surface
        }
        val previousCount = state.detourAllow?.takeIf {
            it.sessionId == session.id && it.surface == intervention.surface
        }?.count ?: previousDetourCheckIn?.consecutiveAllowCount ?: 0
        val allowCount = (previousCount + 1).coerceAtMost(MAX_ALLOW_BACKOFF_MULTIPLIER)
        val overrideDuration = allowAnywayDurationMillis * allowCount
        state = state.copy(
            activeSession = session.copy(overrideOccurred = true),
            prompt = null,
            overrideScope = ScopedOverride(
                sessionId = session.id,
                surface = intervention.surface,
                expiresAtMillis = nowMillis + overrideDuration,
            ),
            checkIn = if (checkInsEnabled) {
                IntentionCheckInState(
                    sessionId = session.id,
                    kind = IntentionCheckInKind.DETOUR_RENEWAL,
                    surface = intervention.surface,
                    shownCount = previousDetourCheckIn?.shownCount ?: 0,
                    nextCheckAtMillis = if (previousDetourCheckIn?.suppressed == true) {
                        null
                    } else {
                        nowMillis + overrideDuration
                    },
                    suppressed = previousDetourCheckIn?.suppressed == true,
                    consecutiveAllowCount = allowCount,
                )
            } else null,
            currentSurface = intervention.surface,
            detourAllow = DetourAllowState(session.id, intervention.surface, allowCount),
            returnCooldown = null,
        )
    }

    fun returnFromIntervention(nowMillis: Long, addReturnCooldown: Boolean = true): Boolean {
        val intervention = state.prompt as? TunnelPrompt.Intervention ?: return false
        state = state.copy(
            prompt = null,
            detourAllow = null,
            returnCooldown = if (addReturnCooldown) {
                ReturnCooldown(
                    intervention.sessionId,
                    intervention.surface,
                    nowMillis + returnCooldownMillis,
                )
            } else null,
        )
        return true
    }

    fun returnFromCheckIn(nowMillis: Long, addReturnCooldown: Boolean = true): Boolean {
        val checkIn = state.prompt as? TunnelPrompt.IntentionCheckIn ?: return false
        state = state.copy(
            prompt = null,
            checkIn = null,
            detourAllow = null,
            returnCooldown = if (addReturnCooldown) {
                checkIn.surface?.let {
                    ReturnCooldown(checkIn.sessionId, it, nowMillis + returnCooldownMillis)
                }
            } else null,
        )
        return true
    }

    fun continueCheckIn(nowMillis: Long) {
        val prompt = state.prompt as? TunnelPrompt.IntentionCheckIn ?: return
        val session = state.activeSession?.takeIf { it.id == prompt.sessionId } ?: return
        val checkIn = state.checkIn?.takeIf { it.sessionId == session.id } ?: return
        val nextCount = prompt.shownCount
        val suppress = nextCount >= MAX_CHECK_INS
        val allowCount = if (checkIn.kind == IntentionCheckInKind.DETOUR_RENEWAL) {
            (checkIn.consecutiveAllowCount + 1).coerceAtMost(MAX_ALLOW_BACKOFF_MULTIPLIER)
        } else checkIn.consecutiveAllowCount
        val detourRenewalExpiresAt = nowMillis + detourRenewalExtensionMillis
        val nextCheck = when {
            suppress -> null
            checkIn.kind == IntentionCheckInKind.DETOUR_RENEWAL -> detourRenewalExpiresAt
            else -> nowMillis + browseCheckInIntervalMillis
        }
        state = state.copy(
            prompt = null,
            checkIn = checkIn.copy(shownCount = nextCount, nextCheckAtMillis = nextCheck, suppressed = suppress, consecutiveAllowCount = allowCount),
            detourAllow = if (checkIn.kind == IntentionCheckInKind.DETOUR_RENEWAL && checkIn.surface != null) {
                DetourAllowState(session.id, checkIn.surface, allowCount)
            } else state.detourAllow,
            overrideScope = if (checkIn.kind == IntentionCheckInKind.DETOUR_RENEWAL && checkIn.surface != null) {
                ScopedOverride(session.id, checkIn.surface, detourRenewalExpiresAt)
            } else state.overrideScope,
        )
    }

    private fun rearmVisibleCheckIn(nowMillis: Long): IntentionCheckInState? {
        val prompt = state.prompt as? TunnelPrompt.IntentionCheckIn ?: return state.checkIn
        val checkIn = state.checkIn?.takeIf { it.sessionId == prompt.sessionId } ?: return state.checkIn
        return checkIn.copy(
            // Re-showing the same unresolved check-in after a foreground/surface detour should not
            // consume another logical check-in slot or create a duplicate Attention event.
            shownCount = (checkIn.shownCount - 1).coerceAtLeast(0),
            nextCheckAtMillis = nowMillis,
        )
    }

    fun updateTimeLimit(sessionId: String, nowMillis: Long, durationMillis: Long?): Boolean {
        val session = state.activeSession?.takeIf { it.id == sessionId && it.status == TunnelStatus.ACTIVE } ?: return false
        val normalizedDuration = durationMillis?.takeIf { it > 0L }
        val nextCheckIn = when {
            session.task.isOpenEndedBrowse && normalizedDuration == null && checkInsEnabled -> {
                state.checkIn?.takeIf {
                    it.sessionId == session.id && it.kind == IntentionCheckInKind.OPEN_ENDED_BROWSE
                } ?: IntentionCheckInState(
                    sessionId = session.id,
                    kind = IntentionCheckInKind.OPEN_ENDED_BROWSE,
                    nextCheckAtMillis = nowMillis + browseCheckInIntervalMillis,
                )
            }
            state.checkIn?.let { it.sessionId == session.id && it.kind == IntentionCheckInKind.OPEN_ENDED_BROWSE } == true -> null
            else -> state.checkIn
        }
        state = state.copy(
            activeSession = session.copy(
                startedAtMillis = nowMillis,
                intendedDurationMillis = normalizedDuration,
            ),
            checkIn = nextCheckIn,
        )
        return true
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
            overrideScope = null,
            returnCooldown = null,
            checkIn = null,
            detourAllow = null,
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
            currentSurfaceObservedAtMillis = null,
            detourAllow = null,
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
            currentSurfaceObservedAtMillis = null,
            detourAllow = null,
            leftProtectedAppAtMillis = null,
        )
    }

    fun advanceTime(nowMillis: Long) {
        val session = state.activeSession ?: return
        val previousOverride = state.overrideScope?.takeIf { it.sessionId == session.id }
        val cooldown = state.returnCooldown?.takeIf {
            it.sessionId == session.id && nowMillis < it.untilMillis
        }

        if (activeUsePausedAtMillis != null) {
            if (session.status == TunnelStatus.ACTIVE && session.isExpiredAt(nowMillis)) {
                state = state.copy(
                    activeSession = session.copy(status = TunnelStatus.EXPIRED),
                    prompt = TunnelPrompt.SessionExpired(session.id, session.app, session.task),
                    overrideScope = null,
                    returnCooldown = null,
                    checkIn = null,
                    detourAllow = null,
                )
            } else {
                // Return cooldown is wall-clock protection against stale navigation evidence, but
                // active-use allowance/check-in clocks stay frozen until resumeActiveUse().
                state = state.copy(returnCooldown = cooldown)
            }
            return
        }

        val override = previousOverride?.takeIf { nowMillis < it.expiresAtMillis }
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
                    checkIn = null,
                    detourAllow = null,
                    leftProtectedAppAtMillis = null,
                )
                return
            }
            // A quick app switch does not end the tunnel, but tunnel check-ins must never
            // mature over Home or another app. Preserve the pending browse reminder until return.
            state = state.copy(overrideScope = override, returnCooldown = cooldown)
            return
        }

        if (session.isExpiredAt(nowMillis)) {
            state = state.copy(
                activeSession = session.copy(status = TunnelStatus.EXPIRED),
                prompt = TunnelPrompt.SessionExpired(session.id, session.app, session.task),
                overrideScope = null,
                returnCooldown = null,
                checkIn = null,
                detourAllow = null,
            )
            return
        }

        // If check-ins were toggled off and back on during an open-ended browse tunnel,
        // restart the cadence from now instead of resurrecting an old overdue reminder.
        if (checkInsEnabled && state.prompt == null && state.checkIn == null &&
            session.task.isOpenEndedBrowse && session.intendedDurationMillis == null &&
            state.foregroundPackage == session.app.packageName
        ) {
            state = state.copy(
                checkIn = IntentionCheckInState(
                    sessionId = session.id,
                    kind = IntentionCheckInKind.OPEN_ENDED_BROWSE,
                    nextCheckAtMillis = nowMillis + browseCheckInIntervalMillis,
                ),
            )
        }

        if (checkInsEnabled && state.prompt == null) {
            val checkIn = state.checkIn?.takeIf { it.sessionId == session.id && !it.suppressed }
            if (checkIn?.nextCheckAtMillis != null && nowMillis >= checkIn.nextCheckAtMillis &&
                (checkIn.kind == IntentionCheckInKind.OPEN_ENDED_BROWSE || state.currentSurface == checkIn.surface)
            ) {
                state = state.copy(
                    prompt = TunnelPrompt.IntentionCheckIn(
                        session.id,
                        session.task,
                        checkIn.kind,
                        checkIn.surface,
                        checkIn.shownCount + 1,
                    ),
                    checkIn = checkIn.copy(shownCount = checkIn.shownCount + 1, nextCheckAtMillis = null),
                    overrideScope = null,
                )
                return
            }
        }

        // Expiring a temporary allowance must restore correction immediately even if the
        // accessibility tree does not emit another event at the exact expiry moment.
        if (state.prompt == null && previousOverride != null && override == null &&
            state.foregroundPackage == session.app.packageName &&
            state.currentSurface == previousOverride.surface &&
            SessionPolicy.evaluate(session.task, previousOverride.surface) == PolicyDecision.INTERVENE
        ) {
            state = state.copy(
                prompt = TunnelPrompt.Intervention(session.id, session.task, previousOverride.surface),
                overrideScope = null,
                returnCooldown = cooldown,
            )
            return
        }

        state = state.copy(overrideScope = override, returnCooldown = cooldown)
    }

    fun nextDeadlineMillis(): Long? {
        val session = state.activeSession ?: return null
        if (activeUsePausedAtMillis != null) {
            return session.takeIf { it.status == TunnelStatus.ACTIVE }?.expiresAtMillis
        }
        val deadlines = buildList {
            state.overrideScope?.takeIf { it.sessionId == session.id }?.let { add(it.expiresAtMillis) }
            if (session.status == TunnelStatus.ACTIVE) {
                session.expiresAtMillis?.let(::add)
                state.leftProtectedAppAtMillis?.let { add(it + quickReturnGraceMillis) }
                if (checkInsEnabled && state.leftProtectedAppAtMillis == null && state.prompt == null &&
                    state.foregroundPackage == session.app.packageName
                ) {
                    state.checkIn?.takeIf { !it.suppressed && it.sessionId == session.id }
                        ?.nextCheckAtMillis?.let(::add)
                }
            }
        }
        return deadlines.minOrNull()
    }

    private fun DetectedSurface?.normalizeForTask(task: TunnelTask): DetectedSurface? = when {
        task == TunnelTask.YOUTUBE_SUBSCRIPTIONS -> this
        this == DetectedSurface.YOUTUBE_UNSUBSCRIBED_VIDEO -> DetectedSurface.YOUTUBE_VIDEO
        this == DetectedSurface.YOUTUBE_UNSUBSCRIBED_SHORTS -> DetectedSurface.YOUTUBE_SHORTS
        else -> this
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
        const val MAX_ALLOW_BACKOFF_MULTIPLIER = 3
        const val CURRENT_SURFACE_FRESHNESS_MILLIS = 5_000L
    }
}

private val TunnelTask.isOpenEndedBrowse: Boolean
    get() = this == TunnelTask.INSTAGRAM_BROWSE || this == TunnelTask.YOUTUBE_BROWSE || this == TunnelTask.TIKTOK_BROWSE
