package com.pary.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicLong

private val idGen = AtomicLong(System.currentTimeMillis())

fun newId(): Long = idGen.incrementAndGet()

/** Empty, user-neutral data for a fresh installation. */
object AppDefaults {
    const val profileGroup = ""
    const val platonusUrl = ""
    const val watchChatsEnabled = false
    val lessons: List<Lesson> = emptyList()
}

/** b — каждую неделю, n — числитель, d — знаменатель. */
enum class Parity(val key: String) {
    BOTH("b"), NUM("n"), DEN("d");

    companion object {
        fun ofKey(k: String?): Parity = entries.firstOrNull { it.key == k } ?: BOTH
    }
}

data class Lesson(
    val id: Long = newId(),
    val day: Int = 0,
    val parity: Parity = Parity.BOTH,
    val start: LocalTime = LocalTime.of(9, 0),
    val end: LocalTime = LocalTime.of(10, 30),
    val name: String = "",
    val room: String = "",
    val teacher: String = "",
    val quiet: Boolean = true,
    /** manual — добавлено руками, platonus — загружено из Платонуса. */
    val source: String = "manual",
) {
    val subject: String get() = name.substringBefore(" (").trim()

    val minutes: Int
        get() = ((end.toSecondOfDay() - start.toSecondOfDay()) / 60).coerceAtLeast(0)

    fun keyOn(date: LocalDate): String = "${date}@${start}"
}

data class Task(
    val id: Long = newId(),
    val title: String = "",
    val subject: String = "",
    val due: LocalDateTime? = null,
    /** 0 — низкий, 1 — обычный, 2 — важное. */
    val priority: Int = 1,
    val done: Boolean = false,
    val remindMin: Int = 60,
    val note: String = "",
    val source: String = "manual",
) {
    fun remindAt(): LocalDateTime? = due?.minusMinutes(remindMin.toLong())
}

data class Grade(
    val id: Long = newId(),
    val subject: String = "",
    val value: Int = 5,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
)

data class Exam(
    val id: Long = newId(),
    val subject: String = "",
    val at: LocalDateTime = LocalDateTime.now(),
    val room: String = "",
    val note: String = "",
    val passed: Boolean = false,
)

data class Note(
    val id: Long = newId(),
    val subject: String = "",
    val text: String = "",
    val at: LocalDateTime = LocalDateTime.now(),
)

/** A completed focus interval, retained for weekly productivity history. */
data class FocusSession(
    val id: Long = newId(),
    val completedAt: LocalDateTime = LocalDateTime.now(),
    val minutes: Int = 25,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("at", completedAt.toString()).put("minutes", minutes)

    companion object {
        fun from(o: JSONObject): FocusSession? {
            val completedAt = runCatching { LocalDateTime.parse(o.optString("at")) }.getOrNull() ?: return null
            val minutes = o.optInt("minutes", 0)
            if (minutes !in 1..240) return null
            return FocusSession(
                id = o.optLong("id", newId()),
                completedAt = completedAt,
                minutes = minutes,
            )
        }
    }
}

// ---------- JSON ----------

fun Lesson.toJson(): JSONObject = JSONObject()
    .put("id", id).put("d", day).put("w", parity.key)
    .put("s", start.toString()).put("e", end.toString())
    .put("n", name).put("r", room).put("t", teacher).put("q", quiet).put("src", source)

fun lessonFrom(o: JSONObject): Lesson = Lesson(
    id = o.optLong("id", newId()),
    day = o.optInt("d", 0).coerceIn(0, 6),
    parity = Parity.ofKey(o.optString("w", "b")),
    start = runCatching { LocalTime.parse(o.getString("s")) }.getOrDefault(LocalTime.of(9, 0)),
    end = runCatching { LocalTime.parse(o.getString("e")) }.getOrDefault(LocalTime.of(10, 30)),
    name = o.optString("n", ""),
    room = o.optString("r", ""),
    teacher = o.optString("t", ""),
    quiet = o.optBoolean("q", true),
    source = o.optString("src", "manual"),
)

fun Task.toJson(): JSONObject = JSONObject()
    .put("id", id).put("title", title).put("subject", subject)
    .put("due", due?.toString() ?: "").put("priority", priority)
    .put("done", done).put("remind", remindMin).put("note", note).put("source", source)

fun taskFrom(o: JSONObject): Task = Task(
    id = o.optLong("id", newId()),
    title = o.optString("title", ""),
    subject = o.optString("subject", ""),
    due = o.optString("due", "").takeIf { it.isNotBlank() }?.let { s ->
        runCatching { LocalDateTime.parse(s) }.getOrNull()
    },
    priority = o.optInt("priority", 1).coerceIn(0, 2),
    done = o.optBoolean("done", false),
    remindMin = o.optInt("remind", 60),
    note = o.optString("note", ""),
    source = o.optString("source", "manual"),
)

fun Grade.toJson(): JSONObject = JSONObject()
    .put("id", id).put("subject", subject).put("value", value)
    .put("date", date.toString()).put("note", note)

fun gradeFrom(o: JSONObject): Grade = Grade(
    id = o.optLong("id", newId()),
    subject = o.optString("subject", ""),
    value = o.optInt("value", 5).coerceIn(1, 5),
    date = runCatching { LocalDate.parse(o.optString("date")) }.getOrDefault(LocalDate.now()),
    note = o.optString("note", ""),
)

fun Exam.toJson(): JSONObject = JSONObject()
    .put("id", id).put("subject", subject).put("at", at.toString())
    .put("room", room).put("note", note).put("passed", passed)

fun examFrom(o: JSONObject): Exam = Exam(
    id = o.optLong("id", newId()),
    subject = o.optString("subject", ""),
    at = runCatching { LocalDateTime.parse(o.optString("at")) }.getOrDefault(LocalDateTime.now()),
    room = o.optString("room", ""),
    note = o.optString("note", ""),
    passed = o.optBoolean("passed", false),
)

