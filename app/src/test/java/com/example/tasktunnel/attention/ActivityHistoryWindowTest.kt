package com.example.tasktunnel.attention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ActivityHistoryWindowTest {
    private val kathmandu = TimeZone.getTimeZone("Asia/Kathmandu")

    @Test
    fun windowStartsAtLocalMidnightSixCalendarDaysEarlier() {
        val now = Calendar.getInstance(kathmandu).apply {
            set(2026, Calendar.OCTOBER, 5, 11, 44, 19)
            set(Calendar.MILLISECOND, 321)
        }.timeInMillis

        val expected = Calendar.getInstance(kathmandu).apply {
            set(2026, Calendar.SEPTEMBER, 29, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(expected, ActivityHistoryWindow.startMillis(now, kathmandu))
    }

    @Test
    fun windowIncludesExactlySevenCalendarDatesIncludingToday() {
        val now = Calendar.getInstance(kathmandu).apply {
            set(2026, Calendar.JANUARY, 2, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val start = ActivityHistoryWindow.startMillis(now, kathmandu)
        val calendar = Calendar.getInstance(kathmandu).apply { timeInMillis = start }

        assertEquals(2025, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.DECEMBER, calendar.get(Calendar.MONTH))
        assertEquals(27, calendar.get(Calendar.DAY_OF_MONTH))
        assertTrue(start <= now)
    }
}
