package com.example.tasktunnel.ui.home

import com.example.tasktunnel.attention.AttentionApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUsagePresentationTest {
    @Test
    fun compactSurfaceRows_keepsTopThreeAndGroupsRemainder() {
        val app = usage(
            HomeSurfaceUsage(HomeSurfaceKind.REELS, 31),
            HomeSurfaceUsage(HomeSurfaceKind.MESSAGES, 17),
            HomeSurfaceUsage(HomeSurfaceKind.EXPLORE, 8),
            HomeSurfaceUsage(HomeSurfaceKind.HOME, 2),
            HomeSurfaceUsage(HomeSurfaceKind.UNCLASSIFIED, 1),
        )

        val rows = app.compactSurfaceRows()

        assertEquals(listOf("Reels", "Messages", "Explore", "Other"), rows.map { it.label })
        assertEquals(3, rows.last().durationMillis)
        assertTrue(rows.last().isOther)
    }

    @Test
    fun primarySurface_ignoresOtherAndUnclassified() {
        val app = usage(
            HomeSurfaceUsage(HomeSurfaceKind.UNCLASSIFIED, 20),
            HomeSurfaceUsage(HomeSurfaceKind.OTHER, 15),
            HomeSurfaceUsage(HomeSurfaceKind.MESSAGES, 10),
            HomeSurfaceUsage(HomeSurfaceKind.REELS, 8),
        )

        val primary = app.primarySurface()

        assertEquals(HomeSurfaceKind.MESSAGES, primary?.kind)
    }

    @Test
    fun compactSurfaceRows_doesNotInventOtherWhenEverythingFits() {
        val app = usage(
            HomeSurfaceUsage(HomeSurfaceKind.VIDEO, 20),
            HomeSurfaceUsage(HomeSurfaceKind.SHORTS, 10),
        )

        val rows = app.compactSurfaceRows()

        assertEquals(2, rows.size)
        assertFalse(rows.any { it.isOther })
    }

    private fun usage(vararg rows: HomeSurfaceUsage) = HomeAppUsage(
        app = AttentionApp.INSTAGRAM,
        totalTrackedDurationMillis = rows.sumOf(HomeSurfaceUsage::durationMillis),
        surfaceRows = rows.toList(),
        coverage = 1.0,
        shouldDeemphasizeComposition = false,
        showMeaningfulUnclassifiedNote = false,
    )
}
