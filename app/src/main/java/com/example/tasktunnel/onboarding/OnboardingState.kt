package com.example.tasktunnel.onboarding

import android.content.Context

enum class OnboardingStep { VALUE, HOW_IT_WORKS, DISCLOSURE, VERIFY, CONFIGURE }

data class OnboardingProgress(
    val completed: Boolean = false,
    val step: OnboardingStep = OnboardingStep.VALUE,
)

object OnboardingFlow {
    fun next(progress: OnboardingProgress): OnboardingProgress = when (progress.step) {
        OnboardingStep.VALUE -> progress.copy(step = OnboardingStep.HOW_IT_WORKS)
        OnboardingStep.HOW_IT_WORKS -> progress.copy(step = OnboardingStep.DISCLOSURE)
        OnboardingStep.DISCLOSURE -> progress.copy(step = OnboardingStep.VERIFY)
        OnboardingStep.VERIFY -> progress.copy(step = OnboardingStep.CONFIGURE)
        OnboardingStep.CONFIGURE -> progress.copy(completed = true)
    }

    fun completeLater(): OnboardingProgress = OnboardingProgress(completed = true)
}

object OnboardingPreferences {
    private const val NAME = "onboarding"
    private const val COMPLETED = "completed"
    private const val STEP = "step"

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
}
