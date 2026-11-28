package com.pary.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pary.app.data.FocusLogic
import com.pary.app.data.Lesson
import com.pary.app.data.Store
import com.pary.app.data.Task
import com.pary.app.ui.ParyCard
import com.pary.app.ui.ParyBarChart
import com.pary.app.ui.ParyBarEntry
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

/** Вкладка «Фокус»: таймер, нагрузка, окна между парами, риск пропусков, цель по баллу. */
@Composable
fun FocusScreen(modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ParyCard {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("РЕЖИМ ПРОДУКТИВНОСТИ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("Фокус", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Управляй временем, планируй паузы и отслеживай учебную нагрузку.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${Store.tasks.count { !it.done }}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Text("активных задач", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("${Store.lessons.size}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Text("пар в расписании", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        FocusTimerCard()
        DailyFocusGoalCard()
        FocusHistoryCard()
        FreeWindowsCard()
        AttendanceRiskCard()
        WeekLoadCard()
        GradeGoalCard()
    }
}

@Composable
private fun FocusHistoryCard() {
    val today = LocalDate.now()
    val days = FocusLogic.weeklyTrend(Store.focusSessions, today.minusDays(6), 7)
    val totalMinutes = days.sumOf { it.minutes }
    val totalSessions = days.sumOf { it.sessions }
    val entries = days.map { day ->
        val hours = day.minutes / 60
        val minutes = day.minutes % 60
        val label = when {
            day.minutes == 0 -> "—"
            hours > 0 && minutes > 0 -> "${hours}ч${minutes}м"
            hours > 0 -> "${hours}ч"
            else -> "${minutes}м"
        }
        ParyBarEntry(
            label = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")),
            value = day.minutes.toFloat(),
            valueLabel = label,
            detail = if (day.sessions == 0) "" else "${day.sessions} сесс.",
        )
    }

    FocusCard("Фокус за 7 дней") {
        Text(
            "${totalMinutes / 60} ч ${totalMinutes % 60} мин · ${totalSessions} сессий",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        if (totalSessions == 0) {
            Text(
                "Завершённые фокус-сессии появятся здесь. История сохраняется между днями и входит в резервную копию.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ParyBarChart(entries, modifier = Modifier.fillMaxWidth(), chartHeight = 120.dp)
        }
    }
}

@Composable
private fun FocusCard(title: String, content: @Composable () -> Unit) {
    ParyCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

// ---------- таймер ----------

@Composable
private fun FocusTimerCard() {
    var workMin by rememberSaveable { mutableStateOf(25) }
    var focusPhase by rememberSaveable { mutableStateOf(true) }
    var left by rememberSaveable { mutableStateOf(25 * 60) }
    var running by remember { mutableStateOf(false) }
    var sessions by rememberSaveable { mutableStateOf(0) }
    val breakMin = if (workMin >= 50) 10 else 5
    val total = (if (focusPhase) workMin else breakMin) * 60

    LaunchedEffect(running) {
        while (running) {
            delay(1000L)
            left -= 1
            if (left <= 0) {
                running = false
                if (focusPhase) {
                    sessions += 1
                    Store.recordFocusSession(workMin)
                }
                focusPhase = !focusPhase
                left = (if (focusPhase) workMin else breakMin) * 60
            }
        }
    }

    val shown = left.coerceAtLeast(0)
    val clock = String.format(Locale.US, "%02d:%02d", shown / 60, shown % 60)
    val progress = (1f - shown / total.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(progress, animationSpec = tween(450), label = "focus-progress")
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val progressColor = MaterialTheme.colorScheme.primary

    FocusCard(if (focusPhase) "Таймер учёбы" else "Время перерыва") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (focusPhase) "ВРЕМЯ СОСРЕДОТОЧИТЬСЯ" else "МОЖНО ПЕРЕДОХНУТЬ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(184.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                    val stroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = stroke,
                    )
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = stroke,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(clock, style = MaterialTheme.typography.displaySmall.copy(fontSize = 39.sp))
                    Text(
                        when {
                            running -> "идёт отсчёт"
                            shown < total -> "пауза"
                            else -> "готов к старту"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { running = !running }) {
                Text(if (running) "Пауза" else "Старт")
            }
            OutlinedButton(onClick = {
                running = false
                left = total
            }) { Text("Сброс") }
        }
        if (!running) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 25, 50, 90).forEach { minutes ->
                    OutlinedButton(
                        onClick = {
                            workMin = minutes
                            focusPhase = true
                            left = minutes * 60
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    ) { Text("$minutes") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    running = false
                    if (focusPhase) {
                        val elapsedMin = ((workMin * 60 - left) / 60).coerceAtLeast(0)
                        if (elapsedMin > 0) {
                            sessions += 1
                            Store.recordFocusSession(elapsedMin)
                        }
                    }
                    focusPhase = !focusPhase
                    left = (if (focusPhase) workMin else breakMin) * 60
                },
                modifier = Modifier.weight(1f),
            ) { Text(if (focusPhase) "Завершить фокус" else "Вернуться к учёбе") }
            OutlinedButton(
                onClick = {
                    running = false
                    focusPhase = true
                    left = workMin * 60
                },
                modifier = Modifier.weight(1f),
            ) { Text("Новый цикл") }
        }
        Text(
            "Подходов завершено: $sessions · перерыв: $breakMin мин",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


@Composable
private fun DailyFocusGoalCard() {
    val goal = Store.focusGoalMin
    val done = Store.focusTodayMin
    val progress = (done.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
    FocusCard("Цель фокуса на сегодня") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${done / 60} ч ${done % 60} мин",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                " из ${goal / 60} ч ${goal % 60} мин",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 3.dp),
            )
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text(
            if (done >= goal) "Дневная цель выполнена · подходов: ${Store.focusTodaySessions}"
            else "Осталось ${goal - done} мин · подходов: ${Store.focusTodaySessions}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(60, 90, 120, 180).forEach { minutes ->
                OutlinedButton(
                    onClick = { Store.updateFocusGoal(minutes) },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 7.dp),
                ) {
                    Text("${minutes / 60} ч", color = if (goal == minutes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// ---------- окна между парами ----------

private data class FreeWindow(val from: LocalTime, val to: LocalTime, val minutes: Int)

/** Промежутки между парами длиной от 30 минут. */
private fun freeWindows(lessons: List<Lesson>): List<FreeWindow> {
    val sorted = lessons.sortedBy { it.start }
    val out = mutableListOf<FreeWindow>()
    for (i in 0 until sorted.size - 1) {
        val a = sorted[i].end
        val b = sorted[i + 1].start
        val mins = (b.toSecondOfDay() - a.toSecondOfDay()) / 60
        if (mins >= 30) out.add(FreeWindow(a, b, mins))
    }
    return out
}

@Composable
private fun FreeWindowsCard() {
    val today = LocalDate.now()
    val top: List<Task> = Store.openTasks()
        .sortedWith(compareByDescending<Task> { it.priority }.thenBy { it.due?.toString() ?: "9" })

    FocusCard("Окна между парами") {
        listOf(0, 1).forEach { shift ->
            val d = today.plusDays(shift.toLong())
            val lessons = Store.dayLessons(d)
            val title = if (shift == 0) "Сегодня" else "Завтра"
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (lessons.isEmpty()) {
                Text(
                    "Пар нет — день свободен.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val windows = freeWindows(lessons).take(4)
                if (windows.isEmpty()) {
                    Text(
                        "Окон между парами нет.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    windows.forEachIndexed { i, w ->
                        val hint = top.getOrNull(shift * 4 + i)?.title?.take(40)
                        val line = "${w.from}–${w.to} · ${w.minutes} мин" +
                            (if (hint != null) " → $hint" else "")
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Text(
            "Стрелкой показана самая важная открытая задача, которую можно закрыть в это окно.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- риск пропусков ----------

@Composable
private fun AttendanceRiskCard() {
    val att = Store.attendance.values
    val limit = Store.missLimit.coerceAtLeast(1)
    val missed = att.filter { !it.present }
        .groupBy { it.subject }
        .mapValues { it.value.size }
        .entries
        .sortedByDescending { it.value }
        .take(6)

    var streak = 0
    for (a in att.sortedBy { it.key }.asReversed()) {
        if (a.present) streak += 1 else break
    }

    FocusCard("Пропуски и запас") {
        Text(
            "Подряд на парах: $streak",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (missed.isEmpty()) {
            Text("Пропусков нет — так держать.", style = MaterialTheme.typography.bodyMedium)
        } else {
            missed.forEach { (subject, count) ->
                val over = count >= limit
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        subject.ifBlank { "Без предмета" },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                    Text(
                        "$count из $limit",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (over) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { (count / limit.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (over) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ---------- нагрузка недели ----------

@Composable
private fun WeekLoadCard() {
    val today = LocalDate.now()
    val open = Store.openTasks()
    val rows = (0..6).map { i ->
        val date = today.plusDays(i.toLong())
        val minutes = Store.dayLessons(date).sumOf { it.minutes }
        val due = open.count { it.due?.toLocalDate() == date }
        val label = if (i == 0) "Сег" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru"))
        val valueLabel = if (minutes == 0) "—" else {
            val hours = minutes / 60
            val remainder = minutes % 60
            if (hours > 0 && remainder > 0) "${hours}ч${remainder}м"
            else if (hours > 0) "${hours}ч"
            else "${remainder}м"
        }
        ParyBarEntry(
            label = label,
            value = minutes.toFloat(),
            valueLabel = valueLabel,
            detail = if (due > 0) "${due} зад." else "",
        )
    }

    FocusCard("Нагрузка на 7 дней") {
        Text(
            "Продолжительность пар по дням. Подпись под днём показывает количество задач с дедлайном.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        ParyBarChart(entries = rows, modifier = Modifier.fillMaxWidth(), chartHeight = 132.dp)
    }
}

// ---------- цель по среднему баллу ----------

private fun fivesWord(n: Int): String = when {
    n % 10 == 1 && n % 100 != 11 -> "пятёрка"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "пятёрки"
    else -> "пятёрок"
}

@Composable
private fun GradeGoalCard() {
    var target by rememberSaveable { mutableStateOf(4.5f) }
    val t = Math.round(target * 10) / 10.0
    val values = Store.grades.map { it.value }
    val n = values.size
    val sum = values.sum()

    FocusCard("Цель по среднему баллу") {
        Text(
            "Хочу средний: " + String.format(Locale.US, "%.1f", t),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = target,
            onValueChange = { target = it },
            valueRange = 3f..4.9f,
            steps = 18,
        )
        if (n == 0) {
            Text("Оценок пока нет — добавь их во вкладке «Учёба».")
        } else {
            val avg = sum.toDouble() / n
            val need = if (avg >= t) 0 else ceil((t * n - sum) / (5.0 - t)).toInt().coerceAtLeast(0)
            Text(
                "Сейчас: " + String.format(Locale.US, "%.2f", avg) + " (оценок: $n)",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (need == 0) "Цель уже достигнута."
                else "Нужно подряд: $need ${fivesWord(need)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
