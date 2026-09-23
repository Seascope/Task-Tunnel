package com.example.tasktunnel.drift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriftDetectorTest {
    @Test
    fun repeatedSameAppDoesNotTrigger() {
        val detector = detector()

        repeat(20) { detector.observeForeground(INSTAGRAM, it.toLong()) }

        assertNull(detector.state.episode)
        assertEquals(1, detector.state.transitions.size)
    }

    @Test
    fun twoDistinctSelectedAppsDoNotReachThresholdThree() {
        val detector = detector()

        detector.observeForeground(INSTAGRAM, 0)
        detector.observeForeground(YOUTUBE, 10)

        assertNull(detector.pendingCheckIn)
    }

    @Test
    fun threeDistinctSelectedAppsWithinWindowTrigger() {
        val detector = detector()

        trigger(detector)

        assertEquals(listOf(INSTAGRAM, REDDIT, YOUTUBE), detector.pendingCheckIn?.involvedPackages)
    }

    @Test
    fun redditInstagramYouTubeSequenceTriggersInObservedOrder() {
        val detector = detector(selected = SELECTED_WITH_TIKTOK)

        detector.observeForeground(REDDIT, 0)
        detector.observeForeground(INSTAGRAM, 10)
        detector.observeForeground(YOUTUBE, 20)

        assertEquals(listOf(REDDIT, INSTAGRAM, YOUTUBE), detector.pendingCheckIn?.involvedPackages)
    }

    @Test
    fun instagramYouTubeTikTokSequenceTriggersInObservedOrder() {
        val detector = detector(selected = SELECTED_WITH_TIKTOK)

        detector.observeForeground(INSTAGRAM, 0)
        detector.observeForeground(YOUTUBE, 10)
        detector.observeForeground(TIKTOK, 20)

        assertEquals(listOf(INSTAGRAM, YOUTUBE, TIKTOK), detector.pendingCheckIn?.involvedPackages)
    }


    @Test
    fun pendingEpisodeStaysFrozenAtTheThreeAppsThatTriggeredIt() {
        val detector = detector(selected = SELECTED_WITH_TIKTOK)

        detector.observeForeground(INSTAGRAM, 0)
        detector.observeForeground(REDDIT, 10)
        detector.observeForeground(YOUTUBE, 20)
        detector.observeForeground(TIKTOK, 30)

        assertEquals(listOf(INSTAGRAM, REDDIT, YOUTUBE), detector.pendingCheckIn?.involvedPackages)
    }

    @Test
    fun threeDistinctAppsOutsideWindowDoNotTrigger() {
        val detector = detector(window = 60, quiet = 1_000)

        detector.observeForeground(INSTAGRAM, 0)
        detector.observeForeground(REDDIT, 61)
        detector.observeForeground(YOUTUBE, 62)

        assertNull(detector.state.episode)
    }

    @Test
    fun nonSelectedAppsDoNotContribute() {
        val detector = detector()

        detector.observeForeground(INSTAGRAM, 0)
        detector.observeForeground(CHROME, 5)
        detector.observeForeground(YOUTUBE, 10)

        assertNull(detector.state.episode)
        assertEquals(listOf(INSTAGRAM, YOUTUBE), detector.state.transitions.map { it.packageName })
    }

    @Test
    fun duplicateTransitionsDoNotInflateDistinctCount() {
        val detector = detector()

        detector.observeForeground(INSTAGRAM, 0)
        detector.observeForeground(YOUTUBE, 10)
        detector.observeForeground(INSTAGRAM, 20)
        detector.observeForeground(YOUTUBE, 30)

        assertNull(detector.state.episode)
        assertEquals(2, detector.state.transitions.map { it.packageName }.distinct().size)
    }

    @Test
    fun checkInIsShownOnlyOncePerEpisode() {
        val detector = detector()
        trigger(detector)
        val episode = requireNotNull(detector.pendingCheckIn)

        detector.markCheckInShown(episode.id)
        detector.observeForeground(INSTAGRAM, 30)

        assertNull(detector.pendingCheckIn)
        assertTrue(detector.state.episode?.checkInShown == true)
    }


    @Test
    fun visibleCheckInSurvivesQuietResetUntilItIsResolved() {
        val detector = detector(quiet = 60)
        trigger(detector)
        val episode = requireNotNull(detector.pendingCheckIn)
        detector.markCheckInShown(episode.id)

        detector.observeForeground(INSTAGRAM, 100)

        assertEquals(episode.id, detector.state.episode?.id)
        assertTrue(detector.state.episode?.checkInShown == true)
    }

    @Test
    fun temporarilyHiddenShownCheckInCanRearmSameFrozenEpisode() {
        val detector = detector(quiet = 60)
        trigger(detector)
        val episode = requireNotNull(detector.pendingCheckIn)
        detector.markCheckInShown(episode.id)

        // Keep it marked shown through a long UI boundary so quiet-reset cannot erase it.
        detector.observeForeground(YOUTUBE, 100)
        detector.rearmShownCheckIn()

        assertEquals(episode.id, detector.pendingCheckIn?.id)
        assertEquals(episode.involvedPackages, detector.pendingCheckIn?.involvedPackages)
    }

    @Test
    fun resolvingEpisodeStartsFreshSequenceFromCurrentApp() {
        val detector = detector(selected = SELECTED_WITH_TIKTOK)
        trigger(detector)
        val episode = requireNotNull(detector.pendingCheckIn)

        detector.acknowledge(episode.id, 20)

        assertNull(detector.pendingCheckIn)
        assertNull(detector.state.episode)
        assertEquals(listOf(YOUTUBE), detector.state.transitions.map { it.packageName })
    }

    @Test
    fun quietPeriodStillResetsFreshSequenceAndLaterSequenceCanTrigger() {
        var id = 0
        val detector = detector(quiet = 100, idFactory = { "episode-${++id}" })
        trigger(detector)
        detector.acknowledge("episode-1", 20)

        detector.observeForeground(INSTAGRAM, 120)
        assertNull(detector.state.episode)
        detector.observeForeground(REDDIT, 121)
        detector.observeForeground(YOUTUBE, 122)

        assertEquals("episode-2", detector.pendingCheckIn?.id)
    }

    @Test
    fun selectedPoolReplacementClearsTransientEpisode() {
        val detector = detector()
        trigger(detector)

        detector.replaceSelectedPackages(setOf(INSTAGRAM, YOUTUBE))

        assertNull(detector.state.episode)
        assertTrue(detector.state.transitions.isEmpty())
        assertFalse(REDDIT in detector.selectedPackages)
    }

    @Test
    fun resolvedEpisodeCanRetriggerFromFreshThreeAppSequenceWithoutCooldown() {
        var id = 0
        val detector = detector(
            window = 200,
            quiet = 500,
            idFactory = { "episode-${++id}" },
            selected = SELECTED_WITH_TIKTOK,
        )

        detector.observeForeground(INSTAGRAM, 0)
        detector.observeForeground(REDDIT, 10)
        detector.observeForeground(YOUTUBE, 20)
        detector.acknowledge("episode-1", 20)

        // The resolved YouTube episode becomes the baseline. One new app is not enough to
        // re-show Drift, but a second distinct app completes a fresh three-app sequence.
        detector.observeForeground(INSTAGRAM, 30)
        assertNull(detector.pendingCheckIn)
        detector.observeForeground(TIKTOK, 40)

        assertEquals("episode-2", detector.pendingCheckIn?.id)
        assertEquals(listOf(YOUTUBE, INSTAGRAM, TIKTOK), detector.pendingCheckIn?.involvedPackages)
    }

    private fun trigger(detector: DriftDetector) {
        detector.observeForeground(INSTAGRAM, 0)
        detector.observeForeground(REDDIT, 10)
        detector.observeForeground(YOUTUBE, 20)
    }

    private fun detector(
        window: Long = 60,
        quiet: Long = 60,
        idFactory: () -> String = { "episode-1" },
        selected: Set<String> = SELECTED,
    ) = DriftDetector(
        selectedPackages = selected,
        policy = DriftPolicy(
            distinctAppThreshold = 3,
            rollingWindowMillis = window,
            quietResetMillis = quiet,
        ),
        idFactory = idFactory,
    )

    companion object {
        private const val INSTAGRAM = "com.instagram.android"
        private const val YOUTUBE = "com.google.android.youtube"
        private const val REDDIT = "com.reddit.frontpage"
        private const val TIKTOK = "com.zhiliaoapp.musically"
        private const val CHROME = "com.android.chrome"
        private val SELECTED = setOf(INSTAGRAM, YOUTUBE, REDDIT)
        private val SELECTED_WITH_TIKTOK = setOf(INSTAGRAM, YOUTUBE, TIKTOK, REDDIT)
    }
}
