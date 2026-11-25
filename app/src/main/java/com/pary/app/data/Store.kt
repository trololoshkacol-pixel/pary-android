package com.pary.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Единственное хранилище приложения: настройки и данные в SharedPreferences,
 * состояние видно Compose через mutableStateOf.
 */
object Store {
    private const val PREF = "pary"

    /** Платонус Карагандинского технического университета — адрес по умолчанию. */
    const val DEFAULT_PLATONUS = "platonus.kstu.kz"
    private var sp: SharedPreferences? = null

    /** Вызывается после любого изменения данных — чтобы обновить уведомления и виджет. */
    var onChanged: (() -> Unit)? = null

    private var ready = false

    var lessons by mutableStateOf(emptyList<Lesson>()); private set
    var tasks by mutableStateOf(emptyList<Task>()); private set
    var grades by mutableStateOf(emptyList<Grade>()); private set
    var exams by mutableStateOf(emptyList<Exam>()); private set
    var notes by mutableStateOf(emptyList<Note>()); private set
    var attendance by mutableStateOf(emptyMap<String, Att>()); private set
    var journal by mutableStateOf(emptyList<JournalSubject>()); private set
    var platonusAttendance by mutableStateOf(emptyList<PlatonusAttendance>()); private set
    var journalAt by mutableStateOf(0L); private set
    var widgetMode by mutableStateOf("next"); private set
    var chatDrafts by mutableStateOf(emptyList<ChatDraft>()); private set
    var watchChats by mutableStateOf(true); private set
    var watchedPkgs by mutableStateOf(setOf("com.whatsapp", "org.telegram.messenger")); private set
    var wakeLog by mutableStateOf(emptyList<Int>()); private set
    var brain by mutableStateOf(emptyMap<String, Double>()); private set
    var telegramToken by mutableStateOf(""); private set
    var telegramAuto by mutableStateOf(false); private set
    var telegramOffset by mutableStateOf(0L); private set
    var telegramStatus by mutableStateOf(""); private set

    var theme by mutableStateOf("dark"); private set
    var accent by mutableStateOf(0); private set
    var focusTodayMin by mutableStateOf(0); private set
    var focusTodaySessions by mutableStateOf(0); private set
    var focusGoalMin by mutableStateOf(90); private set
    private var focusDate = LocalDate.now().toString()
    var animations by mutableStateOf(true); private set
    var silentMode by mutableStateOf(true); private set
    var smartAlarm by mutableStateOf(true); private set
    var prepMin by mutableStateOf(90); private set
    var alarmBefore by mutableStateOf(1440); private set
    var missLimit by mutableStateOf(3); private set
    var profileName by mutableStateOf(""); private set
    var profileGroup by mutableStateOf("ТАЭС-26-1"); private set
    var afterNote by mutableStateOf(""); private set
    var anchor by mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
        private set
    var anchorParity by mutableStateOf(Parity.NUM); private set

    var platonusUrl by mutableStateOf(""); private set
    var platonusLogin by mutableStateOf(""); private set
    var platonusPassword by mutableStateOf(""); private set
    var platonusLastSync by mutableStateOf(0L); private set
    var platonusGpa by mutableStateOf(""); private set
    var platonusName by mutableStateOf(""); private set
    var platonusGroup by mutableStateOf(""); private set
    var platonusStatus by mutableStateOf(""); private set

    // ---------- инициализация ----------

