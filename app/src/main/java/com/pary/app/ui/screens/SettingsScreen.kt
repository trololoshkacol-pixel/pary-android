package com.pary.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pary.app.data.ChatImport
import com.pary.app.data.Logic
import com.pary.app.data.Platonus
import com.pary.app.data.PlatonusGrades
import com.pary.app.data.PlatonusFullSync
import com.pary.app.data.PlatonusSyncScheduler
import com.pary.app.data.Store
import com.pary.app.notify.Sync
import com.pary.app.ui.AccentHex
import com.pary.app.ui.AccentNames
import com.pary.app.ui.Chip
import com.pary.app.ui.ChoiceRow
import com.pary.app.ui.ParyCard
import com.pary.app.ui.ParyTextField
import com.pary.app.ui.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Короткая навигация по разделам «Ещё».
 * itemIndex — позиция item в LazyColumn (0 — шапка с чипсами, 1..7 — разделы ниже).
 */
private data class NavSection(val key: String, val label: String, val itemIndex: Int)

private const val MAX_CHAT_IMPORT_CHARS = 3_000_000

/** Читает экспорт чата ограниченными порциями, не загружая потенциально огромный файл целиком. */
private fun readChatExport(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
        buildString {
            val buffer = CharArray(8_192)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                require(length + count <= MAX_CHAT_IMPORT_CHARS) {
                    "Файл слишком большой. Максимум — 3 МБ текста."
                }
                append(buffer, 0, count)
            }
        }
    }
}.getOrNull()

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var editProfile by remember { mutableStateOf(false) }
    val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                readChatExport(ctx, uri)
            }.getOrNull()
            val ok = text != null && Store.importJson(text)
            Toast.makeText(
                ctx,
                if (ok) "Данные загружены" else "Не получилось прочитать файл",
                Toast.LENGTH_LONG,
            ).show()
            if (ok) Sync.refresh(ctx)
        }
    }
    val dndGranted = nm.isNotificationPolicyAccessGranted
    val notifGranted = nm.areNotificationsEnabled()

    // Порядок item'ов ниже обязан совпадать: 0 — шапка с чипсами, дальше 7 разделов в этом порядке.
    val nav = remember {
        listOf(
            NavSection("profile", "Профиль", 1),
            NavSection("look", "Внешний вид", 2),
            NavSection("notify", "Уведомления", 3),
            NavSection("platonus", "Платонус", 4),
            NavSection("chats", "Чаты", 5),
            NavSection("telegram", "Telegram", 6),
            NavSection("data", "Данные", 7),
        )
    }
    val activeSection by remember {
        derivedStateOf {
            nav.lastOrNull { it.itemIndex <= listState.firstVisibleItemIndex }?.key
        }
    }
    val goTo: (NavSection) -> Unit = { s ->
        scope.launch { listState.animateScrollToItem(s.itemIndex) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ===== 0. Шапка и быстрая навигация =====
        item {
            val shape = RoundedCornerShape(24.dp)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                ),
                            ),
                        )
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f), shape)
                        .padding(17.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text("АККАУНТ И НАСТРОЙКИ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("Профиль", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Личные данные, оформление, уведомления и подключение учебного портала.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text(if (Store.platonusLogin.isNotBlank()) "Настроен" else "Не настроен", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text("Platonus", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column {
                            Text(if (notifGranted) "Активны" else "Выключены", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text("Уведомления", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    nav.forEach { s ->
                        Chip(
                            text = s.label,
                            selected = activeSection == s.key,
                            small = true,
                        ) { goTo(s) }
                    }
                }
            }
        }

        // ===== 1. Профиль =====
        item {
            Column(Modifier.fillMaxWidth()) {
                SectionHeader("Профиль")
                Text(
                    "Имя и группа — для приветствия, виджета и записей.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                ParyCard(onClick = { editProfile = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                Store.profileName.trim().take(1).uppercase().ifEmpty { "?" },
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                Store.profileName.trim().ifBlank { "Нажми, чтобы вписать имя" },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                Store.profileGroup.trim().ifBlank { "группа не указана" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "✎",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ===== 2. Внешний вид =====
        item {
            Column(Modifier.fillMaxWidth()) {
                SectionHeader("Внешний вид")
                Text(
                    "Три режима оформления, пять акцентов и управление анимациями.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                ParyCard {
                    Text("Тема", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            Triple("light", "Светлая", Color(0xFFF5F5F2)),
                            Triple("dark", "Тёмная", Color(0xFF171819)),
                            Triple("amoled", "AMOLED", Color(0xFF000000)),
                        ).forEach { (key, label, preview) ->
                            val selected = Store.theme == key
                            Column(
                                modifier = Modifier
                                    .width(96.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(preview)
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                                        RoundedCornerShape(16.dp),
                                    )
                                    .clickable {
                                        Store.updateTheme(key)
                                        Sync.refresh(ctx)
                                    }
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.size(13.dp).clip(CircleShape).background(Color(0xFFB69CFF)))
                                    Box(Modifier.size(13.dp).clip(CircleShape).background(Color(0xFF5EEAD4)))
                                    Box(Modifier.size(13.dp).clip(CircleShape).background(Color(0xFFFFB86B)))
                                }
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (key == "light") Color(0xFF202434) else Color(0xFFF3F5FA),
                                    maxLines = 1,
                                )
                                Text(
                                    if (selected) "Выбрано" else "Выбрать",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (key == "light") Color(0xFF596174) else Color(0xFFB8BDCB),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when (Store.theme) {
                            "amoled" -> "Чёрный фон для OLED-экранов и экономии энергии."
                            "light" -> "Чистая светлая поверхность с мягким контрастом."
                            "midnight" -> "Тёмная тема Swiss Modern."
                            "forest" -> "Тёмная тема Swiss Modern."
                            else -> "Строгая графитовая тема Swiss Modern."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Фирменный акцент", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AccentHex.forEachIndexed { i, c ->
                            val on = Store.accent == i
                            Box(
                                modifier = Modifier
                                    .size(if (on) 36.dp else 30.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .then(
                                        if (on) {
                                            Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.onSurface,
                                                CircleShape,
                                            )
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable { Store.updateAccent(i); Sync.refresh(ctx) },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        AccentNames[Store.accent.coerceIn(0, AccentNames.lastIndex)],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    SwitchRow(
                        "Анимации",
                        "Плавное появление карточек",
                        Store.animations,
                    ) { Store.updateAnimations(it) }
                }
                Spacer(Modifier.height(12.dp))
                ParyCard {
                    Text("Виджет", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Долгое нажатие на рабочем столе → Виджеты → Пары. Что показывать:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    val modes = listOf("next", "today", "tasks", "week")
                    ChoiceRow(
                        listOf("Пара", "Сегодня", "Задачи", "Дни"),
                        modes.indexOf(Store.widgetMode).coerceAtLeast(0),
                        onSelect = {
                            Store.updateWidgetMode(modes[it])
                            Sync.refresh(ctx)
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when (Store.widgetMode) {
                            "today" -> "Список всех пар на сегодня с временем и аудиторией."
                            "tasks" -> "Ближайшие дела с дедлайнами."
                            "week" -> "Сводка по ближайшим дням."
                            else -> "Текущая пара с прогрессом или следующая пара."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ===== 3. Уведомления =====
        item {
            Column(Modifier.fillMaxWidth()) {
                SectionHeader("Уведомления")
                Text(
                    "Разрешения, тихий режим, умный будильник и вечерняя надпись.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                ParyCard {
                    Text(
                        if (notifGranted) "Уведомления разрешены" else "Уведомления выключены",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Без разрешения не будет напоминаний о парах, задачах и будильнике.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!notifGranted) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Открыть настройки уведомлений →",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName),
                                    )
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                ParyCard {
                    SwitchRow(
                        "Тихий режим на парах",
                        if (dndGranted) "Звук выключается сам, потом возвращается"
                        else "Нужно разрешение «Не беспокоить»",
                        Store.silentMode,
                    ) { on ->
                        Store.updateSilentMode(on)
                        if (on && !dndGranted) {
                            runCatching {
                                ctx.startActivity(
                                    Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                                )
                            }
                        }
                        Sync.refresh(ctx)
                    }
                    if (!dndGranted) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Открыть доступ к «Не беспокоить» →",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                                    )
                                }
                            },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    SwitchRow(
                        "Умный будильник",
                        "Вечером сам ставит будильник на первую пару",
                        Store.smartAlarm,
                    ) {
                        Store.updateSmartAlarm(it)
                        Sync.refresh(ctx)
                    }
                    if (Store.smartAlarm) {
                        Spacer(Modifier.height(10.dp))
                        Stepper(
                            "На сборы и дорогу",
                            Logic.dur(Store.prepMin),
                            minus = { Store.updatePrep(Store.prepMin - 15); Sync.refresh(ctx) },
                            plus = { Store.updatePrep(Store.prepMin + 15); Sync.refresh(ctx) },
                        )
                        Spacer(Modifier.height(8.dp))
                        val learned = Store.learnedPrep()
                        if (learned != null && kotlin.math.abs(learned - Store.prepMin) >= 15) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Память: обычно встаёшь за " + Logic.dur(learned) + " · подставить",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    Store.updatePrep(learned)
                                    Sync.refresh(ctx)
                                },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Stepper(
                            "Будить, если первая пара",
                            if (Store.alarmBefore >= 1440) {
                                "в любое время"
                            } else {
                                "раньше ${"%02d:00".format(Store.alarmBefore / 60)}"
                            },
                            minus = { Store.updateAlarmBefore(Store.alarmBefore - 60); Sync.refresh(ctx) },
                            plus = { Store.updateAlarmBefore(Store.alarmBefore + 60); Sync.refresh(ctx) },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            Sync.alarmPreview(ctx),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        val am = ctx.getSystemService(android.app.AlarmManager::class.java)
                        val exactOk = am == null || am.canScheduleExactAlarms() ||
                            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S
                        if (!exactOk) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Разреши «Будильники и напоминания», иначе время может съехать →",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.clickable {
                                    runCatching {
                                        ctx.startActivity(
                                            Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                                .setData(android.net.Uri.parse("package:" + ctx.packageName)),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                ParyCard {
                    Text("Что после пар", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    var note by remember { mutableStateOf(Store.afterNote) }
                    ParyTextField(note, { note = it }, "Надпись в уведомлении вечером")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Сохранить",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { Store.updateAfterNote(note.trim()) }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
        }

        // ===== 4. Платонус =====
        item {
            Column(Modifier.fillMaxWidth()) {
                SectionHeader("Платонус")
                Text(
                    "Расписание и оценки из журнала; данные хранятся только на телефоне.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                PlatonusCard()
            }
        }

        // ===== 5. Чаты =====
        item {
            Column(Modifier.fillMaxWidth()) {
                SectionHeader("Чаты")
                Text(
                    "Домашка из WhatsApp и Telegram — из уведомлений или экспорта.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                var chatResult by remember { mutableStateOf("") }
                val importChat = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    chatResult = "Читаю переписку…"
                    val text = runCatching {
                        readChatExport(ctx, uri)
                    }.getOrNull()
                    if (text.isNullOrBlank()) {
                        chatResult = "Не получилось прочитать файл"
                    } else {
                        val name = ctx.contentResolver.getType(uri)?.let { "Импорт" } ?: "Импорт"
                        val drafts = runCatching { ChatImport.parse(text, name) }.getOrElse { emptyList() }
                        var added = 0
                        drafts.forEach { if (Store.addChatDraft(it)) added++ }
                        chatResult = if (drafts.isEmpty()) "ДЗ в переписке не нашёл"
                        else "Нашёл ДЗ: ${drafts.size}, добавил новых: $added"
                    }
                }
                ParyCard {
                    Text("Домашка из WhatsApp и Telegram", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Приложение читает уведомления выбранных чатов, находит ДЗ и сроки и предлагает задачи. " +
                            "Тексты остаются на телефоне.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    SwitchRow(
                        "Ловить ДЗ из чатов",
                        "Только выбранные мессенджеры",
                        Store.watchChats,
                    ) { Store.updateWatchChats(it) }

                    val listenerOn = androidx.core.app.NotificationManagerCompat
                        .getEnabledListenerPackages(ctx).contains(ctx.packageName)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (listenerOn) "Доступ к уведомлениям: разрешён"
                        else "Доступ к уведомлениям: выключен",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (listenerOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    if (!listenerOn) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Открыть доступ к уведомлениям →",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                                    )
                                }
                            },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Чаты:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    val apps = listOf(
                        "com.whatsapp" to "WhatsApp",
                        "org.telegram.messenger" to "Telegram",
                        "com.viber.voip" to "Viber",
                        "com.vkontakte.android" to "VK",
                        "com.discord" to "Discord",
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        apps.forEach { (pkg, name) ->
                            val on = pkg in Store.watchedPkgs
                            Text(
                                name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(
                                        if (on) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                    )
                                    .clickable {
                                        Store.updateWatched(
                                            if (on) Store.watchedPkgs - pkg else Store.watchedPkgs + pkg,
                                        )
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Или импортировать экспорт переписки",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                runCatching {
                                    importChat.launch(arrayOf("text/plain", "application/json", "text/html", "*/*"))
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    if (chatResult.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            chatResult,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ===== 6. Telegram =====
        item {
            Column(Modifier.fillMaxWidth()) {
                SectionHeader("Telegram")
                Text(
                    "Свой бот вместо экспорта переписки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                var token by remember { mutableStateOf(Store.telegramToken) }
                var checkMsg by remember { mutableStateOf(Store.telegramStatus) }
                ParyCard {
                    Text("Telegram-бот", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Бот в группе сам присылает сообщения в приложение — без уведомлений и экспорта.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "1. @BotFather → /newbot → скопируй токен\n2. /setprivacy → Disable, иначе бот не видит переписку\n3. Добавь бота в группу",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    ParyTextField(token, { token = it }, "Токен бота")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Проверить",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable {
                                    val t = token.trim()
                                    if (t.isBlank()) {
                                        checkMsg = "Вставь токен"
                                    } else {
                                        checkMsg = "Проверяю…"
                                        scope.launch {
                                            val r = withContext(Dispatchers.IO) {
                                                com.pary.app.data.TelegramApi.getMe(t)
                                            }
                                            checkMsg = r.value?.let { "@$it на связи" } ?: r.error
                                            Store.updateTelegramStatus(checkMsg)
                                            if (r.value != null) Store.updateTelegram(t, Store.telegramAuto)
                                        }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                        Text(
                            "@BotFather",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    runCatching {
                                        ctx.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                android.net.Uri.parse("https://t.me/BotFather"),
                                            ),
                                        )
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    SwitchRow(
                        "Забирать сообщения автоматически",
                        "Каждые 15 минут в фоне",
                        Store.telegramAuto,
                    ) {
                        Store.updateTelegram(token.ifBlank { Store.telegramToken }, it)
                        Sync.refresh(ctx)
                        if (it) {
                            scope.launch {
                                withContext(Dispatchers.IO) { Sync.pollTelegram(ctx) }
                                checkMsg = Store.telegramStatus
                            }
                        }
                    }
                    if (checkMsg.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            checkMsg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ===== 7. Данные =====
        item {
            Column(Modifier.fillMaxWidth()) {
                SectionHeader("Данные")
                Text(
                    "Резервная копия на телефоне и действия, которые заменяют данные.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                ParyCard {
                    Text("Резервная копия", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Расписание, задачи, оценки и отметки сохраняются только на телефоне. Можно отправить копию себе.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Поделиться копией (JSON) →",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Пары — резервная копия")
                                putExtra(Intent.EXTRA_TEXT, Store.exportJson())
                            }
                            runCatching {
                                ctx.startActivity(Intent.createChooser(send, "Куда отправить копию"))
                            }
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
                // Опасные действия отделены: красная карточка и error-цвет у кнопок.
                ParyCard(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        "Опасные действия",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Загрузка копии заменит задачи, оценки и отметки. Сброс удалит все данные приложения.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Загрузить копию из файла →",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                            .clickable {
                                runCatching {
                                    importLauncher.launch(
                                        arrayOf("application/json", "text/plain", "*/*"),
                                    )
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    if (Build.VERSION.SDK_INT >= 33) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Сбросить все данные приложения →",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                                .clickable {
                                    runCatching {
                                        ctx.startActivity(
                                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                .setData(android.net.Uri.parse("package:${ctx.packageName}")),
                                        )
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        // ===== 8. Прочее =====
        item {
            Column(Modifier.fillMaxWidth()) {
                SectionHeader("Прочее")
                Text(
                    "Версия приложения.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                ParyCard {
                    Text("О приложении", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    val version = runCatching {
                        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
                    }.getOrDefault("?")
                    Text(
                        "Пары · версия $version\nСделано для учёбы: расписание, напоминания и автоматизация.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (editProfile) {
        ProfileDialog(
            name = Store.profileName,
            group = Store.profileGroup,
            onDismiss = { editProfile = false },
            onSave = { n, g -> Store.setProfile(n, g); editProfile = false },
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Stepper(label: String, value: String, minus: () -> Unit, plus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        StepperButton("−", minus)
        Spacer(Modifier.width(8.dp))
        StepperButton("+", plus)
    }
}

@Composable
private fun StepperButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ProfileDialog(name: String, group: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var n by remember { mutableStateOf(name) }
    var g by remember { mutableStateOf(group) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text("Профиль", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                ParyTextField(n, { n = it }, "Имя")
                Spacer(Modifier.height(10.dp))
                ParyTextField(g, { g = it }, "Группа")
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Сохранить",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                            .clickable { onSave(n.trim(), g.trim()) }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatonusCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(Store.platonusUrl) }
    var login by remember { mutableStateOf(Store.platonusLogin) }
    var pass by remember { mutableStateOf(Store.platonusPassword) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf(Store.platonusStatus) }
    var diag by remember { mutableStateOf("") }
    var showDiag by remember { mutableStateOf(false) }
    val last = Store.platonusLastSync
    val imported = Store.importedCount()

    ParyCard {
        Text("Вход в журнал", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Вход в вузовский Платонус, чтобы подтянуть расписание. Логин и пароль хранятся только на телефоне; API неофициальный, поэтому формат может отличаться.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        ParyTextField(url, { url = it }, "Адрес Платонуса")
        Spacer(Modifier.height(6.dp))
        ParyTextField(login, { login = it }, "Логин")
        Spacer(Modifier.height(8.dp))
        ParyTextField(pass, { pass = it }, "Пароль", password = true)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (busy) "Синхронизирую…" else "Синхронизировать всё",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (busy) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                    )
                    .clickable(enabled = !busy) {
                        Store.updatePlatonus(url, login, pass)
                        busy = true
                        result = "Синхронизирую профиль, журнал, задания и расписание…"
                        scope.launch {
                            val out = withContext(Dispatchers.IO) { PlatonusFullSync.fetch(url, login, pass) }
                            result = out.message
                            Store.updatePlatonusStatus(out.message)
                            out.lessons?.let { Store.replaceImported(it) }
                            out.subjects?.let { Store.updateJournal(it) }
                            out.tasks?.let { Store.replacePlatonusTasks(it) }
                            out.attendance?.let { Store.replacePlatonusAttendance(it) }
                            if (out.lessons != null || out.subjects != null || out.tasks != null || out.attendance != null || out.gpa.isNotBlank()) {
                                Store.updatePlatonusProfile(out.gpa, out.name, out.group)
                                Store.updatePlatonusLastSync(System.currentTimeMillis())
                                PlatonusSyncScheduler.schedule(ctx)
                                Sync.refresh(ctx)
                            }
                            busy = false
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Диагностика",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(enabled = !busy) {
                        Store.updatePlatonus(url, login, pass)
                        busy = true
                        result = "Собираю диагностику…"
                        scope.launch {
                            diag = withContext(Dispatchers.IO) {
                                runCatching { Platonus(url, login, pass).diagnose() }
                                    .getOrElse { "Ошибка: ${it.message}" }
                            }
                            result = "Диагностика готова — можно показать мне"
                            busy = false
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Оценки из журнала",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(enabled = !busy) {
                    Store.updatePlatonus(url, login, pass)
                    busy = true
                    result = "Загружаю журнал…"
                    scope.launch {
                        val out = withContext(Dispatchers.IO) { PlatonusGrades.fetch(url, login, pass) }
                        result = out.message
                        Store.updatePlatonusStatus(out.message)
                        if (out.subjects != null) {
                            Store.updateJournal(out.subjects)
                            Store.updatePlatonusLastSync(System.currentTimeMillis())
                        }
                        busy = false
                    }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        if (result.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(result, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        if (imported > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Из Платонуса загружено пар: $imported" +
                    if (last > 0) {
                        " · обновлено " + runCatching {
                            java.time.Instant.ofEpochMilli(last)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"))
                        }.getOrDefault("")
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Длинный текст диагностики свёрнут: по умолчанию его не видно.
        if (diag.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { showDiag = !showDiag }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    if (showDiag) "Скрыть диагностику" else "Показать диагностику",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (showDiag) "▴" else "▾",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (showDiag) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        diag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
