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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pary.app.data.ChatDraft
import com.pary.app.data.ChatParser
import com.pary.app.data.Logic
import com.pary.app.data.MiniBrain
import com.pary.app.data.Store
import com.pary.app.data.Task
import com.pary.app.data.TaskBucket
import com.pary.app.notify.Sync
import com.pary.app.ui.Chip
import com.pary.app.ui.EmptyState
import com.pary.app.ui.LocalExtra
import com.pary.app.ui.ParyCard
import com.pary.app.ui.Ru
import com.pary.app.ui.dialogs.TaskEditorDialog
import com.pary.app.ui.rememberNow
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Секции экрана задач. «Позже» — срок после завтрашнего дня,
 * «Без срока» — открытая задача без даты (Logic.bucketOf их не различает).
 */
private enum class TaskSection { OVERDUE, TODAY, TOMORROW, LATER, NO_DUE, DONE }

private val taskSectionOrder = listOf(
    TaskSection.OVERDUE,
    TaskSection.TODAY,
    TaskSection.TOMORROW,
    TaskSection.LATER,
    TaskSection.NO_DUE,
    TaskSection.DONE,
)

private fun taskSectionOf(t: Task, now: LocalDateTime): TaskSection = when {
    t.done -> TaskSection.DONE
    t.due == null -> TaskSection.NO_DUE
    else -> when (Logic.bucketOf(t, now)) {
        TaskBucket.OVERDUE -> TaskSection.OVERDUE
        TaskBucket.TODAY -> TaskSection.TODAY
        TaskBucket.TOMORROW -> TaskSection.TOMORROW
        // LATER в Logic — это всё, что дальше завтрашнего дня.
        else -> TaskSection.LATER
    }
}

private fun taskSectionTitle(s: TaskSection): String = when (s) {
    TaskSection.OVERDUE -> "Просрочено"
    TaskSection.TODAY -> "Сегодня"
    TaskSection.TOMORROW -> "Завтра"
    TaskSection.LATER -> "Позже"
    TaskSection.NO_DUE -> "Без срока"
    TaskSection.DONE -> "Готово"
}

/**
 * MiniBrain.explain может появиться отдельным патчем, поэтому зовём его
 * по имени через reflection: экран не ломается, пока функции нет.
 * Ожидаемая сигнатура — explain(weights: Map<String, Double>, text: String): String,
 * есть и перегрузка explain(text: String).
 */
private val miniBrainExplain: java.lang.reflect.Method? by lazy {
    runCatching {
        val explain = MiniBrain::class.java.methods.filter {
            it.name == "explain" || it.name.startsWith("explain\$")
        }
        fun paramAt(m: java.lang.reflect.Method, i: Int): Class<*>? = m.parameterTypes.getOrNull(i)
        val mapAndText = explain.firstOrNull { m ->
            val p0 = paramAt(m, 0)
            val p1 = paramAt(m, 1)
            m.parameterTypes.size == 2 && p0 != null && p1 != null &&
                Map::class.java.isAssignableFrom(p0) && CharSequence::class.java.isAssignableFrom(p1)
        }
        val onlyText = explain.firstOrNull { m ->
            m.parameterTypes.size == 1 &&
                paramAt(m, 0)?.let { CharSequence::class.java.isAssignableFrom(it) } == true
        }
        mapAndText ?: onlyText ?: explain.firstOrNull()
    }.getOrNull()
}

