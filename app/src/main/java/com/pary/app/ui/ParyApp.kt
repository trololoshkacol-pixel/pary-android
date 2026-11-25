package com.pary.app.ui

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pary.app.data.Store
import com.pary.app.ui.screens.FocusScreen
import com.pary.app.ui.screens.OnboardingScreen
import com.pary.app.ui.screens.ScheduleScreen
import com.pary.app.ui.screens.SettingsScreen
import com.pary.app.ui.screens.StudyScreen
import com.pary.app.ui.screens.TasksScreen
import com.pary.app.ui.screens.TodayScreen

private data class TabItem(val label: String, val icon: ImageVector)
private const val ONBOARD_PREFS = "pary_onboard"

@Composable
fun ParyApp(initialTab: Int = 0) {
    val ctx = LocalContext.current
    var onboarded by remember {
        val prefs = ctx.getSharedPreferences(ONBOARD_PREFS, Context.MODE_PRIVATE)
        mutableStateOf(
            prefs.getBoolean("done", false) ||
                Store.profileName.isNotBlank() ||
                Store.platonusLogin.isNotBlank(),
        )
    }
    if (onboarded) MainShell(
        initialTab = if (initialTab == 2) 4 else initialTab,
        initialMoreSection = if (initialTab == 2) 1 else 0,
    ) else OnboardingScreen {
        ctx.getSharedPreferences(ONBOARD_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("done", true).apply()
        onboarded = true
    }
}

/** Compact Swiss navigation: five academic destinations, with secondary tools grouped under More. */
@Composable
private fun ParyDock(tabs: List<TabItem>, selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.16f))
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f), shape)
            .clip(shape)
            .padding(horizontal = 7.dp, vertical = 7.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEachIndexed { index, item ->
                val active = selected == index
                val scale by animateFloatAsState(
                    targetValue = if (active) 1f else 0.97f,
                    animationSpec = tween(160),
                    label = "swiss_dock_scale",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .scale(scale)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            role = androidx.compose.ui.semantics.Role.Button,
                            onClickLabel = "Открыть раздел ${item.label}",
                        ) { onSelect(index) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 42.dp, height = 29.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.11f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreWorkspace(
    modifier: Modifier = Modifier,
    section: Int,
    onSectionChange: (Int) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "ЕЩЁ В PARY",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                color = MaterialTheme.colorScheme.primary,
            )
            ChoiceRow(listOf("Профиль", "Задачи", "Фокус"), section, onSelect = onSectionChange)
        }
        Box(Modifier.weight(1f)) {
            when (section) {
                0 -> SettingsScreen(Modifier.fillMaxSize())
                1 -> TasksScreen(Modifier.fillMaxSize())
                else -> FocusScreen(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun MainShell(initialTab: Int, initialMoreSection: Int = 0) {
    var tab by rememberSaveable { mutableStateOf(initialTab.coerceIn(0, 4)) }
    var moreSection by rememberSaveable { mutableStateOf(initialMoreSection.coerceIn(0, 2)) }
    val tabStateHolder = rememberSaveableStateHolder()
    val tabs = listOf(
        TabItem("Главная", Icons.Filled.Home),
        TabItem("Расписание", Icons.Filled.DateRange),
        TabItem("Оценки", Icons.Filled.Star),
        TabItem("Посещения", Icons.Filled.CheckCircle),
        TabItem("Ещё", Icons.Filled.Settings),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { ParyDock(tabs = tabs, selected = tab, onSelect = { tab = it }) },
    ) { pad ->
        Crossfade(
            targetState = tab,
            animationSpec = tween(if (Store.animations) 210 else 0),
            label = "swiss_screen_transition",
        ) { t ->
            tabStateHolder.SaveableStateProvider(t) {
                val contentModifier = Modifier.fillMaxSize().padding(pad)
                when (t) {
                    0 -> TodayScreen(
                        contentModifier,
                        onOpenTasks = { moreSection = 1; tab = 4 },
                        onNavigate = { requested ->
                            when (requested) {
                                1 -> tab = 1
                                2 -> { moreSection = 1; tab = 4 }
                                4 -> { moreSection = 2; tab = 4 }
                                else -> tab = requested.coerceIn(0, 4)
                            }
                        },
                    )
                    1 -> ScheduleScreen(contentModifier)
                    2 -> StudyScreen(contentModifier, initialSection = 0)
                    3 -> StudyScreen(contentModifier, initialSection = 2)
                    else -> MoreWorkspace(
                        modifier = contentModifier,
                        section = moreSection,
                        onSectionChange = { moreSection = it },
                    )
                }
            }
        }
    }
}

@Composable
fun Placeholder(title: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
    }
}
