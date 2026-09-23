package com.example.tasktunnel.attention

import com.example.tasktunnel.tunnel.DetectedSurface
import com.example.tasktunnel.tunnel.TunnelTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttentionPresentationTest {
    @Test
    fun quietIntentionalSessionHasNoListHighlight() {
        val episode = taskEpisode(
            event(100, AttentionEventType.INTENT, AttentionSubtype.PURPOSE_SELECTED),
            event(200, AttentionEventType.TRANSITION, AttentionSubtype.SURFACE_ENTERED, DetectedSurface.INSTAGRAM_MESSAGES),
        )

        assertNull(episodeHighlight(episode))
        assertEquals(
            listOf("Started with Reply to messages"),
            episodeStory(episode).map { it.text },
        )
    }

    @Test
    fun returnedDetourBecomesShortHumanReadableStory() {
        val episode = taskEpisode(
            event(100, AttentionEventType.INTENT, AttentionSubtype.PURPOSE_SELECTED),
            event(200, AttentionEventType.TRANSITION, AttentionSubtype.SURFACE_ENTERED, DetectedSurface.INSTAGRAM_MESSAGES),
            event(300, AttentionEventType.INTERVENTION, AttentionSubtype.SURFACE_INTERVENTION, DetectedSurface.INSTAGRAM_REELS),
            event(310, AttentionEventType.DECISION, AttentionSubtype.RETURN, DetectedSurface.INSTAGRAM_REELS),
            event(400, AttentionEventType.TRANSITION, AttentionSubtype.SURFACE_RETURNED, DetectedSurface.INSTAGRAM_MESSAGES),
        )

        assertEquals("Returned from Reels", episodeHighlight(episode))
        assertEquals(
            listOf(
                "Started with Reply to messages",
                "Opened Reels",
                "You chose to go back",
                "Returned to Messages",
            ),
            episodeStory(episode).map { it.text },
        )
    }

    @Test
    fun allowedDetourHighlightsTheSurfaceWithoutTelemetryLanguage() {
        val episode = taskEpisode(
            event(100, AttentionEventType.INTENT, AttentionSubtype.PURPOSE_SELECTED),
            event(200, AttentionEventType.INTERVENTION, AttentionSubtype.SURFACE_INTERVENTION, DetectedSurface.INSTAGRAM_REELS),
            event(300, AttentionEventType.DECISION, AttentionSubtype.ALLOW_ANYWAY, DetectedSurface.INSTAGRAM_REELS),
        )

        assertEquals("Continued on Reels", episodeHighlight(episode))
    }

    private fun taskEpisode(vararg events: AttentionEvent) = AttentionEpisode(
        id = "tunnel:t1",
        type = AttentionEpisodeType.TASK_TUNNEL,
        startedAtMillis = events.first().timestampMillis,
        endedAtMillis = events.last().timestampMillis,
        app = AttentionApp.INSTAGRAM,
        task = TunnelTask.INSTAGRAM_MESSAGES,
        involvedApps = emptyList(),
        involvedPackages = emptyList(),
        events = events.toList(),
    )

    private fun event(
        timestampMillis: Long,
        type: AttentionEventType,
        subtype: AttentionSubtype,
        surface: DetectedSurface? = null,
    ) = AttentionEvent(
        timestampMillis = timestampMillis,
        type = type,
        subtype = subtype,
        app = AttentionApp.INSTAGRAM,
        surface = surface,
        task = TunnelTask.INSTAGRAM_MESSAGES,
        tunnelId = "t1",
    )
}
