package com.pary.app.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.pary.app.MainActivity
import com.pary.app.R
import com.pary.app.data.Lesson
import com.pary.app.data.Logic
import com.pary.app.data.Store
import com.pary.app.data.TelegramApi
import org.json.JSONArray
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Уведомление-статус, виджет, будильник, напоминания о задачах и тихий режим. */
object Sync {
    const val CH_STATUS = "pary_status"
    const val CH_TASK = "pary_task"
    const val CH_ALARM = "pary_alarm"

    private const val ID_STATUS = 1
    private const val ID_ALARM = 2
    private const val ID_TASK_BASE = 1000
    private const val ID_DRAFT_BASE = 20000
    private const val PREFS = "pary"

    private val PI_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun refresh(ctx: Context) {
        runCatching {
            Store.init(ctx)
            ensureChannels(ctx)
            val now = LocalDateTime.now()
            planAlarms(ctx)
            rescheduleTasks(ctx)
            Store.tasks.filter { it.done }.forEach { cancelTaskNotification(ctx, it.id) }
            applyDnd(ctx, now)
            updateStatus(ctx, now)
            updateWidget(ctx, now)
            scheduleNextTick(ctx, now)
            scheduleTelegramPoll(ctx)
        }
    }

    /** После загрузки телефона, смены часового пояса или импорта данных. */
    fun syncAll(ctx: Context) {
        runCatching {
            Store.init(ctx)
            ensureChannels(ctx)
            rescheduleTasks(ctx)
            val now = LocalDateTime.now()
            planAlarms(ctx)
            applyDnd(ctx, now)
            updateStatus(ctx, now)
            updateWidget(ctx, now)
            scheduleNextTick(ctx, now)
            scheduleTelegramPoll(ctx)
        }
    }

    // ---------- каналы ----------

