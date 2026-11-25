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

data class AttendanceDayStats(val date: LocalDate, val present: Int, val missed: Int) {
    val total: Int get() = present + missed
    val attendancePercent: Double? get() = if (total == 0) null else present * 100.0 / total
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

    /** Статистика по календарным дням; пустые дни сохраняются, некорректные ключи игнорируются. */
    fun attendanceTrend(attendance: Collection<Att>, from: LocalDate, days: Int): List<AttendanceDayStats> {
        if (days <= 0) return emptyList()
        val grouped = attendance.mapNotNull { record -> record.date?.let { it to record } }.groupBy({ it.first }, { it.second })
        return (0 until days).map { offset ->
            val date = from.plusDays(offset.toLong())
            val records = grouped[date].orEmpty()
            AttendanceDayStats(date, records.count { it.present }, records.count { !it.present })
        }
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


    // ---------- кредитный журнал и расчёт итогов ----------

    fun assessmentAverage(records: List<AssessmentRecord>, kind: AssessmentKind): Double? {
        val selected = records.filter { it.kind == kind && it.maxScore > 0.0 }
        if (selected.isEmpty()) return null
        val earned = selected.sumOf { it.score }
        val possible = selected.sumOf { it.maxScore }
        return if (possible <= 0.0) null else (earned / possible * 100.0).coerceIn(0.0, 100.0)
    }

    /** Текущий процент с перенормировкой только уже заполненных компонентов. */
    fun gradebookCurrent(subject: GradebookSubject): Double? {
        val parts = listOf(
            assessmentAverage(subject.records, AssessmentKind.RATING1) to subject.rating1Weight,
            assessmentAverage(subject.records, AssessmentKind.RATING2) to subject.rating2Weight,
            assessmentAverage(subject.records, AssessmentKind.EXAM) to subject.examWeight,
        ).filter { it.first != null && it.second > 0 }
        val weight = parts.sumOf { it.second }
        if (weight == 0) return null
        return parts.sumOf { it.first!! * it.second } / weight
    }

    /** Итог доступен только после заполнения всех компонентов с ненулевым весом. */
    fun gradebookFinal(subject: GradebookSubject): Double? {
        val weights = listOf(
            AssessmentKind.RATING1 to subject.rating1Weight,
            AssessmentKind.RATING2 to subject.rating2Weight,
            AssessmentKind.EXAM to subject.examWeight,
        )
        if (weights.sumOf { it.second } != 100) return null
        val parts = weights.map { (kind, weight) ->
            val avg = assessmentAverage(subject.records, kind)
            if (weight > 0 && avg == null) return null
            (avg ?: 0.0) * weight
        }
        return parts.sum() / 100.0
    }

    fun requiredExamPercent(subject: GradebookSubject, targetPercent: Double): Double? {
        if (subject.examWeight <= 0 || subject.rating1Weight + subject.rating2Weight + subject.examWeight != 100) return null
        val r1 = assessmentAverage(subject.records, AssessmentKind.RATING1)
        val r2 = assessmentAverage(subject.records, AssessmentKind.RATING2)
        if ((subject.rating1Weight > 0 && r1 == null) || (subject.rating2Weight > 0 && r2 == null)) return null
        val earned = (r1 ?: 0.0) * subject.rating1Weight + (r2 ?: 0.0) * subject.rating2Weight
        return ((targetPercent.coerceIn(0.0, 100.0) * 100.0 - earned) / subject.examWeight).coerceAtLeast(0.0)
    }

    /** Типовая буквенная шкала 4.0; пороги подлежат настройке под регламент вуза. */
    fun letterGrade(percent: Double): Pair<String, Double> {
        val p = percent.coerceIn(0.0, 100.0)
        return when {
            p >= 95.0 -> "A" to 4.0
            p >= 90.0 -> "A-" to 3.67
            p >= 85.0 -> "B+" to 3.33
            p >= 80.0 -> "B" to 3.0
            p >= 75.0 -> "B-" to 2.67
            p >= 70.0 -> "C+" to 2.33
            p >= 65.0 -> "C" to 2.0
            p >= 60.0 -> "C-" to 1.67
            p >= 55.0 -> "D+" to 1.33
            p >= 50.0 -> "D" to 1.0
            else -> "F" to 0.0
        }
    }

    /** GPA взвешивается по кредитам, а не по количеству предметов. */
    fun weightedGpa(subjects: List<GradebookSubject>): Double? {
        val completed = subjects.mapNotNull { subject ->
            gradebookFinal(subject)?.let { subject to it }
        }
        val credits = completed.sumOf { it.first.credits }
        if (credits <= 0) return null
        return completed.sumOf { (subject, percent) -> letterGrade(percent).second * subject.credits } / credits
    }
}
