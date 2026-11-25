package com.pary.app.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Пара с отметкой посещаемости: ключ — «дата@HH:MM». */
data class Att(
    val key: String = "",
    val present: Boolean = true,
    val subject: String = "",
) {
    val date: LocalDate? get() = key.substringBefore("@").let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val time: LocalTime? get() = key.substringAfter("@", "").let { runCatching { LocalTime.parse(it) }.getOrNull() }
}

enum class TaskBucket { OVERDUE, TODAY, TOMORROW, LATER, DONE }

/** Чистая логика без Android — её проверяют unit-тесты. */
object Logic {

    fun flip(p: Parity): Parity = when (p) {
        Parity.NUM -> Parity.DEN
        Parity.DEN -> Parity.NUM
        Parity.BOTH -> Parity.BOTH
    }

    /** Неделя считается от понедельника-якоря, чётность меняется каждые 7 дней. */
    fun parityOn(anchor: LocalDate, anchorParity: Parity, date: LocalDate): Parity {
        val base = if (anchorParity == Parity.BOTH) Parity.NUM else anchorParity
        val weeks = Math.floorDiv(ChronoUnit.DAYS.between(anchor, date), 7L)
        return if (Math.floorMod(weeks, 2L) == 0L) base else flip(base)
    }

    fun lessonsOn(all: List<Lesson>, date: LocalDate, parity: Parity): List<Lesson> =
        all.filter { it.day == date.dayOfWeek.value - 1 && (it.parity == Parity.BOTH || it.parity == parity) }
            .sortedWith(compareBy({ it.start }, { it.end }))

    /** Непрерывные «тихие» отрезки дня: короткие перерывы между парами не растягивают тишину. */
    fun silentSpans(day: List<Lesson>, gapMin: Long = 40): List<Pair<LocalTime, LocalTime>> {
        val out = mutableListOf<Pair<LocalTime, LocalTime>>()
        for (l in day.sortedBy { it.start }) {
            if (!l.quiet) continue
            val last = out.lastOrNull()
            if (last != null && Duration.between(last.second, l.start).toMinutes() <= gapMin) {
                out[out.size - 1] = last.first to l.end
            } else {
                out.add(l.start to l.end)
            }
        }
        return out
    }

    fun currentLesson(day: List<Lesson>, now: LocalDateTime): Lesson? =
        day.asSequence()
            .sortedBy { it.start }
            .firstOrNull { !it.start.isAfter(now.toLocalTime()) && now.toLocalTime().isBefore(it.end) }

    fun nextLesson(day: List<Lesson>, now: LocalDateTime): Lesson? =
        day.asSequence()
            .filter { it.start.isAfter(now.toLocalTime()) }
            .minByOrNull { it.start }

    fun subjects(all: List<Lesson>): List<String> =
        all.map { it.subject }.filter { it.isNotBlank() }.distinct().sorted()

    /** subject → (был, пропустил). */
    fun subjectStats(att: Collection<Att>): Map<String, Pair<Int, Int>> {
        val out = linkedMapOf<String, Pair<Int, Int>>()
        for (a in att) {
            val s = a.subject.ifBlank { "Без предмета" }
            val cur = out[s] ?: (0 to 0)
            out[s] = if (a.present) cur.first + 1 to cur.second else cur.first to cur.second + 1
        }
        return out
    }

    fun average(values: List<Int>): Double? =
        if (values.isEmpty()) null else values.sum().toDouble() / values.size

    fun bucketOf(t: Task, now: LocalDateTime): TaskBucket {
        if (t.done) return TaskBucket.DONE
        val due = t.due ?: return TaskBucket.LATER
        val today = now.toLocalDate()
        return when {
            due.isBefore(now) -> TaskBucket.OVERDUE
            due.toLocalDate() == today -> TaskBucket.TODAY
            due.toLocalDate() == today.plusDays(1) -> TaskBucket.TOMORROW
            else -> TaskBucket.LATER
        }
    }

    fun sortedTasks(tasks: List<Task>, now: LocalDateTime): List<Task> {
        val order = mapOf(
            TaskBucket.OVERDUE to 0, TaskBucket.TODAY to 1,
            TaskBucket.TOMORROW to 2, TaskBucket.LATER to 3, TaskBucket.DONE to 4,
        )
        return tasks.sortedWith(
            compareBy<Task> { order[bucketOf(it, now)] ?: 5 }
                .thenByDescending { it.priority }
                .thenBy { it.due ?: LocalDateTime.MAX }
        )
    }

    fun humanLeft(target: LocalDateTime, now: LocalDateTime): String {
        val mins = Duration.between(now, target).toMinutes()
        if (mins <= 0) return "сейчас"
        val d = mins / 1440
        val h = (mins % 1440) / 60
        val m = mins % 60
        return when {
            d > 0 && h > 0 -> "$d д $h ч"
            d > 0 -> "$d д"
            h > 0 && m > 0 -> "$h ч $m мин"
            h > 0 -> "$h ч"
            else -> "$m мин"
        }
    }

    fun dur(min: Int): String = when {
        min >= 60 && min % 60 == 0 -> "${min / 60} ч"
        min >= 60 -> "${min / 60} ч ${min % 60} мин"
        else -> "$min мин"
    }
}
