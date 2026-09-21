package com.example.tasktunnel.friction

import android.content.Context

/** Stores only tiny intervention-effectiveness counters. No accessibility or user content is stored. */
class AdaptiveFrictionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: FrictionContext): FrictionProfile {
        val prefix = keyPrefix(context)
        val lastVariant = preferences.getString("$prefix.last_variant", null)?.let { stored ->
            runCatching { InterventionVariant.valueOf(stored) }.getOrNull()
        }
        return FrictionProfile(
            directScore = preferences.getInt("$prefix.direct_score", 0)
                .coerceIn(AdaptiveFrictionEngine.MIN_SCORE, AdaptiveFrictionEngine.MAX_SCORE),
            intentRecallScore = preferences.getInt("$prefix.intent_recall_score", 0)
                .coerceIn(AdaptiveFrictionEngine.MIN_SCORE, AdaptiveFrictionEngine.MAX_SCORE),
            shortPauseScore = preferences.getInt("$prefix.short_pause_score", 0)
                .coerceIn(AdaptiveFrictionEngine.MIN_SCORE, AdaptiveFrictionEngine.MAX_SCORE),
            completedInteractions = preferences.getInt("$prefix.completed", 0).coerceAtLeast(0),
            lastVariant = lastVariant,
        )
    }

    fun save(context: FrictionContext, profile: FrictionProfile) {
        val prefix = keyPrefix(context)
        preferences.edit()
            .putInt("$prefix.direct_score", profile.directScore)
            .putInt("$prefix.intent_recall_score", profile.intentRecallScore)
            .putInt("$prefix.short_pause_score", profile.shortPauseScore)
            .putInt("$prefix.completed", profile.completedInteractions)
            .apply {
                if (profile.lastVariant == null) {
                    remove("$prefix.last_variant")
                } else {
                    putString("$prefix.last_variant", profile.lastVariant.name)
                }
            }
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun keyPrefix(context: FrictionContext): String = buildString {
        append(context.app.name)
        append('.')
        append(context.task.name)
        append('.')
        append(context.surface.name)
    }

    companion object {
        const val PREFS_NAME = "adaptive_friction"
    }
}
