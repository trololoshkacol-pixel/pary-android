package com.pary.app.data

import java.time.LocalDate

data class FocusDayStats(
    val date: LocalDate,
    val minutes: Int,
    val sessions: Int,
)

/** Pure focus-history aggregation, kept independent from Android and Compose. */
object FocusLogic {
    fun weeklyTrend(
        sessions: Collection<FocusSession>,
        from: LocalDate,
        days: Int,
    ): List<FocusDayStats> {
        if (days <= 0) return emptyList()
        val grouped = sessions
            .filter { it.minutes in 1..240 }
            .groupBy { it.completedAt.toLocalDate() }
        return (0 until days).map { offset ->
            val date = from.plusDays(offset.toLong())
            val daySessions = grouped[date].orEmpty()
            FocusDayStats(
                date = date,
                minutes = daySessions.sumOf { it.minutes },
                sessions = daySessions.size,
            )
        }
    }
}
