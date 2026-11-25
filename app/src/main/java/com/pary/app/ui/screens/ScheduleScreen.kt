package com.pary.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pary.app.data.Lesson
import com.pary.app.data.Parity
import com.pary.app.data.Store
import com.pary.app.ui.ChoiceRow
import com.pary.app.ui.LocalExtra
import com.pary.app.ui.ParyCard
import com.pary.app.ui.Ru
import com.pary.app.ui.SectionHeader
import com.pary.app.ui.dialogs.LessonEditorDialog
import com.pary.app.ui.rememberNow

@Composable
fun ScheduleScreen(modifier: Modifier = Modifier) {
    val now = rememberNow(30)
    val today = now.toLocalDate()
    var view by rememberSaveable { mutableStateOf(if (Store.parityToday() == Parity.DEN) 1 else 0) }
    var editing by remember { mutableStateOf<Lesson?>(null) }
    var contextFor by remember { mutableStateOf<Lesson?>(null) }
    var creatingFor by remember { mutableStateOf<Int?>(null) }

    val parity = if (view == 0) Parity.NUM else Parity.DEN
    val todayIdx = today.dayOfWeek.value - 1
    val liveParity = Store.parityToday()
    val all = Store.lessons
    val dayItems = remember(all, parity) {
        (0..6).associateWith { d ->
            all.filter { it.day == d && (it.parity == Parity.BOTH || it.parity == parity) }
                .sortedWith(compareBy({ it.start }, { it.end }))
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            val weekLessons = dayItems.values.flatten()
            val todayLessons = Store.dayLessons(today)
            val shape = RoundedCornerShape(12.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    .padding(17.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ТВОЯ УЧЕБНАЯ НЕДЕЛЯ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("Расписание", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "${if (parity == Parity.NUM) "Числитель" else "Знаменатель"} · ${weekLessons.size} занятий",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { creatingFor = todayIdx },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Добавить пару", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${todayLessons.size}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("сегодня", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("${weekLessons.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("за неделю", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("${weekLessons.map { it.name }.distinct().size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("предметов", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            ParyCard {
                Text("Какая неделя идёт сейчас", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                val cur = Store.parityToday()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((i, p) in listOf(Parity.NUM, Parity.DEN).withIndex()) {
                        val on = cur == p
                        Text(
                            if (p == Parity.NUM) "Числитель" else "Знаменатель",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable { Store.setWeek(p) }
                                .padding(vertical = 10.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Числитель/Знаменатель настраивается выше. Нажми на пару, чтобы изменить, долгий тап — контекст",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("Сетка", modifier = Modifier.weight(1f))
            }
            ChoiceRow(
                listOf("Числитель", "Знаменатель"),
                view,
                onSelect = { view = it },
            )
        }

        for (d in 0..6) {
            val items = dayItems[d].orEmpty()
            if (items.isEmpty() && d == 6) continue
            item(key = "day$d") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            Ru.dowFull[d],
                            style = MaterialTheme.typography.titleSmall,
                            color = if (d == today.dayOfWeek.value - 1) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (items.isEmpty()) "выходной" else "${items.size} ${Ru.plural(items.size, "пара", "пары", "пар")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (items.isEmpty()) {
                        TextButton(
                            onClick = { creatingFor = d },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text("＋ Добавить пару")
                        }
                    }
                    items.forEach { l ->
                        val live = d == todayIdx && parity == liveParity &&
                            !l.start.isAfter(now.toLocalTime()) && now.toLocalTime().isBefore(l.end)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    if (live) 1.5.dp else 1.dp,
                                    if (live) LocalExtra.current.success
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                                    MaterialTheme.shapes.medium,
                                )
                                .pointerInput(l) {
                                    detectTapGestures(
                                        onTap = { editing = l },
                                        onLongPress = { contextFor = l },
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(10.dp), contentAlignment = Alignment.Center) {
                                if (live) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(LocalExtra.current.success),
                                    )
                                }
                            }
                            Column(Modifier.width(46.dp)) {
                                Text(
                                    Ru.time(l.start),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (live) LocalExtra.current.success
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    Ru.time(l.end),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(
                                    l.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOf(l.room, l.teacher).filter { it.isNotBlank() }.joinToString(" · ")
                                        .ifBlank { "—" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (l.parity != Parity.BOTH) {
                                Text(
                                    if (l.parity == Parity.NUM) "числ." else "знам.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Text(
                        "+ пара на этот день",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 4.dp, top = 2.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { creatingFor = d }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { creatingFor = today.dayOfWeek.value - 1 }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Добавить пару",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (all.isEmpty()) {
            item {
                Text(
                    "Расписание пустое — добавь первую пару",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalExtra.current.warning,
                )
            }
        }
    }

    editing?.let { l ->
        LessonEditorDialog(
            initial = l,
            onDismiss = { editing = null },
            onSave = { Store.updateLesson(it); editing = null },
            onDelete = { Store.deleteLesson(l.id); editing = null },
        )
    }
    contextFor?.let { l ->
        AlertDialog(
            onDismissRequest = { contextFor = null },
            title = { Text(l.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    val extra = listOf(l.room, l.teacher).filter { it.isNotBlank() }.joinToString(" · ")
                    val line = "${Ru.dowFull[l.day]}, ${Ru.time(l.start)}–${Ru.time(l.end)}" +
                        (if (extra.isBlank()) "" else " · $extra")
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Удалить пару",
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalExtra.current.danger,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { Store.deleteLesson(l.id); contextFor = null }
                            .padding(vertical = 10.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { contextFor = null; editing = l }) { Text("Изменить") }
            },
            dismissButton = {
                TextButton(onClick = { contextFor = null }) { Text("Отмена") }
            },
        )
    }
    creatingFor?.let { d ->
        LessonEditorDialog(
            initial = null,
            dayDefault = d,
            parityDefault = if (view == 0) Parity.NUM else Parity.DEN,
            onDismiss = { creatingFor = null },
            onSave = { Store.addLesson(it); creatingFor = null },
        )
    }
}
