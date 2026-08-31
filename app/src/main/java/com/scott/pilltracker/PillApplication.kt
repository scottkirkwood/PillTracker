package com.scott.pilltracker

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.scott.pilltracker.alarm.AlarmScheduler
import com.scott.pilltracker.alarm.NotificationHelper
import com.scott.pilltracker.data.PillRepository
import com.scott.pilltracker.sync.SyncWorker
import java.util.concurrent.TimeUnit

class PillApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Create Notification Channels
        NotificationHelper(this)

        // 2. Initialize repository and schedule routines
        val repository = PillRepository.getInstance(this)
        val scheduler = AlarmScheduler(this)
        scheduler.scheduleAllDailyRoutines(repository.configFlow.value.routines)

        // 3. Setup periodic background sync (every 6 hours when network available)
        val syncConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicSyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(syncConstraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PillPeriodicSync",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSyncRequest
        )
    }
}
