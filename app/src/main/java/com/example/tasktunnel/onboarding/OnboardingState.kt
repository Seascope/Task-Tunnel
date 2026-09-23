package com.example.tasktunnel.onboarding

import android.content.Context

enum class OnboardingStep { VALUE, APPS, ACCESSIBILITY, DRIFT, TRY, READY }

data class OnboardingProgress(
    val completed: Boolean = false,
    val step: OnboardingStep = OnboardingStep.VALUE,
)

data class SetupChecklistState(
    val supportedAppInstalled: Boolean,
    val accessibilityEnabled: Boolean,
    val firstTunnelStarted: Boolean,
) {
    val complete: Boolean
        get() = supportedAppInstalled && accessibilityEnabled && firstTunnelStarted
}

object OnboardingFlow {
    fun next(progress: OnboardingProgress): OnboardingProgress = when (progress.step) {
        OnboardingStep.VALUE -> progress.copy(step = OnboardingStep.APPS)
        OnboardingStep.APPS -> progress.copy(step = OnboardingStep.ACCESSIBILITY)
        OnboardingStep.ACCESSIBILITY -> progress.copy(step = OnboardingStep.DRIFT)
        OnboardingStep.DRIFT -> progress.copy(step = OnboardingStep.TRY)
        OnboardingStep.TRY -> progress.copy(step = OnboardingStep.READY)
        OnboardingStep.READY -> progress.copy(completed = true)
    }

    fun completeLater(): OnboardingProgress = OnboardingProgress(completed = true)
}

object OnboardingPreferences {
    private const val NAME = "onboarding"
    private const val COMPLETED = "completed"
    private const val STEP = "step"
    private const val FIRST_TUNNEL_STARTED = "first_tunnel_started"

    fun load(context: Context): OnboardingProgress {
        val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val step = runCatching {
            OnboardingStep.valueOf(preferences.getString(STEP, null) ?: OnboardingStep.VALUE.name)
        }.getOrDefault(OnboardingStep.VALUE)
        return OnboardingProgress(preferences.getBoolean(COMPLETED, false), step)
    }

    fun save(context: Context, progress: OnboardingProgress) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(COMPLETED, progress.completed)
            .putString(STEP, progress.step.name)
            .apply()
    }

    fun markFirstTunnelStarted(context: Context) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(FIRST_TUNNEL_STARTED, true)
            .apply()
    }

    fun hasStartedFirstTunnel(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(FIRST_TUNNEL_STARTED, false)
}
