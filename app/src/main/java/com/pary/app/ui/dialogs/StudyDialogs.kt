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
