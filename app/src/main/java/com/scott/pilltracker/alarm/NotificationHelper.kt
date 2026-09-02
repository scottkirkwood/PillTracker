package com.scott.pilltracker.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.scott.pilltracker.MainActivity
import com.scott.pilltracker.R
import com.scott.pilltracker.model.PillItem

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Delete deprecated alarm channel if present to prevent caching silent settings
            try {
                notificationManager.deleteNotificationChannel("pill_channel_alarm")
            } catch (_: Exception) {}

            // 1. Quiet Channel (Silent, heads-up, no sound/vibration)
            val quietChannel = NotificationChannel(
                CHANNEL_QUIET_ID,
                context.getString(R.string.channel_quiet_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_quiet_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
            }

            // 2. Escalated Alarm Channel (Loud alarm ringtone, strong vibration, high priority)
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM_ID,
                context.getString(R.string.channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_alarm_desc)
                setSound(alarmSound, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 300, 800, 300, 800)
                setShowBadge(true)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(quietChannel)
            notificationManager.createNotificationChannel(alarmChannel)
        }
    }

    // Show Stage 1 Quiet Reminder
    fun showQuietNotification(routine: String, title: String, message: String, activeItems: List<PillItem>) {
        val notifId = getNotificationIdForRoutine(routine)

        // Open App Intent
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTINE, routine)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Done
        val doneIntent = Intent(context, PillActionReceiver::class.java).apply {
            action = ACTION_TAKE_ROUTINE
            putExtra(EXTRA_ROUTINE, routine)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 1,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Remind Later (Snooze 1 hour)
        val snoozeIntent = Intent(context, PillActionReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_ROUTINE, routine)
            putExtra(EXTRA_SNOOZE_MINUTES, 60)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText("${activeItems.size} items scheduled")

        activeItems.take(6).forEach { item ->
            val prefix = if (item.isPrescription) "🚨 Rx: " else "• "
            inboxStyle.addLine("$prefix${item.name} (${item.dosage})")
        }
        if (activeItems.size > 6) {
            inboxStyle.addLine("+ ${activeItems.size - 6} more supplements...")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_QUIET_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(inboxStyle)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(android.R.drawable.checkbox_on_background, "Done", donePendingIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, "Remind in 1h", snoozePendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted
        }
    }

    // Show Stage 2 Escalated Audible Alarm
    fun showEscalatedAlarm(routine: String, title: String, message: String, activeItems: List<PillItem>) {
        val notifId = getNotificationIdForRoutine(routine)

        // 1. Cancel previous quiet notification with same ID first so Android does not preserve CHANNEL_QUIET_ID!
        notificationManager.cancel(notifId)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTINE, routine)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Done
        val doneIntent = Intent(context, PillActionReceiver::class.java).apply {
            action = ACTION_TAKE_ROUTINE
            putExtra(EXTRA_ROUTINE, routine)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 1,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Snooze 30 mins
        val snoozeIntent = Intent(context, PillActionReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_ROUTINE, routine)
            putExtra(EXTRA_SNOOZE_MINUTES, 30)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ALARM_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ $title (Missed Reminder)")
            .setContentText(message)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(contentPendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(alarmSound, android.media.AudioManager.STREAM_ALARM)
            .setVibrate(longArrayOf(0, 800, 300, 800, 300, 800))
            .addAction(android.R.drawable.checkbox_on_background, "Done", donePendingIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, "Snooze 30m", snoozePendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (e: SecurityException) {
            // Security exception if permission missing
        }

        // 2. Play audible alarm ringtone on USAGE_ALARM stream
        AlarmRingtonePlayer.play(context)
    }

    fun cancelNotification(routine: String) {
        val notifId = getNotificationIdForRoutine(routine)
        notificationManager.cancel(notifId)
        AlarmRingtonePlayer.stop()
    }

    companion object {
        const val CHANNEL_QUIET_ID = "pill_channel_quiet"
        const val CHANNEL_ALARM_ID = "pill_channel_alarm_v2"

        const val ACTION_TAKE_ROUTINE = "com.scott.pilltracker.ACTION_TAKE_ROUTINE"
        const val ACTION_SNOOZE = "com.scott.pilltracker.ACTION_SNOOZE"

        const val EXTRA_ROUTINE = "extra_routine"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"

        fun getNotificationIdForRoutine(routine: String): Int {
            return when (routine.lowercase()) {
                "morning" -> 1001
                "evening" -> 1002
                else -> 1003
            }
        }
    }
}
