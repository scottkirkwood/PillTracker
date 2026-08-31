package com.scott.pilltracker.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.scott.pilltracker.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class PillRepository(private val context: Context) {

    private val gson = Gson()
    private val prefs: SharedPreferences = context.getSharedPreferences("pill_prefs", Context.MODE_PRIVATE)

    private val configFile = File(context.filesDir, "cached_config.json")
    private val logsFile = File(context.filesDir, "pill_logs.json")

    private val _configFlow = MutableStateFlow(loadLocalConfig())
    val configFlow: StateFlow<PillsConfig> = _configFlow.asStateFlow()

    private val _logsFlow = MutableStateFlow(loadLocalLogs())
    val logsFlow: StateFlow<List<PillLog>> = _logsFlow.asStateFlow()

    private val _syncStateFlow = MutableStateFlow(computeSyncState())
    val syncStateFlow: StateFlow<SyncState> = _syncStateFlow.asStateFlow()

    var baseUrl: String
        get() = prefs.getString("server_url", "https://forusers.com") ?: "https://forusers.com"
        set(value) = prefs.edit().putString("server_url", value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong("last_sync_timestamp", 0L)
        private set(value) = prefs.edit().putLong("last_sync_timestamp", value).apply()

    init {
        _syncStateFlow.value = computeSyncState()
    }

    // Default Seed Dataset
    fun getDefaultSeedConfig(): PillsConfig {
        return PillsConfig(
            version = 1,
            updatedAt = "2026-08-31T00:00:00Z",
            syncIntervalDays = 7,
            routines = mapOf(
                "morning" to PillRoutineConfig(
                    time = "07:30",
                    label = "Morning Stack",
                    quietRemind = true,
                    escalateAlarm = true,
                    escalateAfterMinutes = 60
                ),
                "evening" to PillRoutineConfig(
                    time = "20:00",
                    label = "Evening Stack",
                    quietRemind = true,
                    escalateAlarm = true,
                    escalateAfterMinutes = 60
                ),
                "adhoc" to PillRoutineConfig(
                    time = "",
                    label = "Quick / Ad-hoc Logging",
                    quietRemind = false,
                    escalateAlarm = false,
                    escalateAfterMinutes = 0
                )
            ),
            items = listOf(
                // Morning (around 7:30 am)
                PillItem("candesartan", "Candesartan", "8mg", "morning", "prescription", "Doctor's prescription - morning BP", active = true, sortOrder = 1, requiresAlarm = true),
                PillItem("curcumin", "Curcumin", "300mg", "morning", "supplement", "Better with fat / meal", active = true, sortOrder = 2),
                PillItem("k2", "K2", "120mcg", "morning", "supplement", "Fat-soluble", active = true, sortOrder = 3),
                PillItem("vitamin_c", "Vitamin C", "600mg", "morning", "supplement", "", active = true, sortOrder = 4),
                PillItem("zinc", "Zinc", "2mg", "morning", "supplement", "Take with food", active = true, sortOrder = 5),
                PillItem("d3", "D3", "1000IU", "morning", "supplement", "Fat-soluble", active = true, sortOrder = 6),
                PillItem("berberine", "Berberine", "500mg", "morning", "supplement", "Take before/with meal", active = true, sortOrder = 7),
                PillItem("omega3", "Omega 3 Fish Oil", "2x1g", "morning", "supplement", "Take with meal", active = true, sortOrder = 8),
                PillItem("turmeric", "Turmeric", "500mg", "morning", "supplement", "", active = true, sortOrder = 9),
                PillItem("nmn", "NMN", "500mg", "morning", "supplement", "Morning energy", active = true, sortOrder = 10),

                // Evening (around 8:00 pm)
                PillItem("rosuvastatin", "Rosuvastatin", "10mg", "evening", "prescription", "Doctor's prescription - evening cholesterol", active = true, sortOrder = 11, requiresAlarm = true),
                PillItem("coq10", "Coenzyme Q10", "200mg", "evening", "supplement", "Better with fat / meal", active = true, sortOrder = 12),
                PillItem("magnesium", "Magnesium Bisglycinate", "2x200mg", "evening", "supplement", "Pre-bed relaxation", active = true, sortOrder = 13),
                PillItem("melatonin", "Melatonin", "2mg", "evening", "supplement", "Timed release (30-60m before bed)", active = true, sortOrder = 14),

                // Ad-hoc items
                PillItem("creatine", "Creatine", "5g", "adhoc", "optional", "Add to morning coffee", active = true, sortOrder = 15),
                PillItem("glycine", "Glycine", "1500mg", "adhoc", "optional", "Add to evening tea", active = true, sortOrder = 16),
                PillItem("dog_pill", "Dog Pill", "1 dose", "adhoc", "optional", "Give pill to dog", active = true, sortOrder = 17)
            )
        )
    }

    private fun loadLocalConfig(): PillsConfig {
        if (!configFile.exists()) {
            val seed = getDefaultSeedConfig()
            saveLocalConfig(seed)
            return seed
        }
        return try {
            val json = configFile.readText()
            gson.fromJson(json, PillsConfig::class.java) ?: getDefaultSeedConfig()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached config: ${e.message}")
            getDefaultSeedConfig()
        }
    }

    private fun saveLocalConfig(config: PillsConfig) {
        try {
            configFile.writeText(gson.toJson(config))
            _configFlow.value = config
        } catch (e: Exception) {
            Log.e(TAG, "Error saving local config: ${e.message}")
        }
    }

    private fun loadLocalLogs(): List<PillLog> {
        if (!logsFile.exists()) return emptyList()
        return try {
            val json = logsFile.readText()
            val type = object : TypeToken<List<PillLog>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading pill logs: ${e.message}")
            emptyList()
        }
    }

    private fun saveLocalLogs(logs: List<PillLog>) {
        try {
            logsFile.writeText(gson.toJson(logs))
            _logsFlow.value = logs
            _syncStateFlow.value = computeSyncState()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving pill logs: ${e.message}")
        }
    }

    // 7-day Offline Check
    fun isCacheStale(): Boolean {
        if (lastSyncTimestamp == 0L) return false
        val maxAllowedMillis = TimeUnit.DAYS.toMillis(_configFlow.value.syncIntervalDays.toLong().coerceAtLeast(1))
        val elapsed = System.currentTimeMillis() - lastSyncTimestamp
        return elapsed > maxAllowedMillis
    }

    fun computeSyncState(): SyncState {
        val unsyncedCount = _logsFlow.value.count { !it.isSynced }
        return when {
            isCacheStale() -> SyncState.STALE_CACHE_WARNING
            unsyncedCount > 0 -> SyncState.PENDING_UPLOAD
            lastSyncTimestamp > 0 -> SyncState.SYNCED
            else -> SyncState.OFFLINE_CACHE
        }
    }

    // Check if morning/evening routine was logged today
    fun isRoutineTakenToday(routine: String): Boolean {
        val todayPrefix = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return _logsFlow.value.any { it.routine.equals(routine, ignoreCase = true) && it.timestamp.startsWith(todayPrefix) }
    }

    // Log routine intake (Android is source of truth)
    suspend fun logRoutineTaken(
        routine: String,
        itemsTaken: List<String>,
        itemsSkipped: List<String> = emptyList(),
        notes: String = ""
    ): PillLog = withContext(Dispatchers.IO) {
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val logId = "log_${System.currentTimeMillis()}_${routine}"
        val newLog = PillLog(
            id = logId,
            timestamp = nowIso,
            loggedAt = nowIso,
            routine = routine,
            itemsTaken = itemsTaken,
            itemsSkipped = itemsSkipped,
            notes = notes,
            deviceId = "android_${android.os.Build.MODEL}",
            isSynced = false
        )

        val updatedLogs = listOf(newLog) + _logsFlow.value
        saveLocalLogs(updatedLogs)
        newLog
    }

    // Log ad-hoc item intake (Creatine, Glycine, Dog Pill)
    suspend fun logAdhocTaken(itemId: String, notes: String = ""): PillLog = withContext(Dispatchers.IO) {
        logRoutineTaken(
            routine = "adhoc",
            itemsTaken = listOf(itemId),
            itemsSkipped = emptyList(),
            notes = notes.ifBlank { "Quick logged $itemId" }
        )
    }

    // Toggle item stock / active status
    suspend fun toggleItemStock(itemId: String, active: Boolean) = withContext(Dispatchers.IO) {
        val current = _configFlow.value
        val updatedItems = current.items.map {
            if (it.id == itemId) it.copy(active = active) else it
        }
        saveLocalConfig(current.copy(items = updatedItems))
    }

    // Full Cloud Sync (Source of Truth config fetch + logs push)
    suspend fun syncWithCloud(): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Push unsynced logs to forusers.com
            val unsyncedLogs = _logsFlow.value.filter { !it.isSynced }
            if (unsyncedLogs.isNotEmpty()) {
                val postUrl = URL("$baseUrl/api/pills/logs")
                val conn = (postUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                val payload = mapOf("logs" to unsyncedLogs)
                OutputStreamWriter(conn.outputStream).use { it.write(gson.toJson(payload)) }

                if (conn.responseCode in 200..299) {
                    // Mark logs as synced locally
                    val updated = _logsFlow.value.map { log ->
                        if (unsyncedLogs.any { it.id == log.id }) log.copy().apply { isSynced = true } else log
                    }
                    saveLocalLogs(updated)
                } else {
                    Log.w(TAG, "Failed pushing logs, HTTP ${conn.responseCode}")
                }
                conn.disconnect()
            }

            // 2. Fetch latest Config from forusers.com (Source of Truth)
            val getUrl = URL("$baseUrl/api/pills/config")
            val getConn = (getUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }

            if (getConn.responseCode in 200..299) {
                val cloudConfig = InputStreamReader(getConn.inputStream).use {
                    gson.fromJson(it, PillsConfig::class.java)
                }
                if (cloudConfig != null) {
                    saveLocalConfig(cloudConfig)
                    lastSyncTimestamp = System.currentTimeMillis()
                    _syncStateFlow.value = computeSyncState()
                    return@withContext Result.success("Synced successfully (v${cloudConfig.version})")
                }
            }
            getConn.disconnect()

            lastSyncTimestamp = System.currentTimeMillis()
            _syncStateFlow.value = computeSyncState()
            Result.success("Synced logs successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}")
            _syncStateFlow.value = computeSyncState()
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "PillRepository"

        @Volatile
        private var instance: PillRepository? = null

        fun getInstance(context: Context): PillRepository {
            return instance ?: synchronized(this) {
                instance ?: PillRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
