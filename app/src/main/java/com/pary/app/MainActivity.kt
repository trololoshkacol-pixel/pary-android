package com.pary.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.pary.app.data.ChatImport
import com.pary.app.data.Store
import com.pary.app.data.PlatonusSyncScheduler
import com.pary.app.ui.ParyTheme
import com.pary.app.notify.Sync
import com.pary.app.ui.ParyApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        PlatonusSyncScheduler.schedule(this)
        Store.onChanged = { Sync.refresh(this) }
        val importedSharedText = importSharedText(intent)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        setContent {
            ParyTheme(Store.theme, Store.accent, Store.animations) {
                val view = LocalView.current
                LaunchedEffect(Store.theme) {
                    val w = (view.context as? ComponentActivity)?.window ?: return@LaunchedEffect
                    WindowCompat.getInsetsController(w, view).apply {
                        isAppearanceLightStatusBars = Store.theme == "light"
                        isAppearanceLightNavigationBars = Store.theme == "light"
                    }
                }
                ParyApp(initialTab = if (importedSharedText) 2 else 0)
            }
        }
    }

    private fun importSharedText(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_SEND) return false
        val shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (shared.isBlank()) return false
        val source = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            ?.takeIf { it.isNotBlank() }
            ?: "Импорт из другого приложения"
        return ChatImport.parseShared(shared, source)
            .map { Store.addChatDraft(it) }
            .any { it }
    }

    override fun onResume() {
        super.onResume()
        Sync.refresh(this)
    }
}
