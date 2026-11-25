package com.pary.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pary.app.data.Att
import com.pary.app.data.Lesson
import com.pary.app.data.Logic
import com.pary.app.data.Parity
import com.pary.app.data.Store
import com.pary.app.data.Task
import com.pary.app.ui.LocalExtra
import com.pary.app.ui.ParyCard
import com.pary.app.ui.Ru
import com.pary.app.ui.NoticeCard
import com.pary.app.ui.SectionHeader
import com.pary.app.ui.dialogs.TaskEditorDialog
import com.pary.app.ui.StatTile
import com.pary.app.ui.ThinProgress
import com.pary.app.ui.rememberNow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun TodayScreen(
    modifier: Modifier = Modifier,
    onOpenTasks: (() -> Unit)? = null,
    onNavigate: ((Int) -> Unit)? = null,
) {
    val now = rememberNow(30)
    val today = now.toLocalDate()
    val day = remember(today, Store.lessons, Store.anchor, Store.anchorParity) { Store.dayLessons(today) }
    val cur = remember(day, now) { Logic.currentLesson(day, now) }
    val next = remember(day, now) { Logic.nextLesson(day, now) }
    val open = remember(Store.tasks) { Store.openTasks() }
    val dueSoon = remember(open) { open.filter { it.due != null }.sortedBy { it.due } }
    val deadline = dueSoon.firstOrNull()
    var selected by remember { mutableStateOf<Lesson?>(null) }
    var newTask by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val notifOff = !ctx.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()
    var noticeHidden by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(today, day) }
        if (notifOff && !noticeHidden) {
            item {
                NoticeCard(
                    "Уведомления выключены",
                    "Без разрешения не придут напоминания о парах и задачах, не сработает будильник.",
                    "Включить →",
                    onAction = {
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName),
                            )
                        }
                    },
                    onClose = { noticeHidden = true },
                )
            }
        }
        item { Hero(cur, next, today, now) }
        item {
            QuickActions(
                onTasks = { onNavigate?.invoke(2) ?: onOpenTasks?.invoke() },
                onSchedule = { onNavigate?.invoke(1) },
                onFocus = { onNavigate?.invoke(4) },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("${day.size}", "пар сегодня", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatTile("${dueSoon.size}", "задач", LocalExtra.current.warning, Modifier.weight(1f))
                StatTile(
                    Store.avgAll()?.let { String.format("%.1f", it) } ?: "—",
                    "средний балл",
                    LocalExtra.current.success,
                    Modifier.weight(1f),
                )
            }
        }
        item {
            val totalTasks = Store.tasks.size
            val completedTasks = Store.tasks.count { it.done }
            val progress = if (totalTasks == 0) 0f else completedTasks / totalTasks.toFloat()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), MaterialTheme.shapes.large)
                    .clickable { onNavigate?.invoke(2) ?: onOpenTasks?.invoke() }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Прогресс задач", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Text("$completedTasks / $totalTasks", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                ThinProgress(progress)
                Text(
                    when {
                        totalTasks == 0 -> "Добавь первую задачу — Pary поможет держать всё под контролем"
                        completedTasks == totalTasks -> "Все задачи закрыты. Отличная работа!"
                        else -> "Осталось выполнить: ${totalTasks - completedTasks}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (deadline != null) {
            item { DeadlineCard(deadline, open.size, now, onOpenTasks) }
        }
        if (Store.smartAlarm) {
            item { AlarmCard(today, now) }
        }
        if (Store.chatDrafts.isNotEmpty()) {
            item { ChatDraftsCard(Store.chatDrafts.size, onOpenTasks) }
        }
        item {
            Column {
                SectionHeader(
                    "Расписание дня",
                    if (day.isEmpty()) "свободно" else "${day.size} ${Ru.plural(day.size, "пара", "пары", "пар")}",
                )
                if (day.isEmpty()) {
                    ParyCard {
                        Text("Сегодня пар нет — можно выдохнуть", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    day.forEach { l -> LessonRow(l, today, now) { selected = l } }
                }
            }
        }
        if (dueSoon.isNotEmpty()) {
            item {
                Column {
                    SectionHeader("Что не забыть", "задачи")
                    dueSoon.take(4).forEach { t ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { Store.toggleTask(t.id) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    Ru.dueLabel(t.due, today) + if (t.subject.isNotBlank()) " · ${t.subject}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { newTask = true }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Новая задача",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    selected?.let { l ->
        LessonDialog(l, today, onDismiss = { selected = null })
    }
    if (newTask) {
        TaskEditorDialog(
            initial = null,
            subjects = Store.subjectNames(),
            onDismiss = { newTask = false },
            onSave = { Store.addTask(it); newTask = false },
        )
    }
}

@Composable
private fun QuickActions(
    onTasks: () -> Unit,
    onSchedule: () -> Unit,
    onFocus: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("БЫСТРЫЙ ДОСТУП", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.8.sp))
            Spacer(Modifier.weight(1f))
            Text(
                "в один тап",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickActionTile(
                title = "Задачи",
                subtitle = "Дедлайны",
                icon = Icons.Filled.CheckCircle,
                modifier = Modifier.weight(1f),
                onClick = onTasks,
            )
            QuickActionTile(
                title = "Расписание",
                subtitle = "Неделя",
                icon = Icons.Filled.DateRange,
                modifier = Modifier.weight(1f),
                onClick = onSchedule,
            )
            QuickActionTile(
                title = "Фокус",
                subtitle = "Таймер",
                icon = Icons.Filled.PlayArrow,
                modifier = Modifier.weight(1f),
                onClick = onFocus,
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun Header(today: LocalDate, day: List<Lesson>) {
    val name = Store.profileName.trim()
    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 5 -> "Доброй ночи"
        hour < 12 -> "Доброе утро"
        hour < 18 -> "Добрый день"
        else -> "Добрый вечер"
    }
    val parity = when (Store.parityOn(today)) {
        Parity.NUM -> "ЧИСЛИТЕЛЬ"
        Parity.DEN -> "ЗНАМЕНАТЕЛЬ"
        Parity.BOTH -> ""
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 5.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "PARY",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.1.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale("ru"))).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            if (name.isBlank()) greeting else "$greeting, $name",
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                listOf(Ru.date(today), Store.profileGroup.trim()).filter { it.isNotBlank() }.joinToString("  /  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (parity.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                ) {
                    Text(parity, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outline))
            Text(
                if (day.isEmpty()) "СВОБОДНЫЙ ДЕНЬ" else "${day.size} ЗАНЯТИЙ  ·  ПЕРВОЕ В ${Ru.time(day.first().start)}",
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Hero(cur: Lesson?, next: Lesson?, today: LocalDate, now: LocalDateTime) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(17.dp),
    ) {
        when {
            cur != null -> {
                val total = Duration.between(cur.start, cur.end).toSeconds().coerceAtLeast(1)
                val done = Duration.between(cur.start, now).toSeconds().coerceAtLeast(0).toFloat()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "СЕЙЧАС ИДЁТ",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "до ${Ru.time(cur.end)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(cur.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
                Spacer(Modifier.height(2.dp))
                Text(
                    listOf(cur.room, "${Ru.time(cur.start)}–${Ru.time(cur.end)}").filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                ThinProgress(done / total)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "осталось",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        Logic.humanLeft(cur.end.atDate(today), now),
                        style = MaterialTheme.typography.headlineSmall,
                        color = accent,
                    )
                }
                if (next != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Дальше в ${Ru.time(next.start)} · ${next.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            next != null -> {
                Text(
                    "ДАЛЬШЕ",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(next.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(
                    "в ${Ru.time(next.start)} · через " + Logic.humanLeft(next.start.atDate(today), now) +
                        if (next.room.isNotBlank()) " · ${next.room}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                Text(
                    if (LocalTime.now().isBefore(LocalTime.of(12, 0))) "ПАР ЕЩЁ НЕТ" else "НА СЕГОДНЯ ВСЁ",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text("Свободный день", style = MaterialTheme.typography.headlineSmall)
                val nx = (1..7).firstNotNullOfOrNull { i ->
                    val d = today.plusDays(i.toLong())
                    Store.dayLessons(d).firstOrNull()?.let { d to it }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (nx != null)
                        "Ближайшая пара: ${Ru.dow(nx.first)} в ${Ru.time(nx.second.start)} · ${nx.second.name}"
                    else "В ближайшую неделю пар нет",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (Store.afterNote.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(Store.afterNote, style = MaterialTheme.typography.bodySmall, color = accent)
                }
            }
        }
    }
}

@Composable
private fun LessonRow(l: Lesson, date: LocalDate, now: LocalDateTime, onClick: () -> Unit) {
    val att: Att? = Store.attendanceFor(l, date)
    val live = !l.start.isAfter(now.toLocalTime()) && now.toLocalTime().isBefore(l.end)
    val past = !l.end.isAfter(now.toLocalTime())
    val accent = MaterialTheme.colorScheme.primary
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(if (live) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(46.dp)) {
            Text(
                Ru.time(l.start),
                style = MaterialTheme.typography.titleSmall,
                color = if (live) accent else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                Ru.time(l.end),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .padding(horizontal = 10.dp)
                .width(3.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(50))
                .background(if (live) accent else MaterialTheme.colorScheme.outline),
        )
        Column(Modifier.weight(1f)) {
            Text(
                l.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (past && att == null) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                listOf(l.room, l.teacher).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            att?.present == true -> StatusBadge("был", LocalExtra.current.success)
            att?.present == false -> StatusBadge("пропуск", LocalExtra.current.danger)
            live -> StatusBadge("идёт", accent)
            else -> Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LessonDialog(l: Lesson, date: LocalDate, onDismiss: () -> Unit) {
    val att = Store.attendanceFor(l, date)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text(l.name, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${Ru.time(l.start)}–${Ru.time(l.end)}" +
                        listOf(l.room, l.teacher).filter { it.isNotBlank() }.joinToString(" · ", " · ", ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                DialogOption("Был на паре", Icons.Filled.Check, LocalExtra.current.success) {
                    Store.mark(l, date, true); onDismiss()
                }
                DialogOption("Пропустил", Icons.Filled.Close, LocalExtra.current.danger) {
                    Store.mark(l, date, false); onDismiss()
                }
                if (att != null) {
                    DialogOption("Снять отметку", Icons.Filled.DateRange, MaterialTheme.colorScheme.onSurfaceVariant) {
                        Store.unmark(l, date); onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogOption(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ---------- ближайший дедлайн ----------

@Composable
private fun DeadlineCard(task: Task, totalOpen: Int, now: LocalDateTime, onOpenTasks: (() -> Unit)?) {
    val due = task.due ?: return
    val extra = LocalExtra.current
    val overdue = due.isBefore(now)
    val tone = if (overdue) extra.danger else extra.warning
    val left = Logic.humanLeft(due, now)
    val leftLabel = when {
        overdue -> "просрочено"
        left == "сейчас" -> "вот-вот"
        else -> "через $left"
    }
    ParyCard(onClick = onOpenTasks) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = tone, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                "БЛИЖАЙШИЙ ДЕДЛАЙН",
                style = MaterialTheme.typography.labelSmall,
                color = tone,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                leftLabel,
                style = MaterialTheme.typography.labelMedium,
                color = tone,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            task.title.ifBlank { "Без названия" },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            listOfNotNull(
                task.subject.takeIf { it.isNotBlank() },
                Ru.dueLabel(due, now.toLocalDate()),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OpenTasksAction(totalOpen, onOpenTasks)
    }
}

@Composable
private fun OpenTasksAction(count: Int, onOpenTasks: (() -> Unit)?) {
    if (onOpenTasks == null) {
        Text(
            "Список задач — во вкладке «Задачи»",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onOpenTasks)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (count > 1) "Открыть задачи · $count" else "Открыть задачи",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text("→", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

// ---------- будильник ----------

/** Подпись дня для карточки будильника: «завтра» / «послезавтра» / дата. */
private fun wakeDayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today.plusDays(1) -> "завтра"
    today.plusDays(2) -> "послезавтра"
    else -> Ru.dateShort(d)
}

@Composable
private fun AlarmCard(today: LocalDate, now: LocalDateTime) {
    val extra = LocalExtra.current
    val prep = Store.prepMin
    // Правила те же, что в Sync.planAlarms (порог «будить, если первая пара», сборы, не раньше 5:00),
    // но день берём начиная с завтрашнего — карточка про подъём на первую пару завтра.
    var wakeDate: LocalDate? = null
    var wakeLesson: Lesson? = null
    var wakeAt: LocalDateTime? = null
    for (i in 1..7) {
        val d = today.plusDays(i.toLong())
        val first = Store.dayLessons(d).firstOrNull() ?: continue
        val startMin = first.start.hour * 60 + first.start.minute
        if (startMin >= Store.alarmBefore) continue
        val w = d.atTime(first.start).minusMinutes(prep.toLong())
        if (w.toLocalTime().hour < 5) continue
        if (!w.isAfter(now.plusMinutes(2))) continue
        wakeDate = d
        wakeLesson = first
        wakeAt = w
        break
    }
    val day = wakeDate
    val lesson = wakeLesson
    val wake = wakeAt

    ParyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Notifications, null, tint = extra.success, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                "БУДИЛЬНИК",
                style = MaterialTheme.typography.labelSmall,
                color = extra.success,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "сборы " + Logic.dur(prep),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        val d = day
        val l = lesson
        val w = wake
        if (d != null && l != null && w != null) {
            Text(
                "встать в ${Ru.time(w)}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "Первая пара ${wakeDayLabel(d, today)} в ${Ru.time(l.start)}" +
                    if (l.name.isNotBlank()) " · ${l.name}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Звонить некуда",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "На неделе нет подходящих пар — будильник не поставится",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- новые ДЗ из чатов ----------

@Composable
private fun ChatDraftsCard(count: Int, onOpenTasks: (() -> Unit)?) {
    val extra = LocalExtra.current
    val base = Modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.primaryContainer)
    Row(
        modifier = (if (onOpenTasks != null) base.clickable(onClick = onOpenTasks) else base)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Email, null, tint = extra.warning, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Новых ДЗ из чатов: $count",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Проверь и добавь в задачи",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onOpenTasks != null) {
            Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
