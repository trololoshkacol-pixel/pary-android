package com.pary.app

import com.pary.app.data.Logic
import com.pary.app.data.Lesson
import com.pary.app.data.Parity
import com.pary.app.data.Task
import com.pary.app.data.TaskBucket
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LogicTest {

    @Test
    fun freshInstallDefaultsDoNotContainStudentSpecificData() {
        assertEquals("", com.pary.app.data.AppDefaults.profileGroup)
        assertEquals("", com.pary.app.data.AppDefaults.platonusUrl)
        assertEquals(false, com.pary.app.data.AppDefaults.watchChatsEnabled)
        assertEquals(emptyList<com.pary.app.data.Lesson>(), com.pary.app.data.AppDefaults.lessons)
    }

    private val anchor = LocalDate.of(2026, 11, 16) // понедельник

    @Test
    fun parityFlipsEveryWeek() {
        assertEquals(Parity.NUM, Logic.parityOn(anchor, Parity.NUM, anchor))
        assertEquals(Parity.NUM, Logic.parityOn(anchor, Parity.NUM, anchor.plusDays(6)))
        assertEquals(Parity.DEN, Logic.parityOn(anchor, Parity.NUM, anchor.plusDays(7)))
        assertEquals(Parity.NUM, Logic.parityOn(anchor, Parity.NUM, anchor.plusDays(14)))
        assertEquals(Parity.DEN, Logic.parityOn(anchor, Parity.DEN, anchor))
        assertEquals(Parity.NUM, Logic.parityOn(anchor, Parity.DEN, anchor.plusDays(7)))
    }

    @Test
    fun parityWorksForDatesBeforeAnchor() {
        assertEquals(Parity.DEN, Logic.parityOn(anchor, Parity.NUM, anchor.minusDays(7)))
        assertEquals(Parity.NUM, Logic.parityOn(anchor, Parity.NUM, anchor.minusDays(14)))
    }

    @Test
    fun lessonsAreFilteredByDayAndParity() {
        val lessons = listOf(
            Lesson(day = 0, parity = Parity.BOTH, start = LocalTime.of(15, 5), end = LocalTime.of(16, 50), name = "ИКТ"),
            Lesson(day = 0, parity = Parity.NUM, start = LocalTime.of(13, 10), end = LocalTime.of(14, 55), name = "Физ-ра"),
            Lesson(day = 0, parity = Parity.DEN, start = LocalTime.of(17, 0), end = LocalTime.of(18, 45), name = "Матан"),
            Lesson(day = 1, parity = Parity.BOTH, start = LocalTime.of(9, 0), end = LocalTime.of(10, 45), name = "Сети"),
        )
        val monday = anchor
        val numDay = Logic.lessonsOn(lessons, monday, Parity.NUM)
        assertEquals(listOf("Физ-ра", "ИКТ"), numDay.map { it.name })
        val denDay = Logic.lessonsOn(lessons, monday, Parity.DEN)
        assertEquals(listOf("ИКТ", "Матан"), denDay.map { it.name })
    }

    @Test
    fun silentSpansMergeShortBreaks() {
        val lessons = listOf(
            Lesson(day = 0, start = LocalTime.of(9, 0), end = LocalTime.of(10, 30)),
            Lesson(day = 0, start = LocalTime.of(10, 40), end = LocalTime.of(12, 10)),
            Lesson(day = 0, start = LocalTime.of(13, 0), end = LocalTime.of(14, 30)),
            Lesson(day = 0, start = LocalTime.of(15, 0), end = LocalTime.of(16, 30), quiet = false),
        )
        val spans = Logic.silentSpans(lessons)
        assertEquals(2, spans.size)
        assertEquals(LocalTime.of(9, 0), spans[0].first)
        assertEquals(LocalTime.of(13, 0), spans[1].first)
        assertEquals(LocalTime.of(14, 30), spans[1].second)
    }

    @Test
    fun taskBucketsAreComputedCorrectly() {
        val now = LocalDateTime.of(2026, 11, 23, 12, 0)
        val overdue = Task(title = "a", due = now.minusHours(1))
        val today = Task(title = "b", due = now.plusHours(3))
        val tomorrow = Task(title = "c", due = now.plusDays(1))
        val later = Task(title = "d", due = now.plusDays(5))
        val noDue = Task(title = "e")
        val done = Task(title = "f", due = now.minusDays(2), done = true)

        assertEquals(TaskBucket.OVERDUE, Logic.bucketOf(overdue, now))
        assertEquals(TaskBucket.TODAY, Logic.bucketOf(today, now))
        assertEquals(TaskBucket.TOMORROW, Logic.bucketOf(tomorrow, now))
        assertEquals(TaskBucket.LATER, Logic.bucketOf(later, now))
        assertEquals(TaskBucket.LATER, Logic.bucketOf(noDue, now))
        assertEquals(TaskBucket.DONE, Logic.bucketOf(done, now))

        val sorted = Logic.sortedTasks(listOf(later, today, done, overdue, tomorrow, noDue), now)
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), sorted.map { it.title })
    }

    @Test
    fun attendanceTrendGroupsByDateAndKeepsEmptyDays() {
        val start = LocalDate.of(2026, 11, 20)
        val records = listOf(
            com.pary.app.data.Att(key = "2026-11-20@09:00", present = true, subject = "Math"),
            com.pary.app.data.Att(key = "2026-11-20@11:00", present = false, subject = "Math"),
            com.pary.app.data.Att(key = "2026-11-22@10:00", present = true, subject = "History"),
            com.pary.app.data.Att(key = "bad-key", present = false),
        )
        val trend = Logic.attendanceTrend(records, start, 3)
        assertEquals(3, trend.size)
        assertEquals(1, trend[0].present)
        assertEquals(1, trend[0].missed)
        assertEquals(50.0, trend[0].attendancePercent ?: 0.0, 0.001)
        assertEquals(0, trend[1].total)
        assertEquals(1, trend[2].present)
        assertEquals(emptyList<com.pary.app.data.AttendanceDayStats>(), Logic.attendanceTrend(records, start, 0))
    }

    @Test
    fun averageAndHumanLeft() {
        assertEquals(4.5, Logic.average(listOf(5, 4)) ?: 0.0, 0.001)
        assertEquals(null, Logic.average(emptyList()))
        val now = LocalDateTime.of(2026, 11, 23, 12, 0)
        assertEquals("1 ч 30 мин", Logic.humanLeft(now.plusMinutes(90), now))
        assertEquals("2 д", Logic.humanLeft(now.plusDays(2), now))
    }
    @Test
    fun currentLessonFindsEarliestMatchingLessonEvenWhenInputIsUnsorted() {
        val now = LocalDateTime.of(2026, 11, 23, 10, 15)
        val later = Lesson(start = LocalTime.of(11, 0), end = LocalTime.of(12, 30), name = "Позже")
        val current = Lesson(start = LocalTime.of(10, 0), end = LocalTime.of(11, 30), name = "Сейчас")
        val earlierEnded = Lesson(start = LocalTime.of(8, 0), end = LocalTime.of(9, 30), name = "Раньше")

        assertEquals("Сейчас", Logic.currentLesson(listOf(later, earlierEnded, current), now)?.name)
    }

    @Test
    fun nextLessonFindsNearestFutureLessonEvenWhenInputIsUnsorted() {
        val now = LocalDateTime.of(2026, 11, 23, 10, 15)
        val far = Lesson(start = LocalTime.of(14, 0), end = LocalTime.of(15, 30), name = "Далеко")
        val nearest = Lesson(start = LocalTime.of(11, 0), end = LocalTime.of(12, 30), name = "Ближайшая")
        val ended = Lesson(start = LocalTime.of(9, 0), end = LocalTime.of(10, 0), name = "Закончилась")

        assertEquals("Ближайшая", Logic.nextLesson(listOf(far, ended, nearest), now)?.name)
    }

    @Test
    fun lessonLookupRespectsStartInclusiveAndEndExclusiveBoundaries() {
        val lesson = Lesson(start = LocalTime.of(10, 0), end = LocalTime.of(11, 30), name = "Пара")
        val atStart = LocalDateTime.of(2026, 11, 23, 10, 0)
        val atEnd = LocalDateTime.of(2026, 11, 23, 11, 30)

        assertEquals("Пара", Logic.currentLesson(listOf(lesson), atStart)?.name)
        assertEquals(null, Logic.currentLesson(listOf(lesson), atEnd))
        assertEquals(null, Logic.nextLesson(listOf(lesson), atEnd))
    }



    @Test
    fun gradebookUsesWeightedR1R2AndExamScores() {
        val subject = com.pary.app.data.GradebookSubject(
            subject = "Математика",
            credits = 5,
            records = listOf(
                com.pary.app.data.AssessmentRecord(kind = com.pary.app.data.AssessmentKind.RATING1, score = 80.0),
                com.pary.app.data.AssessmentRecord(kind = com.pary.app.data.AssessmentKind.RATING2, score = 90.0),
                com.pary.app.data.AssessmentRecord(kind = com.pary.app.data.AssessmentKind.EXAM, score = 70.0),
            ),
        )
        assertEquals(79.0, Logic.gradebookFinal(subject) ?: 0.0, 0.001)
    }

    @Test
    fun gradebookCurrentShowsProvisionalAverageUntilExamIsEntered() {
        val subject = com.pary.app.data.GradebookSubject(
            records = listOf(
                com.pary.app.data.AssessmentRecord(kind = com.pary.app.data.AssessmentKind.RATING1, score = 80.0),
                com.pary.app.data.AssessmentRecord(kind = com.pary.app.data.AssessmentKind.RATING2, score = 90.0),
            ),
        )
        assertEquals(85.0, Logic.gradebookCurrent(subject) ?: 0.0, 0.001)
        assertEquals(null, Logic.gradebookFinal(subject))
    }

    @Test
    fun requiredExamScoreAndCreditWeightedGpaAreCorrect() {
        val first = com.pary.app.data.GradebookSubject(
            subject = "Математика", credits = 5,
            records = listOf(
                com.pary.app.data.AssessmentRecord(kind = com.pary.app.data.AssessmentKind.RATING1, score = 80.0),
                com.pary.app.data.AssessmentRecord(kind = com.pary.app.data.AssessmentKind.RATING2, score = 90.0),
            ),
        )
        assertEquals(85.0, Logic.requiredExamPercent(first, 85.0) ?: 0.0, 0.001)
        val completed = first.copy(records = first.records + com.pary.app.data.AssessmentRecord(kind = com.pary.app.data.AssessmentKind.EXAM, score = 70.0))
        val second = com.pary.app.data.GradebookSubject(
            subject = "История", credits = 2,
            records = listOf(
                com.pary.app.data.AssessmentRecord(kind = com.pary.app.data.AssessmentKind.RATING1, score = 95.0),
                com.pary.app.data.AssessmentRecord(kind = com.pary.app.data.AssessmentKind.RATING2, score = 95.0),
                com.pary.app.data.AssessmentRecord(kind = com.pary.app.data.AssessmentKind.EXAM, score = 95.0),
            ),
        )
        assertEquals(3.05, Logic.weightedGpa(listOf(completed, second)) ?: 0.0, 0.001)
    }

    @Test
    fun assessmentAverageUsesTotalEarnedOverTotalPossiblePoints() {
        val records = listOf(
            com.pary.app.data.AssessmentRecord(kind = com.pary.app.data.AssessmentKind.RATING1, score = 8.0, maxScore = 10.0),
            com.pary.app.data.AssessmentRecord(kind = com.pary.app.data.AssessmentKind.RATING1, score = 18.0, maxScore = 20.0),
        )
        assertEquals(86.6667, Logic.assessmentAverage(records, com.pary.app.data.AssessmentKind.RATING1) ?: 0.0, 0.001)
    }

    @Test
    fun gradebookRejectsWeightsThatDoNotAddUpToOneHundred() {
        val subject = com.pary.app.data.GradebookSubject(
            rating1Weight = 30,
            rating2Weight = 30,
            examWeight = 30,
            records = listOf(
                com.pary.app.data.AssessmentRecord(kind = com.pary.app.data.AssessmentKind.RATING1, score = 80.0),
                com.pary.app.data.AssessmentRecord(kind = com.pary.app.data.AssessmentKind.RATING2, score = 80.0),
                com.pary.app.data.AssessmentRecord(kind = com.pary.app.data.AssessmentKind.EXAM, score = 80.0),
            ),
        )
        assertEquals(null, Logic.gradebookFinal(subject))
        assertEquals(null, Logic.requiredExamPercent(subject, 80.0))
    }

    @Test
    fun gradebookGpaTrendUsesOnlyCompletedSubjectsAndDatedRecords() {
        val firstDate = LocalDate.of(2026, 11, 1)
        val finalDate = LocalDate.of(2026, 11, 15)

        fun completedSubject(name: String, credits: Int, score: Double, date: LocalDate) =
            com.pary.app.data.GradebookSubject(
                subject = name,
                credits = credits,
                records = com.pary.app.data.AssessmentKind.entries.map { kind ->
                    com.pary.app.data.AssessmentRecord(kind = kind, score = score, date = date)
                },
            )

        val first = completedSubject("Математика", 3, 80.0, firstDate)
        val second = completedSubject("История", 1, 95.0, finalDate)
        val unfinished = com.pary.app.data.GradebookSubject(
            subject = "Физика",
            credits = 8,
            records = listOf(
                com.pary.app.data.AssessmentRecord(
                    kind = com.pary.app.data.AssessmentKind.RATING1,
                    score = 100.0,
                    date = firstDate,
                ),
                com.pary.app.data.AssessmentRecord(
                    kind = com.pary.app.data.AssessmentKind.RATING2,
                    score = 100.0,
                    date = firstDate,
                ),
            ),
        )

        val trend = Logic.gradebookGpaTrend(listOf(first, second, unfinished), finalDate, 3)
        assertEquals(3, trend.size)
        assertEquals(firstDate, trend.first().date)
        assertEquals(1, trend[0].completedSubjects)
        assertEquals(3, trend[0].credits)
        assertEquals(Logic.letterGrade(80.0).second, trend[0].gpa ?: -1.0, 0.001)
        assertEquals(1, trend[1].completedSubjects)
        assertEquals(2, trend[2].completedSubjects)
        assertEquals(4, trend[2].credits)
        val expectedWeightedGpa = (
            Logic.letterGrade(80.0).second * 3 + Logic.letterGrade(95.0).second
        ) / 4.0
        assertEquals(expectedWeightedGpa, trend[2].gpa ?: -1.0, 0.001)
        assertEquals(emptyList<com.pary.app.data.GpaTrendPoint>(), Logic.gradebookGpaTrend(listOf(first), finalDate, 0))
    }

    @Test
    fun assessmentPercentNormalizesDifferentMaximumScores() {
        val record = com.pary.app.data.AssessmentRecord(score = 18.0, maxScore = 20.0)
        assertEquals(90.0, record.percent, 0.001)
        assertEquals("A-", Logic.letterGrade(92.0).first)
    }
}
