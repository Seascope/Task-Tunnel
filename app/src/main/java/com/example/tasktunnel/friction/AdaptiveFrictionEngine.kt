package com.example.tasktunnel.friction

import com.example.tasktunnel.tunnel.DetectedSurface
import com.example.tasktunnel.tunnel.SupportedApp
import com.example.tasktunnel.tunnel.TunnelTask

enum class InterventionVariant {
    DIRECT,
    INTENT_RECALL,
    SHORT_PAUSE,
}

enum class FrictionOutcome {
    RETURN,
    END_TUNNEL,
    ALLOW_ANYWAY,
}

data class FrictionContext(
    val app: SupportedApp,
    val task: TunnelTask,
    val surface: DetectedSurface,
) {
    init {
        require(task.app == app) { "Friction context app must match the task app." }
    }

    companion object {
        fun from(task: TunnelTask, surface: DetectedSurface): FrictionContext = FrictionContext(
            app = task.app,
            task = task,
            surface = surface,
        )
    }
}

data class FrictionProfile(
    val directScore: Int = 0,
    val intentRecallScore: Int = 0,
    val shortPauseScore: Int = 0,
    val completedInteractions: Int = 0,
    val lastVariant: InterventionVariant? = null,
) {
    fun scoreFor(variant: InterventionVariant): Int = when (variant) {
        InterventionVariant.DIRECT -> directScore
        InterventionVariant.INTENT_RECALL -> intentRecallScore
        InterventionVariant.SHORT_PAUSE -> shortPauseScore
    }

    fun withScore(variant: InterventionVariant, score: Int): FrictionProfile = when (variant) {
        InterventionVariant.DIRECT -> copy(directScore = score)
        InterventionVariant.INTENT_RECALL -> copy(intentRecallScore = score)
        InterventionVariant.SHORT_PAUSE -> copy(shortPauseScore = score)
    }
}

/**
 * Pure deterministic intervention selector.
 *
 * The first three completed encounters deliberately sample the small intervention library:
 * Direct -> Intent Recall -> Short Pause. After that, recent effectiveness scores drive selection.
 * The previous variant is avoided when another variant is within [reasonableScoreGap] points of the
 * current best score, which keeps the system from becoming visually habitual without forcing a
 * clearly ineffective intervention.
 */
class AdaptiveFrictionEngine(
    private val reasonableScoreGap: Int = DEFAULT_REASONABLE_SCORE_GAP,
) {
    fun chooseVariant(profile: FrictionProfile): InterventionVariant {
        return when (profile.completedInteractions) {
            0 -> InterventionVariant.DIRECT
            1 -> InterventionVariant.INTENT_RECALL
            2 -> InterventionVariant.SHORT_PAUSE
            else -> chooseScoredVariant(profile)
        }
    }

    fun recordOutcome(
        profile: FrictionProfile,
        variant: InterventionVariant,
        outcome: FrictionOutcome,
    ): FrictionProfile {
        val delta = when (outcome) {
            FrictionOutcome.RETURN -> 2
            FrictionOutcome.END_TUNNEL -> 1
            FrictionOutcome.ALLOW_ANYWAY -> -1
        }
        val nextScore = (profile.scoreFor(variant) + delta).coerceIn(MIN_SCORE, MAX_SCORE)
        return profile
            .withScore(variant, nextScore)
            .copy(
                completedInteractions = profile.completedInteractions + 1,
                lastVariant = variant,
            )
    }

    private fun chooseScoredVariant(profile: FrictionProfile): InterventionVariant {
        val variants = InterventionVariant.entries
        val bestScore = variants.maxOf(profile::scoreFor)
        val reasonable = variants.filter { variant ->
            profile.scoreFor(variant) >= bestScore - reasonableScoreGap
        }
        val nonRepeating = reasonable.filter { it != profile.lastVariant }
        val pool = nonRepeating.ifEmpty { reasonable }
        val poolBestScore = pool.maxOf(profile::scoreFor)
        val tiedBest = pool.filter { profile.scoreFor(it) == poolBestScore }.toSet()

        val rotationOffset = profile.completedInteractions.mod(variants.size)
        val deterministicOrder = variants.drop(rotationOffset) + variants.take(rotationOffset)
        return deterministicOrder.first { it in tiedBest }
    }

    companion object {
        const val MIN_SCORE = -4
        const val MAX_SCORE = 6
        const val DEFAULT_REASONABLE_SCORE_GAP = 2
        const val SHORT_PAUSE_MILLIS = 2_000L
    }
}
