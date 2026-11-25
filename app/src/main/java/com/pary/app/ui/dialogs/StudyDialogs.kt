package com.pary.app.ui.dialogs

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pary.app.data.Exam
import com.pary.app.data.Grade
import com.pary.app.data.Note
import com.pary.app.data.newId
import com.pary.app.ui.ChoiceRow
import com.pary.app.ui.LocalExtra
import com.pary.app.ui.ParyTextField
import com.pary.app.ui.Ru
import com.pary.app.ui.TimeField
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
private fun DialogFrame(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}

@Composable
private fun SaveRow(
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    saveLabel: String = "Сохранить",
    onSave: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextButton(onClick = onDismiss) { Text("Отмена") }
        Spacer(Modifier.weight(1f))
        if (onDelete != null) {
            TextButton(onClick = onDelete) { Text("Удалить", color = LocalExtra.current.danger) }
        }
        Text(
            saveLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                .clickable(onClick = onSave)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun GradeEditorDialog(
    subjects: List<String>,
    onDismiss: () -> Unit,
    onSave: (Grade) -> Unit,
) {
    var subject by remember { mutableStateOf(subjects.firstOrNull() ?: "") }
    var value by remember { mutableStateOf(5) }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var error by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    DialogFrame("Новая оценка", onDismiss) {
        ParyTextField(subject, { subject = it }, "Предмет")
        if (subjects.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ChoiceRow(subjects.take(8), subjects.indexOf(subject), onSelect = { subject = subjects[it] })
        }
        Spacer(Modifier.height(14.dp))
        Text("Оценка", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        ChoiceRow(listOf("2", "3", "4", "5"), value - 2, onSelect = { value = it + 2 })
        Spacer(Modifier.height(14.dp))
        Text(
            "Дата: ${Ru.dateShort(date)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                DatePickerDialog(
                    ctx,
                    { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                    date.year, date.monthValue - 1, date.dayOfMonth,
                ).show()
            },
        )
        Spacer(Modifier.height(10.dp))
        ParyTextField(note, { note = it }, "Комментарий (необязательно)")
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.danger)
        }
        Spacer(Modifier.height(14.dp))
        SaveRow(onDismiss = onDismiss, onSave = {
            if (subject.isBlank()) error = "Укажи предмет"
            else onSave(Grade(id = newId(), subject = subject.trim(), value = value, date = date, note = note.trim()))
        })
    }
}

@Composable
fun ExamEditorDialog(
    subjects: List<String>,
    initial: Exam? = null,
    onDismiss: () -> Unit,
    onSave: (Exam) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var subject by remember { mutableStateOf(initial?.subject ?: subjects.firstOrNull() ?: "") }
    var room by remember { mutableStateOf(initial?.room ?: "") }
    var date by remember { mutableStateOf(initial?.at?.toLocalDate() ?: LocalDate.now().plusDays(7)) }
    var time by remember { mutableStateOf(initial?.at?.toLocalTime() ?: LocalTime.of(9, 0)) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var passed by remember { mutableStateOf(initial?.passed ?: false) }
    var error by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    DialogFrame("Экзамен", onDismiss) {
        ParyTextField(subject, { subject = it }, "Предмет")
        if (subjects.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ChoiceRow(subjects.take(8), subjects.indexOf(subject), onSelect = { subject = subjects[it] })
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        DatePickerDialog(
                            ctx,
                            { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                            date.year, date.monthValue - 1, date.dayOfMonth,
                        ).show()
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text("Дата", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Ru.dateShort(date), style = MaterialTheme.typography.titleMedium)
            }
            TimeField("Время", time, Modifier.weight(1f)) {
                TimePickerDialog(ctx, { _, h, m -> time = LocalTime.of(h, m) }, time.hour, time.minute, true).show()
            }
        }
        Spacer(Modifier.height(10.dp))
        ParyTextField(room, { room = it }, "Аудитория (необязательно)")
        Spacer(Modifier.height(10.dp))
        ParyTextField(note, { note = it }, "Что взять / заметка", singleLine = false)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Уже сдан", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Можно снять галочку, если передумал",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.Switch(checked = passed, onCheckedChange = { passed = it })
        }
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.danger)
        }
        Spacer(Modifier.height(14.dp))
        SaveRow(onDismiss = onDismiss, onDelete = onDelete, onSave = {
            if (subject.isBlank()) error = "Укажи предмет"
            else onSave(
                Exam(
                    id = initial?.id ?: newId(),
                    subject = subject.trim(),
                    at = LocalDateTime.of(date, time),
                    room = room.trim(),
                    note = note.trim(),
                    passed = passed,
                ),
            )
        })
    }
}

@Composable
fun NoteEditorDialog(
    subjects: List<String>,
    onDismiss: () -> Unit,
    onSave: (Note) -> Unit,
) {
    var subject by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    DialogFrame("Заметка", onDismiss) {
        ParyTextField(subject, { subject = it }, "Предмет (необязательно)")
        if (subjects.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ChoiceRow(subjects.take(8), subjects.indexOf(subject), onSelect = { subject = subjects[it] })
        }
        Spacer(Modifier.height(10.dp))
        ParyTextField(text, { text = it }, "Текст заметки", singleLine = false)
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.danger)
        }
        Spacer(Modifier.height(14.dp))
        SaveRow(onDismiss = onDismiss, onSave = {
            if (text.isBlank()) error = "Пустая заметка"
            else onSave(Note(id = newId(), subject = subject.trim(), text = text.trim()))
        })
    }
}