    fun init(ctx: Context) {
        if (ready) return
        val s = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp = s
        lessons = readArray(s.getString("lessons", null), ::lessonFrom).ifEmpty { defaultLessons() }
        tasks = readArray(s.getString("tasks_v4", null), ::taskFrom)
        grades = readArray(s.getString("grades", null), ::gradeFrom)
        exams = readArray(s.getString("exams", null), ::examFrom)
        notes = readArray(s.getString("notes", null), ::noteFrom)
        attendance = loadAttendance(s)
        journal = readArray(s.getString("journal_v1", null), ::journalSubjectFrom)
        platonusAttendance = readArray(s.getString("plat_att", null)) { PlatonusAttendance.from(it) }
        theme = s.getString("theme", "dark") ?: "dark"
        accent = s.getInt("accent", 0).coerceIn(0, 9)
        focusDate = s.getString("focus_date", LocalDate.now().toString()) ?: LocalDate.now().toString()
        focusTodayMin = s.getInt("focus_today_min", 0).coerceAtLeast(0)
        focusTodaySessions = s.getInt("focus_today_sessions", 0).coerceAtLeast(0)
        focusGoalMin = s.getInt("focus_goal_min", 90).coerceIn(15, 480)
        if (focusDate != LocalDate.now().toString()) {
            focusDate = LocalDate.now().toString()
            focusTodayMin = 0
            focusTodaySessions = 0
        }
        animations = s.getBoolean("anim", true)
        silentMode = s.getBoolean("silent", true)
        smartAlarm = s.getBoolean("alarm", true)
        prepMin = s.getInt("prep", 90)
        alarmBefore = s.getInt("alarmBefore", 1440)
        missLimit = s.getInt("limit", 3)
        profileName = s.getString("pname", "") ?: ""
        profileGroup = s.getString("pgroup", "ТАЭС-26-1") ?: ""
        afterNote = s.getString("note", "") ?: ""
        anchorParity = Parity.ofKey(s.getString("anchorT", "n")).let {
            if (it == Parity.BOTH) Parity.NUM else it
        }
        platonusUrl = s.getString("plat_url", DEFAULT_PLATONUS) ?: DEFAULT_PLATONUS
        platonusLogin = s.getString("plat_login", "") ?: ""
        platonusPassword = Secure.decrypt(s.getString("plat_pass", "") ?: "")
        platonusStatus = s.getString("plat_status", "") ?: ""
        platonusLastSync = s.getLong("plat_last", 0L)
        platonusGpa = s.getString("plat_gpa", "") ?: ""
        platonusName = s.getString("plat_name", "") ?: ""
        platonusGroup = s.getString("plat_group", "") ?: ""
        journalAt = s.getLong("journal_at", 0L)
        widgetMode = s.getString("widget_mode", "next") ?: "next"
        chatDrafts = readArray(s.getString("chat_drafts", null), ::chatDraftFrom)
        watchChats = s.getBoolean("watch_chats", true)
        telegramToken = Secure.decrypt(s.getString("tg_token", "") ?: "")
        telegramAuto = s.getBoolean("tg_auto", false)
        telegramOffset = s.getLong("tg_offset", 0L)
        telegramStatus = s.getString("tg_status", "") ?: ""
        brain = runCatching {
            val o = org.json.JSONObject(s.getString("brain", "{}") ?: "{}")
            o.keys().asSequence().associateWith { o.optDouble(it, 0.0) }
        }.getOrDefault(emptyMap())
        wakeLog = s.getString("wake_log", "").orEmpty()
            .split(",").mapNotNull { it.trim().toIntOrNull() }.takeLast(20)
        watchedPkgs = (s.getStringSet("watched_pkgs", null) ?: setOf("com.whatsapp", "org.telegram.messenger"))
            .toSet()
        anchor = runCatching { LocalDate.parse(s.getString("anchor", "") ?: "") }
            .getOrElse { LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        ready = true
        if (!s.contains("att")) attachFromLegacy(s)
    }

    private fun loadAttendance(s: SharedPreferences): Map<String, Att> {
        val raw = s.getString("att", null)
        if (raw == null) {
            // старый формат: ключи m_<дата><HHMM> со значением «p:Предмет» / «m:Предмет»
            val out = linkedMapOf<String, Att>()
            for ((k, v) in s.all) {
                if (!k.startsWith("m_") || v !is String || v.length < 3) continue
                val body = k.substring(2)
                if (body.length < 14) continue
                val key = body.substring(0, 10) + "@" +
                    body.substring(10, 12) + ":" + body.substring(12, 14)
                out[key] = Att(key, v[0] == 'p', v.substringAfter(":", "?"))
            }
            return out
        }
        return readArray(raw, { o ->
            Att(o.optString("k"), o.optBoolean("p", true), o.optString("s"))
        }).associateBy { it.key }
    }

    private fun attachFromLegacy(s: SharedPreferences) {
        val legacy = s.all.keys.filter { it.startsWith("m_") }
        if (legacy.isEmpty()) return
        val e = s.edit()
        legacy.forEach { e.remove(it) }
        e.putString("att", attJson())
        e.apply()
    }

    private fun <T> readArray(json: String?, parse: (JSONObject) -> T): List<T> = try {
        if (json.isNullOrBlank()) emptyList()
        else {
            val a = JSONArray(json)
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                runCatching { parse(o) }.getOrNull()
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    // ---------- настройки ----------

    fun updateTheme(v: String) { theme = v; persist() }
    fun updateAccent(v: Int) { accent = v.coerceIn(0, 9); persist() }
    fun updateFocusGoal(minutes: Int) { focusGoalMin = minutes.coerceIn(15, 480); persist() }
    fun recordFocusSession(minutes: Int) {
        if (minutes !in 1..240) return
        val today = LocalDate.now().toString()
        if (focusDate != today) {
            focusDate = today
            focusTodayMin = 0
            focusTodaySessions = 0
        }
        focusTodayMin += minutes
        focusTodaySessions += 1
        persist()
    }
    fun updateAnimations(v: Boolean) { animations = v; persist() }
    fun updateSilentMode(v: Boolean) { silentMode = v; persist() }
    fun updateSmartAlarm(v: Boolean) { smartAlarm = v; persist() }
    fun updatePrep(v: Int) { prepMin = v.coerceIn(15, 480); persist() }
    fun updateAlarmBefore(v: Int) { alarmBefore = v.coerceIn(360, 1440); persist() }
    fun updateMissLimit(v: Int) { missLimit = v.coerceIn(1, 30); persist() }
    fun setProfile(name: String, group: String) {
        profileName = name.trim(); profileGroup = group.trim(); persist()
    }
    fun updateAfterNote(v: String) { afterNote = v; persist() }
    fun setWeek(p: Parity) {
        anchor = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        anchorParity = if (p == Parity.BOTH) Parity.NUM else p
        persist()
    }

    fun updatePlatonus(url: String, login: String, password: String) {
        platonusUrl = url.trim()
        platonusLogin = login.trim()
        platonusPassword = password
        persist()
    }

    fun updateTelegram(token: String, auto: Boolean) {
        telegramToken = token.trim()
        telegramAuto = auto
        persist()
    }

    fun updateTelegramOffset(v: Long) {
        telegramOffset = v
        persist()
    }

    fun updateTelegramStatus(v: String) {
        telegramStatus = v
        persist()
    }

    fun isChatHomework(text: String): Boolean = MiniBrain.judge(brain, text)

    /** Учит мини-ИИ на твоём решении. */
    fun teachBrain(text: String, positive: Boolean) {
        brain = MiniBrain.learn(brain.toMutableMap(), text, positive)
        persist()
    }

    /** Запоминает, за сколько до пары ты реально встал. */
    fun recordWake(prepMinutes: Int) {
        if (prepMinutes !in 15..480) return
        wakeLog = (wakeLog + prepMinutes).takeLast(20)
        persist()
    }

    /** Привычка: медиана последних подъёмов. */
    fun learnedPrep(): Int? {
        if (wakeLog.size < 2) return null
        val sorted = wakeLog.sorted()
        return sorted[sorted.size / 2].coerceIn(15, 480)
    }

    fun updateWatchChats(v: Boolean) {
        watchChats = v
        persist()
    }

    fun updateWatched(pkgs: Set<String>) {
        watchedPkgs = pkgs
        persist()
    }

    /** Кладёт черновик задачи; одинаковые сообщения не дублируются. */
    fun addChatDraft(draft: ChatDraft): Boolean {
        val norm = draft.text.trim().lowercase()
        if (norm.isBlank()) return false
        if (chatDrafts.any { it.text.trim().lowercase() == norm && it.chat == draft.chat }) return false
        chatDrafts = (chatDrafts + draft).takeLast(60)
        persist()
        return true
    }

    fun dismissDraft(id: Long) {
        chatDrafts.firstOrNull { it.id == id }?.let { teachBrain(it.text, false) }
        chatDrafts = chatDrafts.filterNot { it.id == id }
        persist()
    }

    fun acceptDraft(id: Long) {
        val d = chatDrafts.firstOrNull { it.id == id } ?: return
        teachBrain(d.text, true)
        tasks = tasks + Task(
            title = d.text.take(120),
            subject = ChatParser.subjectFrom(d.chat),
            due = d.due,
            priority = 2,
            note = "Из чата: ${d.chat}",
        )
        chatDrafts = chatDrafts.filterNot { it.id == id }
        persist()
    }

    fun updateWidgetMode(v: String) {
        widgetMode = v
        persist()
    }

    fun updateJournal(list: List<JournalSubject>) {
        journal = list
        journalAt = System.currentTimeMillis()
        persist()
    }

    fun updatePlatonusProfile(gpa: String, name: String, group: String) {
        if (gpa.isNotBlank()) platonusGpa = gpa
        if (name.isNotBlank()) platonusName = name
        if (group.isNotBlank()) platonusGroup = group
        persist()
    }

    fun replacePlatonusAttendance(list: List<PlatonusAttendance>) {
        platonusAttendance = list.distinctBy { it.key + it.subject }
        persist()
    }

    fun replacePlatonusTasks(list: List<Task>) {
        tasks = tasks.filterNot { it.source == "platonus" } + list
        persist()
    }

    fun updatePlatonusStatus(v: String) {
        platonusStatus = v
        persist()
    }

    fun updatePlatonusLastSync(ms: Long) {
        platonusLastSync = ms
        persist()
    }

    /** Заменяет только пары, пришедшие из Платонуса; добавленные руками остаются. */
    fun replaceImported(list: List<Lesson>) {
        lessons = lessons.filterNot { it.source == "platonus" } + list
        persist()
    }

    fun importedCount(): Int = lessons.count { it.source == "platonus" }

    fun parityToday(): Parity = Logic.parityOn(anchor, anchorParity, LocalDate.now())
    fun parityOn(date: LocalDate): Parity = Logic.parityOn(anchor, anchorParity, date)
    fun dayLessons(date: LocalDate): List<Lesson> = Logic.lessonsOn(lessons, date, parityOn(date))
    fun subjects(): List<String> = Logic.subjects(lessons)
    fun subjectNames(): List<String> = (Logic.subjects(lessons) + grades.map { it.subject } + tasks.map { it.subject })
        .filter { it.isNotBlank() }.distinct().sorted()

    // ---------- расписание ----------

    fun addLesson(l: Lesson) { lessons = lessons + l; persist() }
    fun updateLesson(l: Lesson) { lessons = lessons.map { if (it.id == l.id) l else it }; persist() }
    fun deleteLesson(id: Long) { lessons = lessons.filterNot { it.id == id }; persist() }

    // ---------- отметки ----------

    fun attendanceFor(l: Lesson, date: LocalDate): Att? = attendance[l.keyOn(date)]

    fun mark(l: Lesson, date: LocalDate, present: Boolean) {
        val key = l.keyOn(date)
        attendance = attendance + (key to Att(key, present, l.subject))
        persist()
    }

    fun unmark(l: Lesson, date: LocalDate) {
        attendance = attendance - l.keyOn(date)
        persist()
    }

    fun stats(): Map<String, Pair<Int, Int>> = Logic.subjectStats(attendance.values)

    fun totalPresent(): Int = attendance.values.count { it.present }
    fun totalMissed(): Int = attendance.values.count { !it.present }

    // ---------- задачи ----------

    fun addTask(t: Task) { tasks = tasks + t; persist() }
    fun updateTask(t: Task) { tasks = tasks.map { if (it.id == t.id) t else it }; persist() }
    fun deleteTask(id: Long) { tasks = tasks.filterNot { it.id == id }; persist() }
    fun toggleTask(id: Long) {
        tasks = tasks.map { if (it.id == id) it.copy(done = !it.done) else it }; persist()
    }

    fun openTasks(): List<Task> = tasks.filterNot { it.done }

    fun removeCompleted() {
        tasks = tasks.filterNot { it.done }
        persist()
    }

    // ---------- оценки ----------

    fun addGrade(g: Grade) { grades = grades + g; persist() }
    fun deleteGrade(id: Long) { grades = grades.filterNot { it.id == id }; persist() }
    fun avgAll(): Double? = Logic.average(grades.map { it.value })
    fun avgBySubject(): Map<String, Double> = grades.groupBy { it.subject }
        .mapValues { (_, v) -> Logic.average(v.map { it.value }) ?: 0.0 }

    // ---------- экзамены ----------

    fun addExam(e: Exam) { exams = exams + e; persist() }
    fun updateExam(e: Exam) { exams = exams.map { if (it.id == e.id) e else it }; persist() }
    fun deleteExam(id: Long) { exams = exams.filterNot { it.id == id }; persist() }
    fun upcomingExams(): List<Exam> =
        exams.filterNot { it.passed }.sortedBy { it.at }

    // ---------- заметки ----------

    fun addNote(n: Note) { notes = notes + n; persist() }
    fun deleteNote(id: Long) { notes = notes.filterNot { it.id == id }; persist() }

    // ---------- сохранение ----------

    private fun journalSubjectFrom(o: JSONObject): JournalSubject = JournalSubject.from(o)

    private fun chatDraftFrom(o: JSONObject): ChatDraft = ChatDraft.from(o)

    private fun attJson(): String {
        val a = JSONArray()
        for (v in attendance.values) a.put(JSONObject().put("k", v.key).put("p", v.present).put("s", v.subject))
        return a.toString()
    }

    private fun persist() {
        val s = sp ?: return
        val e = s.edit()
        e.putString("lessons", JSONArray().also { a -> lessons.forEach { a.put(it.toJson()) } }.toString())
        e.putString("tasks_v4", JSONArray().also { a -> tasks.forEach { a.put(it.toJson()) } }.toString())
        e.putString("grades", JSONArray().also { a -> grades.forEach { a.put(it.toJson()) } }.toString())
        e.putString("exams", JSONArray().also { a -> exams.forEach { a.put(it.toJson()) } }.toString())
        e.putString("notes", JSONArray().also { a -> notes.forEach { a.put(it.toJson()) } }.toString())
        e.putString("att", attJson())
        e.putString("journal_v1", JSONArray().also { a -> journal.forEach { a.put(it.toJson()) } }.toString())
        e.putString("plat_att", JSONArray().also { a -> platonusAttendance.forEach { a.put(it.toJson()) } }.toString())
        e.putLong("journal_at", journalAt)
        e.putString("chat_drafts", JSONArray().also { a -> chatDrafts.forEach { a.put(it.toJson()) } }.toString())
        e.putBoolean("watch_chats", watchChats)
        e.putStringSet("watched_pkgs", watchedPkgs.toSet())
        e.putString("wake_log", wakeLog.joinToString(","))
        e.putString("brain", JSONObject().also { o -> brain.forEach { (k, v) -> o.put(k, v) } }.toString())
        e.putBoolean("tg_auto", telegramAuto)
        e.putLong("tg_offset", telegramOffset)
        e.putString("tg_status", telegramStatus)
        if (telegramToken.isBlank()) {
            e.putString("tg_token", "")
        } else {
            runCatching { Secure.encrypt(telegramToken) }.getOrNull()?.let { e.putString("tg_token", it) }
        }
        e.putString("plat_status", platonusStatus)
            .putString("plat_gpa", platonusGpa).putString("plat_name", platonusName)
            .putString("plat_group", platonusGroup)
        e.putString("theme", theme).putInt("accent", accent).putBoolean("anim", animations)
            .putString("focus_date", focusDate).putInt("focus_today_min", focusTodayMin)
            .putInt("focus_today_sessions", focusTodaySessions).putInt("focus_goal_min", focusGoalMin)
            .putBoolean("silent", silentMode).putBoolean("alarm", smartAlarm)
            .putInt("prep", prepMin).putInt("alarmBefore", alarmBefore).putInt("limit", missLimit)
            .putString("widget_mode", widgetMode)
            .putString("pname", profileName).putString("pgroup", profileGroup)
            .putString("note", afterNote)
            .putString("anchor", anchor.toString()).putString("anchorT", anchorParity.key)
            .putString("plat_url", platonusUrl).putString("plat_login", platonusLogin)
            .putLong("plat_last", platonusLastSync)
        if (platonusPassword.isBlank()) {
            e.putString("plat_pass", "")
        } else {
            runCatching { Secure.encrypt(platonusPassword) }.getOrNull()?.let { e.putString("plat_pass", it) }
        }
        e.apply()
        onChanged?.invoke()
    }

    // ---------- резервная копия ----------

    fun exportJson(): String = JSONObject()
        .put("version", 4)
        .put("lessons", JSONArray().also { a -> lessons.forEach { a.put(it.toJson()) } })
        .put("tasks", JSONArray().also { a -> tasks.forEach { a.put(it.toJson()) } })
        .put("grades", JSONArray().also { a -> grades.forEach { a.put(it.toJson()) } })
        .put("exams", JSONArray().also { a -> exams.forEach { a.put(it.toJson()) } })
        .put("notes", JSONArray().also { a -> notes.forEach { a.put(it.toJson()) } })
        .put("att", JSONArray().also { a -> attendance.values.forEach { a.put(JSONObject().put("k", it.key).put("p", it.present).put("s", it.subject)) } })
        .toString(2)

    fun importJson(text: String): Boolean {
        return try {
            val o = JSONObject(text)
            val l = readArray(o.optJSONArray("lessons")?.toString(), ::lessonFrom).filterIsInstance<Lesson>()
            val t = readArray(o.optJSONArray("tasks")?.toString(), ::taskFrom).filterIsInstance<Task>()
            val g = readArray(o.optJSONArray("grades")?.toString(), ::gradeFrom).filterIsInstance<Grade>()
            val ex = readArray(o.optJSONArray("exams")?.toString(), ::examFrom).filterIsInstance<Exam>()
            val n = readArray(o.optJSONArray("notes")?.toString(), ::noteFrom).filterIsInstance<Note>()
            val at = readArray(o.optJSONArray("att")?.toString(), { x ->
                Att(x.optString("k"), x.optBoolean("p", true), x.optString("s"))
            }).filterIsInstance<Att>()
            if (l.isEmpty() && t.isEmpty() && g.isEmpty() && ex.isEmpty() && n.isEmpty()) return false
            lessons = l.ifEmpty { lessons }
            tasks = t
            grades = g
            exams = ex
            notes = n
            attendance = at.associateBy { it.key }
            persist()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------- стартовое расписание ----------

    fun seedSchedule() {
        lessons = defaultLessons()
        // Сид собран для недели 23.11.2026 — это знаменатель.
        anchor = LocalDate.of(2026, 11, 23)
        anchorParity = Parity.DEN
        persist()
    }

    /**
     * Стартовое расписание группы ТАЭС-26-1 (Платонус, КарТУ).
     * Пары, одинаковые в обе недели, лежат один раз с parity = BOTH — чтобы не было дублей.
     * В одном дне одного типа недели пары идут по слотам и не пересекаются:
     * 1) 9:00–10:45, 2) 10:55–12:40, 3) 13:10–14:55, 4) 15:05–16:50, 5) 17:00–18:45.
     */
    private fun defaultLessons(): List<Lesson> = listOf(
        // каждую неделю — и в числителе, и в знаменателе
        Lesson(day = 0, parity = Parity.BOTH, start = LocalTime.of(13, 10), end = LocalTime.of(14, 55), name = "Физическая культура", room = "Спорткомплекс, спортзал", teacher = "Толеуов Д.Р.", quiet = false),
        Lesson(day = 3, parity = Parity.BOTH, start = LocalTime.of(10, 55), end = LocalTime.of(12, 40), name = "История Казахстана", room = "корпус №1, 230", teacher = "Нурлигенова З.Н."),
        Lesson(day = 4, parity = Parity.BOTH, start = LocalTime.of(13, 10), end = LocalTime.of(14, 55), name = "Казахский (Русский) язык", room = "корпус №1, 323", teacher = "Аманжолова Т.А."),
        Lesson(day = 4, parity = Parity.BOTH, start = LocalTime.of(15, 5), end = LocalTime.of(16, 50), name = "Иностранный язык", room = "514", teacher = ""),
        Lesson(day = 4, parity = Parity.BOTH, start = LocalTime.of(17, 0), end = LocalTime.of(18, 45), name = "Линал и матан", room = "корпус №1, 314", teacher = "Андрющенко Ю.А."),
        Lesson(day = 5, parity = Parity.BOTH, start = LocalTime.of(15, 5), end = LocalTime.of(16, 50), name = "Физическая культура", room = "Спорткомплекс, спортзал", teacher = "Толеуов Д.Р.", quiet = false),

        // числитель — неделя 16 ноября 2026
        Lesson(day = 0, parity = Parity.NUM, start = LocalTime.of(15, 5), end = LocalTime.of(16, 50), name = "Казахский (Русский) язык", room = "корпус №1, 323", teacher = "Аманжолова Т.А."),
        Lesson(day = 3, parity = Parity.NUM, start = LocalTime.of(9, 0), end = LocalTime.of(10, 45), name = "ИКТ (лаб.)", room = "корпус №1, 257", teacher = "Акылтаева С.Д."),
        Lesson(day = 3, parity = Parity.NUM, start = LocalTime.of(13, 10), end = LocalTime.of(14, 55), name = "Линал и матан (лек.)", room = "корпус №1, 305", teacher = "Кажикенова С.Ш."),
        Lesson(day = 3, parity = Parity.NUM, start = LocalTime.of(15, 5), end = LocalTime.of(16, 50), name = "ИКТ (лек.)", room = "Главный корпус, 420", teacher = "Жумакаева К.Н."),

        // знаменатель — неделя 23 ноября 2026
        Lesson(day = 0, parity = Parity.DEN, start = LocalTime.of(15, 5), end = LocalTime.of(16, 50), name = "ИКТ (лаб.)", room = "корпус №1, 257", teacher = "Акылтаева С.Д."),
        Lesson(day = 5, parity = Parity.DEN, start = LocalTime.of(13, 10), end = LocalTime.of(14, 55), name = "История Казахстана (лекция)", room = "корпус №1, 209", teacher = "Нурлигенова З.Н."),
    )}
