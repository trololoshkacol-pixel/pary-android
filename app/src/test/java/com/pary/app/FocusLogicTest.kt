package com.pary.app

import com.pary.app.data.FocusLogic
import com.pary.app.data.FocusSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class FocusLogicTest {
    @Test
    fun weeklyTrendGroupsSessionsAndKeepsEmptyDays() {
        val start = LocalDate.of(2026, 11, 20)
        val sessions = listOf(
            FocusSession(completedAt = LocalDateTime.of(2026, 11, 20, 9, 0), minutes = 25),
            FocusSession(completedAt = LocalDateTime.of(2026, 11, 20, 10, 0), minutes = 50),
            FocusSession(completedAt = LocalDateTime.of(2026, 11, 22, 14, 0), minutes = 15),
            FocusSession(completedAt = LocalDateTime.of(2026, 11, 20, 11, 0), minutes = 0),
        )

        val trend = FocusLogic.weeklyTrend(sessions, start, 3)
        assertEquals(3, trend.size)
        assertEquals(75, trend[0].minutes)
        assertEquals(2, trend[0].sessions)
        assertEquals(0, trend[1].minutes)
        assertEquals(0, trend[1].sessions)
        assertEquals(15, trend[2].minutes)
        assertEquals(1, trend[2].sessions)
        assertEquals(emptyList<com.pary.app.data.FocusDayStats>(), FocusLogic.weeklyTrend(sessions, start, 0))
    }
}
