package com.scott.pilltracker.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.scott.pilltracker.data.PillRepository

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = PillRepository.getInstance(applicationContext)
        Log.d(TAG, "Starting background Pill sync with forusers.com...")

        return try {
            val syncResult = repository.syncWithCloud()
            if (syncResult.isSuccess) {
                Log.d(TAG, "Pill sync complete: ${syncResult.getOrNull()}")
                Result.success()
            } else {
                Log.w(TAG, "Pill sync failed: ${syncResult.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pill sync exception: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
    }
}