private fun brainExplain(text: String): String? {
    val m = miniBrainExplain ?: return null
    return runCatching {
        val params = m.parameterTypes
        if (params.isEmpty() || params.size > 3) return@runCatching null
        // Аргументы подбираем по типам: подойдёт и explain(text), и explain(weights, text).
        val args = arrayOfNulls<Any?>(params.size)
        for (i in params.indices) {
            val p = params[i]
            args[i] = when {
                CharSequence::class.java.isAssignableFrom(p) -> text
                Map::class.java.isAssignableFrom(p) -> Store.brain
                LocalDateTime::class.java.isAssignableFrom(p) -> LocalDateTime.now()
                LocalDate::class.java.isAssignableFrom(p) -> LocalDate.now()
                else -> return@runCatching null
            }
        }
        when (val res = m.invoke(MiniBrain, *args)) {
            null -> null
            is String -> res.takeIf { it.isNotBlank() }
            is Boolean -> if (res) "похоже на домашку" else "не точно домашка — проверь сам"
            is Iterable<*> -> res.joinToString(", ") { it.toString() }.takeIf { it.isNotBlank() }
            else -> res.toString().takeIf { it.isNotBlank() }
        }
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}

/** Короткое объяснение, почему сообщение из чата попало в задачи. */
private fun draftWhy(d: ChatDraft): String {
    brainExplain(d.text)?.let { why ->
        // Черновик мог прийти при других весах: если MiniBrain теперь говорит «не ДЗ»,
        // не врём пользователю словом «почему», а честно показываем вердикт.
        return if (why.trimStart().startsWith("не ДЗ")) "MiniBrain: $why" else "Почему: $why"
    }
    val hints = buildList {
        if (ChatParser.isHomework(d.text)) add("нашлись слова про учёбу")
        if (d.due != null) add("из текста понятен срок")
        if (d.chat.isNotBlank()) add("чат «${ChatParser.subjectFrom(d.chat)}»")
    }
    return if (hints.isEmpty()) {
        "Почему: MiniBrain посчитал сообщение домашкой"
    } else {
        "Почему: ${hints.joinToString(", ")}"
    }
}

@Composable
fun TasksScreen(modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val now = rememberNow(30)
    var filter by rememberSaveable { mutableStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Task?>(null) }
    var creating by remember { mutableStateOf(false) }

    val all = Logic.sortedTasks(Store.tasks, now)
    val open = all.filterNot { it.done }
    val overdueCount = open.count { it.due != null && it.due.isBefore(now) }
    val filtered = when (filter) {
        1 -> all.filter { !it.done && it.due?.toLocalDate() == now.toLocalDate() }
        2 -> all.filter { !it.done && it.due != null && it.due.isBefore(now) }
        3 -> all.filter { it.done }
        4 -> all.filter { !it.done && it.due?.toLocalDate() == now.toLocalDate().plusDays(1) }
        5 -> all.filter { !it.done && it.due == null }
        else -> open
    }
    val query = search.trim()
    val shown = if (query.isBlank()) filtered else filtered.filter { task ->
        task.title.contains(query, ignoreCase = true) ||
            task.subject.contains(query, ignoreCase = true) ||
            task.note.contains(query, ignoreCase = true)
    }
    // Секции считаем один раз, чтобы заголовок и карточки не расходились.
    val grouped = taskSectionOrder.mapNotNull { section ->
        val list = shown.filter { taskSectionOf(it, now) == section }
        if (list.isEmpty()) null else section to list
    }
    val drafts = Store.chatDrafts.takeLast(8).map { it to draftWhy(it) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "task_search") {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Найти задачу или предмет") },
                shape = MaterialTheme.shapes.medium,
            )
        }
        if (Store.chatDrafts.isEmpty()) {
            item(key = "share_import_hint") {
                ParyCard {
                    Text(
                        "Поиск заданий в чатах",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "В Telegram, WhatsApp, VK и других приложениях нажми «Поделиться» у сообщения и выбери Pary. Приложение проверит текст локально и покажет найденное задание здесь — без входа в твои аккаунты.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (Store.chatDrafts.isNotEmpty()) {
            item(key = "drafts_header") {
                ParyCard {
                    Text(
                        "Из чатов · ${Store.chatDrafts.size}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Черновики из отслеживаемых чатов: проверь причину, потом «В задачи» или «Скрыть».",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            for ((d, why) in drafts) {
                item(key = "draft_${d.id}") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                                MaterialTheme.shapes.medium,
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            d.chat.ifBlank { "Чат" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            d.text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            why,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        d.due?.let {
                            Text(
                                "срок: ${Ru.dueLabel(it, now.toLocalDate())}",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalExtra.current.warning,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "В задачи",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable {
                                        Store.acceptDraft(d.id)
                                        Sync.refresh(ctx)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                            Text(
                                "Скрыть",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { Store.dismissDraft(d.id) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }
        }

        item(key = "title") {
            val shape = RoundedCornerShape(24.dp)
            val completed = Store.tasks.count { it.done }
            val progress = if (Store.tasks.isEmpty()) 0f else completed / Store.tasks.size.toFloat()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            ),
                        ),
                    )
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f), shape)
                    .padding(17.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ЛИЧНЫЙ ПЛАН", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("Задачи", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            when {
                                open.isEmpty() -> "Всё сделано — новых дедлайнов нет"
                                overdueCount > 0 -> "Просрочено $overdueCount · открытых ${open.size}"
                                else -> "Открытых ${open.size} · всё идёт по плану"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { creating = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Добавить задачу", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.weight(1f).height(7.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("$completed/${Store.tasks.size}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item(key = "filters") {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "Все" to 0,
                    "Сегодня" to 1,
                    "Просрочено" to 2,
                    "Завтра" to 4,
                    "Без срока" to 5,
                    "Готово" to 3,
                ).forEach { (label, idx) ->
                    Chip(label, selected = filter == idx, small = true) { filter = idx }
                }
            }
        }

        if (shown.isEmpty()) {
            item(key = "empty") {
                TasksEmptyState(
                    filter = filter,
                    onCreate = { creating = true },
                    onShowAll = { filter = 0 },
                )
            }
        }

        for ((section, list) in grouped) {
            item(key = "h_${section.name}") {
                TaskSectionHeader(section, list.size)
            }
            items(list.size, key = { list[it].id }) { i ->
                TaskRow(list[i], now) { editing = list[i] }
            }
        }

        if (all.any { it.done } && filter != 3) {
            item(key = "clear_done") {
                TextButton(onClick = { Store.removeCompleted() }) {
                    Text("Убрать выполненные", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    editing?.let { t ->
        TaskEditorDialog(
            initial = t,
            subjects = Store.subjectNames(),
            onDismiss = { editing = null },
            onSave = { Store.updateTask(it); editing = null },
            onDelete = { Store.deleteTask(t.id); editing = null },
        )
    }
    if (creating) {
        TaskEditorDialog(
            initial = null,
            subjects = Store.subjectNames(),
            onDismiss = { creating = false },
            onSave = { Store.addTask(it); creating = false },
        )
    }
}

@Composable
private fun TaskSectionHeader(section: TaskSection, count: Int) {
    val tone = when (section) {
        TaskSection.OVERDUE -> LocalExtra.current.danger
        TaskSection.TODAY -> MaterialTheme.colorScheme.primary
        TaskSection.DONE -> LocalExtra.current.success
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tone))
        Spacer(Modifier.width(8.dp))
        Text(
            taskSectionTitle(section),
            style = MaterialTheme.typography.titleSmall,
            color = tone,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TasksEmptyState(filter: Int, onCreate: () -> Unit, onShowAll: () -> Unit) {
    val (icon, title, subtitle) = when (filter) {
        1 -> Triple(
            Icons.Filled.DateRange,
            "На сегодня пусто",
            "Ничего не горит. Загляни в «Все» — вдруг там есть задачи на завтра.",
        )
        2 -> Triple(
            Icons.Filled.CheckCircle,
            "Просрочек нет",
            "Так держать! Планы на сегодня и дальше — в разделе «Все».",
        )
        3 -> Triple(
            Icons.Filled.CheckCircle,
            "Пока ничего не выполнено",
            "Отметь задачу кружком-галочкой в карточке — она сразу появится здесь.",
        )
        else -> Triple(
            Icons.Filled.CheckCircle,
            "Всё чисто",
            "Открытых задач нет. Добавь новую кнопкой ＋ сверху — например, «сдать лабу в пятницу».",
        )
    }
    ParyCard {
        EmptyState(icon, title, subtitle)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (filter) {
                1 -> {
                    TextButton(onClick = onCreate) { Text("Новая задача") }
                    TextButton(onClick = onShowAll) {
                        Text("Все задачи", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                2, 3 -> TextButton(onClick = onShowAll) { Text("Показать все") }
                else -> TextButton(onClick = onCreate) { Text("Новая задача") }
            }
        }
    }
}

@Composable
private fun TaskRow(t: Task, now: LocalDateTime, onEdit: () -> Unit) {
    val danger = LocalExtra.current.danger
    val warning = LocalExtra.current.warning
    val overdue = !t.done && t.due != null && t.due.isBefore(now)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
            .clickable(onClick = onEdit)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Крупная зона нажатия: отметить выполненной можно в один тап, без диалога.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    role = Role.Checkbox,
                    onClickLabel = if (t.done) "Вернуть в работу" else "Отметить выполненной",
                ) { Store.toggleTask(t.id) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (t.done) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(
                        1.5.dp,
                        if (t.done) Color.Transparent else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (t.done) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (t.done) TextDecoration.LineThrough else null,
                color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val meta = buildList {
                if (t.due != null) add(Ru.dueLabel(t.due, now.toLocalDate()) + if (overdue) " · просрочено" else "")
                if (t.subject.isNotBlank()) add(t.subject)
                if (t.note.isNotBlank()) add(t.note)
            }
            if (meta.isNotEmpty()) {
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) danger else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!t.done && t.due != null) {
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Logic.humanLeft(t.due, now),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (overdue) danger else warning,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (!t.done && t.priority == 2) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(7.dp).clip(CircleShape).background(danger))
        }
    }
}
