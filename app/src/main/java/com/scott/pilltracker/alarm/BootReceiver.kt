package com.scott.pilltracker.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.scott.pilltracker.data.PillRepository

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d(TAG, "Device booted or package updated. Rescheduling all pill routines...")

            val repository = PillRepository.getInstance(context)
            val scheduler = AlarmScheduler(context)
            val config = repository.configFlow.value

            scheduler.scheduleAllDailyRoutines(config.routines)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
