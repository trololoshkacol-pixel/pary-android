package com.pary.app.notify

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Runs Telegram polling outside the short-lived broadcast receiver lifecycle. */
class TelegramPollWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    override fun doWork(): Result {
        Sync.pollTelegram(applicationContext)
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "pary-telegram-poll"
    }
}
