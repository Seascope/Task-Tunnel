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
    fun keepGoingAcknowledgesEpisodeAndPreventsImmediateRepeat() {
        val detector = detector()
        trigger(detector)
        val episode = requireNotNull(detector.pendingCheckIn)

        detector.acknowledge(episode.id)
        detector.observeForeground(REDDIT, 30)

        assertNull(detector.pendingCheckIn)
        assertTrue(detector.state.episode?.acknowledged == true)
    }

    @Test
    fun quietPeriodResetsEpisodeAndLaterSequenceCanTrigger() {
        var id = 0
        val detector = detector(quiet = 100, idFactory = { "episode-${++id}" })
        trigger(detector)
        detector.acknowledge("episode-1")

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

    private fun trigger(detector: DriftDetector) {
        detector.observeForeground(INSTAGRAM, 0)
        detector.observeForeground(REDDIT, 10)
        detector.observeForeground(YOUTUBE, 20)
    }

    private fun detector(
        window: Long = 60,
        quiet: Long = 60,
        idFactory: () -> String = { "episode-1" },
    ) = DriftDetector(
        selectedPackages = SELECTED,
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
        private const val CHROME = "com.android.chrome"
        private val SELECTED = setOf(INSTAGRAM, YOUTUBE, REDDIT)
    }
}
