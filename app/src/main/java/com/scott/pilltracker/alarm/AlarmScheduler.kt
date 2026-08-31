package com.scott.pilltracker.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.scott.pilltracker.model.PillRoutineConfig
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // Schedule exact daily alarm for morning and evening routines
    fun scheduleAllDailyRoutines(routines: Map<String, PillRoutineConfig>) {
        routines.forEach { (routineKey, routineConfig) ->
            if (routineConfig.time.isNotBlank()) {
                scheduleDailyRoutine(routineKey, routineConfig.time)
            }
        }
    }

    // Schedule Stage 1 (Quiet heads-up notification)
    fun scheduleDailyRoutine(routine: String, timeString: String) {
        val parts = timeString.split(":")
        if (parts.size != 2) return

        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If time already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val requestCode = getRequestCode(routine, STAGE_QUIET)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ROUTINE, routine)
            putExtra(EXTRA_STAGE, STAGE_QUIET)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled Stage 1 quiet alarm for $routine at ${calendar.time}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot schedule exact alarm: ${e.message}")
        }
    }

    // Schedule Stage 2 Escalation (Audible alarm after N minutes)
    fun scheduleEscalationAlarm(routine: String, delayMinutes: Int) {
        val triggerTime = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)
        val requestCode = getRequestCode(routine, STAGE_ESCALATE)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ROUTINE, routine)
            putExtra(EXTRA_STAGE, STAGE_ESCALATE)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled Stage 2 escalation alarm for $routine in $delayMinutes mins")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot schedule escalation alarm: ${e.message}")
        }
    }

    // Cancel Stage 2 Escalation (Called when pills are taken)
    fun cancelEscalationAlarm(routine: String) {
        val requestCode = getRequestCode(routine, STAGE_ESCALATE)
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled Stage 2 escalation alarm for $routine")
        }
    }

    // Snooze routine
    fun snooze(routine: String, minutes: Int) {
        cancelEscalationAlarm(routine)
        scheduleEscalationAlarm(routine, minutes)
    }

    private fun getRequestCode(routine: String, stage: Int): Int {
        val base = when (routine.lowercase()) {
            "morning" -> 2000
            "evening" -> 3000
            else -> 4000
        }
        return base + stage
    }

    companion object {
        private const val TAG = "AlarmScheduler"

        const val EXTRA_ROUTINE = "extra_routine"
        const val EXTRA_STAGE = "extra_stage"

        const val STAGE_QUIET = 1
        const val STAGE_ESCALATE = 2
    }
}
