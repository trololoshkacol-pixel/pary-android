package com.pary.app.ui.dialogs

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pary.app.data.Lesson
import com.pary.app.data.Parity
import com.pary.app.data.Store
import com.pary.app.ui.ChoiceRow
import com.pary.app.ui.LocalExtra
import com.pary.app.ui.ParyTextField
import com.pary.app.ui.TimeField
import java.time.LocalTime

@Composable
fun LessonEditorDialog(
    initial: Lesson?,
    dayDefault: Int = 0,
    parityDefault: Parity = Parity.BOTH,
    onDismiss: () -> Unit,
    onSave: (Lesson) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var room by remember { mutableStateOf(initial?.room ?: "") }
    var teacher by remember { mutableStateOf(initial?.teacher ?: "") }
    var day by remember { mutableStateOf(initial?.day ?: dayDefault) }
    var parity by remember { mutableStateOf(initial?.parity ?: parityDefault) }
    var start by remember { mutableStateOf(initial?.start ?: LocalTime.of(9, 0)) }
    var end by remember { mutableStateOf(initial?.end ?: LocalTime.of(10, 30)) }
    var quiet by remember { mutableStateOf(initial?.quiet ?: true) }
    var error by remember { mutableStateOf("") }

    fun pick(cur: LocalTime, set: (LocalTime) -> Unit) {
        TimePickerDialog(ctx, { _, h, m -> set(LocalTime.of(h, m)) }, cur.hour, cur.minute, true).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Text(
                    if (initial == null) "Новая пара" else "Изменить пару",
                    style = MaterialTheme.typography.titleLarge,
                )
                val known = remember {
                    Store.lessons.map { it.name to it }.distinctBy { it.first }.take(6)
                }
                if (initial == null && known.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Недавние",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        known.forEach { (n, l) ->
                            Text(
                                n,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        name = n
                                        if (room.isBlank()) room = l.room
                                        if (teacher.isBlank()) teacher = l.teacher
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                ParyTextField(name, { name = it }, "Предмет")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ParyTextField(room, { room = it }, "Аудитория", Modifier.weight(1f))
                    ParyTextField(teacher, { teacher = it }, "Преподаватель", Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Text("День недели", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                ChoiceRow(listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"), day, onSelect = { day = it })
                Spacer(Modifier.height(14.dp))
                Text("Неделя", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                ChoiceRow(
                    listOf("Каждую", "Числитель", "Знаменатель"),
                    when (parity) { Parity.BOTH -> 0; Parity.NUM -> 1; Parity.DEN -> 2 },
                    onSelect = { parity = when (it) { 1 -> Parity.NUM; 2 -> Parity.DEN; else -> Parity.BOTH } },
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeField("Начало", start, Modifier.weight(1f)) { pick(start) { start = it } }
                    TimeField("Конец", end, Modifier.weight(1f)) { pick(end) { end = it } }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Тихий режим", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Выключать звук телефона на этой паре",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = quiet, onCheckedChange = { quiet = it })
                }
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
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Text(
                            "Сохранить",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .clickableSave {
                                    val n = name.trim()
                                    when {
                                        n.isEmpty() -> error = "Укажи название пары"
                                        !end.isAfter(start) -> error = "Конец должен быть позже начала"
                                        else -> onSave(
                                            Lesson(
                                                id = initial?.id ?: com.pary.app.data.newId(),
                                                day = day,
                                                parity = parity,
                                                start = start,
                                                end = end,
                                                name = n,
                                                room = room.trim(),
                                                teacher = teacher.trim(),
                                                quiet = quiet,
                                            ),
                                        )
                                    }
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.clickableSave(block: () -> Unit): Modifier = this.clickable(onClick = block)