fun Note.toJson(): JSONObject = JSONObject()
    .put("id", id).put("subject", subject).put("text", text).put("at", at.toString())

fun noteFrom(o: JSONObject): Note = Note(
    id = o.optLong("id", newId()),
    subject = o.optString("subject", ""),
    text = o.optString("text", ""),
    at = runCatching { LocalDateTime.parse(o.optString("at")) }.getOrDefault(LocalDateTime.now()),
)

// ---------- журнал Платонуса ----------

data class JournalExam(
    val title: String = "",
    val date: String = "",
    val mark: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("t", title).put("d", date).put("m", mark)

    companion object {
        fun from(o: JSONObject): JournalExam = JournalExam(
            o.optString("t"), o.optString("d"), o.optString("m"),
        )
    }
}

data class JournalSubject(
    val name: String = "",
    val total: String = "",
    val center: String = "",
    val tutors: String = "",
    val exams: List<JournalExam> = emptyList(),
    /** Цвет предмета из журнала Платонуса: "#RRGGBB" или "#AARRGGBB", пусто — нет. */
    val color: String = "",
    val remoteId: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("n", name).put("total", total).put("center", center).put("tutors", tutors)
        .put("color", color).put("remoteId", remoteId)
        .put("exams", JSONArray().also { a -> exams.forEach { a.put(it.toJson()) } })

    companion object {
        fun from(o: JSONObject): JournalSubject = JournalSubject(
            name = o.optString("n"),
            total = o.optString("total"),
            center = o.optString("center"),
            tutors = o.optString("tutors"),
            color = o.optString("color"),
            remoteId = o.optString("remoteId"),
            exams = (0 until (o.optJSONArray("exams")?.length() ?: 0)).mapNotNull { i ->
                o.optJSONArray("exams")?.optJSONObject(i)?.let(JournalExam::from)
            },
        )
    }
}

// ---------- черновики задач из чатов ----------

data class ChatDraft(
    val id: Long = newId(),
    val text: String = "",
    val chat: String = "",
    val pkg: String = "",
    val at: Long = System.currentTimeMillis(),
    val due: LocalDateTime? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("text", text).put("chat", chat).put("pkg", pkg).put("at", at)
        .put("due", due?.toString() ?: "")

    companion object {
        fun from(o: JSONObject): ChatDraft = ChatDraft(
            id = o.optLong("id", newId()),
            text = o.optString("text"),
            chat = o.optString("chat"),
            pkg = o.optString("pkg"),
            at = o.optLong("at", 0L),
            due = o.optString("due").takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
        )
    }
}


data class PlatonusAttendance(
    val key: String,
    val subject: String,
    val present: Boolean,
    val status: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("k", key).put("s", subject).put("p", present).put("status", status)
    companion object {
        fun from(o: JSONObject) = PlatonusAttendance(o.optString("k"), o.optString("s"), o.optBoolean("p"), o.optString("status"))
    }
}


// ---------- ручной кредитный журнал ----------

enum class AssessmentKind(val key: String, val label: String) {
    RATING1("r1", "Рубежный контроль 1"),
    RATING2("r2", "Рубежный контроль 2"),
    EXAM("exam", "Экзамен"),
}

data class AssessmentRecord(
    val id: Long = newId(),
    val kind: AssessmentKind = AssessmentKind.RATING1,
    val title: String = "",
    val score: Double = 0.0,
    val maxScore: Double = 100.0,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
) {
    val percent: Double get() = if (maxScore > 0.0) (score / maxScore * 100.0).coerceIn(0.0, 100.0) else 0.0

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("kind", kind.key).put("title", title)
        .put("score", score).put("max", maxScore).put("date", date.toString()).put("note", note)

    companion object {
        fun from(o: JSONObject): AssessmentRecord = AssessmentRecord(
            id = o.optLong("id", newId()),
            kind = AssessmentKind.entries.firstOrNull { it.key == o.optString("kind") } ?: AssessmentKind.RATING1,
            title = o.optString("title", ""),
            score = o.optDouble("score", 0.0).coerceAtLeast(0.0),
            maxScore = o.optDouble("max", 100.0).coerceAtLeast(0.01),
            date = runCatching { LocalDate.parse(o.optString("date")) }.getOrDefault(LocalDate.now()),
            note = o.optString("note", ""),
        )
    }
}

data class GradebookSubject(
    val id: Long = newId(),
    val subject: String = "",
    val credits: Int = 3,
    /** Веса в процентах: стандартная модель 30 + 30 + 40. */
    val rating1Weight: Int = 30,
    val rating2Weight: Int = 30,
    val examWeight: Int = 40,
    val records: List<AssessmentRecord> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("subject", subject).put("credits", credits)
        .put("w1", rating1Weight).put("w2", rating2Weight).put("we", examWeight)
        .put("records", JSONArray().also { a -> records.forEach { a.put(it.toJson()) } })

    companion object {
        fun from(o: JSONObject): GradebookSubject = GradebookSubject(
            id = o.optLong("id", newId()),
            subject = o.optString("subject", ""),
            credits = o.optInt("credits", 3).coerceIn(1, 30),
            rating1Weight = o.optInt("w1", 30).coerceIn(0, 100),
            rating2Weight = o.optInt("w2", 30).coerceIn(0, 100),
            examWeight = o.optInt("we", 40).coerceIn(0, 100),
            records = (0 until (o.optJSONArray("records")?.length() ?: 0)).mapNotNull { i ->
                o.optJSONArray("records")?.optJSONObject(i)?.let(AssessmentRecord::from)
            },
        )
    }
}
