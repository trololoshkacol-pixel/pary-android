package com.pary.app.notify

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pary.app.data.Store
import java.time.LocalDate

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Sync.syncAll(ctx)
    }
}

class WidgetProvider : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Sync.refresh(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        Sync.refresh(ctx)
    }
}

class TaskReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra("taskId", -1L)
        if (id > 0) Sync.notifyTask(ctx, id)
    }
}

class PollReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        val request = androidx.work.OneTimeWorkRequestBuilder<TelegramPollWorker>()
            .setConstraints(constraints)
            .build()
        androidx.work.WorkManager.getInstance(ctx.applicationContext).enqueueUniqueWork(
            TelegramPollWorker.UNIQUE_WORK_NAME,
            androidx.work.ExistingWorkPolicy.KEEP,
            request,
        )
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Sync.notifyAlarm(ctx, intent.action == "pary.TEST")
    }
}

class MarkReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        when (intent.action) {
            "pary.MARK_P", "pary.MARK_M" -> {
                val id = intent.getLongExtra("lessonId", -1L)
                val date = intent.getStringExtra("date")
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
                val lesson = Store.lessons.firstOrNull { it.id == id } ?: return
                Store.mark(lesson, date, intent.action == "pary.MARK_P")
                Sync.refresh(ctx)
            }

            "pary.TASK_DONE" -> {
                val id = intent.getLongExtra("taskId", -1L)
                Store.tasks.firstOrNull { it.id == id }?.let { Store.updateTask(it.copy(done = true)) }
                Sync.cancelTaskNotification(ctx, id)
                Sync.rescheduleTasks(ctx)
                Sync.refresh(ctx)
            }

            "pary.TASK_SNOOZE" -> {
                val id = intent.getLongExtra("taskId", -1L)
                Sync.cancelTaskNotification(ctx, id)
                if (id > 0) Sync.snoozeTask(ctx, id)
            }

            "pary.DRAFT_ADD" -> {
                val id = intent.getLongExtra("draftId", -1L)
                if (id > 0) {
                    Store.acceptDraft(id)
                    Sync.cancelDraftNotification(ctx, id)
                    Sync.refresh(ctx)
                }
            }

            "pary.DRAFT_SKIP" -> {
                val id = intent.getLongExtra("draftId", -1L)
                if (id > 0) {
                    Store.dismissDraft(id)
                    Sync.cancelDraftNotification(ctx, id)
                }
            }

            "pary.ALARM_OFF" -> {
                Sync.recordWake(ctx)
                Sync.cancelAlarm(ctx)
            }

            "pary.SNOOZE" -> {
                Sync.cancelAlarm(ctx)
                Sync.setAlarm(ctx, System.currentTimeMillis() + 5 * 60_000, false)
            }
        }
    }
}
