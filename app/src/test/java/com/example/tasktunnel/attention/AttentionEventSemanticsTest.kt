package com.example.tasktunnel.attention

import com.example.tasktunnel.drift.DriftEpisode
import com.example.tasktunnel.tunnel.DetectedSurface
import com.example.tasktunnel.tunnel.SupportedApp
import com.example.tasktunnel.tunnel.TunnelPrompt
import com.example.tasktunnel.tunnel.TunnelSession
import com.example.tasktunnel.tunnel.TunnelTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionEventSemanticsTest {
    private val semantics = AttentionEventSemantics()
    private val session = TunnelSession(
        id = "tunnel-1",
        app = SupportedApp.INSTAGRAM,
        task = TunnelTask.INSTAGRAM_MESSAGES,
        startedAtMillis = 100,
    )

    @Test
    fun purposeSelectionRecordsStructuredIntent() {
        val event = semantics.purposeSelected(session, 100)

        assertEquals(AttentionEventType.INTENT, event.type)
        assertEquals(AttentionSubtype.PURPOSE_SELECTED, event.subtype)
        assertEquals(AttentionApp.INSTAGRAM, event.app)
        assertEquals(TunnelTask.INSTAGRAM_MESSAGES, event.task)
        assertEquals("tunnel-1", event.tunnelId)
    }

    @Test
    fun meaningfulBlockedSurfaceRecordsTransitionAndRepeatedRawObservationDoesNotSpam() {
        val first = semantics.surfaceObserved(session, DetectedSurface.INSTAGRAM_REELS, 200)
        val repeated = semantics.surfaceObserved(session, DetectedSurface.INSTAGRAM_REELS, 201)

        assertEquals(AttentionSubtype.SURFACE_ENTERED, first?.subtype)
        assertEquals(DetectedSurface.INSTAGRAM_REELS, first?.surface)
        assertNull(repeated)
    }

    @Test
    fun unknownAndGenericSurfaceDoNotCreateMisleadingHistory() {
        assertNull(semantics.surfaceObserved(session, DetectedSurface.UNKNOWN, 200))
        assertNull(semantics.surfaceObserved(session, DetectedSurface.INSTAGRAM_OTHER, 201))
    }

    @Test
    fun allowedReturnAfterBlockedSurfaceIsRecordedAsReturnTransition() {
        semantics.surfaceObserved(session, DetectedSurface.INSTAGRAM_REELS, 200)

        val event = semantics.surfaceObserved(session, DetectedSurface.INSTAGRAM_MESSAGES, 300)

        assertEquals(AttentionSubtype.SURFACE_RETURNED, event?.subtype)
    }

    @Test
    fun interventionShownIsRecordedOnceUntilUserDecision() {
        val prompt = TunnelPrompt.Intervention(session.id, session.task, DetectedSurface.INSTAGRAM_REELS)

        assertEquals(
            AttentionEventType.INTERVENTION,
            semantics.tunnelPromptShown(prompt, session, 210)?.type,
        )
        assertNull(semantics.tunnelPromptShown(prompt, session, 211))
        semantics.tunnelDecision(
            session,
            AttentionSubtype.RETURN,
            AttentionDecision.RETURN,
            220,
            DetectedSurface.INSTAGRAM_REELS,
        )
        assertEquals(
            AttentionSubtype.SURFACE_INTERVENTION,
            semantics.tunnelPromptShown(prompt, session, 230)?.subtype,
        )
    }

    @Test
    fun tunnelChoicesRecordDecisionsWithoutFailureSemantics() {
        val choices = listOf(
            Triple(AttentionSubtype.RETURN, AttentionDecision.RETURN, DetectedSurface.INSTAGRAM_REELS),
            Triple(AttentionSubtype.ALLOW_ANYWAY, AttentionDecision.ALLOW_ANYWAY, DetectedSurface.INSTAGRAM_REELS),
            Triple(AttentionSubtype.END_TUNNEL, AttentionDecision.END_TUNNEL, null),
            Triple(AttentionSubtype.EXPIRY_FINISH, AttentionDecision.FINISH, null),
            Triple(AttentionSubtype.EXPIRY_CONTINUE, AttentionDecision.CONTINUE, null),
            Triple(AttentionSubtype.EXPIRY_CHOOSE_ANOTHER, AttentionDecision.CHOOSE_ANOTHER_PURPOSE, null),
        )

        choices.forEachIndexed { index, (subtype, decision, surface) ->
            val event = semantics.tunnelDecision(session, subtype, decision, 300L + index, surface)
            assertEquals(AttentionEventType.DECISION, event.type)
            assertEquals(subtype, event.subtype)
            assertEquals(decision, event.decision)
        }
    }

    @Test
    fun expiryReachedRecordsIntervention() {
        val prompt = TunnelPrompt.SessionExpired(session.id, session.app, session.task)

        val event = semantics.tunnelPromptShown(prompt, session, 500)

        assertEquals(AttentionSubtype.SESSION_EXPIRED, event?.subtype)
        assertEquals(AttentionEventType.INTERVENTION, event?.type)
    }

    @Test
    fun driftCheckInAndChoicesRemainInOneStructuredEpisode() {
        val episode = DriftEpisode(
            id = "drift-1",
            startedAtMillis = 1_000,
            involvedPackages = listOf(
                "com.instagram.android",
                "com.reddit.frontpage",
                "com.google.android.youtube",
            ),
            latestTransitionAtMillis = 1_100,
        )

        val shown = semantics.driftCheckInShown(episode, 1_101)
        val keepGoing = semantics.driftDecision(
            episode,
            AttentionSubtype.KEEP_GOING,
            AttentionDecision.KEEP_GOING,
            "com.google.android.youtube",
            1_102,
        )
        val setIntention = semantics.driftDecision(
            episode,
            AttentionSubtype.SET_INTENTION,
            AttentionDecision.SET_INTENTION,
            "com.google.android.youtube",
            1_103,
        )

        assertEquals(listOf(AttentionSubtype.DRIFT_SEQUENCE, AttentionSubtype.DRIFT_CHECK_IN), shown.map { it.subtype })
        assertTrue(shown.all { it.driftEpisodeId == "drift-1" })
        assertEquals(AttentionDecision.KEEP_GOING, keepGoing.decision)
        assertEquals(AttentionDecision.SET_INTENTION, setIntention.decision)
        assertTrue(semantics.driftCheckInShown(episode, 1_104).isEmpty())
    }

    @Test
    fun persistedModelCannotContainDetectorOrAccessibilityPayloads() {
        val fieldNames = AttentionEvent::class.java.declaredFields.map { it.name.lowercase() }
        val bannedFragments = listOf("resource", "fingerprint", "confidence", "node", "content", "text")

        assertFalse(fieldNames.any { field -> bannedFragments.any(field::contains) })
    }
}