    private fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, "Статус пар", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_TASK, "Напоминания о задачах", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Дедлайны и напоминания по учёбе"
                enableVibration(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, "Будильник", NotificationManager.IMPORTANCE_HIGH).apply {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setSound(
                    uri,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 400, 600)
                setBypassDnd(true)
            },
        )
    }

    // ---------- статус ----------

    private fun updateStatus(ctx: Context, now: LocalDateTime) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val today = now.toLocalDate()
        val day = Store.dayLessons(today)
        val cur = Logic.currentLesson(day, now)
        val next = Logic.nextLesson(day, now)

        val target: Lesson? = cur?.takeIf { Store.attendanceFor(it, today) == null }
            ?: day.lastOrNull { !it.end.isAfter(now.toLocalTime()) && Store.attendanceFor(it, today) == null }

        var title: String
        var body: String
        var pct = -1

        if (cur != null) {
            title = listOf(cur.name, cur.room).filter { it.isNotBlank() }.joinToString(" · ")
            val total = Duration.between(cur.start, cur.end).toSeconds().coerceAtLeast(1)
            val passed = Duration.between(cur.start, now).toSeconds().coerceAtLeast(0)
            pct = (passed * 100 / total).toInt().coerceIn(0, 100)
            val rest = Logic.humanLeft(today.atTime(cur.end), now)
            val after = day.firstOrNull { it.start.isAfter(cur.end) }
            body = "Ещё $rest · до ${fmt(cur.end)}" +
                if (after != null) "\nДальше в ${fmt(after.start)}: ${after.name}" else "\nЭто последняя пара"
        } else if (next != null) {
            title = if (day.firstOrNull() === next) "Первая пара в ${fmt(next.start)}" else "Перерыв до ${fmt(next.start)}"
            body = "${next.name}${if (next.room.isNotBlank()) " · ${next.room}" else ""}\nчерез " +
                Logic.humanLeft(today.atTime(next.start), now)
        } else {
            val tomorrow = today.plusDays(1)
            val nx = (1..7).firstNotNullOfOrNull { i ->
                val d = today.plusDays(i.toLong())
                Store.dayLessons(d).firstOrNull()?.let { d to it }
            }
            title = Store.afterNote.trim().ifBlank { if (day.isEmpty()) "Сегодня пар нет" else "Пары закончились" }
            body = if (nx != null) {
                val (d, l) = nx
                val whenS = when (d) {
                    tomorrow -> "Завтра"
                    else -> "В ${ruDow(d)}"
                }
                "$whenS в ${fmt(l.start)}: ${l.name}" + if (l.room.isNotBlank()) " · ${l.room}" else ""
            } else {
                "Ближайшие дни свободны"
            }
        }

        val missed = Store.totalMissed()
        if (missed > 0) body += "\nОтметок пропусков: $missed"

        val builder = Notification.Builder(ctx, CH_STATUS)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(title)
            .setContentText(body.substringBefore("\n"))
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(ctx))
        if (pct >= 0) builder.setProgress(100, pct, false)
        if (target != null) {
            builder.addAction(markAction(ctx, target, today, true))
            builder.addAction(markAction(ctx, target, today, false))
        }
        runCatching { nm.notify(ID_STATUS, builder.build()) }
    }

    private fun markAction(ctx: Context, l: Lesson, date: LocalDate, present: Boolean): Notification.Action {
        val i = Intent(ctx, MarkReceiver::class.java)
            .setAction(if (present) "pary.MARK_P" else "pary.MARK_M")
            .putExtra("lessonId", l.id)
            .putExtra("date", date.toString())
        val pi = PendingIntent.getBroadcast(ctx, if (present) 11 else 12, i, PI_FLAGS)
        return Notification.Action.Builder(null, if (present) "Был" else "Пропустил", pi).build()
    }

    // ---------- виджет ----------

    /** Маленькая шапка виджета: «Пары · пн 23 ноя». */
    private fun widgetHead(today: LocalDate): String =
        "Пары · " + ruDow(today) + " " + ruDate(today)

    /** Склонение: 1 пара, 2 пары, 5 пар. */
    private fun parasText(n: Int): String {
        val tail = if (n % 10 == 1 && n % 100 != 11) "пара"
        else if (n % 10 in 2..4 && n % 100 !in 12..14) "пары"
        else "пар"
        return n.toString() + " " + tail
    }

    /** Строка режима «week»: «пн: 13:10, 15:05». */
    private fun weekLine(d: LocalDate, lessons: List<Lesson>): String =
        ruDow(d) + ": " + lessons.joinToString(", ") { fmt(it.start) }

    private fun updateWidget(ctx: Context, now: LocalDateTime) {
        val awm = AppWidgetManager.getInstance(ctx) ?: return
        val ids = awm.getAppWidgetIds(ComponentName(ctx, WidgetProvider::class.java))
        if (ids.isEmpty()) return

        val prefs = ctx.getSharedPreferences("pary_widgets", Context.MODE_PRIVATE)
        val today = now.toLocalDate()
        val day = Store.dayLessons(today)
        val cur = Logic.currentLesson(day, now)
        val next = Logic.nextLesson(day, now)

        for (id in ids) {
            val mode = prefs.getString("mode_$id", Store.widgetMode) ?: "next"
            val customTitle = prefs.getString("title_$id", "").orEmpty().trim()
            val theme = prefs.getString("theme_$id", Store.theme) ?: Store.theme
            val head: String
            val title: String
            val body: String
            var pct = -1
            var bodyLines = 4

            when (mode) {
                "today" -> {
                    head = "РАСПИСАНИЕ · ${ruDow(today)} ${ruDate(today)}"
                    title = if (day.isEmpty()) "Пар нет" else "Сегодня · " + parasText(day.size)
                    bodyLines = 6
                    body = day.joinToString("\n") { l ->
                        "• " + fmt(l.start) + "  " + l.name +
                            if (l.room.isNotBlank()) " · " + l.room else ""
                    }
                }
                "tasks" -> {
                    val openAll = Store.tasks.filterNot { it.done }
                        .sortedWith(compareBy({ it.due == null }, { it.due }))
                    val open = openAll.take(5)
                    head = "ЗАДАЧИ · ${openAll.size} АКТИВНЫХ"
                    title = if (open.isEmpty()) "Задач нет" else "Ближайшие дедлайны"
                    bodyLines = 5
                    body = if (open.isEmpty()) Store.afterNote.trim().ifBlank { "Всё сделано ✓" }
                    else open.joinToString("\n") { t ->
                        "• " + t.title + (t.due?.let { " — " + Logic.humanLeft(it, now) } ?: "")
                    }
                }
                "week" -> {
                    head = "РАСПИСАНИЕ · БЛИЖАЙШИЕ ДНИ"
                    title = "Твоя учебная неделя"
                    val parts = mutableListOf<String>()
                    for (i in 0..6) {
                        val d = today.plusDays(i.toLong())
                        val ls = Store.dayLessons(d)
                        if (ls.isEmpty()) continue
                        parts.add(weekLine(d, ls))
                        if (parts.size >= 6) break
                    }
                    bodyLines = 6
                    body = if (parts.isEmpty()) "На ближайшие дни пар нет" else parts.joinToString("\n")
                }
                "stats" -> {
                    head = "МОЯ УЧЁБА · СТАТИСТИКА"
                    val avg = Store.avgAll()
                    val total = Store.totalPresent() + Store.totalMissed()
                    val percent = if (total == 0) null else Store.totalPresent() * 100 / total
                    title = avg?.let { "Средний балл · " + String.format(java.util.Locale.US, "%.2f", it) }
                        ?: "Оценок пока нет"
                    bodyLines = 4
                    body = "Оценок: ${Store.grades.size}\nПосещаемость: " +
                        (percent?.let { "$it% (${Store.totalPresent()}/$total)" } ?: "нет отметок") +
                        "\nЗадач выполнено: ${Store.tasks.count { it.done }}/${Store.tasks.size}"
                    if (percent != null) pct = percent
                }
                "notes" -> {
                    val note = Store.notes.maxByOrNull { it.at }
                    head = "ЗАМЕТКИ · ПОСЛЕДНЯЯ"
                    title = note?.subject?.takeIf { it.isNotBlank() } ?: "Быстрая заметка"
                    bodyLines = 5
                    body = note?.text?.take(500) ?: "Пока нет заметок. Добавь первую в разделе «Учёба»."
                }
                "custom" -> {
                    head = "МОЙ ВИДЖЕТ"
                    title = "Своя карточка"
                    bodyLines = 6
                    body = prefs.getString("body_$id", "").orEmpty().ifBlank { "Открой настройки виджета и добавь свой текст." }
                }
                "drafts" -> {
                    val drafts = Store.chatDrafts.takeLast(4).reversed()
                    head = "ИМПОРТ ИЗ ЧАТОВ · ${Store.chatDrafts.size}"
                    title = if (drafts.isEmpty()) "Новых заданий нет" else "Проверь задания"
                    bodyLines = 5
                    body = if (drafts.isEmpty()) "Поделись сообщением из Telegram, WhatsApp или VK с Pary."
                    else drafts.joinToString("\n") { "• " + it.text.take(100) }
                }
                else -> {
                    head = "БЛИЖАЙШАЯ ПАРА · ${ruDow(today)}"
                    if (cur != null) {
                        title = cur.name
                        val total = Duration.between(cur.start, cur.end).toSeconds().coerceAtLeast(1)
                        val passed = Duration.between(cur.start, now).toSeconds().coerceAtLeast(0)
                        pct = (passed * 100 / total).toInt().coerceIn(0, 100)
                        body = "${fmt(cur.start)}–${fmt(cur.end)}" +
                            (if (cur.room.isNotBlank()) " · ${cur.room}" else "") +
                            "\nОсталось " + Logic.humanLeft(today.atTime(cur.end), now)
                    } else if (next != null) {
                        title = next.name
                        body = "Начало в ${fmt(next.start)}" +
                            (if (next.room.isNotBlank()) " · ${next.room}" else "") +
                            "\nЧерез " + Logic.humanLeft(today.atTime(next.start), now)
                    } else {
                        title = if (day.isEmpty()) "Сегодня пар нет" else "Пары закончились"
                        val nx = (1..7).firstNotNullOfOrNull { i ->
                            val d = today.plusDays(i.toLong())
                            Store.dayLessons(d).firstOrNull()?.let { d to it }
                        }
                        body = if (nx != null) "${ruDow(nx.first)} в ${fmt(nx.second.start)} · ${nx.second.name}"
                        else Store.afterNote.trim().ifBlank { "Можно отдохнуть" }
                    }
                }
            }

            val light = theme == "light"
            val headColor = if (light) 0xFF7A8494.toInt() else 0xFF9CA6B8.toInt()
            val titleColor = if (light) 0xFF171923.toInt() else 0xFFF7F5FF.toInt()
            val bodyColor = if (light) 0xFF535D6D.toInt() else 0xFFBEC4D2.toInt()
            val bg = when (theme) {
                "light" -> R.drawable.widget_bg_light
                "amoled" -> R.drawable.widget_bg_amoled
                else -> R.drawable.widget_bg
            }

            val options = runCatching { awm.getAppWidgetOptions(id) }.getOrNull()
            val minW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 260) ?: 260
            val minH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180) ?: 180
            val compact = minW < 220 || minH < 120
            val rv = RemoteViews(ctx.packageName, if (compact) R.layout.widget_compact else R.layout.widget)
            rv.setTextViewText(R.id.w_head, customTitle.ifBlank { head })
            rv.setTextViewText(R.id.w_title, title)
            rv.setTextViewText(R.id.w_body, body)
            rv.setInt(R.id.w_body, "setMaxLines", if (compact) bodyLines.coerceAtMost(2) else bodyLines.coerceAtMost(6))
            rv.setTextColor(R.id.w_head, headColor)
            rv.setTextColor(R.id.w_title, titleColor)
            rv.setTextColor(R.id.w_body, bodyColor)
            rv.setViewVisibility(R.id.w_body, if (body.isBlank()) View.GONE else View.VISIBLE)
            rv.setViewVisibility(R.id.w_bar, if (pct >= 0) View.VISIBLE else View.GONE)
            if (pct >= 0) rv.setProgressBar(R.id.w_bar, 100, pct, false)
            rv.setInt(R.id.w_root, "setBackgroundResource", bg)
            rv.setOnClickPendingIntent(R.id.w_root, openApp(ctx))
            runCatching { awm.updateAppWidget(id, rv) }
        }
    }

    // ---------- тихий режим ----------

    private fun applyDnd(ctx: Context, now: LocalDateTime) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.isNotificationPolicyAccessGranted) return
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val t = now.toLocalTime()
        val want = Store.silentMode &&
            Logic.silentSpans(Store.dayLessons(now.toLocalDate())).any { !t.isBefore(it.first) && t.isBefore(it.second) }
        val on = sp.getBoolean("silenced", false)
        runCatching {
            when {
                want && !on -> {
                    sp.edit().putInt("prevF", nm.currentInterruptionFilter).putBoolean("silenced", true).apply()
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                }
                !want && on -> {
                    val prev = sp.getInt("prevF", NotificationManager.INTERRUPTION_FILTER_ALL)
                    nm.setInterruptionFilter(
                        if (prev < NotificationManager.INTERRUPTION_FILTER_ALL) NotificationManager.INTERRUPTION_FILTER_ALL
                        else prev,
                    )
                    sp.edit().putBoolean("silenced", false).apply()
                }
            }
        }
    }

    // ---------- будильник ----------

    fun setAlarm(ctx: Context, atMs: Long, test: Boolean) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val ai = Intent(ctx, AlarmReceiver::class.java)
        if (test) ai.action = "pary.TEST"
        val pi = PendingIntent.getBroadcast(ctx, if (test) 21 else 22, ai, PI_FLAGS)
        val show = openApp(ctx)
        val ok = runCatching {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atMs, show), pi)
        }.isSuccess
        if (!ok) {
            runCatching {
                if (canExact(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
            }
        }
    }

    fun cancelPlanned(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(ctx, 22, Intent(ctx, AlarmReceiver::class.java), PI_FLAGS)
        runCatching { am.cancel(pi) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("alarmFor", "").putString("alarmMsg", "").apply()
    }

    /**
     * Планирует подъём на ближайшую подходящую пару: время пары минус сборы.
     * Пересчитывается при каждом запуске приложения, смене расписания и настроек.
     */
    fun planAlarms(ctx: Context) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!Store.smartAlarm) {
            cancelPlanned(ctx)
            planText = "Будильник выключен"
            return
        }
        val now = LocalDateTime.now()
        var wake: LocalDateTime? = null
        var lesson: Lesson? = null
        var day: LocalDate? = null
        for (i in 0..8) {
            val d = now.toLocalDate().plusDays(i.toLong())
            val first = Store.dayLessons(d).firstOrNull() ?: continue
            val startMin = first.start.hour * 60 + first.start.minute
            if (startMin >= Store.alarmBefore) continue
            val w = d.atTime(first.start).minusMinutes(Store.prepMin.toLong())
            if (w.toLocalTime().hour < 5) continue
            if (!w.isAfter(now.plusMinutes(2))) continue
            wake = w; lesson = first; day = d
            break
        }
        val l = lesson
        val w = wake
        val d0 = day
        if (l == null || w == null || d0 == null) {
            cancelPlanned(ctx)
            planText = "Будильник не нужен — подходящих пар нет"
            return
        }
        val msg = "Первая пара в ${fmt(l.start)} — ${l.name}" + if (l.room.isNotBlank()) " · ${l.room}" else ""
        sp.edit().putString("alarmFor", d0.toString()).putString("alarmMsg", msg)
            .putInt("alarmStartMin", l.start.hour * 60 + l.start.minute).apply()
        planText = "Будильник: ${ruDate(d0)} в ${fmt(w)} (пара в ${fmt(l.start)})"
        setAlarm(ctx, w.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), false)
    }

    /** Текст о ближайшем запланированном подъёме. */
    var planText: String = ""
        private set

    fun alarmPreview(ctx: Context): String {
        Store.init(ctx)
        runCatching { planAlarms(ctx) }
        return planText.ifBlank { "Будильник не нужен — подходящих пар нет" }
    }

    // ---------- напоминания о задачах ----------

    fun rescheduleTasks(ctx: Context) {
        Store.init(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = runCatching { JSONArray(sp.getString("task_alarms", "[]")) }.getOrNull()
        if (old != null) {
            for (i in 0 until old.length()) {
                val code = old.optInt(i)
                runCatching { am?.cancel(taskPi(ctx, code, -1)) }
            }
        }
        val codes = mutableListOf<Int>()
        val now = LocalDateTime.now()
        for (t in Store.tasks.filterNot { it.done }) {
            val at = t.remindAt() ?: continue
            if (!at.isAfter(now)) continue
            val code = (t.id % 1_000_000).toInt()
            val pi = taskPi(ctx, code, t.id)
            val ms = at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            runCatching {
                if (canExact(am)) am?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
                else am?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            }
            codes += code
        }
        sp.edit().putString("task_alarms", JSONArray(codes).toString()).apply()
    }

    fun notifyTask(ctx: Context, taskId: Long) {
        Store.init(ctx)
        val t = Store.tasks.firstOrNull { it.id == taskId } ?: return
        if (t.done) return
        ensureChannels(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val open = openApp(ctx)
        val doneI = Intent(ctx, MarkReceiver::class.java).setAction("pary.TASK_DONE").putExtra("taskId", taskId)
        val snoozeI = Intent(ctx, MarkReceiver::class.java).setAction("pary.TASK_SNOOZE").putExtra("taskId", taskId)
        val b = Notification.Builder(ctx, CH_TASK)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(t.title)
            .setContentText(
                listOfNotNull(
                    t.subject.takeIf { it.isNotBlank() },
                    t.due?.let { "срок: ${fmt(it.toLocalTime())}, ${ruDate(it.toLocalDate())}" },
                ).joinToString(" · "),
            )
            .setStyle(Notification.BigTextStyle().bigText(
                listOfNotNull(
                    t.subject.takeIf { it.isNotBlank() },
                    t.due?.let { "срок: ${ruDate(it.toLocalDate())} ${fmt(it.toLocalTime())}" },
                    t.note.takeIf { it.isNotBlank() },
                ).joinToString("\n"),
            ))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    null, "Готово",
                    PendingIntent.getBroadcast(ctx, 31, doneI, PI_FLAGS),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    null, "Отложить 15 мин",
                    PendingIntent.getBroadcast(ctx, 32, snoozeI, PI_FLAGS),
                ).build(),
            )
        runCatching { nm.notify(ID_TASK_BASE + taskId.toInt(), b.build()) }
    }

    fun snoozeTask(ctx: Context, taskId: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = taskPi(ctx, (taskId % 1_000_000).toInt(), taskId)
        val ms = System.currentTimeMillis() + 15 * 60_000
        runCatching {
            if (canExact(am)) am?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            else am?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    /** Записывает, во сколько ты реально встал, чтобы выучить привычку. */
    fun recordWake(ctx: Context) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val startMin = sp.getInt("alarmStartMin", -1)
        if (startMin < 0) return
        val now = LocalDateTime.now()
        val prep = startMin - (now.hour * 60 + now.minute)
        Store.recordWake(prep)
        sp.edit().putInt("alarmStartMin", -1).apply()
    }

    fun cancelAlarm(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.cancel(ID_ALARM) }
    }

    fun notifyDraft(ctx: Context, d: com.pary.app.data.ChatDraft) {
        ensureChannels(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val code = ID_DRAFT_BASE + (d.id % 50000).toInt()
        val addPi = PendingIntent.getBroadcast(
            ctx, code + 50000,
            Intent(ctx, MarkReceiver::class.java).setAction("pary.DRAFT_ADD").putExtra("draftId", d.id),
            PI_FLAGS,
        )
        val skipPi = PendingIntent.getBroadcast(
            ctx, code + 90000,
            Intent(ctx, MarkReceiver::class.java).setAction("pary.DRAFT_SKIP").putExtra("draftId", d.id),
            PI_FLAGS,
        )
        val b = Notification.Builder(ctx, CH_TASK)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Возможно, ДЗ · " + d.chat.ifBlank { "чат" })
            .setContentText(d.text)
            .setStyle(Notification.BigTextStyle().bigText(d.text))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .addAction(Notification.Action.Builder(null, "В задачи", addPi).build())
            .addAction(Notification.Action.Builder(null, "Не надо", skipPi).build())
        runCatching { nm.notify(code, b.build()) }
    }

    fun cancelDraftNotification(ctx: Context, id: Long) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.cancel(ID_DRAFT_BASE + (id % 50000).toInt()) }
    }

    fun cancelTaskNotification(ctx: Context, taskId: Long) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.cancel(ID_TASK_BASE + taskId.toInt()) }
    }

    private fun taskPi(ctx: Context, code: Int, taskId: Long): PendingIntent {
        val i = Intent(ctx, TaskReceiver::class.java).putExtra("taskId", taskId)
        return PendingIntent.getBroadcast(ctx, code, i, PI_FLAGS)
    }

    // ---------- будильник-пробуждение ----------

    fun notifyAlarm(ctx: Context, test: Boolean) {
        Store.init(ctx)
        ensureChannels(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val msg = if (test) "Проверка: так будет звонить будильник"
        else ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("alarmMsg", "").orEmpty()
        val off = PendingIntent.getBroadcast(
            ctx, 41,
            Intent(ctx, MarkReceiver::class.java).setAction("pary.ALARM_OFF"), PI_FLAGS,
        )
        val snooze = PendingIntent.getBroadcast(
            ctx, 42,
            Intent(ctx, MarkReceiver::class.java).setAction("pary.SNOOZE"), PI_FLAGS,
        )
        val b = Notification.Builder(ctx, CH_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Пора вставать")
            .setContentText(msg.ifBlank { "Пора на пары" })
            .setCategory(Notification.CATEGORY_ALARM)
            .setTimeoutAfter(5 * 60_000L)
            .setContentIntent(off)
            .addAction(Notification.Action.Builder(null, "Выключить", off).build())
            .addAction(Notification.Action.Builder(null, "Ещё 5 минут", snooze).build())
        val n = b.build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        runCatching { nm.notify(ID_ALARM, n) }
    }

    // ---------- следующее пробуждение ----------

    /** Каждые 15 минут забираем новые сообщения из групп через бота. */
    private fun scheduleTelegramPoll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            ctx, 61,
            Intent(ctx, PollReceiver::class.java),
            PI_FLAGS,
        )
        if (!Store.telegramAuto || Store.telegramToken.isBlank()) {
            runCatching { am.cancel(pi) }
            return
        }
        runCatching {
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 5 * 60_000,
                15 * 60_000,
                pi,
            )
        }
    }

    /** Забирает сообщения бота и превращает их в черновики задач. */
    fun pollTelegram(ctx: Context) {
        runCatching {
            Store.init(ctx)
            if (!Store.telegramAuto) return
            val token = Store.telegramToken
            if (token.isBlank()) return
            val r = TelegramApi.getUpdates(token, Store.telegramOffset)
            val pair = r.value
            if (pair == null) {
                Store.updateTelegramStatus(r.error)
                return
            }
            val (updates, next) = pair
            Store.updateTelegramOffset(next)
            var added = 0
            for (u in updates) {
                if (u.text.length < 4) continue
                if (!Store.isChatHomework(u.text)) continue
                val draft = com.pary.app.data.ChatDraft(
                    text = u.text.take(200),
                    chat = u.chatTitle,
                    pkg = "telegram_bot",
                    due = com.pary.app.data.ChatParser.dueFrom(u.text),
                )
                if (Store.addChatDraft(draft)) {
                    added++
                    notifyDraft(ctx, draft)
                }
            }
            if (added > 0) {
                Store.updateTelegramStatus("Новых сообщений с ДЗ: $added")
                refresh(ctx)
            }
        }
    }

    private fun scheduleNextTick(ctx: Context, now: LocalDateTime) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val today = now.toLocalDate()
        val evs = mutableListOf<LocalDateTime>()
        for (d in listOf(today, today.plusDays(1))) {
            for (l in Store.dayLessons(d)) {
                evs.add(d.atTime(l.start))
                evs.add(d.atTime(l.end))
            }
        }
        evs.add(today.atTime(20, 0))
        evs.add(today.plusDays(1).atStartOfDay())
        evs.add(today.atTime(23, 59))
        val next = evs.filter { it.isAfter(now) }.minOrNull() ?: now.plusHours(1)
        val ms = next.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = PendingIntent.getBroadcast(
            ctx, 51,
            Intent(ctx, BootReceiver::class.java).setAction("pary.TICK"), PI_FLAGS,
        )
        runCatching {
            if (canExact(am)) am?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            else am?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    private fun canExact(am: AlarmManager?): Boolean =
        am != null && (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())

    fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PI_FLAGS)

    // ---------- мелочи ----------

    private fun fmt(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

    private fun fmt(t: LocalDateTime): String = fmt(t.toLocalTime())

    private fun ruDow(d: LocalDate): String =
        listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")[d.dayOfWeek.value - 1]

    private fun ruDate(d: LocalDate): String =
        "${d.dayOfMonth} " + listOf(
            "янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек",
        )[d.monthValue - 1]

}
