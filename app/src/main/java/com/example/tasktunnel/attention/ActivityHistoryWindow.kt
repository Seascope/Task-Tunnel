package com.example.tasktunnel.attention

import java.util.Calendar
import java.util.TimeZone

object ActivityHistoryWindow {
    const val DAY_COUNT = 7

    fun startMillis(
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -(DAY_COUNT - 1))
    }.timeInMillis
}
