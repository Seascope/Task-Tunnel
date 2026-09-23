package com.example.tasktunnel.attention

import com.example.tasktunnel.tunnel.TunnelTask

enum class ReviewInsightType {
    DETOUR,
    DRIFT_PATH,
    DRIFT_TIMING,
}

data class ReviewInsight(
    val type: ReviewInsightType,
    val headline: String,
    val supportingText: String,
    val detailText: String? = null,
    val app: AttentionApp? = null,
    val packageSequence: List<String> = emptyList(),
)

/**
 * Converts qualifying analytics patterns into a small set of human-readable takeaways.
 *
 * Review intentionally exposes very little: one strongest detour story, one recurring Drift
 * path, one Drift timing tendency, then another distinct detour only if a slot remains.
 * Related detour + recovery patterns are merged into one insight instead of competing for space.
 */
object ReviewInsightFactory {
    fun create(patterns: List<AttentionPattern>, maxInsights: Int = 3): List<ReviewInsight> {
        if (maxInsights <= 0 || patterns.isEmpty()) return emptyList()

        val commonDetours = patterns.filter { it.type == AttentionPatternType.COMMON_DETOUR_SURFACE }
        val recoveries = patterns.filter { it.type == AttentionPatternType.RECOVERY_AFTER_SURFACE }
        val recurringDrift = patterns.firstOrNull { it.type == AttentionPatternType.RECURRING_DRIFT_PATH }
        val driftTiming = patterns.firstOrNull { it.type == AttentionPatternType.TIME_OF_DAY_DRIFT }

        val insights = mutableListOf<ReviewInsight>()
        val usedRecoveryPatterns = mutableSetOf<AttentionPattern>()

        commonDetours.firstOrNull()?.let { common ->
            val recovery = matchingRecovery(common, recoveries)
            if (recovery != null) usedRecoveryPatterns += recovery
            insights += detourInsight(common, recovery)
        }

        recurringDrift?.let { insights += driftPathInsight(it) }
        driftTiming?.let { insights += driftTimingInsight(it) }

        commonDetours.drop(1).forEach { common ->
            if (insights.size >= maxInsights) return@forEach
            val recovery = matchingRecovery(common, recoveries)
            if (recovery != null) usedRecoveryPatterns += recovery
            insights += detourInsight(common, recovery)
        }

        recoveries.forEach { recovery ->
            if (insights.size >= maxInsights) return@forEach
            if (recovery !in usedRecoveryPatterns && commonDetours.none { sameDetourContext(it, recovery) }) {
                insights += recoveryInsight(recovery)
            }
        }

        return insights.take(maxInsights)
    }

    private fun matchingRecovery(
        common: AttentionPattern,
        recoveries: List<AttentionPattern>,
    ): AttentionPattern? = recoveries.firstOrNull { sameDetourContext(common, it) }

    private fun sameDetourContext(a: AttentionPattern, b: AttentionPattern): Boolean =
        a.app == b.app && a.task == b.task && a.surface == b.surface

    private fun detourInsight(common: AttentionPattern, recovery: AttentionPattern?): ReviewInsight {
        val surface = surfaceLabel(common.surface)
        val appName = common.app?.displayName ?: "this app"
        val taskContext = taskContext(common.task)
        val headline = if (taskContext != null) {
            "$surface is your most common detour when you're $taskContext."
        } else {
            "$surface is your most common detour in $appName."
        }
        val detail = recovery?.let {
            if (it.sampleSize == common.evidenceCount) {
                "You returned ${it.evidenceCount} of those ${it.sampleSize} times."
            } else {
                "You returned after ${it.evidenceCount} of ${it.sampleSize} recent $surface detours."
            }
        }
        return ReviewInsight(
            type = ReviewInsightType.DETOUR,
            headline = headline,
            supportingText = "${common.evidenceCount} of your last ${common.sampleSize} $appName detours went there.",
            detailText = detail,
            app = common.app,
        )
    }

    private fun recoveryInsight(recovery: AttentionPattern): ReviewInsight {
        val surface = surfaceLabel(recovery.surface)
        return ReviewInsight(
            type = ReviewInsightType.DETOUR,
            headline = "You usually return after $surface.",
            supportingText = "You went back after ${recovery.evidenceCount} of your last ${recovery.sampleSize} detours there.",
            app = recovery.app,
        )
    }

    private fun driftPathInsight(pattern: AttentionPattern) = ReviewInsight(
        type = ReviewInsightType.DRIFT_PATH,
        headline = "A familiar Drift path keeps showing up.",
        supportingText = "This sequence has appeared ${pattern.evidenceCount} times recently.",
        packageSequence = pattern.packageSequence,
    )

    private fun driftTimingInsight(pattern: AttentionPattern): ReviewInsight {
        val bucket = pattern.timeBucket
        val phrase = when (bucket) {
            DriftTimeBucket.MORNING -> "in the morning"
            DriftTimeBucket.AFTERNOON -> "in the afternoon"
            DriftTimeBucket.EVENING -> "in the evening"
            DriftTimeBucket.LATE_NIGHT -> "late at night"
            null -> "around a similar time"
        }
        return ReviewInsight(
            type = ReviewInsightType.DRIFT_TIMING,
            headline = "Drift tends to happen $phrase.",
            supportingText = "${pattern.evidenceCount} of your last ${pattern.sampleSize} Drift check-ins started $phrase.",
        )
    }

    private fun taskContext(task: TunnelTask?): String? = when (task) {
        TunnelTask.INSTAGRAM_MESSAGES -> "replying to messages"
        TunnelTask.INSTAGRAM_SEARCH -> "looking something up"
        TunnelTask.INSTAGRAM_POST -> "posting something"
        TunnelTask.INSTAGRAM_BROWSE -> "browsing intentionally"
        TunnelTask.YOUTUBE_SEARCH_WATCH -> "searching for or watching something specific"
        TunnelTask.YOUTUBE_SUBSCRIPTIONS -> "checking subscriptions"
        TunnelTask.YOUTUBE_SHORTS -> "watching Shorts intentionally"
        TunnelTask.YOUTUBE_BROWSE -> "browsing intentionally"
        TunnelTask.TIKTOK_SEARCH_WATCH -> "searching for or watching something specific"
        TunnelTask.TIKTOK_INBOX -> "checking your Inbox"
        TunnelTask.TIKTOK_BROWSE -> "browsing intentionally"
        null -> null
    }
}
