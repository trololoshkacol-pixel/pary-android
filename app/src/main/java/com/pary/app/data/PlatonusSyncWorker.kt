package com.pary.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.pary.app.notify.Sync
import java.util.concurrent.TimeUnit

class PlatonusSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        Store.init(applicationContext)
        val url = Store.platonusUrl
        val login = Store.platonusLogin
        val password = Store.platonusPassword
        if (login.isBlank() || password.isBlank()) return Result.success()
        val out = runCatching { PlatonusFullSync.fetch(url, login, password) }
            .getOrElse { return Result.retry() }
        if (out.lessons == null && out.subjects == null && out.tasks == null && out.attendance == null && out.gpa.isBlank()) {
            Store.updatePlatonusStatus(out.message)
            return Result.retry()
        }
        out.lessons?.let { Store.replaceImported(it) }
        out.subjects?.let { Store.updateJournal(it) }
        out.tasks?.let { Store.replacePlatonusTasks(it) }
        out.attendance?.let { Store.replacePlatonusAttendance(it) }
        Store.updatePlatonusProfile(out.gpa, out.name, out.group)
        Store.updatePlatonusStatus(out.message)
        Store.updatePlatonusLastSync(System.currentTimeMillis())
        Sync.refresh(applicationContext)
        return Result.success()
    }
}

object PlatonusSyncScheduler {
    private const val UNIQUE_NAME = "pary-platonus-sync"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<PlatonusSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