@Composable
fun GradebookSubjectDialog(
    initial: com.pary.app.data.GradebookSubject? = null,
    onDismiss: () -> Unit,
    onSave: (com.pary.app.data.GradebookSubject) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var subject by remember { mutableStateOf(initial?.subject ?: "") }
    var credits by remember { mutableStateOf((initial?.credits ?: 3).toString()) }
    var r1 by remember { mutableStateOf((initial?.rating1Weight ?: 30).toString()) }
    var r2 by remember { mutableStateOf((initial?.rating2Weight ?: 30).toString()) }
    var exam by remember { mutableStateOf((initial?.examWeight ?: 40).toString()) }
    var error by remember { mutableStateOf("") }
    DialogFrame(if (initial == null) "Новый предмет" else "Настройки предмета", onDismiss) {
        ParyTextField(subject, { subject = it }, "Название дисциплины")
        Spacer(Modifier.height(10.dp))
        ParyTextField(credits, { credits = it.filter(Char::isDigit).take(2) }, "Кредиты ECTS")
        Spacer(Modifier.height(12.dp))
        Text("Вес компонентов · сумма должна быть 100%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        ParyTextField(r1, { r1 = it.filter(Char::isDigit).take(3) }, "Рубежный контроль 1 · %")
        Spacer(Modifier.height(8.dp))
        ParyTextField(r2, { r2 = it.filter(Char::isDigit).take(3) }, "Рубежный контроль 2 · %")
        Spacer(Modifier.height(8.dp))
        ParyTextField(exam, { exam = it.filter(Char::isDigit).take(3) }, "Экзамен · %")
        val total = (r1.toIntOrNull() ?: 0) + (r2.toIntOrNull() ?: 0) + (exam.toIntOrNull() ?: 0)
        Text("Итого: $total%", style = MaterialTheme.typography.labelSmall, color = if (total == 100) MaterialTheme.colorScheme.primary else LocalExtra.current.warning)
        if (error.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.danger)
        }
        Spacer(Modifier.height(14.dp))
        SaveRow(onDismiss = onDismiss, onDelete = onDelete, onSave = {
            val c = credits.toIntOrNull()
            val w1 = r1.toIntOrNull()
            val w2 = r2.toIntOrNull()
            val we = exam.toIntOrNull()
            when {
                subject.isBlank() -> error = "Укажи название дисциплины"
                c == null || c !in 1..30 -> error = "Количество кредитов: от 1 до 30"
                w1 == null || w2 == null || we == null || listOf(w1, w2, we).any { it !in 0..100 } -> error = "Проверь веса компонентов"
                w1 + w2 + we != 100 -> error = "Сумма весов должна быть ровно 100%"
                else -> onSave(
                    (initial ?: com.pary.app.data.GradebookSubject()).copy(
                        subject = subject.trim(), credits = c,
                        rating1Weight = w1, rating2Weight = w2, examWeight = we,
                    ),
                )
            }
        })
    }
}

@Composable
fun AssessmentEditorDialog(
    onDismiss: () -> Unit,
    onSave: (com.pary.app.data.AssessmentRecord) -> Unit,
) {
    var kindIndex by remember { mutableStateOf(0) }
    var title by remember { mutableStateOf("") }
    var score by remember { mutableStateOf("") }
    var maxScore by remember { mutableStateOf("100") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val kinds = com.pary.app.data.AssessmentKind.entries
    DialogFrame("Добавить баллы", onDismiss) {
        Text("Компонент", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        ChoiceRow(kinds.map { it.label }, kindIndex, onSelect = { kindIndex = it })
        Spacer(Modifier.height(10.dp))
        ParyTextField(title, { title = it }, "Название работы (например, тест №1)")
        Spacer(Modifier.height(8.dp))
        ParyTextField(score, { score = it.replace(',', '.').filter { c -> c.isDigit() || c == '.' }.take(8) }, "Набранные баллы")
        Spacer(Modifier.height(8.dp))
        ParyTextField(maxScore, { maxScore = it.replace(',', '.').filter { c -> c.isDigit() || c == '.' }.take(8) }, "Максимум баллов")
        Spacer(Modifier.height(8.dp))
        ParyTextField(note, { note = it }, "Комментарий (необязательно)")
        if (error.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.danger)
        }
        Spacer(Modifier.height(14.dp))
        SaveRow(onDismiss = onDismiss, onSave = {
            val value = score.toDoubleOrNull()
            val max = maxScore.toDoubleOrNull()
            when {
                value == null || max == null || max <= 0.0 -> error = "Введи корректные баллы и максимум"
                value < 0.0 || value > max -> error = "Баллы должны быть от 0 до максимума"
                else -> onSave(
                    com.pary.app.data.AssessmentRecord(
                        kind = kinds[kindIndex],
                        title = title.trim().ifBlank { kinds[kindIndex].label },
                        score = value, maxScore = max, note = note.trim(),
                    ),
                )
            }
        })
    }
}
