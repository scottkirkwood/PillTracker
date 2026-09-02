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
        val repository = com.scott.pilltracker.data.PillRepository.getInstance(context)
        val now = System.currentTimeMillis()

        routines.forEach { (routineKey, routineConfig) ->
            if (routineConfig.time.isNotBlank()) {
                val parts = routineConfig.time.split(":")
                if (parts.size == 2) {
                    val hour = parts[0].toIntOrNull() ?: return@forEach
                    val minute = parts[1].toIntOrNull() ?: return@forEach

                    val todayCal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val isTaken = repository.isRoutineTakenToday(routineKey)
                    val escalateDelay = if (routineConfig.escalateAfterMinutes > 0) routineConfig.escalateAfterMinutes else 60

                    if (!isTaken) {
                        val scheduledTimeToday = todayCal.timeInMillis
                        val escalateTimeToday = scheduledTimeToday + (escalateDelay * 60 * 1000L)

                        if (now < scheduledTimeToday) {
                            // Routine due later today
                            scheduleDailyRoutine(routineKey, routineConfig.time)
                        } else if (now in scheduledTimeToday until escalateTimeToday) {
                            // Within the quiet grace period today; ensure escalation alarm is armed!
                            if (routineConfig.escalateAlarm) {
                                val remainingMinutes = ((escalateTimeToday - now) / 60000L).toInt().coerceAtLeast(1)
                                scheduleEscalationAlarm(routineKey, remainingMinutes)
                            }
                            // Also schedule tomorrow's stage 1
                            scheduleDailyRoutine(routineKey, routineConfig.time)
                        } else if (now >= escalateTimeToday) {
                            // Escalation is OVERDUE today!
                            if (routineConfig.escalateAlarm) {
                                // Trigger escalation alarm immediately
                                val intent = Intent(context, AlarmReceiver::class.java).apply {
                                    putExtra(EXTRA_ROUTINE, routineKey)
                                    putExtra(EXTRA_STAGE, STAGE_ESCALATE)
                                }
                                context.sendBroadcast(intent)
                            }
                            // Also schedule tomorrow's stage 1
                            scheduleDailyRoutine(routineKey, routineConfig.time)
                        }
                    } else {
                        // Already taken today: schedule for tomorrow
                        scheduleDailyRoutine(routineKey, routineConfig.time)
                    }
                }
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

        setExactOrAlarmClock(calendar.timeInMillis, pendingIntent, routine)
        Log.d(TAG, "Scheduled Stage 1 quiet alarm for $routine at ${calendar.time}")
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

        setExactOrAlarmClock(triggerTime, pendingIntent, routine)
        Log.d(TAG, "Scheduled Stage 2 escalation alarm for $routine in $delayMinutes mins (at ${java.util.Date(triggerTime)})")
    }

    // Sets alarm clock or exact alarm with fallback
    private fun setExactOrAlarmClock(triggerTime: Long, pendingIntent: PendingIntent, routine: String) {
        val showIntent = PendingIntent.getActivity(
            context,
            getRequestCode(routine, STAGE_ESCALATE),
            Intent(context, com.scott.pilltracker.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_ROUTINE, routine)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Scheduled setAlarmClock for $routine at ${java.util.Date(triggerTime)}")
            return
        } catch (e: SecurityException) {
            Log.w(TAG, "setAlarmClock security exception: ${e.message}, falling back...")
        }

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
            Log.d(TAG, "Scheduled setExactAndAllowWhileIdle for $routine at ${java.util.Date(triggerTime)}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot schedule exact alarm: ${e.message}, using inexact fallback")
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    // Cancel Stage 2 Escalation (Called when pills are taken)
    fun cancelEscalationAlarm(routine: String) {
        AlarmRingtonePlayer.stop()
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
