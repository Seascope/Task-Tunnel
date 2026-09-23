package com.example.tasktunnel.attention

import com.example.tasktunnel.tunnel.DetectedSurface
import com.example.tasktunnel.tunnel.TunnelTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewInsightFactoryTest {
    @Test
    fun mergesCommonDetourAndRecoveryIntoOneInsight() {
        val common = pattern(
            type = AttentionPatternType.COMMON_DETOUR_SURFACE,
            surface = DetectedSurface.INSTAGRAM_REELS,
            evidence = 4,
            sample = 6,
        )
        val recovery = pattern(
            type = AttentionPatternType.RECOVERY_AFTER_SURFACE,
            surface = DetectedSurface.INSTAGRAM_REELS,
            evidence = 3,
            sample = 4,
        )

        val insights = ReviewInsightFactory.create(listOf(common, recovery))

        assertEquals(1, insights.size)
        assertEquals(ReviewInsightType.DETOUR, insights.single().type)
        assertTrue(insights.single().headline.contains("Reels"))
        assertEquals("You returned 3 of those 4 times.", insights.single().detailText)
    }

    @Test
    fun prioritizesDetourThenDriftPathThenDriftTiming() {
        val patterns = listOf(
            pattern(AttentionPatternType.TIME_OF_DAY_DRIFT, evidence = 4, sample = 6, timeBucket = DriftTimeBucket.LATE_NIGHT),
            pattern(
                AttentionPatternType.RECURRING_DRIFT_PATH,
                evidence = 3,
                sample = 3,
                packages = listOf("com.instagram.android", "com.reddit.frontpage", "com.google.android.youtube"),
            ),
            pattern(AttentionPatternType.COMMON_DETOUR_SURFACE, DetectedSurface.INSTAGRAM_REELS, 4, 6),
        )

        val insights = ReviewInsightFactory.create(patterns)

        assertEquals(
            listOf(ReviewInsightType.DETOUR, ReviewInsightType.DRIFT_PATH, ReviewInsightType.DRIFT_TIMING),
            insights.map { it.type },
        )
    }

    @Test
    fun capsInsightsAtThreeAndKeepsDistinctDetoursForSpareSlots() {
        val first = pattern(AttentionPatternType.COMMON_DETOUR_SURFACE, DetectedSurface.INSTAGRAM_REELS, 5, 7)
        val second = pattern(
            AttentionPatternType.COMMON_DETOUR_SURFACE,
            DetectedSurface.INSTAGRAM_EXPLORE,
            4,
            6,
            task = TunnelTask.INSTAGRAM_POST,
        )
        val third = pattern(
            AttentionPatternType.RECOVERY_AFTER_SURFACE,
            DetectedSurface.INSTAGRAM_HOME,
            4,
            5,
            task = TunnelTask.INSTAGRAM_BROWSE,
        )
        val timing = pattern(
            AttentionPatternType.TIME_OF_DAY_DRIFT,
            evidence = 4,
            sample = 6,
            timeBucket = DriftTimeBucket.EVENING,
        )

        val insights = ReviewInsightFactory.create(listOf(first, second, third, timing))

        assertEquals(3, insights.size)
        assertEquals(ReviewInsightType.DETOUR, insights[0].type)
        assertEquals(ReviewInsightType.DRIFT_TIMING, insights[1].type)
        assertEquals(ReviewInsightType.DETOUR, insights[2].type)
    }

    private fun pattern(
        type: AttentionPatternType,
        surface: DetectedSurface? = null,
        evidence: Int,
        sample: Int,
        task: TunnelTask? = TunnelTask.INSTAGRAM_MESSAGES,
        timeBucket: DriftTimeBucket? = null,
        packages: List<String> = emptyList(),
    ) = AttentionPattern(
        type = type,
        app = if (type == AttentionPatternType.COMMON_DETOUR_SURFACE || type == AttentionPatternType.RECOVERY_AFTER_SURFACE) AttentionApp.INSTAGRAM else null,
        task = task,
        surface = surface,
        packageSequence = packages,
        timeBucket = timeBucket,
        evidenceCount = evidence,
        sampleSize = sample,
        latestEvidenceMillis = 100L,
    )
}
