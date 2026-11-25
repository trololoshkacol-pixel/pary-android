package com.pary.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pary.app.data.Store
import com.pary.app.ui.ParyTextField

/** Быстрый старт без обязательного подключения внешних учебных систем. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(Store.profileName) }
    var group by rememberSaveable { mutableStateOf(Store.profileGroup) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text("Pary", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("Твоя учёба — под контролем", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Планируй пары, собирай домашние задания из чатов, следи за сроками и учись без хаоса. " +
                "Профиль нужен только для приветствия и персональных подписей.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        ParyTextField(name, { name = it }, "Как тебя называть")
        ParyTextField(group, { group = it }, "Группа")
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                Store.setProfile(name, group)
                onDone()
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Начать пользоваться")
        }
        TextButton(
            onClick = {
                if (name.isNotBlank()) Store.setProfile(name, group)
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Пропустить")
        }
        Text(
            "Подключение Platonus необязательно — его можно настроить позже в разделе «Ещё».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
