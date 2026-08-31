package com.scott.pilltracker.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.scott.pilltracker.data.PillRepository
import com.scott.pilltracker.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PillActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val routine = intent.getStringExtra(NotificationHelper.EXTRA_ROUTINE) ?: "morning"
        val repository = PillRepository.getInstance(context)
        val notificationHelper = NotificationHelper(context)
        val scheduler = AlarmScheduler(context)

        when (intent.action) {
            NotificationHelper.ACTION_TAKE_ROUTINE -> {
                Log.d(TAG, "User clicked 'Done' for $routine from notification")

                // Cancel Stage 2 Escalated Alarm and dismiss notification immediately
                scheduler.cancelEscalationAlarm(routine)
                notificationHelper.cancelNotification(routine)

                // Log routine intake asynchronously
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val activeItems = repository.configFlow.value.items
                            .filter { it.routine.equals(routine, ignoreCase = true) && it.active }
                            .map { it.id }

                        repository.logRoutineTaken(
                            routine = routine,
                            itemsTaken = activeItems,
                            itemsSkipped = emptyList(),
                            notes = "Logged via notification action"
                        )

                        // Trigger cloud sync
                        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>().build()
                        WorkManager.getInstance(context).enqueue(syncRequest)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error logging routine: ${e.message}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            NotificationHelper.ACTION_SNOOZE -> {
                val snoozeMinutes = intent.getIntExtra(NotificationHelper.EXTRA_SNOOZE_MINUTES, 60)
                Log.d(TAG, "User clicked 'Remind Later / Snooze' ($snoozeMinutes mins) for $routine")

                notificationHelper.cancelNotification(routine)
                scheduler.snooze(routine, snoozeMinutes)
            }
        }
    }

    companion object {
        private const val TAG = "PillActionReceiver"
    }
}
