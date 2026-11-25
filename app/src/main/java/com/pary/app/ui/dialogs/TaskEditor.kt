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
import com.pary.app.data.Task
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
fun TaskEditorDialog(
    initial: Task?,
    subjects: List<String>,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var subject by remember { mutableStateOf(initial?.subject ?: "") }
    var dueDate by remember { mutableStateOf(initial?.due?.toLocalDate() ?: LocalDate.now()) }
    var dueTime by remember { mutableStateOf(initial?.due?.toLocalTime() ?: LocalTime.of(23, 59)) }
    var hasDue by remember { mutableStateOf(initial?.due != null) }
    var priority by remember { mutableStateOf(initial?.priority ?: 1) }
    var remind by remember { mutableStateOf(initial?.remindMin ?: 60) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var error by remember { mutableStateOf("") }

    fun pickDate() {
        DatePickerDialog(
            ctx,
            { _, y, m, d -> dueDate = LocalDate.of(y, m + 1, d) },
            dueDate.year, dueDate.monthValue - 1, dueDate.dayOfMonth,
        ).show()
    }

    fun pickTime() {
        TimePickerDialog(ctx, { _, h, m -> dueTime = LocalTime.of(h, m) }, dueTime.hour, dueTime.minute, true).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text(if (initial == null) "Новая задача" else "Задача", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(14.dp))
                ParyTextField(title, { title = it }, "Что нужно сделать")
                Spacer(Modifier.height(10.dp))
                ParyTextField(subject, { subject = it }, "Предмет (необязательно)")
                if (subjects.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    ChoiceRow(
                        (subjects).take(8),
                        selected = subjects.indexOf(subject),
                        onSelect = { subject = subjects[it] },
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("Срок", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "Дата",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (hasDue) Ru.dateShort(dueDate) else "без срока",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable { hasDue = true; pickDate() },
                        )
                    }
                    if (hasDue) {
                        TimeField("Время", dueTime, Modifier.weight(1f)) { pickTime() }
                    }
                }
                if (hasDue) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Убрать срок",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { hasDue = false },
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("Важность", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                ChoiceRow(listOf("Неважно", "Обычно", "Важно"), priority, onSelect = { priority = it })
                Spacer(Modifier.height(14.dp))
                Text("Напомнить", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                ChoiceRow(
                    listOf("В момент", "За 15 мин", "За час", "За 3 ч", "За день"),
                    when (remind) {
                        0 -> 0; 15 -> 1; 60 -> 2; 180 -> 3; else -> 4
                    },
                    onSelect = { remind = listOf(0, 15, 60, 180, 1440)[it] },
                )
                Spacer(Modifier.height(10.dp))
                ParyTextField(note, { note = it }, "Заметка (необязательно)", singleLine = false)
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.danger)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Spacer(Modifier.weight(1f))
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text("Удалить", color = LocalExtra.current.danger)
                        }
                    }
                    Text(
                        "Сохранить",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                            .clickable {
                                if (title.isBlank()) error = "Назови задачу"
                                else onSave(
                                    Task(
                                        id = initial?.id ?: newId(),
                                        title = title.trim(),
                                        subject = subject.trim(),
                                        due = if (hasDue) LocalDateTime.of(dueDate, dueTime) else null,
                                        priority = priority,
                                        done = initial?.done ?: false,
                                        remindMin = remind,
                                        note = note.trim(),
                                    ),
                                )
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
