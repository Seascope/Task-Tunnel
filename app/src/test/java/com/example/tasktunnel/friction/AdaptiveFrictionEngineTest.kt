package com.example.tasktunnel.friction

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveFrictionEngineTest {
    private val engine = AdaptiveFrictionEngine()

    @Test
    fun firstThreeCompletedEncountersBootstrapAllVariants() {
        var profile = FrictionProfile()
        assertEquals(InterventionVariant.DIRECT, engine.chooseVariant(profile))

        profile = engine.recordOutcome(profile, InterventionVariant.DIRECT, FrictionOutcome.RETURN)
        assertEquals(InterventionVariant.INTENT_RECALL, engine.chooseVariant(profile))

        profile = engine.recordOutcome(profile, InterventionVariant.INTENT_RECALL, FrictionOutcome.RETURN)
        assertEquals(InterventionVariant.SHORT_PAUSE, engine.chooseVariant(profile))
    }

    @Test
    fun returnAddsTwoPoints() {
        val updated = engine.recordOutcome(
            FrictionProfile(),
            InterventionVariant.DIRECT,
            FrictionOutcome.RETURN,
        )
        assertEquals(2, updated.directScore)
        assertEquals(1, updated.completedInteractions)
        assertEquals(InterventionVariant.DIRECT, updated.lastVariant)
    }

    @Test
    fun endTunnelAddsOnePoint() {
        val updated = engine.recordOutcome(
            FrictionProfile(),
            InterventionVariant.INTENT_RECALL,
            FrictionOutcome.END_TUNNEL,
        )
        assertEquals(1, updated.intentRecallScore)
    }

    @Test
    fun allowAnywaySubtractsOnePoint() {
        val updated = engine.recordOutcome(
            FrictionProfile(),
            InterventionVariant.SHORT_PAUSE,
            FrictionOutcome.ALLOW_ANYWAY,
        )
        assertEquals(-1, updated.shortPauseScore)
    }

    @Test
    fun scoresAreClamped() {
        val high = engine.recordOutcome(
            FrictionProfile(directScore = AdaptiveFrictionEngine.MAX_SCORE),
            InterventionVariant.DIRECT,
            FrictionOutcome.RETURN,
        )
        val low = engine.recordOutcome(
            FrictionProfile(shortPauseScore = AdaptiveFrictionEngine.MIN_SCORE),
            InterventionVariant.SHORT_PAUSE,
            FrictionOutcome.ALLOW_ANYWAY,
        )
        assertEquals(AdaptiveFrictionEngine.MAX_SCORE, high.directScore)
        assertEquals(AdaptiveFrictionEngine.MIN_SCORE, low.shortPauseScore)
    }

    @Test
    fun avoidsRepeatingLastVariantWhenAnotherVariantIsReasonable() {
        val profile = FrictionProfile(
            directScore = 5,
            intentRecallScore = 4,
            shortPauseScore = 0,
            completedInteractions = 6,
            lastVariant = InterventionVariant.DIRECT,
        )
        assertEquals(InterventionVariant.INTENT_RECALL, engine.chooseVariant(profile))
    }

    @Test
    fun repeatsClearlyBetterVariantWhenAlternativesAreNotReasonable() {
        val profile = FrictionProfile(
            directScore = 6,
            intentRecallScore = 0,
            shortPauseScore = -2,
            completedInteractions = 6,
            lastVariant = InterventionVariant.DIRECT,
        )
        assertEquals(InterventionVariant.DIRECT, engine.chooseVariant(profile))
    }

    @Test
    fun tieBreakingIsDeterministic() {
        val profile = FrictionProfile(
            directScore = 2,
            intentRecallScore = 2,
            shortPauseScore = 2,
            completedInteractions = 4,
            lastVariant = null,
        )
        assertEquals(engine.chooseVariant(profile), engine.chooseVariant(profile))
    }
}
