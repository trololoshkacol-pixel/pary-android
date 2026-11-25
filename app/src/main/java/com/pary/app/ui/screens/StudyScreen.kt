package com.pary.app.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pary.app.data.Exam
import com.pary.app.data.Grade
import com.pary.app.data.JournalSubject
import com.pary.app.data.Logic
import com.pary.app.data.Note
import com.pary.app.data.Store
import com.pary.app.ui.ChoiceRow
import com.pary.app.ui.EmptyState
import com.pary.app.ui.LocalExtra
import com.pary.app.ui.ParyCard
import com.pary.app.ui.Ru
import com.pary.app.ui.SectionHeader
import com.pary.app.ui.ThinProgress
import com.pary.app.ui.dialogs.ExamEditorDialog
import com.pary.app.ui.dialogs.GradeEditorDialog
import com.pary.app.ui.dialogs.NoteEditorDialog
import com.pary.app.ui.rememberNow

@Composable
fun StudyScreen(modifier: Modifier = Modifier, initialSection: Int = 0) {
    val now = rememberNow(60)
    var section by rememberSaveable { mutableStateOf(initialSection.coerceIn(0, 4)) }
    var gradeFor by remember { mutableStateOf<Boolean>(false) }
    var examFor by remember { mutableStateOf(false) }
    var noteFor by remember { mutableStateOf(false) }
    var editingExam by remember { mutableStateOf<Exam?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            val accent = MaterialTheme.colorScheme.primary
            val cardShape = RoundedCornerShape(12.dp)
            val sectionCount = when (section) {
                0 -> Store.grades.size
                1 -> Store.exams.size
                2 -> Store.attendance.size
                3 -> Store.notes.size
                else -> Store.journal.size
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, cardShape)
                    .padding(17.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ТВОЙ ПРОГРЕСС", style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
                        Text("Учёба", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Успеваемость, экзамены и личная статистика",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (section == 0 || section == 1 || section == 3) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(accent)
                                .clickable {
                                    when (section) {
                                        0 -> gradeFor = true
                                        1 -> examFor = true
                                        3 -> noteFor = true
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Добавить", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            Store.avgAll()?.let { String.format("%.2f", it) } ?: "—",
                            style = MaterialTheme.typography.headlineSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("средний балл", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$sectionCount", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (section) {
                                0 -> "оценок"
                                1 -> "экзаменов"
                                2 -> "отметок"
                                3 -> "заметок"
                                else -> "предметов"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            ChoiceRow(
                listOf("Оценки", "Экзамены", "Посещения", "Заметки", "Платонус"),
                section,
                onSelect = { section = it },
            )
        }

        when (section) {
            0 -> {
                item { WeeklyStudySummary(now) }
                gradesSection()
            }
            1 -> examsSection(now) { editingExam = it }
            2 -> attendanceSection()
            3 -> notesSection()
            else -> journalSection()
        }
    }

    if (gradeFor) {
        GradeEditorDialog(
            Store.subjectNames(),
            onDismiss = { gradeFor = false },
            onSave = { Store.addGrade(it); gradeFor = false },
        )
    }
    if (examFor) {
        ExamEditorDialog(
            Store.subjectNames(),
            onDismiss = { examFor = false },
            onSave = { Store.addExam(it); examFor = false },
        )
    }
    editingExam?.let { e ->
        ExamEditorDialog(
            Store.subjectNames(),
            initial = e,
            onDismiss = { editingExam = null },
            onSave = { Store.updateExam(it); editingExam = null },
            onDelete = { Store.deleteExam(e.id); editingExam = null },
        )
    }
    if (noteFor) {
        NoteEditorDialog(
            Store.subjectNames(),
            onDismiss = { noteFor = false },
            onSave = { Store.addNote(it); noteFor = false },
        )
    }
}


@Composable
private fun WeeklyStudySummary(now: java.time.LocalDateTime) {
    val context = LocalContext.current
    val from = now.toLocalDate().minusDays(6)
    val recentGrades = Store.grades.filter { !it.date.isBefore(from) && !it.date.isAfter(now.toLocalDate()) }
    val recentAttendance = Store.attendance.values.filter { a ->
        val date = a.date
        date != null && !date.isBefore(from) && !date.isAfter(now.toLocalDate())
    }
    val average = Logic.average(recentGrades.map { it.value })
    val present = recentAttendance.count { it.present }
    val missed = recentAttendance.size - present
    ParyCard {
        Text("Последние 7 дней", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("${recentGrades.size}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text("оценок", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(average?.let { String.format("%.2f", it) } ?: "—", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text("средний балл", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(if (recentAttendance.isEmpty()) "—" else "${present * 100 / recentAttendance.size}%", style = MaterialTheme.typography.headlineSmall, color = LocalExtra.current.success)
                Text("посещаемость", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (recentAttendance.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Отметки: был $present · пропустил $missed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Поделиться итогом недели",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                val report = buildString {
                    append("Pary · итоги за 7 дней\n")
                    append("Оценок: ${recentGrades.size}\n")
                    append("Средний балл: ${average?.let { String.format("%.2f", it) } ?: "нет данных"}\n")
                    append("Посещаемость: ${if (recentAttendance.isEmpty()) "нет данных" else "${present * 100 / recentAttendance.size}%"}\n")
                    append("Был: $present · пропустил: $missed")
                }
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report),
                            "Поделиться итогом",
                        ),
                    )
                }
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.gradesSection() {
    item {
        val avg = Store.avgAll()
        ParyCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    avg?.let { String.format("%.2f", it) } ?: "—",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "средний балл · ${Store.grades.size} ${Ru.plural(Store.grades.size, "оценка", "оценки", "оценок")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            ThinProgress(((avg ?: 0.0) / 5.0).toFloat())
            Spacer(Modifier.height(6.dp))
            Text(
                "До «отлично» осталось " + ("%.2f".format(5.0 - (avg ?: 5.0))) + " балла",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (Store.grades.isNotEmpty()) {
        item(key = "grade_subject_chart") {
            val chartSubjects = Store.grades.groupBy { it.subject }
                .mapValues { (_, grades) -> Logic.average(grades.map { it.value }) ?: 0.0 }
                .entries.sortedByDescending { it.value }.take(6)
            ParyCard {
                Text("Успеваемость по предметам", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Средний балл по каждому предмету",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                chartSubjects.forEachIndexed { index, entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                entry.key.ifBlank { "Без предмета" },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                String.format("%.1f", entry.value),
                                style = MaterialTheme.typography.labelLarge,
                                color = colorForAvg(entry.value),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        ThinProgress((entry.value / 5.0).toFloat().coerceIn(0f, 1f), height = 7)
                    }
                    if (index != chartSubjects.lastIndex) Spacer(Modifier.height(10.dp))
                }
                if (Store.grades.size > 6) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Показаны 6 предметов с наивысшим средним баллом",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (Store.grades.isEmpty()) {
        item {
            ParyCard {
                EmptyState(
                    Icons.Filled.Add,
                    "Оценок пока нет",
                    "Добавляй оценки — приложение само посчитает средний балл по предметам.",
                )
            }
        }
    } else {
        val bySubject = Store.grades.groupBy { it.subject }
        item { SectionHeader("По предметам", "${bySubject.size}") }
        for ((subject, list) in bySubject.entries.sortedBy { it.key }) {
            item(key = "g_$subject") {
                val avg = Logic.average(list.map { it.value }) ?: 0.0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            subject.ifBlank { "Без предмета" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            list.sortedBy { it.date }.joinToString("  ") { it.value.toString() },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        String.format("%.1f", avg),
                        style = MaterialTheme.typography.titleMedium,
                        color = colorForAvg(avg),
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.examsSection(
    now: java.time.LocalDateTime,
    onEdit: (Exam) -> Unit,
) {
    val upcoming = Store.exams.filterNot { it.passed }.sortedBy { it.at }
    if (upcoming.isEmpty()) {
        item {
            ParyCard {
                EmptyState(
                    Icons.Filled.Add,
                    "Экзаменов нет",
                    "Добавь даты экзаменов — приложение покажет, сколько дней осталось.",
                )
            }
        }
    }
    for (e in upcoming) {
        item(key = "e_${e.id}") {
            val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), e.at.toLocalDate())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onEdit(e) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(e.subject, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        "${Ru.date(e.at.toLocalDate())} · ${Ru.time(e.at.toLocalTime())}" +
                            if (e.room.isNotBlank()) " · ${e.room}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    when {
                        days < 0 -> "прошёл"
                        days == 0L -> "сегодня"
                        days == 1L -> "завтра"
                        else -> "$days ${Ru.plural(days.toInt(), "день", "дня", "дней")}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        days <= 1 -> LocalExtra.current.danger
                        days <= 3 -> LocalExtra.current.warning
                        else -> MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
    val passed = Store.exams.filter { it.passed }
    if (passed.isNotEmpty()) {
        item {
            ParyCard {
                Text("Сданные: ${passed.size}", style = MaterialTheme.typography.titleSmall)
                passed.forEach { e ->
                    Text(
                        "${e.subject} · ${Ru.dateShort(e.at.toLocalDate())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.attendanceSection() {
    val stats = Store.stats()
    val imported = Store.platonusAttendance
    item {
        val present = imported.count { it.present }
        val missed = imported.size - present
        ParyCard {
            Text("Посещаемость из Platonus", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text("$present", style = MaterialTheme.typography.headlineSmall, color = LocalExtra.current.success)
                    Text("присутствовал", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column {
                    Text("$missed", style = MaterialTheme.typography.headlineSmall, color = if (missed > 0) LocalExtra.current.danger else MaterialTheme.colorScheme.primary)
                    Text("пропусков", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column {
                    Text(if (imported.isEmpty()) "—" else "${present * 100 / imported.size}%", style = MaterialTheme.typography.headlineSmall)
                    Text("посещаемость", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (imported.isEmpty()) {
                Text("Platonus пока не отдал записи посещаемости для доступных предметов.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                imported.sortedByDescending { it.key }.take(8).forEach { record ->
                    Text(
                        "${record.key.replace("@", " · ")}  ${if (record.present) "Был" else "Пропуск"} · ${record.subject}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (record.present) LocalExtra.current.success else LocalExtra.current.danger,
                    )
                }
            }
        }
    }
    item {
        val present = Store.totalPresent()
        val missed = Store.totalMissed()
        val total = present + missed
        ParyCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${total}",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.padding(top = 6.dp)) {
                    Text("отметок всего", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "был: $present · пропусков: $missed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (total > 0) {
                Spacer(Modifier.height(10.dp))
                ThinProgress(present.toFloat() / total)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Посещаемость ${present * 100 / total}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (stats.isEmpty()) {
        item {
            ParyCard {
                EmptyState(
                    Icons.Filled.Add,
                    "Нет отметок",
                    "Нажми на пару в разделе «Сегодня», чтобы отметить: был или пропустил.",
                )
            }
        }
    } else {
        item { SectionHeader("По предметам", "лимит ${Store.missLimit}") }
        for ((subject, v) in stats.entries.sortedBy { it.key }) {
            item(key = "a_$subject") {
                val missed = v.second
                val over = missed >= Store.missLimit
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            subject,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "$missed/${Store.missLimit}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (over) LocalExtra.current.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    ThinProgress((missed.toFloat() / Store.missLimit).coerceAtMost(1f))
                    Text(
                        "был ${v.first} · пропустил $missed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.notesSection() {
    if (Store.notes.isEmpty()) {
        item {
            ParyCard {
                EmptyState(
                    Icons.Filled.Add,
                    "Заметок нет",
                    "Короткие мысли, формулы и «не забыть» по предметам.",
                )
            }
        }
    }
    for (n in Store.notes.sortedByDescending { it.at }) {
        item(key = "n_${n.id}") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { Store.deleteNote(n.id) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (n.subject.isNotBlank()) {
                    Text(
                        n.subject,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(n.text, style = MaterialTheme.typography.bodyMedium)
                Text(
                    Ru.date(n.at.toLocalDate()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (Store.notes.isNotEmpty()) {
        item {
            Text(
                "Нажми на заметку, чтобы удалить",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun colorForAvg(avg: Double) = when {
    avg >= 4.5 -> LocalExtra.current.success
    avg >= 3.5 -> MaterialTheme.colorScheme.primary
    avg >= 2.5 -> LocalExtra.current.warning
    else -> LocalExtra.current.danger
}

/** Итог предмета, если он есть: пусто, «0» и «0.0» — это «нет оценки». */
private fun markOf(s: JournalSubject): Double? = s.total.toDoubleOrNull()?.takeIf { it > 0.0 }

/** 85.0 → «85», 4.5 → «4,5»: без хвоста «,0» и с запятой по-русски. */
private fun formatMark(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.1f", value)

/** "#RRGGBB" / "#AARRGGBB" / "FF0000" → Color; битый hex или пусто → null (нейтральная полоска). */
private fun colorFromHex(raw: String): Color? {
    val s = raw.trim().removePrefix("0x").removePrefix("0X").removePrefix("#")
    if (s.length != 6 && s.length != 8) return null
    if (s.any { it.digitToIntOrNull(16) == null }) return null
    val value = s.toLongOrNull(16) ?: return null
    return if (s.length == 6) Color(0xFF000000L or value) else Color(value)
}

private fun nowDateTime(): java.time.LocalDateTime = java.time.LocalDateTime.now()

private fun androidx.compose.foundation.lazy.LazyListScope.journalSection() {
    val list = Store.journal
    if (list.isEmpty()) {
        item {
            ParyCard {
                EmptyState(
                    Icons.Filled.Star,
                    "Журнал ещё не загружен",
                    "Открой «Ещё» → Платонус → «Оценки из журнала», чтобы подтянуть предметы и баллы.",
                )
            }
        }
        return
    }
    // Предметы с итогом — сверху, дальше по алфавиту.

    item {
        val openPlatonusTasks = Store.openTasks().filter { it.source == "platonus" && it.due != null }
        val overdueTasks = openPlatonusTasks.filter { it.due!!.isBefore(nowDateTime()) }
        val urgentTasks = openPlatonusTasks.filter { !it.due!!.isBefore(nowDateTime()) && it.due!!.isBefore(nowDateTime().plusDays(4)) }
        val pending = list.sumOf { subject -> subject.exams.count { exam ->
            val mark = exam.mark.toDoubleOrNull()
            mark == null || mark <= 0.0
        } }
        val weak = list.mapNotNull { subject -> markOf(subject)?.let { subject to it } }
            .filter { it.second < 3.5f }
            .sortedBy { it.second }
        val upcoming = list.flatMap { subject ->
            subject.exams.mapNotNull { exam ->
                val date = runCatching {
                    java.time.LocalDate.parse(exam.date, java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                }.getOrNull()
                date?.takeIf { !it.isBefore(java.time.LocalDate.now()) }?.let { subject.name to it }
            }
        }.sortedBy { it.second }.take(3)
        ParyCard {
            Text("План действий", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(Store.platonusGpa.ifBlank { "—" }, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text("GPA из профиля", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f)) {
                    Text("$pending", style = MaterialTheme.typography.headlineSmall, color = if (pending > 0) LocalExtra.current.warning else LocalExtra.current.success)
                    Text("ожидают оценки", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (Store.platonusName.isNotBlank() || Store.platonusGroup.isNotBlank()) {
                Text(
                    listOf(Store.platonusName, Store.platonusGroup).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (overdueTasks.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Просроченные задания: ${overdueTasks.size}", style = MaterialTheme.typography.labelMedium, color = LocalExtra.current.danger)
                overdueTasks.take(3).forEach { Text("• ${it.title} · ${it.subject}", style = MaterialTheme.typography.bodySmall) }
            }
            if (urgentTasks.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Скоро дедлайн", style = MaterialTheme.typography.labelMedium, color = LocalExtra.current.warning)
                urgentTasks.sortedBy { it.due }.take(3).forEach { task ->
                    Text("• ${task.title} · ${task.due!!.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"))}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (weak.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Нужно подтянуть", style = MaterialTheme.typography.labelMedium, color = LocalExtra.current.warning)
                weak.take(3).forEach { (subject, mark) ->
                    Text("• ${subject.name}: ${formatMark(mark)} — удели предмету дополнительное время", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (upcoming.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Ближайшие контрольные", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                upcoming.forEach { (subject, date) ->
                    Text("• $subject — ${date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"))}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (pending == 0 && weak.isEmpty() && upcoming.isEmpty()) {
                Text("По текущему журналу срочных проблем не видно.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    val sorted = list.sortedWith(
        compareByDescending<JournalSubject> { markOf(it) != null }
            .thenBy { it.name.lowercase() },
    )
    item {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val withMark = list.count { markOf(it) != null }
        val avg = list.mapNotNull { markOf(it) }.average()
        ParyCard {
            Text("Журнал Платонуса", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Предметов: ${list.size} · с итогом: $withMark",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (Store.journalAt > 0) {
                val whenText = runCatching {
                    java.time.Instant.ofEpochMilli(Store.journalAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"))
                }.getOrDefault("")
                Text(
                    "обновлено $whenText" + if (!avg.isNaN()) " · средний " + String.format("%.2f", avg) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Поделиться оценками",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable {
                            val sb = StringBuilder("Журнал Платонуса\n")
                            if (!avg.isNaN()) sb.append("Средний: ").append(String.format("%.2f", avg)).append("\n")
                            sorted.forEach { j ->
                                sb.append("• ").append(j.name).append(" — ")
                                    .append(markOf(j)?.let { formatMark(it) } ?: "нет оценки")
                                    .append("\n")
                            }
                            runCatching {
                                ctx.startActivity(
                                    android.content.Intent.createChooser(
                                        android.content.Intent(android.content.Intent.ACTION_SEND)
                                            .setType("text/plain")
                                            .putExtra(android.content.Intent.EXTRA_TEXT, sb.toString()),
                                        "Оценки",
                                    ),
                                )
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
    for (s in sorted) {
        item(key = "j_${s.name}") {
            val total = markOf(s)
            val color = when {
                total == null -> MaterialTheme.colorScheme.onSurfaceVariant
                total >= 4.5 -> LocalExtra.current.success
                total >= 3.5 -> MaterialTheme.colorScheme.primary
                total >= 2.5 -> LocalExtra.current.warning
                else -> LocalExtra.current.danger
            }
            val subjectColor = colorFromHex(s.color) ?: MaterialTheme.colorScheme.outlineVariant
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(width = 4.dp, height = 26.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(subjectColor),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        s.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (total != null) {
                        Text(
                            formatMark(total),
                            style = MaterialTheme.typography.titleMedium,
                            color = color,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        Text(
                            "нет оценки",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val meta = buildList {
                    if (s.center.isNotBlank() && s.center != "0") add("центр ${s.center}")
                    if (s.tutors.isNotBlank()) add(s.tutors)
                }
                if (meta.isNotEmpty()) {
                    Text(
                        meta.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                s.exams.forEach { e ->
                    val parts = mutableListOf(e.title.ifBlank { "Экзамен" })
                    if (e.date.isNotBlank()) parts.add(e.date)
                    // «0» у экзамена — это ещё не выставленная оценка, не показываем.
                    val mark = e.mark.takeIf { raw ->
                        raw.isNotBlank() && (raw.toDoubleOrNull()?.let { it > 0.0 } ?: true)
                    }
                    if (mark != null) parts.add(mark)
                    Text(
                        parts.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
