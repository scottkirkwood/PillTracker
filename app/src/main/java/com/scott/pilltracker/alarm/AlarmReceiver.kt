package com.scott.pilltracker.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.scott.pilltracker.data.PillRepository

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val routine = intent.getStringExtra(AlarmScheduler.EXTRA_ROUTINE) ?: "morning"
        val stage = intent.getIntExtra(AlarmScheduler.EXTRA_STAGE, AlarmScheduler.STAGE_QUIET)

        val repository = PillRepository.getInstance(context)
        val notificationHelper = NotificationHelper(context)
        val scheduler = AlarmScheduler(context)

        val config = repository.configFlow.value
        val routineConfig = config.routines[routine]
        val activeItems = config.items.filter { it.routine.equals(routine, ignoreCase = true) && it.active }

        // If routine is already taken today, do nothing and cancel alarms
        if (repository.isRoutineTakenToday(routine)) {
            Log.d(TAG, "Routine $routine already taken today, skipping notification.")
            scheduler.cancelEscalationAlarm(routine)
            notificationHelper.cancelNotification(routine)
            return
        }

        val routineLabel = routineConfig?.label ?: if (routine == "morning") "Morning Stack" else "Evening Stack"

        when (stage) {
            AlarmScheduler.STAGE_QUIET -> {
                Log.d(TAG, "Firing Stage 1 Quiet Notification for $routine")
                notificationHelper.showQuietNotification(
                    routine = routine,
                    title = "Time for your $routineLabel",
                    message = "Tap 'Done' when taken or 'Remind in 1h'",
                    activeItems = activeItems
                )

                // Schedule Stage 2 Escalation (Audible alarm) after configured delay (e.g. 60 min)
                val escalateDelay = routineConfig?.escalateAfterMinutes ?: 60
                if (routineConfig?.escalateAlarm != false) {
                    scheduler.scheduleEscalationAlarm(routine, escalateDelay)
                }

                // Schedule next day's Stage 1 alarm
                if (routineConfig?.time != null) {
                    scheduler.scheduleDailyRoutine(routine, routineConfig.time)
                }
            }

            AlarmScheduler.STAGE_ESCALATE -> {
                Log.d(TAG, "Firing Stage 2 Escalated Alarm for $routine")
                notificationHelper.showEscalatedAlarm(
                    routine = routine,
                    title = "Missed: $routineLabel",
                    message = "Please take your medications now.",
                    activeItems = activeItems
                )
            }
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
    }
}
